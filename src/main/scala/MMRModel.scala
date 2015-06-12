package com.finderbots

import com.finderbots.MMRAlgorithmParams
import grizzled.slf4j.Logger

import io.prediction.controller.{PersistentModelLoader, PersistentModel}
import io.prediction.data.storage.{PropertyMap, elasticsearch, StorageClientConfig}
import io.prediction.data.storage.elasticsearch.StorageClient
import io.prediction.data.store.PEventStore
import org.apache.mahout.math.indexeddataset.Schema
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentFactory
import _root_.io.prediction.data.storage.{elasticsearch, StorageClientConfig}
import _root_.io.prediction.data.storage.elasticsearch.StorageClient
import org.apache.mahout.math.indexeddataset._
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext._
import org.apache.mahout.math.RandomAccessSparseVector
import org.apache.mahout.math.drm.{DrmLike, DrmLikeOps, DistributedContext, CheckpointedDrm}
import org.apache.mahout.sparkbindings._
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.xcontent.XContentFactory
import scala.collection.JavaConversions._
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders.termFilter
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write


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

    // add metadata to items after they have been updated in the index

    writeFields(fieldsRDD, esConfig)
    true
  }

  def writeFields(fieldsRDD: RDD[(String, PropertyMap)], esConfig: StorageClientConfig = StorageClientConfig()): Boolean = {
    val esParallel = esConfig.parallel
    val esTest = esConfig.test
    val esProperties = esConfig.properties
    val esIndexName = indexName

    /*val num = fieldsRDD.count()
    val one = fieldsRDD.take(1)
    val key = one(0)._1
    val value = one(0)._2
    val props = one(0)._2.get[Seq[String]]("category")
    val propsKeys = one(0)._2.keySet
    */

    val f = fieldsRDD.collect()
    val esClient = new elasticsearch.StorageClient(StorageClientConfig(esParallel, esTest, esProperties)).client

    fieldsRDD.foreach { case (item, properties) =>
      val d = 0
      for ( fieldName <- properties.keySet){
        val props = properties.get[Seq[String]](fieldName)
        val debug = 0
        val indexRequest = new IndexRequest(esIndexName, "indicators", item)
          .source(XContentFactory.jsonBuilder() // not using json4s here in case of executor overhead
          .startObject()
          .field(fieldName, props) // todo: should be a list of String?
          .endObject())
        val updateRequest = new UpdateRequest(esIndexName, "indicators", item)
          .doc(XContentFactory.jsonBuilder()
          .startObject()
          .field(fieldName, props)
          .endObject())
          .upsert(indexRequest)
        esClient.update(updateRequest).get() //todo: should build upsert for all properties before performing
      }
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
      "Todo: describe MMRModel"
  }


}

object MMRModel
  extends PersistentModelLoader[MMRAlgorithmParams, MMRModel] {
  @transient lazy val logger = Logger[this.type]
  def apply(id: String, params: MMRAlgorithmParams, sc: Option[SparkContext]): MMRModel = {
    // todo: need changes in PIO to remove the need for this
    val mmrm = new MMRModel(null, null, null, true)
    logger.info("Created dummy null model")
    mmrm
  }

}