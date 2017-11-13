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

import grizzled.slf4j.Logger
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.store.PEventStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTime, Interval }
import com.actionml.helpers.{ ItemID, ItemProps }

import scala.language.postfixOps
import scala.util.Random

object RankingFieldName {
  val UserRank = "userRank"
  val UniqueRank = "uniqueRank"
  val PopRank = "popRank"
  val TrendRank = "trendRank"
  val HotRank = "hotRank"
  val UnknownRank = "unknownRank"
  def toSeq: Seq[String] = Seq(UserRank, UniqueRank, PopRank, TrendRank, HotRank)
  override def toString: String = s"$UserRank, $UniqueRank, $PopRank, $TrendRank, $HotRank"
}

object RankingType {
  val Popular = "popular"
  val Trending = "trending"
  val Hot = "hot"
  val UserDefined = "userDefined"
  val Random = "random"
  def toSeq: Seq[String] = Seq(Popular, Trending, Hot, UserDefined, Random)
  override def toString: String = s"$Popular, $Trending, $Hot, $UserDefined, $Random"
}

class PopModel(fieldsRDD: RDD[(ItemID, ItemProps)])(implicit sc: SparkContext) {

  @transient lazy val logger: Logger = Logger[this.type]

  def calc(
    modelName: String,
    eventNames: Seq[String],
    appName: String,
    duration: Int = 0,
    offsetDate: Option[String] = None): RDD[(ItemID, Double)] = {

    // todo: make end manditory and fill it with "now" upstream if not specified, will simplify logic here
    // end should always be 'now' except in unusual conditions like for testing
    val end = if (offsetDate.isEmpty) DateTime.now else {
      try {
        ISODateTimeFormat.dateTimeParser().parseDateTime(offsetDate.get)
      } catch {
        case e: IllegalArgumentException =>
          logger.warn("Bad end for popModel: " + offsetDate.get + " using 'now'")
          DateTime.now
      }
    }

    val interval = new Interval(end.minusSeconds(duration), end)

    // based on type of popularity model return a set of (item-id, ranking-number) for all items
    logger.info(s"PopModel $modelName using end: $end, and duration: $duration, interval: $interval")

    // if None? debatable, this is either an error or may need to default to popular, why call popModel otherwise
    modelName match {
      case RankingType.Popular     => calcPopular(appName, eventNames, interval)
      case RankingType.Trending    => calcTrending(appName, eventNames, interval)
      case RankingType.Hot         => calcHot(appName, eventNames, interval)
      case RankingType.Random      => calcRandom(appName, interval)
      case RankingType.UserDefined => sc.emptyRDD
      case unknownRankingType =>
        logger.warn(
          s"""
             |Bad rankings param type=[$unknownRankingType] in engine definition params, possibly a bad json value.
             |Use one of the available parameter values ($RankingType).""".stripMargin)
        sc.emptyRDD
    }

  }

  /** Create random rank for all items */
  def calcRandom(
    appName: String,
    interval: Interval): RDD[(ItemID, Double)] = {

    val events = eventsRDD(appName = appName, interval = interval)
    val actionsRDD = events.map(_.targetEntityId).filter(_.isDefined).map(_.get).distinct()
    val itemsRDD = fieldsRDD.map { case (itemID, _) => itemID }

    //    logger.debug(s"ActionsRDD: ${actionsRDD.take(25).mkString(", ")}")
    //    logger.debug(s"ItemsRDD: ${itemsRDD.take(25).mkString(", ")}")
    actionsRDD.union(itemsRDD).distinct().map { itemID => itemID -> Random.nextDouble() }
  }

  /** Creates a rank from the number of named events per item for the duration */
  def calcPopular(
    appName: String,
    eventNames: Seq[String],
    interval: Interval): RDD[(ItemID, Double)] = {
    val events = eventsRDD(appName, eventNames, interval)
    events.map { e => (e.targetEntityId, e.event) }
      .groupByKey()
      .map { case (itemID, itEvents) => (itemID.get, itEvents.size.toDouble) }
      .reduceByKey(_ + _) // make this a double in Elaseticsearch)
  }

  /** Creates a rank for each item by dividing the duration in two and counting named events in both buckets
   *  then dividing most recent by less recent. This ranks by change in popularity or velocity of populatiy change.
   *  Interval(start, end) end instant is always greater than or equal to the start instant.
   */
  def calcTrending(
    appName: String,
    eventNames: Seq[String],
    interval: Interval): RDD[(ItemID, Double)] = {

    logger.info(s"Current Interval: $interval, ${interval.toDurationMillis}")
    val halfInterval = interval.toDurationMillis / 2
    val olderInterval = new Interval(interval.getStart, interval.getStart.plus(halfInterval))
    logger.info(s"Older Interval: $olderInterval")
    val newerInterval = new Interval(interval.getStart.plus(halfInterval), interval.getEnd)
    logger.info(s"Newer Interval: $newerInterval")

    val olderPopRDD = calcPopular(appName, eventNames, olderInterval)
    if (!olderPopRDD.isEmpty()) {
      val newerPopRDD = calcPopular(appName, eventNames, newerInterval)
      newerPopRDD.join(olderPopRDD).map {
        case (item, (newerScore, olderScore)) => item -> (newerScore - olderScore)
      }
    } else sc.emptyRDD

  }

  /** Creates a rank for each item by divding all events per item into three buckets and calculating the change in
   *  velocity over time, in other words the acceleration of popularity change.
   */
  def calcHot(
    appName: String,
    eventNames: Seq[String] = List.empty,
    interval: Interval): RDD[(ItemID, Double)] = {

    logger.info(s"Current Interval: $interval, ${interval.toDurationMillis}")
    val olderInterval = new Interval(interval.getStart, interval.getStart.plus(interval.toDurationMillis / 3))
    logger.info(s"Older Interval: $olderInterval")
    val middleInterval = new Interval(olderInterval.getEnd, olderInterval.getEnd.plus(olderInterval.toDurationMillis))
    logger.info(s"Middle Interval: $middleInterval")
    val newerInterval = new Interval(middleInterval.getEnd, interval.getEnd)
    logger.info(s"Newer Interval: $newerInterval")

    val olderPopRDD = calcPopular(appName, eventNames, olderInterval)
    if (!olderPopRDD.isEmpty()) { // todo: may want to allow an interval with no events, give them 0 counts
      val middlePopRDD = calcPopular(appName, eventNames, middleInterval)
      if (!middlePopRDD.isEmpty()) {
        val newerPopRDD = calcPopular(appName, eventNames, newerInterval)
        val newVelocityRDD = newerPopRDD.join(middlePopRDD).map {
          case (item, (newerScore, middleScore)) => item -> (newerScore - middleScore)
        }
        val oldVelocityRDD = middlePopRDD.join(olderPopRDD).map {
          case (item, (middleScore, olderScore)) => item -> (middleScore - olderScore)
        }
        newVelocityRDD.join(oldVelocityRDD).map {
          case (item, (newVelocity, oldVelocity)) => item -> (newVelocity - oldVelocity)
        }
      } else sc.emptyRDD
    } else sc.emptyRDD
  }

  def eventsRDD(
    appName: String,
    eventNames: Seq[String] = Seq.empty,
    interval: Interval): RDD[Event] = {

    logger.info(s"PopModel getting eventsRDD for startTime: ${interval.getStart} and endTime ${interval.getEnd}")
    PEventStore.find(
      appName = appName,
      startTime = Some(interval.getStart),
      untilTime = Some(interval.getEnd),
      eventNames = if (eventNames.nonEmpty) Some(eventNames) else None)(sc).repartition(sc.defaultParallelism)
  }

}

object PopModel {

  def apply(fieldsRDD: RDD[(ItemID, ItemProps)])(implicit sc: SparkContext): PopModel = {
    new PopModel(fieldsRDD)
  }

  val nameByType: Map[String, String] = Map(
    RankingType.Popular -> RankingFieldName.PopRank,
    RankingType.Trending -> RankingFieldName.TrendRank,
    RankingType.Hot -> RankingFieldName.HotRank,
    RankingType.UserDefined -> RankingFieldName.UserRank,
    RankingType.Random -> RankingFieldName.UniqueRank).withDefaultValue(RankingFieldName.UnknownRank)

}
