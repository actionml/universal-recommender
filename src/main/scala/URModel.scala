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

package com.actionml

import grizzled.slf4j.Logger
import org.apache.predictionio.data.storage.DataMap
import org.apache.mahout.math.indexeddataset.IndexedDataset
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import org.json4s.JsonAST.JArray
import org.json4s._
import com.actionml.helpers.{ IndexedDatasetConversions, ItemID, ItemProps }

/** Universal Recommender models to save in ES */
class URModel(
    coocurrenceMatrices: Seq[(ItemID, IndexedDataset)] = Seq.empty,
    propertiesRDDs: Seq[RDD[(ItemID, ItemProps)]] = Seq.empty,
    typeMappings: Map[String, (String, Boolean)] = Map.empty, // maps fieldname that need type mapping in Elasticsearch
    nullModel: Boolean = false)(implicit sc: SparkContext) {

  @transient lazy val logger: Logger = Logger[this.type]

  /** Save all fields to be indexed by Elasticsearch and queried for recs
   *  This will is something like a table with row IDs = item IDs and separate fields for all
   *  cooccurrence and cross-cooccurrence correlators and metadata for each item. Metadata fields are
   *  limited to text term collections so vector types. Scalar values can be used but depend on
   *  Elasticsearch's support. One exception is the Data scalar, which is also supported
   *  @return always returns true since most other reasons to not save cause exceptions
   */
  def save(dateNames: Seq[String], esIndex: String, esType: String, numESWriteConnections: Option[Int] = None): Boolean = {

    logger.trace(s"Start save model")

    if (nullModel) throw new IllegalStateException("Saving a null model created from loading an old one.")

    // for ES we need to create the entire index in an rdd of maps, one per item so we'll use
    // convert cooccurrence matrices into correlators as RDD[(itemID, (actionName, Seq[itemID])]
    // do they need to be in Elasticsearch format
    logger.info("Converting cooccurrence matrices into correlators")
    val correlatorRDDs: Seq[RDD[(ItemID, ItemProps)]] = coocurrenceMatrices.map {
      case (actionName, dataset) =>
        dataset.asInstanceOf[IndexedDatasetSpark].toStringMapRDD(actionName)
    }

    logger.info("Group all properties RDD")
    val groupedRDD: RDD[(ItemID, ItemProps)] = groupAll(correlatorRDDs ++ propertiesRDDs)
    //    logger.debug(s"Grouped RDD\n${groupedRDD.take(25).mkString("\n")}")

    val esRDD: RDD[Map[String, Any]] = groupedRDD.mapPartitions { iter =>
      iter map {
        case (itemId, itemProps) =>
          val propsMap = itemProps.map {
            case (propName, propValue) =>
              propName -> URModel.extractJvalue(dateNames, propName, propValue)
          }
          propsMap + ("id" -> itemId)
      }
    }

    // todo: this could be replaced with an optional list of properties in the params json because now it
    // goes through every element to find it's property name
    val esFields: List[String] = esRDD.flatMap(_.keySet).distinct().collect.toList
    logger.info(s"ES fields[${esFields.size}]: $esFields")

    EsClient.hotSwap(esIndex, esType, esRDD, esFields, typeMappings, numESWriteConnections)
    true
  }

  // Something in the second def of this function hangs on some data, reverting so this ***disables ranking***
  def groupAll(fields: Seq[RDD[(ItemID, ItemProps)]]): RDD[(ItemID, ItemProps)] = {
    //def groupAll( fields: Seq[RDD[(String, (Map[String, Any]))]]): RDD[(String, (Map[String, Any]))] = {
    //if (fields.size > 1 && !fields.head.isEmpty() && !fields(1).isEmpty()) {
    if (fields.size > 1) {
      fields.head.cogroup[ItemProps](groupAll(fields.drop(1))).map {
        case (key, pairMapSeqs) =>
          // to be safe merge all maps but should only be one per rdd element
          val rdd1Maps = pairMapSeqs._1.foldLeft(Map.empty[String, Any].asInstanceOf[ItemProps])(_ ++ _)
          val rdd2Maps = pairMapSeqs._2.foldLeft(Map.empty[String, Any].asInstanceOf[ItemProps])(_ ++ _)
          val fullMap = rdd1Maps ++ rdd2Maps
          (key, fullMap)
      }
    } else {
      fields.head
    }
  }

  /*  def groupAll(fields: Seq[RDD[(ItemID, ItemProps)]]): RDD[(ItemID, ItemProps)] = {
    fields.fold(sc.emptyRDD[(ItemID, ItemProps)])(_ ++ _).reduceByKey(_ ++ _)
  }
*/
}

object URModel {
  @transient lazy val logger: Logger = Logger[this.type]

  /** This is actually only used to read saved values and since they are in Elasticsearch we don't need to read
   *  this means we create a null model since it will not be used.
   *  todo: we should rejigger the template framework so this is not required.
   *  @param id ignored
   *  @param params ignored
   *  @param sc ignored
   *  @return dummy null model
   */
  def apply(id: String, params: URAlgorithmParams, sc: Option[SparkContext]): URModel = {
    // todo: need changes in PIO to remove the need for this
    new URModel(null, null, null, nullModel = true)(sc.get)
  }

  def extractJvalue(dateNames: Seq[String], key: String, value: Any): Any = value match {
    case JArray(list) => list.map(extractJvalue(dateNames, key, _))
    case JString(s) =>
      if (dateNames.contains(key)) {
        new DateTime(s).toDate
      } else if (RankingFieldName.toSeq.contains(key)) {
        s.toDouble
      } else {
        s
      }
    case JDouble(double) => double
    case JInt(int)       => int
    case JBool(bool)     => bool
    case _               => value
  }

}
