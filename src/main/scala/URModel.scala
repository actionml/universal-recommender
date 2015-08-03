package org.template

import java.util.Date

import grizzled.slf4j.Logger

import io.prediction.controller.{PersistentModelLoader, PersistentModel}
import io.prediction.data.storage.PropertyMap
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
    coocurrenceMatrices: List[(String, IndexedDatasetSpark)],
    fieldsRDD: RDD[(String, PropertyMap)],
    indexName: String,
    dateName: String,
    nullModel: Boolean = false)
    // a little hack to allow a dummy model used to save but not
    // retrieve (see companion object's apply)
  extends PersistentModel[URAlgorithmParams] {
  @transient lazy val logger = Logger[this.type]

  /** Save all fields to be indexed by Elasticsearch and queried for recs
    * This will is something like a table with row IDs = item IDs and separate fields for all
    * cooccurrence and cross-cooccurrence correlators and metadata for each item. Metadata fields are
    * limited to text term collections so vector types. Scalar values can be used but depend on
    * Elasticsearch's support. One exception is the Data scalar, which is also supported
    * @param id
    * @param params from engine.json, algorithm control params
    * @param sc The spark constext already created for execution
    * @return always returns true since most other reasons to not save cause exceptions
    */
  def save(id: String, params: URAlgorithmParams, sc: SparkContext): Boolean = {

    if (nullModel) throw new IllegalStateException("Saving a null model created from loading an old one.")

    // for ES we need to create the entire index in an rdd of maps, one per item so we'll use
    // convert cooccurrence matrices into correlators as RDD[(itemID, (actionName, Seq[itemID])]
    // do they need to be in Elasticsearch format
    logger.info("Converting cooccurrence matrices into correlators")
    val correlators = coocurrenceMatrices.map { case (actionName, dataset) =>
      dataset.toStringMapRDD(actionName)
    }

    // convert the PropertyMap into Map[String, Any] for ES
    // todo: properties come in different types so this should check to make sure the Field has a defined type
    logger.info("Converting PropertyMap into Elasticsearch style rdd")
    val properties = fieldsRDD.map { case (item, pm ) =>
      var m: Map[String, Any] = Map()
      for (key <- pm.keySet){
        val k = key
        val v = pm.get[JValue](key)
        try{ // if we get something unexpected, add ignore and add nothing to the map
          pm.get[JValue](key) match {
            case JArray(list) => // assumes all lists are string tokens for bias
              val l = list.map {
                case JString(s) => s
                case _ => ""
              }
              m = m + (key -> l)
            case JString(s) => // name for this field is in engine params
              if ( key == dateName) {
                val dateTime = new DateTime(s)
                val date: java.util.Date = dateTime.toDate()
                m = m + (key -> date)
              }
          }
        } catch {
          case e: ClassCastException => e
          case e: IllegalArgumentException => e
          //got something we didn't expect so ignore it, put nothing in the map
        }
      }
      (item, m)
    }

    // Elasticsearch takes a Map with all fields, not a tuple
    logger.info("Grouping all correlators into doc + fields for writing to index")
    val esRDDs: List[RDD[(String, Map[String, Any])]] =
      (correlators.asInstanceOf[ List[RDD[(String, Map[String, Any])]]] :+ properties).filterNot(c => c.isEmpty())
    val esFields = groupAll(esRDDs).map { case (item, map) =>
      // todo: every map's items must be checked for value type and converted before writing to ES
      val esMap = map + ("id" -> item)
      esMap
    }


    // May specifiy a remapping parameter to put certain fields in different places in the ES document
    // todo: need to write, then hot swap index to live index, prehaps using aliases? To start let's delete index and
    // recreate it, no swapping yet
    val esIndexURI = s"/${params.indexName}/${params.typeName}"
    logger.info(s"Deleting index: ${esIndexURI}")
    esClient.deleteIndex(params.indexName)
    logger.info(s"Creating new index: ${esIndexURI}")
    esClient.createIndex(params.indexName)

    // es.mapping.id needed to get ES's doc id out of each rdd's Map("id")
    logger.info(s"Writing new ES style rdd to index: ${esIndexURI}")
    esFields.saveToEs (esIndexURI, Map("es.mapping.id" -> "id"))
    // todo: check to see if a Flush is needed after writing all new data to the index
    logger.info(s"Finished writing to index: /${params.indexName}/${params.typeName}")
    true
  }
  
  def groupAll( fields: Seq[RDD[(String, (Map[String, Any]))]]): RDD[(String, (Map[String, Any]))] = {
    if (fields.size > 1 && !fields.head.isEmpty() && !fields(1).isEmpty()) {
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

object URModel
  extends PersistentModelLoader[URAlgorithmParams, URModel] {
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
    val urm = new URModel(null, null, null, true)
    logger.info("Created dummy null model")
    urm
  }

}
