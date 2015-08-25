package org.template

import java.util

import grizzled.slf4j.Logger
import io.prediction.data.storage.{StorageClientConfig, elasticsearch}
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.GetRequest
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.get.GetResponse
import org.json4s.jackson.JsonMethods._
import org.elasticsearch.spark._

import scala.collection.immutable

/** Elasticsearch notes:
  * 1) every query clause wil laffect scores unless it has a constant_score and boost: 0
  * 2) the Spark index writer is fast but must assemble all data for the index before the write occurs
  * 3) many operations must be followed by a refresh before the action takes effect--sortof like a transaction commit
  * 4) to use like a DB you must specify that the index of fields are `not_analyzed` so they won't be lowercased,
  *    stemmed, tokenized, etc. Then the values are literal and must match exactly what is in the query (no analyzer)
  */

/** Defines methods to use on Elasticsearch. */
object esClient {
  @transient lazy val logger = Logger[this.type]

  private val esClient = new elasticsearch.StorageClient(StorageClientConfig())
  //private lazy val client = new elasticsearch.StorageClient(StorageClientConfig()).client
  //should be
//private val client = Storage.getSource("ELASTICSEARCH").client
  private val client = new elasticsearch.StorageClient(StorageClientConfig()).client

  /** Delete all data from an instance but do not commit it. Until the "refresh" is done on the index
    * the changes will not be reflected.
    * @param indexName will delete all types under this index, types are not used by the UR
    * @param refresh
    * @return true if all is well
    */
  def deleteIndex(indexName: String, refresh: Boolean = false): Boolean = {
    if (client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists()) {
      val delete = client.admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet()
      if (!delete.isAcknowledged) {
        logger.info(s"Index ${indexName} wasn't deleted, but may have quietly failed.")
      } else {
        // now refresh to get it 'committed'
        // todo: should do this after the new index is created so no index downtime
        if (refresh) refreshIndex(indexName)
      }
      true
    } else {
      logger.warn(s"Elasticsearch index: ${indexName} wasn't deleted because it didn't exist. This may be an error.")
      false
    }
  }

  /** Creates a new empty index in Elasticsearch
    *"properties": {
                "type" : "string",
                "index" : "not_analyzed",
                "norms" : {
                    "enabled" : false
                }
            }
    * @param indexName elasticsearch name
    * @param refresh should the index be refreshed so the create is committed
    * @return true if all is well
    */
  def createIndex(
    indexName: String,
    indexType: String = "items",
    fieldNames: List[String],
    typeMappings: Option[Map[String, String]] = None,
    refresh: Boolean = false): Boolean = {
    if (!client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists()) {
      var mappings = """
        |{
        |  "properties": {
        """.stripMargin.replace("\n", "")

      def mappingsField(t: String) = {
        s"""
        |    : {
        |      "type": "${t}",
        |      "index": "not_analyzed",
        |      "norms" : {
        |        "enabled" : false
        |      }
        |    },
        """.stripMargin.replace("\n", "")
      }

      val mappingsTail = """
        |    "id": {
        |      "type": "string",
        |      "index": "not_analyzed",
        |      "norms" : {
        |        "enabled" : false
        |      }
        |    }
        |  }
        |}
      """.stripMargin.replace("\n", "")

      fieldNames.foreach { fieldName =>
        if (typeMappings.nonEmpty && typeMappings.get.contains(fieldName))
          mappings += (fieldName + mappingsField(typeMappings.get(fieldName)))
        else
          mappings += (fieldName + mappingsField("string"))
      }
      mappings += mappingsTail

      val cir = new CreateIndexRequest(indexName).mapping("items",mappings)
      val create = client.admin().indices().create(cir).actionGet()
      if (!create.isAcknowledged) {
        logger.info(s"Index ${indexName} wasn't created, but may have quietly failed.")
      } else {
        // now refresh to get it 'committed'
        // todo: should do this after the new index is created so no index downtime
        if (refresh) refreshIndex(indexName)
      }
      true
    } else {
      logger.warn(s"Elasticsearch index: ${indexName} wasn't created because it already exists. This may be an error.")
      false
    }
  }

  /** Commits any pending changes to the index */
  def refreshIndex(indexName: String): Unit = {
    client.admin().indices().refresh(new RefreshRequest(indexName)).actionGet()
  }

  /** Performs a search using the JSON query String
    *
    * @param query the JSON query string parable by Elasticsearch
    * @param indexName the index to search
    * @return a [PredictedResults] collection
    */
  def search(query: String, indexName: String): PredictedResult = {
    val sr = client.prepareSearch(indexName).setSource(query).get()

    if (!sr.isTimedOut) {
      val recs = sr.getHits.getHits.map( hit => new ItemScore(hit.getId, hit.getScore.toDouble) )
      logger.info(s"Results: ${sr.getHits.getHits.size} retrieved of " +
        s"a possible ${sr.getHits.totalHits()}")
      new PredictedResult(recs)
    } else {
      logger.info(s"No results for query ${parse(query)}")
      new PredictedResult(Array.empty[ItemScore])
    }

  }

  /** Gets the "source" field of an Elasticsearch document
    *
    * @param indexName index that contains the doc/item
    * @param typeName type name used to construct ES REST URI
    * @param doc for UR the item id
    * @return source [java.util.Map] of field names to any valid field values or null if empty
    */
  def getSource(indexName: String, typeName: String, doc: String): util.Map[String, AnyRef] = {
    client.prepareGet(indexName, typeName, doc)
      .execute()
      .actionGet().getSource
  }

  def getRDD(sc: SparkContext, index: String, typeName: String): RDD[(String, collection.Map[String, AnyRef])] = {
    sc.esRDD(index + "/" + typeName)
  }
}