package com.finderbots

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

  case class ActionHistory(actionName: String, itemIDs: Set[String])
  val indexName = "mmrindex" //todo: where do we derive this, instance id?

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
    * todo: abstract out dealing with ES
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
    logger.info(s"Query received, user id: ${query.user}")

    val recentItems = getRecentItems(query, ap.eventNames, ap.maxQueryActions)

    val json =
      (
        ("size" -> ap.maxRecs) ~
        ("query"->
          ("bool"->
            ("should"->
              recentItems.map { i =>
                ("terms" -> (i.actionName -> i.itemIDs))}))))

    val esConfig = StorageClientConfig()
    val esClient = new elasticsearch.StorageClient(esConfig).client
    // todo: check for ES exceptions that need to be caught or explained!
    val sr = esClient.prepareSearch(indexName).setSource(compact(render(json))).get()

    if (!sr.isTimedOut) {
      val recs = sr.getHits.getHits.map( hit => new ItemScore(hit.getId, hit.getScore.toDouble) )
      logger.info(s"Recommendations for user id: ${query.user}, ${sr.getHits.getHits.size} retrieved of " +
        s"a possible ${sr.getHits.totalHits()}")
      new PredictedResult(recs)
    } else {
      logger.info(s"No prediction for unknown user ${query.user}.")
      new PredictedResult(Array.empty)
    }
  }

  /** Get recent events of the user on items to create the recommendations query from */
  def getRecentItems(query: Query, actions: List[String], maxQueryActions: Int): List[ActionHistory] = {

    actions.map { action =>

      val recentEvents = try {
        LEventStore.findByEntity(
          appName = ap.appName,
          // entityType and entityId is specified for fast lookup
          entityType = "user",
          entityId = query.user,
          // one query per eventName is not ideal, maybe one query for lots of events then split by eventName
          eventNames = Some(Seq(action)),
          targetEntityType = Some(Some("item")),
          limit = Some(maxQueryActions), // todo: multiple queries is not ideal so revisit to find a better way
          latest = true,
          // set time limit to avoid super long DB access
          timeout = Duration(200, "millis")
        )
      } catch {
        case e: scala.concurrent.TimeoutException =>
          logger.error(s"Timeout when read recent events." +
            s" Empty list is used. ${e}")
          Iterator[Event]()
        case e: Exception =>
          logger.error(s"Error when read recent events: ${e}")
          throw e
      }

      val recentItems: Set[String] = recentEvents.map { event =>
        try {
          event.targetEntityId.get
        } catch {
          case e => {
            logger.error("Can't get targetEntityId of event ${event}.")
            throw e
          }
        }
      }.toSet

      ActionHistory(action, recentItems)
    }
  }

}