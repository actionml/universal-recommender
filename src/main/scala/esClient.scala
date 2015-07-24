package org.template

import java.util

import grizzled.slf4j.Logger
import io.prediction.data.storage.{StorageClientConfig, elasticsearch}
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.GetRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.get.GetResponse
import org.json4s.jackson.JsonMethods._

/** Defines methods to use on Elasticsearch
  * Todo: is an object the right packaging given the PIO workflow?
  */
object esClient {
  @transient lazy val logger = Logger[this.type]

  private lazy val client = new elasticsearch.StorageClient(StorageClientConfig()).client

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
    *
    * @param indexName elasticsearch name
    * @param refresh should the index be refreshed so the create is committed
    * @return true if all is well
    */
  def createIndex(indexName: String, refresh: Boolean = false): Boolean = {
    if (!client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists()) {
      val create = client.admin().indices().create(new CreateIndexRequest(indexName)).actionGet()
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
}