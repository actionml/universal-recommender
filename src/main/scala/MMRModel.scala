package com.finderbots

import com.finderbots.MMRAlgorithmParams
import grizzled.slf4j.Logger

import io.prediction.controller.PersistentModel
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
    indexName: String )
  extends PersistentModel[MMRAlgorithmParams] {
  @transient lazy val logger = Logger[this.type]

  def save(id: String, params: MMRAlgorithmParams, sc: SparkContext): Boolean = {

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
    // todo: this just keeps overwriting the collection
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
    s"describe stuff sent to ES"
  }

}
