package org.template

import java.util

import grizzled.slf4j.Logger
import io.prediction.data.storage.{StorageClientConfig, elasticsearch}
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.GetRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.get.GetResponse
import org.json4s.jackson.JsonMethods._

object esClient {
  @transient lazy val logger = Logger[this.type]

  private lazy val client = new elasticsearch.StorageClient(StorageClientConfig()).client

  def deleteIndex(indexName: String): Boolean = {
    if (client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists()) {
      val delete = client.admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet()
      if (!delete.isAcknowledged) {
        logger.info(s"Index ${indexName} wasn't deleted, but caused no error.")
      }
      true
    } else false // wasn't deleted because it didn't exist
  }

  def search(query: String, indexName: String): PredictedResult = {
    val sr = client.prepareSearch(indexName).setSource(query).get()

    if (!sr.isTimedOut) {
      val recs = sr.getHits.getHits.map( hit => new ItemScore(hit.getId, hit.getScore.toDouble) )
      logger.info(s"Results: ${sr.getHits.getHits.size} retrieved of " +
        s"a possible ${sr.getHits.totalHits()}")
      new PredictedResult(recs)
    } else {
      logger.info(s"No results for query ${parse(query)}")
      new PredictedResult(Array.empty)
    }

  }

  def getSource(indexName: String, typeName: String, item: String): util.Map[String, AnyRef] = {
    client.prepareGet(indexName, typeName, item)
      .execute()
      .actionGet().getSource
  }
}