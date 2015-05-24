package com.finderbots

import io.prediction.controller.P2LAlgorithm
import io.prediction.controller.Params
import io.prediction.data.storage.{Event, BiMap}
import io.prediction.data.store.LEventStore
import org.apache.mahout.math.cf.SimilarityAnalysis
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import scala.concurrent.duration.Duration
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

/**todo: probably need mappings here */



/** Instantiated from engine.json */
case class MMRAlgorithmParams(
  appName: String,// filled in from engine.json
  eventNames: List[String],// names used to ID all user actions
  seed: Option[Long]) extends Params //fixed default make it reproducable unless supplied

/** Creates cooccurrence, cross-cooccurrence and eventually content indicators with
  * [[org.apache.mahout.math.cf.SimilarityAnalysis]] The analysis part of the recommender is
  * done here but the algorithm can predict only when the coocurrence data is indexed in a
  * search engine like Elasticsearch. This is done in MMRModel.save.
  */
class MMRAlgorithm(val ap: MMRAlgorithmParams)
  extends P2LAlgorithm[PreparedData, MMRModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): MMRModel = {
    // No one likes empty training data.
    require(data.actions.take(1).nonEmpty,
      s"Primary action in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    // todo: remove when not debugging
    val debug = data.actions(0)._2.asInstanceOf[IndexedDatasetSpark].matrix.collect
    logger.info("Actions read now creating indicators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.actions.map(_._2).toArray) // strip action names
      .map(_.asInstanceOf[IndexedDatasetSpark]) // we know this is a Spark version of IndexedDataset
    val cooccurrenceIndicators = cooccurrenceIDSs.zip(data.actions.map(_._1)).map(_.swap) //add back the actionNames

    val indexName = "mmrindex" //todo: where do we derive this, instance id?

    // todo: remove when not debugging
    val debug2 = cooccurrenceIndicators(0)._2.matrix.collect
    logger.info("Indicators created now putting into MMRModel")
    new MMRModel(cooccurrenceIndicators, indexName)
  }

  def predict(model: MMRModel, query: Query): PredictedResult = {
    logger.info(s"No results yet tell Pat to get off his ass and write the code!")

    {
      // todo: get user events from event store, query ES and return recs
      Some(new PredictedResult(Array.empty))
    }.getOrElse{
      logger.info(s"No prediction for unknown user ${query.user}.")
      new PredictedResult(Array.empty)
    }
  }

  // boilerplate from inside PIO for getting the query, which will be user action history
  // needs to be modified for use here
  /** Get recent events of the user on items for recommending similar items */
  def getRecentItems(query: Query): Set[String] = {
    // get latest 10 user view item events
    val recentEvents = try {
      LEventStore.findByEntity(
        appName = ap.appName,
        // entityType and entityId is specified for fast lookup
        entityType = "user",
        entityId = query.user,
        eventNames = Some(List("rate", "buy")),// todo: get actual names from engine.json
        targetEntityType = Some(Some("item")),
        limit = Some(10),// todo: may have to do separate queries for each event to get the best number
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

    recentItems
  }

}