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

import org.apache.predictionio.controller.{ EmptyActualResult, EmptyEvaluationInfo, PDataSource, Params }
import org.apache.predictionio.data.storage.PropertyMap
import org.apache.predictionio.data.store.PEventStore
import grizzled.slf4j.Logger
import org.apache.predictionio.core.{ EventWindow, SelfCleaningDataSource }
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import com.actionml.helpers.{ ActionID, ItemID }
import com.actionml.helpers._

/** Taken from engine.json these are passed in to the DataSource constructor
 *
 *  @param appName registered name for the app
 *  @param eventNames a list of named events expected. The first is the primary event, the rest are secondary. These
 *                   will be used to create the primary correlator and cross-cooccurrence secondary correlators.
 */
case class DataSourceParams(
  appName: String,
  eventNames: List[String], // IMPORTANT: eventNames must be exactly the same as URAlgorithmParams eventNames
  eventWindow: Option[EventWindow],
  minEventsPerUser: Option[Int]) // defaults to 1 event, if the user has only one thehy will not contribute to
    // training anyway
    extends Params

/** Read specified events from the PEventStore and creates RDDs for each event. A list of pairs (eventName, eventRDD)
 *  are sent to the Preparator for further processing.
 *  @param dsp parameters taken from engine.json
 */
class DataSource(val dsp: DataSourceParams)
    extends PDataSource[TrainingData, EmptyEvaluationInfo, Query, EmptyActualResult]
    with SelfCleaningDataSource {

  @transient override lazy implicit val logger: Logger = Logger[this.type]

  override def appName: String = dsp.appName
  override def eventWindow: Option[EventWindow] = dsp.eventWindow

  drawInfo("Init DataSource", Seq(
    ("══════════════════════════════", "════════════════════════════"),
    ("App name", appName),
    ("Event window", eventWindow),
    ("Event names", dsp.eventNames),
    ("Min events per user", dsp.minEventsPerUser)))

  /** Reads events from PEventStore and create and RDD for each */
  override def readTraining(sc: SparkContext): TrainingData = {

    val eventNames = dsp.eventNames

    // beware! the following call most likely will alter the event stream in the DB!
    cleanPersistedPEvents(sc) // broken in apache-pio v0.10.0-incubating it erases all data!!!!!!

    val eventsRDD = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(eventNames),
      targetEntityType = Some(Some("item")))(sc).repartition(sc.defaultParallelism)

    // now separate the events by event name
    val eventRDDs: List[(ActionID, RDD[(UserID, ItemID)])] = eventNames.map { eventName =>
      val singleEventRDD = eventsRDD.filter { event =>
        require(eventNames.contains(event.event), s"Unexpected event $event is read.") // is this really needed?
        require(event.entityId.nonEmpty && event.targetEntityId.get.nonEmpty, "Empty user or item ID")
        eventName.equals(event.event)
      }.map { event =>
        (event.entityId, event.targetEntityId.get)
      }

      (eventName, singleEventRDD)
    } filterNot { case (_, singleEventRDD) => singleEventRDD.isEmpty() }

    logger.info(s"Received events ${eventRDDs.map(_._1)}")

    // aggregating all $set/$unsets for metadata fields, which are attached to items
    val fieldsRDD: RDD[(ItemID, PropertyMap)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "item")(sc).repartition(sc.defaultParallelism)
    //    logger.debug(s"FieldsRDD\n${fieldsRDD.take(25).mkString("\n")}")

    // Have a list of (actionName, RDD), for each action
    // todo: some day allow data to be content, which requires rethinking how to use EventStore
    TrainingData(eventRDDs, fieldsRDD, dsp.minEventsPerUser)
  }
}

/** Low level RDD based representation of the data ready for the Preparator
 *
 *  @param actions List of Tuples (actionName, actionRDD)qw
 *  @param fieldsRDD RDD of item keyed PropertyMap for item metadata
 *  @param minEventsPerUser users with less than this many events will not removed from training data
 */
case class TrainingData(
    actions: Seq[(ActionID, RDD[(UserID, ItemID)])],
    fieldsRDD: RDD[(ItemID, PropertyMap)],
    minEventsPerUser: Option[Int] = Some(1)) extends Serializable {

  override def toString: String = {
    val a = actions.map { t =>
      s"${t._1} actions: [count:${t._2.count()}] + sample:${t._2.take(2).toList} "
    }.toString()
    val f = s"Item metadata: [count:${fieldsRDD.count}] + sample:${fieldsRDD.take(2).toList} "
    a + f
  }

}
