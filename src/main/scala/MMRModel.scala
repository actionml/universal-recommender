package org.template

import grizzled.slf4j.Logger

import io.prediction.controller.{PersistentModelLoader, PersistentModel}
import io.prediction.data.storage.PropertyMap
import org.apache.spark.rdd.RDD
import _root_.io.prediction.data.storage.{elasticsearch, StorageClientConfig}
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.template.conversions.IndexedDatasetConversions
import org.elasticsearch.spark._
import org.apache.spark.SparkContext


/** Multimodal Cooccurrence models to save in ES */
class MMRModel(
    coocurrenceMatrices: List[(String, IndexedDatasetSpark)],
    fieldsRDD: RDD[(String, PropertyMap)],
    indexName: String,
    nullModel: Boolean = false)
    // a little hack to allow a dummy model used to save but not
    // retrieve (see companion object's apply)
  extends PersistentModel[MMRAlgorithmParams] {
  @transient lazy val logger = Logger[this.type]

  /** Save all fields to be indexed by Elasticsearch and queried for recs
    * This will is something like a table with row IDs = item IDs and separate fields for all
    * cooccurrence and cross-cooccurrence indicators and metadata for each item. Metadata fields are
    * limited to text term collections so vector types. Scalar values can be used but depend on
    * Elasticsearch's support. One exception is the Data scalar, which is also supported
    * @param id
    * @param params from engine.json, slgorithm control params
    * @param sc The spark constext already created for execution
    * @return always returns true since most other reasons to not save cause exceptions
    */
  def save(id: String, params: MMRAlgorithmParams, sc: SparkContext): Boolean = {

    if (nullModel) throw new IllegalStateException("Saving a null model created from loading an old one.")
    logger.info("Saving mmr model")

    // convert cooccurrence matrices into indicators as RDD[(itemID, (actionName, Seq[itemID])]
    // do they need to be in Elasticsearch format
    val indicators = coocurrenceMatrices.map { case (actionName, dataset) =>
      dataset.toStringMapRDD(actionName)
    }

    // convert the PropertyMap into Map[String, Seq[String]] for ES
    val fields = fieldsRDD.map { case (item, pm ) =>
      var m: Map[String, Seq[String]] = Map()
      for (key <- pm.keySet){
        m = m  + (key -> pm.get[List[String]](key))
      }
      (item, m)
    }

    // Elasticsearch takes a Map with all fields, not a tuple
    val esFields = joinAll(fields, indicators(0), indicators.drop(1)).map { case (item, map) =>
        val esMap = map + ("id" -> item)
        esMap
    }

    val debug = esFields.take(1)(0)

    // May specifiy a remapping parameter to put certain fields in different places in the ES document
    // todo: need to write, then hot swap index to live index, prehaps using aliases? To start let's delete index and
    // recreate it, no swapping yet
    esClient.deleteIndex(params.indexName)

    // es.mapping.id needed to get ES's doc id out of each rdd's Map("id")
    esFields.saveToEs(s"/${params.indexName}/${params.typeName}", Map("es.mapping.id" -> "id"))
    // todo: check to see if a Flush is needed after writing all new data to the index
    // esClient.admin().indices().flush(new FlushRequest("mmrindex")).actionGet()
    val dbFields = esFields.collect()
    true
  }

  /** Joins any number of PairRDDs of tuples with leading id but flattens the result nested tuple after each join
    * since the nesting will have an arbitrary depth and will therefore be cumbersome to deal with. The second
    * item in the input RDD will always be a Map so the maps are merged after each join to reutrn an
    * RDD[(String, Map[String, Seq[String]], which is an item id and a map of (fieldname -> list-of-values).
    * Private only because it's specific to pair RDDs that have maps as the second pair element.
    * @param indicators RDD of (item-id, map-of-filednames -> lists-of-value)
    * @return RDD of (item-id, map-of-all-fieldnames -> lists-of-values)
    */
  private def joinAll(
    indicators: Seq[RDD[(String, Map[String, Seq[String]])]]): RDD[(String, (Map[String, Seq[String]]))] = {
    if (indicators.size == 1) indicators(0)
    else if (indicators.size == 2) indicators(0).join(indicators(1)).map { case (item, (m1, m2)) => (item, m1 ++ m2) }
    else joinAll(indicators(0), indicators(1), indicators.drop(2))
  }

  /** Private, since it is specific to this RDD data, helper for three or more RDDs */
  private def joinAll(
    firstIndicator: RDD[(String, Map[String, Seq[String]])] ,
    secondIndicator: RDD[(String, Map[String, Seq[String]])] ,
    restIndicators: Seq[RDD[(String, Map[String, Seq[String]])]]): RDD[(String, Map[String, Seq[String]])] = {
    var joinedIndicators = firstIndicator.join(secondIndicator).map { case (item, (m1, m2)) => (item, m1 ++ m2) }

    restIndicators.foreach { indicator =>
      joinedIndicators = joinedIndicators.join(indicator).map { case (item, (m1, m2)) =>
        (item, m1 ++ m2) // to avoid nested tuples, an extas pass over the joined rdd merges their maps
      }
    }
    joinedIndicators
  }

  override def toString = {
  /*s"userFeatures: [${userFeatures.count()}]" +
    s"(${userFeatures.take(2).toList}...)" +
    s" productFeatures: [${productFeatures.count()}]" +
    s"(${productFeatures.take(2).toList}...)" +
    s" userStringIntMap: [${userStringIntMap.size}]" +
    s"(${userStringIntMap.take(2)}...)" +
    s" itemStringIntMap: [${itemStringIntMap.size}]" +
    s"(${itemStringIntMap.take(2)}...)" */
      "Todo: describe MMRModel"
  }


}

object MMRModel
  extends PersistentModelLoader[MMRAlgorithmParams, MMRModel] {
  @transient lazy val logger = Logger[this.type]

  /** This is actually only used to read saved values and since they are in Elasticsearch we don't need to read
    * this means we create a null model since it will not be used.
    * todo: we should rejigger the template framework so this is not required.
    * @param id ignored
    * @param params ignored
    * @param sc ignored
    * @return dummy null model
    */
  def apply(id: String, params: MMRAlgorithmParams, sc: Option[SparkContext]): MMRModel = {
    // todo: need changes in PIO to remove the need for this
    val mmrm = new MMRModel(null, null, null, true)
    logger.info("Created dummy null model")
    mmrm
  }

}
