package org.template

import grizzled.slf4j.Logger
import io.prediction.data.storage.Event
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import io.prediction.data.store.PEventStore
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, Interval}


object PopModel {

  @transient lazy val logger = Logger[this.type]

  def calc (
    modelName: Option[String] = None,
    eventNames: List[String],
    appName: String,
    duration: Int = 0,
    startDateOption: Option[String] = None)(implicit sc: SparkContext): Option[RDD[(String, Float)]] = {

    // startDate should always be 'now' except in unusual conditions like for testing
    val startDate = if (startDateOption.isEmpty ) DateTime.now else {
      try {
        ISODateTimeFormat.dateTimeParser().parseDateTime(startDateOption.get)
      } catch {
        case e: IllegalArgumentException => e
          logger.warn("Bad startDate for popModel: " + startDateOption.get + " using 'now'")
          DateTime.now
      }
    }

    // based on type of popularity model return a set of (item-id, ranking-number) for all items
    modelName match {
      case Some("popular") => calcPopular(appName, eventNames, new Interval(startDate.minusSeconds(duration), startDate))
      case Some("trending") => calcTrending(appName, eventNames, new Interval(startDate.minusSeconds(duration), startDate))
      case Some("hot") => calcHot(appName, eventNames, new Interval(startDate.minusSeconds(duration), startDate))
      case _ => None // debatable, this is either an error or may need to default to popular, why call popModel otherwise
    }
  }

  /** Creates a rank from the number of named events per item for the duration */
  def calcPopular(appName: String, eventNames: List[String] = List.empty,
    interval: Interval)(implicit sc: SparkContext): Option[RDD[(String, Float)]] = {

    val events = eventsRDD(appName, eventNames, interval)
    val retval = events.map { e => (e.targetEntityId, e.event) }
      .groupByKey()
      .map { case(itemID, itEvents) => (itemID.get, itEvents.size.toFloat)}
      .reduceByKey (_+_) // make this a double in Elaseticsearch)
    if (!retval.isEmpty()) Some(retval) else None
  }

  /** Creates a rank for each item by dividing the duration in two and counting named events in both buckets
    * then dividing most recent by less recent. This ranks by change in popularity or velocity of populatiy change.
    * Interval(start, end) end instant is always greater than or equal to the start instant.
    */
  def calcTrending(appName: String, eventNames: List[String] = List.empty,
    interval: Interval)(implicit sc: SparkContext): Option[RDD[(String, Float)]] = {

    val olderInterval = new Interval(interval.getStart,
      interval.getStart().plusMillis((interval.toDurationMillis/2)toInt))
    val newerInterval = new Interval(interval.getStart().plusMillis((interval.toDurationMillis/2)toInt), interval.getEnd)

    val intervalMillis = interval.toDurationMillis
    val olderPopRDD = calcPopular(appName, eventNames, olderInterval)
    if ( olderPopRDD.nonEmpty) {
      val newerPopRDD = calcPopular(appName, eventNames, newerInterval)
      if ( newerPopRDD.nonEmpty ) {
        val retval = newerPopRDD.get.join(olderPopRDD.get).map { case (item, (newerScore, olderScore)) =>
          val velocity = (newerScore - olderScore)
          (item, velocity)
        }
        if (!retval.isEmpty()) Some(retval) else None
      } else None
    } else None
  }

  /** Creates a rank for each item by divding all events per item into three buckets and calculating the change in
    * velocity over time, in other words the acceleration of popularity change.
    */
  def calcHot(appName: String, eventNames: List[String] = List.empty,
    interval: Interval)(implicit sc: SparkContext): Option[RDD[(String, Float)]] = {
    val olderInterval = new Interval(interval.getStart,
      interval.getStart().plusMillis((interval.toDurationMillis/3)toInt))
    val middleInterval = new Interval(olderInterval.getEnd,
      olderInterval.getEnd().plusMillis((olderInterval.toDurationMillis)toInt))
    val newerInterval = new Interval(middleInterval.getEnd, interval.getEnd)

    val olderPopRDD = calcPopular(appName, eventNames, olderInterval)
    if (olderPopRDD.nonEmpty){
      val middlePopRDD = calcPopular(appName, eventNames, middleInterval)
      if (middlePopRDD.nonEmpty){
        val newerPopRDD = calcPopular(appName, eventNames, newerInterval)
        if (newerPopRDD.nonEmpty){
          val newVelocityRDD = newerPopRDD.get.join(middlePopRDD.get).map { case( item, (newerScore, olderScore)) =>
            val velocity = (newerScore - olderScore)
            (item, velocity)
          }
          val oldVelocityRDD = middlePopRDD.get.join(olderPopRDD.get).map { case( item, (newerScore, olderScore)) =>
            val velocity = (newerScore - olderScore)
            (item, velocity)
          }
          Some( newVelocityRDD.join(oldVelocityRDD).map { case (item, (newVelocity, oldVelocity)) =>
            val acceleration = (newVelocity - oldVelocity)
            (item, acceleration)
          })
        } else None
      } else None
    } else None
  }

  def eventsRDD(appName: String, eventNames: List[String], interval: Interval)
    (implicit sc: SparkContext): RDD[Event] = {

    PEventStore.find(
      appName = appName,
      startTime = Some(interval.getStart),
      untilTime = Some(interval.getEnd),
      eventNames = Some(eventNames)
    )(sc)
  }

}
