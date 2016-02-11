/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.template

import java.util.Date

import grizzled.slf4j.Logger

import io.prediction.data.storage.PropertyMap
import org.apache.mahout.math.indexeddataset.IndexedDataset
import org.apache.spark.rdd.RDD
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.joda.time.DateTime
import org.json4s.JsonAST.JArray
import org.json4s._
import org.template.conversions.IndexedDatasetConversions
import org.elasticsearch.spark._
import org.apache.spark.SparkContext


/** Universal Recommender models to save in ES */
class URModel(
    coocurrenceMatrices: Option[List[(String, IndexedDataset)]],
    fieldsRDD: Option[RDD[(String, PropertyMap)]],
    indexName: String,
    dateNames: Option[List[String]] = None,
    nullModel: Boolean = false,
    typeMappings: Option[Map[String, String]] = None, // maps fieldname that need type mapping in Elasticsearch
    propertiesRDD: Option[RDD[collection.Map[String, Any]]] = None)
     {
  @transient lazy val logger = Logger[this.type]

  /** Save all fields to be indexed by Elasticsearch and queried for recs
    * This will is something like a table with row IDs = item IDs and separate fields for all
    * cooccurrence and cross-cooccurrence correlators and metadata for each item. Metadata fields are
    * limited to text term collections so vector types. Scalar values can be used but depend on
    * Elasticsearch's support. One exception is the Data scalar, which is also supported
    * @param params from engine.json, algorithm control params
    * @return always returns true since most other reasons to not save cause exceptions
    */
  def save( params: URAlgorithmParams): Boolean = {

    if (nullModel) throw new IllegalStateException("Saving a null model created from loading an old one.")

    val esIndexURI = s"/${params.indexName}/${params.typeName}"

    // for ES we need to create the entire index in an rdd of maps, one per item so we'll use
    // convert cooccurrence matrices into correlators as RDD[(itemID, (actionName, Seq[itemID])]
    // do they need to be in Elasticsearch format
    logger.info("Converting cooccurrence matrices into correlators")
    val correlators = if (coocurrenceMatrices.nonEmpty) coocurrenceMatrices.get.map { case (actionName, dataset) =>
      dataset.asInstanceOf[IndexedDatasetSpark].toStringMapRDD(actionName).asInstanceOf[RDD[(String, Map[String, Any])]]
      //} else List.empty[RDD[(String, Map[String, Seq[String]])]] // empty mena only calculating PopModel
    } else List.empty[RDD[(String, Map[String, Any])]] // empty mena only calculating PopModel

    // getting action names since they will be ES fields
    logger.info(s"Getting a list of action name strings")
    val allActions = coocurrenceMatrices.getOrElse(List.empty[(String, IndexedDatasetSpark)]).map(_._1)

    logger.info(s"Ready to pass date fields names to closure ${dateNames}")
    val closureDateNames = dateNames.getOrElse(List.empty[String])
    // convert the PropertyMap into Map[String, Seq[String]] for ES
    logger.info("Converting PropertyMap into Elasticsearch style rdd")
    var properties = List.empty[RDD[(String, Map[String, Any])]]
    var allPropKeys = List.empty[String]
    if (fieldsRDD.nonEmpty) {
      properties = List(fieldsRDD.get.map { case (item, pm) =>
        var m: Map[String, Any] = Map()
        for (key <- pm.keySet) {
          val k = key
          val v = pm.get[JValue](key)
          try {
            // if we get something unexpected, add ignore and add nothing to the map
            pm.get[JValue](key) match {
              case JArray(list) => // assumes all lists are string tokens for bias
                val l = list.map {
                  case JString(s) => s
                  case _ => ""
                }
                m = m + (key -> l)
              case JString(s) => // name for this field is in engine params
                if (closureDateNames.contains(key)) {
                  // one of the date fields
                  val dateTime = new DateTime(s)
                  val date: java.util.Date = dateTime.toDate()
                  m = m + (key -> date)
                }
              case JDouble(rank) => // only the ranking double from PopModel should be here
                m = m + (key -> rank)
              case JInt(someInt) => // not sure what this is but pass it on
                m = m + (key -> someInt)
            }
          } catch {
            case e: ClassCastException => e
            case e: IllegalArgumentException => e
            case e: MatchError => e
            //got something we didn't expect so ignore it, put nothing in the map
          }
        }
        (item, m)
      })
      allPropKeys = properties.head.flatMap(_._2.keySet).distinct.collect().toList
    }


    // these need to be indexed with "not_analyzed" and no norms so have to
    // collect all field names before ES index create
    val allFields = (allActions ++ allPropKeys).distinct // shouldn't need distinct but it's fast

    if (propertiesRDD.isEmpty) {
      // Elasticsearch takes a Map with all fields, not a tuple
      logger.info("Grouping all correlators into doc + fields for writing to index")
      logger.info(s"Finding non-empty RDDs from a list of ${correlators.length} correlators and " +
        s"${properties.length} properties")
      val esRDDs: List[RDD[(String, Map[String, Any])]] =
        //(correlators ::: properties).filterNot(c => c.isEmpty())// for some reason way too slow
        (correlators ::: properties)
          //c.take(1).length == 0
      if (esRDDs.nonEmpty) {
        val esFields = groupAll(esRDDs).map { case (item, map) =>
          // todo: every map's items must be checked for value type and converted before writing to ES
          val esMap = map + ("id" -> item)
          esMap
        }
        // create a new index then hot-swap the new index by re-aliasing to it then delete old index
        logger.info("New data to index, performing a hot swap of the index.")
        EsClient.hotSwap(
          params.indexName,
          params.typeName,
          esFields.asInstanceOf[RDD[scala.collection.Map[String,Any]]],
          allFields,
          typeMappings)
      } else logger.warn("No data to write. May have been caused by a failed or stopped `pio train`, " +
        "try running it again")

    } else {
      // this happens when updating only the popularity backfill model but to do a hotSwap we need to dup the
      // entire index

      // create a new index then hot-swap the new index by re-aliasing to it then delete old index
      EsClient.hotSwap(params.indexName, params.typeName, propertiesRDD.get, allFields,
        typeMappings)
    }
    true
  }
  
  def groupAll( fields: Seq[RDD[(String, (Map[String, Any]))]]): RDD[(String, (Map[String, Any]))] = {
    //if (fields.size > 1 && !fields.head.isEmpty() && !fields(1).isEmpty()) {
    if (fields.size > 1) {
      fields.head.cogroup[Map[String, Any]](groupAll(fields.drop(1))).map { case (key, pairMapSeqs) =>
        // to be safe merge all maps but should only be one per rdd element
        val rdd1Maps = pairMapSeqs._1.foldLeft(Map.empty[String, Any])(_ ++ _)
        val rdd2Maps = pairMapSeqs._2.foldLeft(Map.empty[String, Any])(_ ++ _)
        val fullMap = rdd1Maps ++ rdd2Maps
        (key, fullMap)
      }
    } else fields.head
  }

  override def toString = {
    s"URModel in Elasticsearch at index: ${indexName}"
  }


}

object URModel {
  @transient lazy val logger = Logger[this.type]

  /** This is actually only used to read saved values and since they are in Elasticsearch we don't need to read
    * this means we create a null model since it will not be used.
    * todo: we should rejigger the template framework so this is not required.
    * @param id ignored
    * @param params ignored
    * @param sc ignored
    * @return dummy null model
    */
  def apply(id: String, params: URAlgorithmParams, sc: Option[SparkContext]): URModel = {
    // todo: need changes in PIO to remove the need for this
    val urm = new URModel(null, null, null, nullModel =  true)
    logger.info("Created dummy null model")
    urm
  }

}
