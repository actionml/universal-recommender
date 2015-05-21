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

case class DataSourceParams(
   appName: String,
   eventNames: List[String])
  extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

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
        val userAction = try {
          if (!eventNames.contains(event.event))
            throw new Exception(s"Unexpected event ${event} is read.")
          // do we really want to throw and exception here? Maybe report and ignore the event
          // entityId and targetEntityId is String and all we care about for input validation
          (event.entityId, event.targetEntityId.get)
        } catch {
          case e: Exception => {
            logger.error(s"Cannot convert ${event} to a user action. Exception: ${e}.")
            throw e
          }
        }
        userAction
      }.cache()
      //todo: take out when not debugging
      val debugActions = actionRDD.take(5)
      //val indexedDataset = IndexedDatasetSpark(actionRDD)(sc)
      (eventName, actionRDD)
    }

    // should have a list of RDDs, one per action

    // for now all IndexedDatasets are considered to have users in rows so all must have the same
    // row dimentionality
    // todo: allows some data to be content, which will not have the same number of rows
    new TrainingData(actionRDDs)
  }
}

class TrainingData(
    val actions: List[(String, RDD[(String, String)])])
  extends Serializable {

  override def toString = {
    actions.map { t =>
      s"${t._1} actions: [count:${t._2.count()}] + sample:${t._2.take(2).toList} "
    }.toString()
  }

}