package org.template

import io.prediction.data.storage.Event
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import io.prediction.data.store.PEventStore
import org.joda.time.DateTime

object PopModel {
  def calc (
    modelName: Option[String] = None,
    eventNames: List[String],
    appName: String,
    duration: Int = 0 )(implicit sc: SparkContext): Option[RDD[(String, Double)]] = {

    // based on type of popularity model return a set of (item-id, ranking-number) for all items
    modelName match {
      case Some("all") => calcPopular(appName, eventNames, duration)
      case Some("popular") => calcPopular(appName, eventNames, duration)
      case Some("trending") => calcTrending(appName, eventNames, duration)
      case Some("hot") => calcHot(appName, eventNames, duration)
      case _ => None
    }
  }

  /* wordcount
  val textFile = spark.textFile("hdfs://...")
  val counts = textFile.flatMap(line => line.split(" "))
                 .map(word => (word, 1))
                 .reduceByKey(_ + _)
   */
  def calcPopular(appName: String, eventNames: List[String] = List.empty,
    duration: Int = 0 )(implicit sc: SparkContext): Option[RDD[(String, Double)]] = {

    val events = getEventsRDD(appName, eventNames, DateTime.now().minusSeconds(duration))
    val retval = Some( events.map { e => (e.targetEntityId, e.event) }
      .groupByKey()
      .map { case(itemID, itEvents) => (itemID.get, itEvents.size.toDouble)}
      .reduceByKey (_+_) )
    val itemEventCounts = retval.get.collect()
    retval

  }

  def calcTrending(appName: String, eventNames: List[String] = List.empty,
    duration: Int = 0 )(implicit sc: SparkContext): Option[RDD[(String, Double)]] = {
    None
  }

  def calcHot(appName: String, eventNames: List[String] = List.empty,
    duration: Int = 0 )(implicit sc: SparkContext): Option[RDD[(String, Double)]] = {
    None
  }


  /*  def find(
    appName : scala.Predef.String,
    channelName : scala.Option[scala.Predef.String] = { /* compiled code */ },
    startTime : scala.Option[org.joda.time.DateTime] = { /* compiled code */ },
    untilTime : scala.Option[org.joda.time.DateTime] = { /* compiled code */ },
    entityType : scala.Option[scala.Predef.String] = { /* compiled code */ },
    entityId : scala.Option[scala.Predef.String] = { /* compiled code */ },
    eventNames : scala.Option[scala.Seq[scala.Predef.String]] = { /* compiled code */ },
    targetEntityType : scala.Option[scala.Option[scala.Predef.String]] = { /* compiled code */ },
    targetEntityId : scala.Option[scala.Option[scala.Predef.String]] = { /* compiled code */ })(sc : org.apache.spark.SparkContext) : org.apache.spark.rdd.RDD[io.prediction.data.storage.Event] = { /* compiled code */ }
   */

  def getEventsRDD(appName: String, eventNames: List[String], begin: DateTime)(implicit sc: SparkContext): RDD[Event] = {

    val endTime = DateTime.now()
    PEventStore.find(
      appName = appName,
      startTime = Some(begin),
      untilTime = Some(endTime),
      eventNames = Some(eventNames)
    )(sc)
  }

}
