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

import java.net.URLEncoder
import java.util

import grizzled.slf4j.Logger
import org.apache.http.util.EntityUtils
import org.apache.predictionio.data.storage.{ DataMap, Storage, StorageClientConfig }
import org.apache.predictionio.workflow.CleanupFunctions
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.elasticsearch.client.RestClient
import org.apache.http.HttpHost
import org.apache.http.auth.{ AuthScope, UsernamePasswordCredentials }
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.nio.entity.NStringEntity
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.elasticsearch.spark._
import org.json4s.JValue
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JString
// import org.json4s.native.Serialization.writePretty
import com.actionml.helpers.{ ItemID, ItemProps }

import scala.collection.immutable
import scala.collection.parallel.mutable
import scala.collection.JavaConverters._

/** Elasticsearch notes:
 *  1) every query clause will affect scores unless it has a constant_score and boost: 0
 *  2) the Spark index writer is fast but must assemble all data for the index before the write occurs
 *  3) to use like a DB you must specify that the index of fields are `not_analyzed` so they won't be lowercased,
 *    stemmed, tokenized, etc. Then the values are literal and must match exactly what is in the query (no analyzer)
 *    Finally the correlator fields should be norms: true to enable norms, this make the score equal to the sum
 *    of dot products divided by the length of each vector. This is the definition of "cosine" similarity.
 *  4) the client, either transport client for < ES5 or the rest client for >= ES5 there should have a timeout set since
 *    the default is very long, several seconds.
 *
 */

/** Defines methods to use on Elasticsearch 5 through the REST client.*/
object EsClient {
  @transient lazy val logger: Logger = Logger[this.type]

  implicit val formats = DefaultFormats

  private val configOpt = Storage.getConfig("ELASTICSEARCH")

  private lazy val client: RestClient = configOpt.map { config =>
    val usernamePassword = (
      config.properties.get("USERNAME"),
      config.properties.get("PASSWORD"))
    val optionalBasicAuth: Option[(String, String)] = usernamePassword match {
      case (None, None) => None
      case (username, password) => Some(
        (username.getOrElse(""), password.getOrElse("")))
    }
    CleanupFunctions.add {
      EsClient.close
    }
    open(getHttpHosts(config), optionalBasicAuth)
  } getOrElse {
    throw new IllegalStateException("No Elasticsearch client configuration detected, check your pio-env.sh for" +
      "proper configuration settings")
  }

  // Method lifted from ESUtils in PredictionIO
  def getHttpHosts(config: StorageClientConfig): Seq[HttpHost] = {
    val hosts = config.properties.get("HOSTS").
      map(_.split(",").toSeq).getOrElse(Seq("localhost"))
    val ports = config.properties.get("PORTS").
      map(_.split(",").toSeq.map(_.toInt)).getOrElse(Seq(9200))
    val schemes = config.properties.get("SCHEMES").
      map(_.split(",").toSeq).getOrElse(Seq("http"))
    (hosts, ports, schemes).zipped.map((h, p, s) => new HttpHost(h, p, s))
  }

  var _sharedRestClient: Option[RestClient] = None

  def open(
    hosts: Seq[HttpHost],
    basicAuth: Option[(String, String)] = None): RestClient = {
    val newClient = _sharedRestClient match {
      case Some(c) => c
      case None => {
        var builder = RestClient.builder(hosts: _*)
        builder = basicAuth match {
          case Some((username, password)) => builder.setHttpClientConfigCallback(
            new BasicAuthProvider(username, password))
          case None => builder
        }
        builder.build()
      }
    }
    _sharedRestClient = Some(newClient)
    newClient
  }

  def close(): Unit = {
    if (!_sharedRestClient.isEmpty) {
      _sharedRestClient.get.close()
      _sharedRestClient = None
    }
  }

  /** Delete all data from an instance but do not commit it. Until the "refresh" is done on the index
   *  the changes will not be reflected.
   *
   *  @param indexName will delete all types under this index, types are not used by the UR
   *  @param refresh
   *  @return true if all is well
   */
  def deleteIndex(indexName: String, refresh: Boolean = false): Boolean = {
    client.performRequest(
      "HEAD",
      s"/$indexName",
      Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
        case 404 => false
        case 200 =>
          client.performRequest(
            "DELETE",
            s"/$indexName",
            Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
              case 200 =>
                if (refresh) refreshIndex(indexName)
              case _ =>
                logger.info(s"Index $indexName wasn't deleted, but may have quietly failed.")
            }
          true
        case _ =>
          throw new IllegalStateException()
          false
      }
  }

  /** Creates a new empty index in Elasticsearch and initializes mappings for fields that will be used
   *
   *  @param indexName    elasticsearch name
   *  @param indexType    names the type of index, usually use the item name
   *  @param fieldNames   ES field names
   *  @param typeMappings indicates which ES fields are to be not_analyzed without norms
   *  @param refresh      should the index be refreshed so the create is committed
   *  @return true if all is well
   */
  def createIndex(
    indexName: String,
    indexType: String,
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty,
    refresh: Boolean = false): Boolean = {
    client.performRequest(
      "HEAD",
      s"/$indexName",
      Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
        case 404 => { // should always be a unique index name so fail unless we get a 404
          var mappings =
            s"""
              |{ "mappings": {
              |    "$indexType": {
              |      "properties": {
            """.stripMargin.replace("\n", "")

          def mappingsField(`type`: String) = {
            s"""
              |    : {
              |      "type": "${`type`}"
              |    },
            """.stripMargin.replace("\n", "")
          }

          val mappingsTail =
            // unused mapping forces the last element to have no comma, fuck JSON
            s"""
              |    "last": {
              |      "type": "keyword"
              |    }
              |}}}}
            """.stripMargin.replace("\n", "")

          fieldNames.foreach { fieldName =>
            if (typeMappings.contains(fieldName))
              mappings +=
                (s""""$fieldName"""" + mappingsField(typeMappings(
                  fieldName)._1))
            else // unspecified fields are treated as not_analyzed strings
              mappings += (s""""$fieldName"""" + mappingsField(
                "keyword"))
          }

          mappings += mappingsTail
          // "id" string is not_analyzed and does not use norms
          // val entity = new NStringEntity(mappings, ContentType.APPLICATION_JSON)
          //logger.info(s"Create index with:\n$indexName\n$mappings\n")
          val entity = new NStringEntity(
            mappings,
            ContentType.
            APPLICATION_JSON)

          client.
            performRequest(
              "PUT",
              s"/$indexName",
              Map.empty[String, String].asJava,
              entity).getStatusLine.
              getStatusCode match {
                case 200 =>
                  // now refresh to get it 'committed'
                  // todo: should do this after the new index is created so no index downtime
                  if (refresh) refreshIndex(indexName)
                case _ =>
                  logger.info(s"Index $indexName wasn't created, but may have quietly failed.")
              }
          true
        }
        case 200 =>
          logger.warn(s"Elasticsearch index: $indexName wasn't created because it already exists. " +
            s"This may be an error. Leaving the old index active.")
          false
        case _ =>
          throw new IllegalStateException(s"/$indexName is invalid.")
          false
      }
  }

  /** Commits any pending changes to the index */
  def refreshIndex(indexName: String): Unit = {
    client.performRequest(
      "POST",
      s"/$indexName/_refresh",
      Map.empty[String, String].asJava)
  }

  /** Create new index and hot-swap the new after it's indexed and ready to take over, then delete the old */
  def hotSwap(
    alias: String,
    typeName: String,
    indexRDD: RDD[Map[String, Any]],
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)] = Map.empty,
    numESWriteConnections: Option[Int] = None): Unit = {
    // get index for alias, change a char, create new one with new id and index it, swap alias and delete old one
    //val aliasMetadata = client.admin().indices().prepareGetAliases(alias).get().getAliases
    val newIndex = alias + "_" + DateTime.now().getMillis.toString

    logger.info(s"Create new index: $newIndex, $typeName, $fieldNames, $typeMappings")
    // todo: this should have a typeMappings that is Map[String, (String, Boolean)] with the Boolean saying to use norms
    // taken out for now since there is no client.admin in the REST client. Have to construct REST call directly
    createIndex(newIndex, typeName, fieldNames, typeMappings, true)

    // throttle writing to the max bulk-write connections, which is one per ES core.
    // todo: can we find this from the cluster itself?
    val repartitionedIndexRDD = if (numESWriteConnections.nonEmpty && indexRDD.context.defaultParallelism >
      numESWriteConnections.get) {
      logger.info(s"defaultParallelism: ${indexRDD.context.defaultParallelism}")
      logger.info(s"Coalesce to: ${numESWriteConnections.get} to reduce number of ES connections for saveToEs")
      indexRDD.coalesce(numESWriteConnections.get)
    } else {
      logger.info(s"Number of ES connections for saveToEs: ${indexRDD.context.defaultParallelism}")
      indexRDD
    }

    val newIndexURI = "/" + newIndex + "/" + typeName
    // TODO check if {"es.mapping.id": "id"} works on ESHadoop Interface of ESv5
    // Repartition to fit into Elasticsearch concurrency limits.

    val usernamePasswordOpt = configOpt flatMap { config =>
      val usernamePassword = (
        config.properties.get("USERNAME"),
        config.properties.get("PASSWORD"))
      usernamePassword match {
        case (None, None) => None
        case (username, password) => Some(
          (username.getOrElse(""), password.getOrElse("")))
      }
    }

    val esConfig = Map("es.mapping.id" -> "id") ++
      usernamePasswordOpt.map(usernamePassword =>
        Map(
          "es.net.http.auth.user" -> usernamePassword._1,
          "es.net.http.auth.pass" -> usernamePassword._2))
      .getOrElse(Map.empty[String, String]) ++
      configOpt.flatMap(config =>
        config.properties.get("SCHEMES").map(schemes =>
          if ("https" == schemes) {
            Map("es.net.ssl" -> "true")
          } else {
            Map("es.net.ssl" -> "false")
          })).getOrElse(Map.empty[String, String])
    repartitionedIndexRDD.saveToEs(newIndexURI, esConfig)

    // get index for alias, change a char, create new one with new id and index it, swap alias and delete old one

    val (oldIndexSet, deleteOldIndexQuery) = client.performRequest(
      // Does the alias exist?
      "HEAD",
      s"/_alias/$alias",
      Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
        case 200 => {
          val response = client.performRequest(
            "GET",
            s"/_alias/$alias",
            Map.empty[String, String].asJava)
          val responseJValue = parse(EntityUtils.toString(response.getEntity))
          val oldIndexSet = responseJValue.extract[Map[String, JValue]].keys
          val oldIndexName = oldIndexSet.head
          client.performRequest(
            // Does the old index exist?
            "HEAD",
            s"/$oldIndexName",
            Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
              case 200 => {
                val deleteOldIndexQuery = s""",{ "remove_index": { "index": "${oldIndexName}"}}"""
                (oldIndexSet, deleteOldIndexQuery)
              }
              case _ => (Set(), "")
            }
        }
        case _ => (Set(), "")
      }

    val aliasQuery =
      s"""
        |{
        |    "actions" : [
        |        { "add":  { "index": "${newIndex}", "alias": "${alias}" } }
        |        ${deleteOldIndexQuery}
        |    ]
        |}
      """.stripMargin.replace("\n", "")

    val entity = new NStringEntity(aliasQuery, ContentType.APPLICATION_JSON)
    client.performRequest(
      "POST",
      "/_aliases",
      Map.empty[String, String].asJava,
      entity)
    oldIndexSet.foreach(deleteIndex(_))
  }

  /** Performs a search using the JSON query String
   *
   *  @param query the JSON query string parable by Elasticsearch
   *  @param indexName the index to search
   *  @return a [PredictedResults] collection
   */
  def search(query: String, indexName: String): Option[JValue] = {
    logger.info(s"Query:\n${query}")
    val response = client.performRequest(
      "POST",
      s"/$indexName/_search",
      Map.empty[String, String].asJava,
      new StringEntity(query, ContentType.APPLICATION_JSON))
    response.getStatusLine.getStatusCode match {
      case 200 =>
        logger.info(s"Got source from query: ${query}")
        Some(parse(EntityUtils.toString(response.getEntity)))
      case _ =>
        logger.info(s"Query: ${query}\nproduced status code: ${response.getStatusLine.getStatusCode}")
        None
    }
  }

  /** Gets the "source" field of an Elasticsearch document
   *
   *  @param indexName index that contains the doc/item
   *  @param typeName type name used to construct ES REST URI
   *  @param doc for UR the item id
   *  @return source [java.util.Map] of field names to any valid field values or null if empty
   */
  def getSource(indexName: String, typeName: String, doc: String): Map[String, List[String]] = {
    val url = s"/$indexName/$typeName/${encodeURIFragment(doc)}"
    var rjv: Option[JValue] = Some(JString(""))
    logger.info(s"using URL: $url")
    try { // ES throws and exception when a doc is not found!!!! Why, oh why--the madness...
      logger.info("performing request")
      val response = client.performRequest(
        "GET",
        url,
        Map.empty[String, String].asJava)
      logger.info(s"got response: $response")

      rjv = response.getStatusLine.getStatusCode match {
        case 200 =>
          val entity = EntityUtils.toString(response.getEntity)
          logger.info(s"got status code: 200\nentity: ${entity}")
          if (entity.isEmpty) {
            None
          } else {
            logger.info(s"About to parse: $entity")
            Some(parse(entity))
          }
        case 404 =>
          logger.info(s"got status code: 404")
          Some(parse("""{"notFound": "true"}"""))
        case _ =>
          logger.info(s"got status code: ${response.getStatusLine.getStatusCode}\nentity: ${EntityUtils.toString(response.getEntity)}")
          None
      }
    } catch {
      case e: org.elasticsearch.client.ResponseException => {
        logger.info("got no data for the item")
        rjv = None
      }
      case _ =>
        logger.info("got unknown exception and so no data for the item")
        rjv = None
    }

    if (rjv.nonEmpty) {
      //val responseJValue = parse(EntityUtils.toString(response.getEntity))
      val result = (rjv.get \ "_source").values.asInstanceOf[Map[String, List[String]]]
      logger.info(s"getSource for ${url} result: ${result}")
      result
    } else {
      logger.info(s"Non-existent item $doc, but that's ok, return backfill recs")
      Map.empty
    }
  }

  def getIndexName(alias: String): Option[String] = {
    val response = client.performRequest(
      "GET",
      s"/_alias/$alias",
      Map.empty[String, String].asJava)
    val responseJValue = parse(EntityUtils.toString(response.getEntity))
    val allIndicesMap = responseJValue.extract[Map[String, JValue]]
    if (allIndicesMap.size == 1) {
      allIndicesMap.headOption.map(_._1)
    } else {
      // delete all the indices that are pointed to by the alias, they can't be used
      logger.warn("There is no 1-1 mapping of index to alias so deleting the old indexes that are referenced by the " +
        "alias. This may have been caused by a crashed or stopped `pio train` operation so try running it again.")
      if (!allIndicesMap.isEmpty) {
        allIndicesMap.keys.foreach(indexName => deleteIndex(indexName, refresh = true))
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

  import java.io.UnsupportedEncodingException

  def encodeURIFragment(s: String): String = {
    var result: String = ""
    try
      result = URLEncoder.encode(s, "UTF-8")
        .replaceAll("\\+", "%20")
        .replaceAll("\\%21", "!")
        .replaceAll("\\%27", "'")
        .replaceAll("\\%28", "(")
        .replaceAll("\\%29", ")")
        .replaceAll("\\%7E", "%7E")
    catch {
      case e: UnsupportedEncodingException =>
        result = s
    }
    result
  }

}

class BasicAuthProvider(
  val username: String,
  val password: String)
    extends HttpClientConfigCallback {

  val credentialsProvider = new BasicCredentialsProvider()
  credentialsProvider.setCredentials(
    AuthScope.ANY,
    new UsernamePasswordCredentials(username, password))

  override def customizeHttpClient(
    httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
  }
}
