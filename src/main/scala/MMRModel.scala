package com.finderbots

import com.finderbots.MMRAlgorithmParams
import grizzled.slf4j.Logger

import io.prediction.controller.{PersistentModelLoader, PersistentModel}
import io.prediction.data.storage.{elasticsearch, StorageClientConfig}
import io.prediction.data.storage.elasticsearch.StorageClient
import org.apache.mahout.math.indexeddataset.Schema
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

/** Multimodal Cooccurrence models to save in ES */
class MMRModel(
    coocurrenceMatrices: List[(String, IndexedDatasetSpark)],
    indexName: String,
    nullModel: Boolean = false)
    // a little hack to allow a dummy model used to save but not
    // retrieve (see companion object's apply)
  extends PersistentModel[MMRAlgorithmParams] {
  @transient lazy val logger = Logger[this.type]

  def save(id: String, params: MMRAlgorithmParams, sc: SparkContext): Boolean = {

    if (nullModel) throw new IllegalStateException("Saving a null model created from loading an old one.")
    logger.info("Saving mmr model")
    val esConfig = StorageClientConfig()
    val esStorageClient = new elasticsearch.StorageClient(esConfig)
    val esSchema = new Schema(
      //"esClient" -> esStorageClient.client, // oddly enough this does serialize
      "parallel" -> esConfig.parallel,
      "test" -> esConfig.test,
      "properties" -> esConfig.properties,
      "indexName" -> indexName)

    val esWriter = new ElasticsearchIndexedDatasetWriter(esSchema)(sc)

    // todo: how do we handle a previously trained indicator set, removing it after the new one is indexed?
    // todo: this just keeps overwriting the collection, which has some benefits
    coocurrenceMatrices.foreach { case (actionName, dataset) =>
      esWriter.writeTo(dataset, actionName) // writing one field at a time, rather than joining all datasets on item ID
    }
    true
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
    s"Todo: describe MMRModel"
  }

}

object MMRModel
  extends PersistentModelLoader[MMRAlgorithmParams, MMRModel] {
  @transient lazy val logger = Logger[this.type]
  def apply(id: String, params: MMRAlgorithmParams, sc: Option[SparkContext]): MMRModel = {
    // todo: need changes in PIO to remove the need for this
    val mmrm = new MMRModel(null, null, true)
    logger.info("Created dummy null model")
    mmrm
  }

}