package com.finderbots

import _root_.io.prediction.controller.PDataSource
import _root_.io.prediction.controller.EmptyEvaluationInfo
import _root_.io.prediction.controller.EmptyActualResult
import _root_.io.prediction.controller.Params
import _root_.io.prediction.data.storage.Event
import _root_.io.prediction.data.store.PEventStore
import org.apache.mahout.math.RandomAccessSparseVector
import org.apache.mahout.math.indexeddataset.{BiDictionary, IndexedDataset}
import org.apache.mahout.sparkbindings._
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

/** Taken from engine.json these are passed in to the DataSource constructor
  *
  * @param appName registered name for the app
  * @param eventNames a list of named events expected. The first is the primary event, the rest are secondary. These
  *                   will be used to create the primary indicator and cross-cooccurrence secondary indicators.
  */
case class DataSourceParams(
   appName: String,
   eventNames: List[String])
  extends Params

/** Read specified events from the PEventStore and creates RDDs for each event. A list of pairs (eventName, eventRDD)
  * are sent to the Preparator for further processing.
  * @param dsp parameters taken from engine.json
  */
class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  /** Reads events from PEventStore and creates and RDD for each */
  override
  def readTraining(sc: SparkContext): TrainingData = {

    val eventNames = dsp.eventNames //todo: get from engine.json, the first is the primary

    val eventsRDD: RDD[Event] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(eventNames),
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("item")))(sc)

    val actionRDDs = eventNames.map { eventName =>
      val actionRDD = eventsRDD.map { event =>

        require(eventNames.contains(event.event), s"Unexpected event ${event} is read.") // is this really needed?
        require(event.entityId.nonEmpty && event.targetEntityId.get.nonEmpty, "Empty user or item ID")

        (event.entityId, event.targetEntityId.get)

      }.cache()
      //todo: take out when not debugging
      val debugActions = actionRDD.take(5)
      (eventName, actionRDD)
    }

    // Have a list of (actionName, RDD), for each action
    // todo: should allow some data to be content indicators, which requires rethinking how to use PEventStore
    new TrainingData(actionRDDs)
  }
}

/** Low level RDD based representation of the data ready for the Preparator
  *
  * @param actions List of Tuples (actionName, actionRDD)
  */
class TrainingData(
    val actions: List[(String, RDD[(String, String)])])
  extends Serializable {

  override def toString = {
    actions.map { t =>
      s"${t._1} actions: [count:${t._2.count()}] + sample:${t._2.take(2).toList} "
    }.toString()
  }

}