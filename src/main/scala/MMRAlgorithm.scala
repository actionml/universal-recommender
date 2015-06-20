package org.template

import java.util

import io.prediction.controller.P2LAlgorithm
import io.prediction.controller.Params
import io.prediction.data.storage.{elasticsearch, StorageClientConfig, Event, BiMap}
import io.prediction.data.store.LEventStore
import org.apache.mahout.math.cf.SimilarityAnalysis
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchRequest}
import org.elasticsearch.index.query.QueryBuilder
import scala.concurrent.duration.Duration
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.convert.wrapAsScala._
import scala.collection.convert.decorateAll._
import grizzled.slf4j.Logger
/*
“fields”: [
   {
     “name”: ”fieldname”
     “values”: [“series”, ...],// values in the field query
     “bias”: -maxFloat..maxFloat// negative means a filter, positive is a boost
   }, ...
 ]
*/
case class Field(name: String, values: Array[String], bias: Float)

/** Instantiated from engine.json */
case class MMRAlgorithmParams(
  appName: String,// filled in from engine.json
  indexName: String = "mmrindex", // can optionally be used to specify the elasticsearch index name
  typeName: String = "items", // can optionally be used to specify the elasticsearch type name
  eventNames: List[String],// names used to ID all user actions
  maxQueryActions: Int,
  maxRecs: Int,
  fieldNames: Array[Field],
  seed: Option[Long]) extends Params //fixed default make it reproducable unless supplied

/** Creates cooccurrence, cross-cooccurrence and eventually content indicators with
  * [[org.apache.mahout.math.cf.SimilarityAnalysis]] The analysis part of the recommender is
  * done here but the algorithm can predict only when the coocurrence data is indexed in a
  * search engine like Elasticsearch. This is done in MMRModel.save.
  *
  * @param ap taken from engine.json to describe limits and event types
  */
class MMRAlgorithm(val ap: MMRAlgorithmParams)
  extends P2LAlgorithm[PreparedData, MMRModel, Query, PredictedResult] {

  case class Indicators(actionName: String, itemIDs: Seq[String])
  val indexName = ap.indexName // taken from engine.json

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): MMRModel = {
    // No one likes empty training data.
    require(data.actions.take(1).nonEmpty,
      s"Primary action in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")

    logger.info("Actions read now creating indicators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.actions.map(_._2).toArray) // strip action names
      .map(_.asInstanceOf[IndexedDatasetSpark]) // we know this is a Spark version of IndexedDataset
    val cooccurrenceIndicators = cooccurrenceIDSs.zip(data.actions.map(_._1)).map(_.swap) //add back the actionNames

    logger.info("Indicators created now putting into MMRModel")
    new MMRModel(cooccurrenceIndicators, data.fieldsRDD, indexName)
  }

  /** Return a list of items recommended for a user identified in the query
    * todo: move to the esClient object
    * The ES json query looks like this:
    *  {
    *    "size": 20
    *    "query": {
    *      "bool": {
    *        "should": [
    *          {
    *            "terms": {
    *              "rate": ["0", "67", "4"]
    *            }
    *          },
    *          {
    *            "terms": {
    *              "buy": ["0", "32"]
    *            }
    *          }
    *        ]
    *      }
    *    }
    *  }
    *
    * @param model <strong>Ignored!</strong> since the model is already in Elasticsearch
    * @param query contains the user id to recommend for
    * @return
    */
  def predict(model: MMRModel, query: Query): PredictedResult = {
    logger.info(s"Query received, user id: ${query.user}, item id: ${query.item}")

    esClient.search(buildQuery(ap, query), indexName)
  }

  def buildQuery(ap: MMRAlgorithmParams, query: Query): String = {

    val user = query.user
    val item = query.item

    val recentUserHistory = getRecentItems(query, ap.eventNames, ap.maxQueryActions)
    val similarItems = getSimilarItemsWithMetadata(item, ap.eventNames)
    val allIndicators = recentUserHistory ++ similarItems

    //val indicatorArray = allIndicators.toArray

    // since users have action history and items have indicators and both correspond to the same "actions" like
    // purchase or view, we'll pass both to the query if the user history or items indicators are empty
    // then metadata must be relied on to return results.

    val json =
      (
        ("size" -> ap.maxRecs) ~
          ("query"->
            ("bool"->
              ("should"->
                allIndicators.map { i =>
                  ("terms" -> (i.actionName -> i.itemIDs) ~ ("boost" -> 1))}

            ))))
    val j = compact(render(json))
    compact(render(json))
  }
/*

                similarItems.get.map { i =>
                  ("terms" -> (i.indicatorName -> i.itemIDs)) ~
                  ("boost" -> 1)

 */
  /** Get similar items for an item, these are already in the item and the action indicators in ES */
  def getSimilarItemsWithMetadata(item: String, actions: List[String]): Seq[Indicators] = {
    val m = esClient.getSource(ap.indexName, ap.typeName, item)

    actions.map { action =>
      val items = try {
        m.get(action).asInstanceOf[util.ArrayList[String]]
      } catch {
        case cce: ClassCastException =>
          logger.warn(s"Bad value in item ${item} corresponding to key: ${action} that was not a List[String]" +
          " ignored.")
          new util.ArrayList[String]
      }
      val rItems = if (items.size() <= ap.maxQueryActions) items else items.subList(0, ap.maxQueryActions - 1)
      Indicators(action, rItems)
    }

  }

  /** Get recent events of the user on items to create the recommendations query from */
  // todo: split up into lsit per action
  def getRecentItems(query: Query, actions: List[String], maxQueryActions: Int): Seq[Indicators] = {


    val recentEvents = try {
      LEventStore.findByEntity(
        appName = ap.appName,
        // entityType and entityId is specified for fast lookup
        entityType = "user",
        entityId = query.user,
        // one query per eventName is not ideal, maybe one query for lots of events then split by eventName
        //eventNames = Some(Seq(action)),// get all and separate later
        targetEntityType = Some(Some("item")),
        // limit = Some(maxQueryActions), // this will get all history then each action can be limited before using in
        // the query
        latest = true,
        // set time limit to avoid super long DB access
        timeout = Duration(200, "millis")
      ).toList
    } catch {
      case e: scala.concurrent.TimeoutException =>
        logger.error(s"Timeout when read recent events." +
          s" Empty list is used. ${e}")
        Iterator[Event]()
      case e: Exception => // fatal because of error, an empty query
        logger.error(s"Error when read recent events: ${e}")
        throw e
    }

    actions.map { action =>
      var items = List[String]()

      for ( event <- recentEvents )
        if (event.event == action && items.size < ap.maxQueryActions) {
          items = event.targetEntityId.get :: items
          // todo: may throw exception and we should ignore the event instead of crashing
        }
      Indicators(action, items)
    }

  }

}