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

import java.util

import grizzled.slf4j.Logger
import org.apache.predictionio.data.storage._

/*
//import org.json4s.JsonAST.{JField, JString}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JValue
//import org.json4s.jackson.JsonMethods._
//import org.json4s.JsonDSL._
*/
import org.json4s.JValue
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

//import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.GetRequest
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.common.settings.{ ImmutableSettings, Settings }
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._
import org.elasticsearch.spark._
import org.elasticsearch.search.SearchHits
import com.actionml.helpers.{ ItemID, ItemProps }

/** Elasticsearch notes:
 *  1) every query clause will affect scores unless it has a constant_score and boost: 0
 *  2) the Spark index writer is fast but must assemble all data for the index before the write occurs
 *  3) many operations must be followed by a refresh before the action takes effect--sortof like a transaction commit
 *  4) to use like a DB you must specify that the index of fields are `not_analyzed` so they won't be lowercased,
 *    stemmed, tokenized, etc. Then the values are literal and must match exactly what is in the query (no analyzer)
 *    Finally the correlator fields should be norms: true to enable norms, this make the score equal to the sum
 *    of dot products divided by the length of each vector. This is the definition of "cosine" similarity.
 *    Todo: norms may not be the best, should experiment to know for sure.
 *  5) the client, either transport client for < ES5 or the rest client for >= ES5 there should be a timeout set since
 *    the default is very long, several seconds.
 */

/** Defines methods to use on Elasticsearch.*/
object EsClient {
  @transient lazy val logger: Logger = Logger[this.type]

  /*  This returns 2 incompatible objects of TransportClient and RestClient

  private lazy val client = if (Storage.getConfig("ELASTICSEARCH5").nonEmpty)
    new elasticsearch5.StorageClient(Storage.getConfig("ELASTICSEARCH5").get).client
  else if (Storage.getConfig("ELASTICSEARCH").nonEmpty)
    new elasticsearch.StorageClient(Storage.getConfig("ELASTICSEARCH").get).client
  else
    throw new IllegalStateException("No Elasticsearch client configuration detected, check your pio-env.sh for" +
      "proper configuration settings")
  */

  /** Gets the client for the right version of Elasticsearch. The ES timeout for the Transport client is
   *  set in elasticsearch.yml with transport.tcp.connect_timeout: 200ms or something like that
   */
  private lazy val client = if (Storage.getConfig("ELASTICSEARCH").nonEmpty) {
    new elasticsearch.StorageClient(Storage.getConfig("ELASTICSEARCH").get).client
  } else {
    throw new IllegalStateException("No Elasticsearch client configuration detected, check your pio-env.sh for" +
      "proper configuration settings")
  }

  /** Delete all data from an instance but do not commit it. Until the "refresh" is done on the index
   *  the changes will not be reflected.
   *
   *  @param indexName will delete all types under this index, types are not used by the UR
   *  @param refresh
   *  @return true if all is well
   */
  def deleteIndex(indexName: String, refresh: Boolean = false): Boolean = {
    //val debug = client.connectedNodes()
    if (client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists) {
      val delete = client.admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet()
      if (!delete.isAcknowledged) {
        logger.warn(s"Index $indexName wasn't deleted, but may have quietly failed.")
      } else {
        // now refresh to get it 'committed'
        // todo: should do this after the new index is created so no index downtime
        if (refresh) refreshIndex(indexName)
      }
      true
    } else {
      logger.warn(s"Elasticsearch index: $indexName wasn't deleted because it didn't exist. This may be an error.")
      false
    }
  }

  /** Creates a new empty index in Elasticsearch and initializes mappings for fields that will be used
   *
   *  @param indexName elasticsearch name
   *  @param indexType names the type of index, usually use the item name
   *  @param fieldNames ES field names
   *  @param typeMappings indicates which ES fields are to be not_analyzed without norms
   *  @param refresh should the index be refreshed so the create is committed
   *  @return true if all is well
   */
  def createIndex(
    indexName: String,
    indexType: String,
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty,
    refresh: Boolean = false): Boolean = {

    if (!client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists) {
      var mappings = """
        |{
        |  "properties": {
        """.stripMargin.replace("\n", "")

      def mappingsField(`type`: String, `normsEnabled`: Boolean) = {
        s"""
        |    : {
        |      "type": "${`type`}",
        |      "index": "not_analyzed",
        |      "norms" : {
        |        "enabled" : "${`normsEnabled`}"
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
        if (typeMappings.contains(fieldName))
          mappings += (fieldName + mappingsField(typeMappings(fieldName)._1, typeMappings(fieldName)._2))
        else // unspecified fields are treated as not_analyzed strings
          mappings += (fieldName + (mappingsField("string", false)))
      }
      mappings += mappingsTail // "id" string is not_analyzed and does not use norms
      logger.info(s"Mappings for the index: $mappings")

      val cir = new CreateIndexRequest(indexName).mapping(indexType, mappings)
      val create = client.admin().indices().create(cir).actionGet()
      if (!create.isAcknowledged) {
        logger.warn(s"Index $indexName wasn't created, but may have quietly failed.")
      } else {
        // now refresh to get it 'committed'
        // todo: should do this after the new index is created so no index downtime
        if (refresh) refreshIndex(indexName)
      }
      true
    } else {
      logger.warn(s"Elasticsearch index: $indexName wasn't created because it already exists. This may be an error.")
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
    typeName: String,
    indexRDD: RDD[Map[String, Any]],
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty): Unit = {
    // get index for alias, change a char, create new one with new id and index it, swap alias and delete old one
    val aliasMetadata = client.admin().indices().prepareGetAliases(alias).get().getAliases
    val newIndex = alias + "_" + DateTime.now().getMillis.toString

    logger.trace(s"Create new index: $newIndex, $typeName, $fieldNames, $typeMappings")
    createIndex(newIndex, typeName, fieldNames, typeMappings)

    val newIndexURI = "/" + newIndex + "/" + typeName
    indexRDD.saveToEs(newIndexURI, Map("es.mapping.id" -> "id"))
    //refreshIndex(newIndex) //appears to not be needed

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
    indices.map { index =>
      if (index.contains(alias) && index != newIndex) deleteIndex(index) //clean out any old orphaned indexes
    }

  }

  /** Performs a search using the JSON query String
   *
   *  @param query the JSON query string parable by Elasticsearch
   *  @param indexName the index to search
   *  @return a [PredictedResults] collection
   */
  def search(query: String, indexName: String, correlators: Seq[String]): Option[SearchHits] = {

    val sr = client.prepareSearch(indexName).setSource(query).get()
    if (!sr.isTimedOut) {
      Some(sr.getHits)
    } else { // ask for raked items like popular
      val rr = client.prepareSearch(indexName).setSource(rankedResults(query, correlators)).get()
      if (!rr.isTimedOut) {
        logger.warn("Elasticsearch timed out during a query so returning ranked items, not user, item, or" +
          s" itemSet based. Query is now: ${rankedResults(query, correlators)}")
        Some(rr.getHits)
      } else {
        None
      }
    }
  }

  /** sorry, a little hacky, remove item, user, and/or itemset from query so all the bizrules
   *  are unchanged but the query will run fast returning ranked results like popular.
   *  Todo: a better way it to pass in a fallback query
   */
  def rankedResults(query: String, correlators: Seq[String]): String = {
    var newQuery = query
    for (correlator <- correlators) {
      // way hacky, should use the removal or replacement functions but can't quite see how to do it
      // Todo: Warning this will have problems if the correlator name can be interpretted as a regex
      newQuery = newQuery.replace(correlator, "__null__") // replaces no matter where in the string, seems dangerous
      /* removeField example that leaves an invalid query
      newQuery = compact(render(parse(newQuery).removeField { // filter one at a time, there must be a better way
        case JField(`correlator`, _) => true
        case _                       => false
      }))
      */
    }
    newQuery
  }

  /** Gets the "source" field of an Elasticsearch document
   *
   *  @param indexName index that contains the doc/item
   *  @param typeName type name used to construct ES REST URI
   *  @param doc for UR the item id
   *  @return source [java.util.Map] of field names to any valid field values or null if empty
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
      var indexName: String = ""
      val itr = allIndicesMap.keysIt()
      while (itr.hasNext)
        indexName = itr.next()
      Some(indexName) // the one index the alias points to
    } else {
      // delete all the indices that are pointed to by the alias, they can't be used
      logger.warn("There is no 1-1 mapping of index to alias so deleting the old indexes that are referenced by the " +
        s"alias: $alias. This may have been caused by a crashed or stopped `pio train` operation so try running it again.")
      if (!allIndicesMap.isEmpty) {
        val i = allIndicesMap.keys().toArray.asInstanceOf[Array[String]]
        for (indexName <- i) {
          deleteIndex(indexName, refresh = true)
        }
      }
      None // if more than one abort, need to clean up bad aliases
    }
  }

  def getRDD(
    alias: String,
    typeName: String)(implicit sc: SparkContext): RDD[(ItemID, ItemProps)] = {
    getIndexName(alias)
      .map(index => sc.esJsonRDD(alias + "/" + typeName) map { case (itemId, json) => itemId -> DataMap(json).fields })
      .getOrElse(sc.emptyRDD)
  }
}
