package com.finderbots

import io.prediction.controller.PAlgorithm
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
case class MRAlgorithmParams(
  appName: String,// filled in from engine.json
  eventNames: List[String],// names used to ID all user actions
  seed: Option[Long]) extends Params //fixed default make it reproducable unless supplied

class MMRAlgorithm(val ap: MRAlgorithmParams)
  extends PAlgorithm[PreparedData, MMRModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): MMRModel = {
    // No one likes empty training data.
    require(data.actions.take(1).nonEmpty,
      s"Primary action in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    // todo: do the cooccurrence calc here but wait for MMRModel.save to store in ES?
    val debug = data.actions(0)._2.asInstanceOf[IndexedDatasetSpark].matrix.collect
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(data.actions.map(_._2).toArray) //strip actionNamed
    val cooccurrenceIndicators = cooccurrenceIDSs.zip(data.actions.map(_._1)) //add back the actionNames
    // todo: need to save rdds for user history and cooccurrence

    new MMRModel(/* something like cooccurrence and user history IndexedDataset and ES object? */)
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

  /** Get recent events of the user on items for recommending similar items */
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