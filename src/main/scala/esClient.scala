/*
 * Licensed to ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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

import java.util

import grizzled.slf4j.Logger
import io.prediction.data.storage.{Storage, StorageClientConfig, elasticsearch}
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.GetRequest
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.{Settings, ImmutableSettings}
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._
import org.elasticsearch.spark._
import org.elasticsearch.node.NodeBuilder._

import scala.collection.immutable
import scala.collection.parallel.mutable

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

  private lazy val client = if (Storage.getConfig("ELASTICSEARCH").nonEmpty)
      new elasticsearch.StorageClient(Storage.getConfig("ELASTICSEARCH").get).client
    else
      throw new IllegalStateException("No Elasticsearch client configuration detected, check your pio-env.sh for" +
        "proper configuration settings")

  // wrong way that uses only default settings, which will be a localhost ES sever.
  //private lazy val client = new elasticsearch.StorageClient(StorageClientConfig()).client

  /** Delete all data from an instance but do not commit it. Until the "refresh" is done on the index
    * the changes will not be reflected.
    * @param indexName will delete all types under this index, types are not used by the UR
    * @param refresh
    * @return true if all is well
    */
  def deleteIndex(indexName: String, refresh: Boolean = false): Boolean = {
    //val debug = client.connectedNodes()
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

  /** Creates a new empty index in Elasticsearch and initializes mappings for fields that will be used
    * @param indexName elasticsearch name
    * @param indexType names the type of index, usually use the item name
    * @param fieldNames ES field names
    * @param typeMappings indicates which ES fields are to be not_analyzed without norms
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
        else // unspecified fields are treated as not_analyzed strings
          mappings += (fieldName + mappingsField("string"))
      }
      mappings += mappingsTail // any other string is not_analyzed

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

  /** Create new index and hot-swap the new after it's indexed and ready to take over, then delete the old */
  def hotSwap(
    alias: String,
    typeName: String = "items",
    indexRDD: RDD[scala.collection.Map[String,Any]],
    fieldNames: List[String],
    typeMappings: Option[Map[String, String]] = None): Unit = {
    // get index for alias, change a char, create new one with new id and index it, swap alias and delete old one
    val aliasMetadata = client.admin().indices().prepareGetAliases(alias).get().getAliases
    val newIndex = alias + "_" + DateTime.now().getMillis.toString
    createIndex(newIndex, typeName, fieldNames, typeMappings)

    val newIndexURI = "/" + newIndex + "/" + typeName
    indexRDD.saveToEs(newIndexURI, Map("es.mapping.id" -> "id"))
    //refreshIndex(newIndex)

    if (!aliasMetadata.isEmpty
    && aliasMetadata.get(alias) != null
    && aliasMetadata.get(alias).get(0) != null) { // was alias so remove the old one
      //append the DateTime to the alias to create an index name
      val oldIndex = aliasMetadata.get(alias).get(0).getIndexRouting
      client.admin().indices().prepareAliases()
        .removeAlias(oldIndex, alias)
        .addAlias(newIndex, alias)
        .execute().actionGet()
      deleteIndex(oldIndex) // now can safely delete the old one since it's not used
    } else { // todo: could be more than one index with 'alias' so
      // no alias so add one
      //to clean up any indexes that exist with the alias name
      val indices = util.Arrays.asList(client.admin().indices().prepareGetIndex().get().indices()).get(0)
      if (indices.contains(alias)) {
        //refreshIndex(alias)
        deleteIndex(alias) // index named like the new alias so delete it
      }
      // slight downtime, but only for one case of upgrading the UR engine from v0.1.x to v0.2.0+
      client.admin().indices().prepareAliases()
        .addAlias(newIndex, alias)
        .execute().actionGet()
    }
    // clean out any old indexes that were the product of a failed train?
    val indices = util.Arrays.asList(client.admin().indices().prepareGetIndex().get().indices()).get(0)
    indices.map{ index =>
      if (index.contains(alias) && index != newIndex) deleteIndex(index) //clean out any old orphaned indexes
    }

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

  /*
  public Set<String> getIndicesFromAliasName(String aliasName) {

    IndicesAdminClient iac = client.admin().indices();
    ImmutableOpenMap<String, List<AliasMetaData>> map = iac.getAliases(new GetAliasesRequest(aliasName))
            .actionGet().getAliases();

    final Set<String> allIndices = new HashSet<>();
    map.keysIt().forEachRemaining(allIndices::add);
    return allIndices;
}
   */
  def getIndexName(alias: String): Option[String] = {

    val allIndicesMap = client.admin().indices().getAliases(new GetAliasesRequest(alias)).actionGet().getAliases

    if (allIndicesMap.size() == 1) { // must be a 1-1 mapping of alias <-> index
      var  indexName: String = ""
      var itr = allIndicesMap.keysIt()
      while ( itr.hasNext )
        indexName = itr.next()
      Some(indexName) // the one index the alias points to
    } else {
      // delete all the indices that are pointed to by the alias, they can't be used
      logger.warn("There is no 1-1 mapping of index to alias so deleting the old indexes that are referenced by the " +
        "alias. This may have been caused by a crashed or stopped `pio train` operation so try running it again.")
      val i = allIndicesMap.keys().toArray.asInstanceOf[Array[String]]
      for ( indexName <- i ){
        deleteIndex(indexName, true)
      }

      None // if more than one abort, need to clean up bad aliases
    }
  }

  def getRDD(sc: SparkContext, alias: String, typeName: String):
  Option[RDD[(String, collection.Map[String, AnyRef])]] = {
    val index = getIndexName(alias)
    if (index.nonEmpty) { // ensures there is a 1-1 mapping of alias to index
      val indexAsRDD = sc.esRDD(alias + "/" + typeName)
      //val debug = indexAsRDD.count()
      Some(indexAsRDD)
    } else None // error so no index for the alias
  }
}