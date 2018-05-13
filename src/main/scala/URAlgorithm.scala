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

import java.util

import grizzled.slf4j.Logger
import org.apache.predictionio.controller.{ P2LAlgorithm, Params }
import org.apache.predictionio.data.storage.{ DataMap, Event, NullModel, PropertyMap }
import org.apache.predictionio.data.store.LEventStore
import org.apache.mahout.math.cf.{ DownsamplableCrossOccurrenceDataset, SimilarityAnalysis }
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import org.json4s.JValue
import org.json4s.DefaultFormats
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.actionml.helpers._

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.language.{ implicitConversions, postfixOps }

/** Available value for algorithm param "RecsModel" */
object RecsModels { // todo: replace this with rankings
  val All = "all"
  val CF = "collabFiltering"
  val BF = "backfill"
  override def toString: String = s"$All, $CF, $BF"
}

/** Setting the option in the params case class doesn't work as expected when the param is missing from
 *  engine.json so set these for use in the algorithm when they are not present in the engine.json
 */
object DefaultURAlgoParams {
  val MaxEventsPerEventType = 500
  val NumResults = 20
  val MaxCorrelatorsPerEventType = 50
  val MaxQueryEvents = 100 // default number of user history events to use in recs query

  val ExpireDateName = "expireDate" // default name for the expire date property of an item
  val AvailableDateName = "availableDate" //defualt name for and item's available after date
  val DateName = "date" // when using a date range in the query this is the name of the item's date
  val RecsModel = RecsModels.All // use CF + backfill
  //val RankingParams = RankingParams()
  val BackfillFieldName = RankingFieldName.PopRank
  val BackfillType = RankingType.Popular
  val BackfillDuration = "3650 days" // for all time

  val ReturnSelf = false
  val NumESWriteConnections: Option[Int] = None
}

/* default values must be set in code not the case class declaration
case class BackfillField(
  name: Option[String] = Some(DefaultURAlgoParams.BackfillFieldName),
  backfillType: Option[String] = Some(DefaultURAlgoParams.BackfillType), // may be 'hot', or 'trending' also
  eventNames: Option[Seq[String]] = None, // None means use the algo eventNames list, otherwise a list of events
  offsetDate: Option[String] = None, // used only for tests, specifies the offset date to start the duration so the most
  // recent date for events going back by from the more recent offsetDate - duration
  duration: Option[String] = Some(DefaultURAlgoParams.BackfillDuration)) // duration worth of events
  // to use in calculation of backfill

case class URAlgorithmParams(
  appName: String, // filled in from engine.json
  indexName: String, // can optionally be used to specify the elasticsearch index name
  typeName: String, // can optionally be used to specify the elasticsearch type name
  recsModel: Option[String] = Some(DefaultURAlgoParams.RecsModel), // "all", "collabFiltering", "backfill"
  eventNames: Seq[String], // names used to ID all user actions
  blacklistEvents: Option[Seq[String]] = None,// None means use the primary event, empty array means no filter
  // number of events in user-based recs query
  maxQueryEvents: Option[Int] = Some(DefaultURAlgoParams.MaxQueryEvents),
  maxEventsPerEventType: Option[Int] = Some(DefaultURAlgoParams.MaxEventsPerEventType),
  maxCorrelatorsPerEventType: Option[Int] = Some(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
  num: Option[Int] = Some(DefaultURAlgoParams.NumResults), // default max # of recs requested
  userBias: Option[Float] = None, // will cause the default search engine boost of 1.0
  itemBias: Option[Float] = None, // will cause the default search engine boost of 1.0
  returnSelf: Option[Boolean] = None, // query building logic defaults this to false
  fields: Option[Seq[Field]] = None, //defaults to no fields
  // leave out for default or popular
  backfillField: Option[BackfillField] = None,
  // name of date property field for when the item is available
  availableDateName: Option[String] = Some(DefaultURAlgoParams.AvailableDateName),
  // name of date property field for when an item is no longer available
  expireDateName: Option[String] = Some(DefaultURAlgoParams.ExpireDateName),
  // used as the subject of a dateRange in queries, specifies the name of the item property
  dateName: Option[String] = Some(DefaultURAlgoParams.DateName),
  seed: Option[Long] = None) // seed is not used presently
  extends Params //fixed default make it reproducible unless supplied
  */

case class RankingParams(
    name: Option[String] = None,
    `type`: Option[String] = None, // See [[com.actionml.BackfillType]]
    eventNames: Option[Seq[String]] = None, // None means use the algo eventNames list, otherwise a list of events
    offsetDate: Option[String] = None, // used only for tests, specifies the offset date to start the duration so the most
    // recent date for events going back by from the more recent offsetDate - duration
    endDate: Option[String] = None,
    duration: Option[String] = None) { // duration worth of events to use in calculation of backfill
  override def toString: String = {
    s"""
       |name: $name,
       |type: ${`type`},
       |eventNames: $eventNames,
       |offsetDate: $offsetDate,
       |endDate: $endDate,
       |duration: $duration
       |""".stripMargin
  }
}

case class DefaultIndicatorParams(
  maxItemsPerUser: Int = DefaultURAlgoParams.MaxQueryEvents, // defaults to maxEventsPerEventType
  maxCorrelatorsPerItem: Int = DefaultURAlgoParams.MaxCorrelatorsPerEventType,
  // defaults to maxCorrelatorsPerEventType
  minLLR: Option[Double] = None) // defaults to none, takes precendence over maxCorrelatorsPerItem

case class IndicatorParams(
  name: String, // must match one in eventNames
  maxItemsPerUser: Option[Int], // defaults to maxEventsPerEventType
  maxCorrelatorsPerItem: Option[Int], // defaults to maxCorrelatorsPerEventType
  minLLR: Option[Double]) // defaults to none, takes precendence over maxCorrelatorsPerItem

case class URAlgorithmParams(
  appName: String, // filled in from engine.json
  indexName: String, // can optionally be used to specify the elasticsearch index name
  typeName: String, // can optionally be used to specify the elasticsearch type name
  recsModel: Option[String] = None, // "all", "collabFiltering", "backfill"
  eventNames: Option[Seq[String]], // names used to ID all user actions
  blacklistEvents: Option[Seq[String]] = None, // None means use the primary event, empty array means no filter
  // number of events in user-based recs query
  maxQueryEvents: Option[Int] = None,
  maxEventsPerEventType: Option[Int] = None,
  maxCorrelatorsPerEventType: Option[Int] = None,
  num: Option[Int] = None, // default max # of recs requested
  userBias: Option[Float] = None, // will cause the default search engine boost of 1.0
  itemBias: Option[Float] = None, // will cause the default search engine boost of 1.0
  returnSelf: Option[Boolean] = None, // query building logic defaults this to false
  fields: Option[Seq[Field]] = None, //defaults to no fields
  // leave out for default or popular
  rankings: Option[Seq[RankingParams]] = None,
  // name of date property field for when the item is available
  availableDateName: Option[String] = None,
  // name of date property field for when an item is no longer available
  expireDateName: Option[String] = None,
  // used as the subject of a dateRange in queries, specifies the name of the item property
  dateName: Option[String] = None,
  indicators: Option[List[IndicatorParams]] = None, // control params per matrix pair
  seed: Option[Long] = None, // seed is not used presently
  numESWriteConnections: Option[Int] = None) // hint about how to coalesce partitions so we don't overload ES when
    // writing the model. The rule of thumb is (numberOfNodesHostingPrimaries * bulkRequestQueueLength) * 0.75
    // for ES 1.7 bulk queue is defaulted to 50
    extends Params //fixed default make it reproducible unless supplied

/** Creates cooccurrence, cross-cooccurrence and eventually content correlators with
 *  [[org.apache.mahout.math.cf.SimilarityAnalysis]] The analysis part of the recommender is
 *  done here but the algorithm can predict only when the coocurrence data is indexed in a
 *  search engine like Elasticsearch. This is done in URModel.save.
 *
 *  @param ap taken from engine.json to describe limits and event types
 */
class URAlgorithm(val ap: URAlgorithmParams)
    extends P2LAlgorithm[PreparedData, NullModel, Query, PredictedResult] {

  implicit val formats = DefaultFormats

  @transient lazy implicit val logger: Logger = Logger[this.type]

  case class BoostableCorrelators(actionName: String, itemIDs: Seq[ItemID], boost: Option[Float] = None) {
    def toFilterCorrelators: FilterCorrelators = {
      FilterCorrelators(actionName, itemIDs)
    }
  }
  case class FilterCorrelators(actionName: String, itemIDs: Seq[ItemID])
  case class ExclusionFields(propertyName: String, values: Seq[String])

  val appName: String = ap.appName
  val recsModel: String = ap.recsModel.getOrElse(DefaultURAlgoParams.RecsModel)
  //val eventNames: Seq[String] = ap.eventNames

  val userBias: Float = ap.userBias.getOrElse(1f)
  val itemBias: Float = ap.itemBias.getOrElse(1f)

  // get max total user history events for the ES query
  lazy val maxQueryEvents = if (ap.indicators.isEmpty) {
    ap.maxQueryEvents.getOrElse(DefaultURAlgoParams.MaxQueryEvents)
  } else { // using the indicator method of setting query events
    ap.indicators.get.foldLeft[Int](0) { (previous, indicator) =>
      previous + indicator.maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxQueryEvents)
    } * 10
    // this assumes one event doesn't happen more than 10 times more often than another
    // not ideal but avoids one query to the event store per event type
  }

  lazy val indicatorParams = if (ap.eventNames.nonEmpty) { // using eventNames shortcut
    ap.eventNames.get.map { eventName =>
      eventName -> DefaultIndicatorParams()
    }.toMap
  } else if (ap.indicators.nonEmpty) { // using indicators for fined tuned control
    ap.indicators.get.map { indicatorParams =>
      indicatorParams.name -> DefaultIndicatorParams(
        maxItemsPerUser = indicatorParams.maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType),
        maxCorrelatorsPerItem = indicatorParams.maxCorrelatorsPerItem.getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
        minLLR = indicatorParams.minLLR)
    }.toMap
  } else {
    throw new IllegalArgumentException("Must have either \"eventNames\" or \"indicators\" in algorithm parameters.")
  }

  val limit: Int = ap.num.getOrElse(DefaultURAlgoParams.NumResults)

  lazy val modelEventNames = if (ap.indicators.isEmpty) {
    if (ap.eventNames.isEmpty) {
      throw new IllegalArgumentException("No eventNames or indicators in engine.json and one of these is required")
    } else { ap.eventNames.get }
  } else { ap.indicators.get.map(_.name) }

  val blacklistEvents = ap.blacklistEvents.getOrElse(Seq(modelEventNames.head)) // empty Seq[String] means no blacklist
  val returnSelf: Boolean = ap.returnSelf.getOrElse(DefaultURAlgoParams.ReturnSelf)
  val fields: Seq[Field] = ap.fields.getOrEmpty

  val randomSeed: Int = ap.seed.getOrElse(System.currentTimeMillis()).toInt

  val numESWriteConnections = if (ap.numESWriteConnections.nonEmpty) ap.numESWriteConnections else DefaultURAlgoParams.NumESWriteConnections

  val maxCorrelatorsPerEventType: Int = ap.maxCorrelatorsPerEventType
    .getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType)
  val maxEventsPerEventType: Int = ap.maxEventsPerEventType
    .getOrElse(DefaultURAlgoParams.MaxEventsPerEventType)

  // Unique by 'type' ranking params, if collision get first.
  lazy val rankingsParams: Seq[RankingParams] = ap.rankings.getOrElse(Seq(RankingParams(
    name = Some(DefaultURAlgoParams.BackfillFieldName),
    `type` = Some(DefaultURAlgoParams.BackfillType),
    eventNames = Some(modelEventNames.take(1)),
    offsetDate = None,
    endDate = None,
    duration = Some(DefaultURAlgoParams.BackfillDuration)))).groupBy(_.`type`).map(_._2.head).toSeq

  val rankingFieldNames: Seq[String] = rankingsParams map { rankingParams =>
    val rankingType = rankingParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
    val rankingFieldName = rankingParams.name.getOrElse(PopModel.nameByType(rankingType))
    rankingFieldName
  }

  val dateNames: Seq[String] = Seq(
    ap.dateName,
    ap.availableDateName,
    ap.expireDateName).collect { case Some(date) => date } distinct

  val esIndex: String = ap.indexName
  val esType: String = ap.typeName

  drawInfo("Init URAlgorithm", Seq(
    ("══════════════════════════════", "════════════════════════════"),
    ("App name", appName),
    ("ES index name", esIndex),
    ("ES type name", esType),
    ("RecsModel", recsModel),
    ("Event names", modelEventNames),
    ("══════════════════════════════", "════════════════════════════"),
    ("Random seed", randomSeed),
    ("MaxCorrelatorsPerEventType", maxCorrelatorsPerEventType),
    ("MaxEventsPerEventType", maxEventsPerEventType),
    ("BlacklistEvents", blacklistEvents),
    ("══════════════════════════════", "════════════════════════════"),
    ("User bias", userBias),
    ("Item bias", itemBias),
    ("Max query events", maxQueryEvents),
    ("Limit", limit),
    ("══════════════════════════════", "════════════════════════════"),
    ("Rankings:", "")) ++ rankingsParams.map(x => (x.`type`.get, x.name)))

  def train(sc: SparkContext, data: PreparedData): NullModel = {

    val model = recsModel match {
      case RecsModels.All => calcAll(data)(sc)
      case RecsModels.CF  => calcAll(data, calcPopular = false)(sc)
      case RecsModels.BF  => calcPop(data)(sc)
      // error, throw an exception
      case unknownRecsModel =>
        throw new IllegalArgumentException(
          s"""
             |Bad algorithm param recsModel=[$unknownRecsModel] in engine definition params, possibly a bad json value.
             |Use one of the available parameter values ($recsModel).""".stripMargin)
    }
    EsClient.close()
    model
  }

  /** Calculates recs model as well as popularity model */
  def calcAll(
    data: PreparedData,
    calcPopular: Boolean = true)(implicit sc: SparkContext): NullModel = {

    /*logger.info("Indicators read now creating correlators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.actions.map(_._2).toArray,
      ap.seed.getOrElse(System.currentTimeMillis()).toInt)
      .map(_.asInstanceOf[IndexedDatasetSpark])
    */

    logger.info("Actions read now creating correlators")
    val cooccurrenceIDSs = if (ap.indicators.isEmpty) { // using one global set of algo params
      SimilarityAnalysis.cooccurrencesIDSs(
        data.actions.map(_._2).toArray,
        randomSeed = ap.seed.getOrElse(System.currentTimeMillis()).toInt,
        maxInterestingItemsPerThing = ap.maxCorrelatorsPerEventType
          .getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
        maxNumInteractions = ap.maxEventsPerEventType.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType))
        .map(_.asInstanceOf[IndexedDatasetSpark])
    } else { // using params per matrix pair, these take the place of eventNames, maxCorrelatorsPerEventType,
      // and maxEventsPerEventType!
      val indicators = ap.indicators.get
      val iDs = data.actions.map(_._2).toSeq
      val datasets = iDs.zipWithIndex.map {
        case (iD, i) =>
          new DownsamplableCrossOccurrenceDataset(
            iD,
            indicators(i).maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType),
            indicators(i).maxCorrelatorsPerItem.getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
            indicators(i).minLLR)
      }.toList

      SimilarityAnalysis.crossOccurrenceDownsampled(
        datasets,
        ap.seed.getOrElse(System.currentTimeMillis()).toInt)
        .map(_.asInstanceOf[IndexedDatasetSpark])
    }

    val cooccurrenceCorrelators = cooccurrenceIDSs.zip(data.actions.map(_._1)).map(_.swap) //add back the actionNames

    val propertiesRDD: RDD[(ItemID, ItemProps)] = if (calcPopular) {
      val ranksRdd = getRanksRDD(data.fieldsRDD)
      data.fieldsRDD.fullOuterJoin(ranksRdd).map {
        case (item, (Some(fieldsPropMap), Some(rankPropMap))) => item -> (fieldsPropMap ++ rankPropMap)
        case (item, (Some(fieldsPropMap), None))              => item -> fieldsPropMap
        case (item, (None, Some(rankPropMap)))                => item -> rankPropMap
        case (item, _)                                        => item -> Map.empty
      }
    } else {
      sc.emptyRDD
    }

    logger.info("Correlators created now putting into URModel")
    new URModel(
      coocurrenceMatrices = cooccurrenceCorrelators,
      propertiesRDDs = Seq(propertiesRDD),
      typeMappings = getMappings).save(dateNames, esIndex, esType, numESWriteConnections)
    new NullModel
  }

  /** This function creates a URModel from an existing index in Elasticsearch + new popularity ranking
   *  It is used when you want to re-calc the popularity model between training on useage data. It leaves
   *  the part of the model created from usage data alone and only modifies the popularity ranking.
   */
  def calcPop(data: PreparedData)(implicit sc: SparkContext): NullModel = {

    // Aggregating all $set/$unsets properties, which are attached to items
    val fieldsRDD: RDD[(ItemID, ItemProps)] = data.fieldsRDD
    // Calc new ranking properties for all items
    val ranksRdd: RDD[(ItemID, ItemProps)] = getRanksRDD(fieldsRDD)
    // Current items RDD from ES
    val currentMetadataRDD: RDD[(ItemID, ItemProps)] = EsClient.getRDD(esIndex, esType)
    val propertiesRDD: RDD[(ItemID, ItemProps)] = currentMetadataRDD.fullOuterJoin(ranksRdd) map {
      case (itemId, maps) =>
        maps match {
          case (Some(metaProp), Some(rankProp)) => itemId -> (metaProp ++ rankProp)
          case (None, Some(rankProp))           => itemId -> rankProp
          case (Some(metaProp), None)           => itemId -> metaProp
          case _                                => itemId -> Map.empty
        }
    }
    //    logger.debug(s"RanksRdd\n${ranksRdd.take(25).mkString("\n")}")

    // returns the existing model plus new popularity ranking
    new URModel(
      propertiesRDDs = Seq(fieldsRDD.cache(), propertiesRDD.cache()),
      typeMappings = getMappings).save(dateNames, esIndex, esType, numESWriteConnections)
    new NullModel
  }

  var queryEventNames: Seq[String] = Seq.empty[String] // if passed in with the query overrides the engine.json list--used in MAP@k
  //testing, this only effects which events are used in queries.

  /** Return a list of items recommended for a user identified in the query
   *  The ES json query looks like this:
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
   *              "buy": ["0", "32"],
   *              "boost": 2
   *            }
   *          },
   *          { // categorical boosts
   *            "terms": {
   *              "category": ["cat1"],
   *              "boost": 1.05
   *            }
   *          }
   *        ],
   *        "must": [ // categorical filters
   *          {
   *            "terms": {
   *              "category": ["cat1"],
   *              "boost": 0
   *            }
   *          },
   *         {
   *        "must_not": [//blacklisted items
   *          {
   *            "ids": {
   *              "values": ["items-id1", "item-id2", ...]
   *            }
   *          },
   *         {
   *           "constant_score": {// date in query must fall between the expire and available dates of an item
   *             "filter": {
   *               "range": {
   *                 "availabledate": {
   *                   "lte": "2015-08-30T12:24:41-07:00"
   *                 }
   *               }
   *             },
   *             "boost": 0
   *           }
   *         },
   *         {
   *           "constant_score": {// date range filter in query must be between these item property values
   *             "filter": {
   *               "range" : {
   *                 "expiredate" : {
   *                   "gte": "2015-08-15T11:28:45.114-07:00"
   *                   "lt": "2015-08-20T11:28:45.114-07:00"
   *                 }
   *               }
   *             }, "boost": 0
   *           }
   *         },
   *         {
   *           "constant_score": { // this orders popular items for backfill
   *              "filter": {
   *                 "match_all": {}
   *              },
   *              "boost": 0.000001 // must have as least a small number to be boostable
   *           }
   *        }
   *      }
   *    }
   *  }
   *
   *  @param model <strong>Ignored!</strong> since the model is already in Elasticsearch
   *  @param query contains query spec
   *  @todo Need to prune that query to minimum required for data include, for instance no need for the popularity
   *       ranking if no PopModel is being used, same for "must" clause and dates.
   */
  def predict(model: NullModel, query: Query): PredictedResult = {

    queryEventNames = query.eventNames.getOrElse(modelEventNames) // eventNames in query take precedence

    val (queryStr, blacklist) = buildQuery(ap, query, rankingFieldNames)
    // old es1 query
    // val searchHitsOpt = EsClient.search(queryStr, esIndex, queryEventNames)
    val searchHitsOpt = EsClient.search(queryStr, esIndex)

    val withRanks = query.withRanks.getOrElse(false)
    val predictedResults = searchHitsOpt match {
      case Some(searchHits) =>
        val hits = (searchHits \ "hits" \ "hits").extract[Seq[JValue]]
        val recs = hits.map { hit =>
          if (withRanks) {
            val source = hit \ "source"
            val ranks: Map[String, Double] = rankingsParams map { backfillParams =>
              val backfillType = backfillParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
              val backfillFieldName = backfillParams.name.getOrElse(PopModel.nameByType(backfillType))
              backfillFieldName -> (source \ backfillFieldName).extract[Double]
            } toMap

            ItemScore((hit \ "_id").extract[String], (hit \ "_score").extract[Double],
              ranks = if (ranks.nonEmpty) Some(ranks) else None)
          } else {
            ItemScore((hit \ "_id").extract[String], (hit \ "_score").extract[Double])
          }
        }.toArray
        logger.info(s"Results: ${hits.length} retrieved of a possible ${(searchHits \ "hits" \ "total").extract[Long]}")
        PredictedResult(recs)

      case _ =>
        logger.info(s"No results for query ${parse(queryStr)}")
        PredictedResult(Array.empty[ItemScore])
    }

    // todo: is this needed to remove ranked items from recs?
    //if (recsModel == RecsModels.CF) {
    //  PredictedResult(predictedResults.filter(_.score != 0.0))
    //} else PredictedResult(predictedResults)

    // should have all blacklisted items excluded
    // todo: need to add dithering, mean, sigma, seed required, make a seed that only changes on some fixed time
    // period so the recs ordering stays fixed for that time period.
    predictedResults
  }

  /** Calculate all fields and items needed for ranking.
   *
   *  @param fieldsRDD all items with their fields
   *  @param sc the current Spark context
   *  @return
   */
  def getRanksRDD(fieldsRDD: RDD[(ItemID, ItemProps)])(implicit sc: SparkContext): RDD[(ItemID, ItemProps)] = {
    val popModel = PopModel(fieldsRDD)
    val rankRDDs: Seq[(String, RDD[(ItemID, Double)])] = rankingsParams map { rankingParams =>
      val rankingType = rankingParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
      val rankingFieldName = rankingParams.name.getOrElse(PopModel.nameByType(rankingType))
      val durationAsString = rankingParams.duration.getOrElse(DefaultURAlgoParams.BackfillDuration)
      val duration = Duration(durationAsString).toSeconds.toInt
      val backfillEvents = rankingParams.eventNames.getOrElse(modelEventNames.take(1))
      val offsetDate = rankingParams.offsetDate
      val rankRdd = popModel.calc(modelName = rankingType, eventNames = backfillEvents, appName, duration, offsetDate)
      rankingFieldName -> rankRdd
    }

    //    logger.debug(s"RankRDDs[${rankRDDs.size}]\n${rankRDDs.map(_._1).mkString(", ")}\n${rankRDDs.map(_._2.take(25).mkString("\n")).mkString("\n\n")}")
    rankRDDs.foldLeft[RDD[(ItemID, ItemProps)]](sc.emptyRDD) {
      case (leftRdd, (fieldName, rightRdd)) =>
        leftRdd.fullOuterJoin(rightRdd).map {
          case (itemId, (Some(propMap), Some(rank))) => itemId -> (propMap + (fieldName -> JDouble(rank)))
          case (itemId, (Some(propMap), None))       => itemId -> propMap
          case (itemId, (None, Some(rank)))          => itemId -> Map(fieldName -> JDouble(rank))
          case (itemId, _)                           => itemId -> Map.empty
        }
    }
  }

  /** Build a query from default algorithms params and the query itself taking into account defaults */
  def buildQuery(
    ap: URAlgorithmParams,
    query: Query,
    backfillFieldNames: Seq[String] = Seq.empty): (String, Seq[Event]) = {

    logger.info(s"Got query: \n${query}")

    val startPos = query.from.getOrElse(0)
    logger.info(s"from: ${startPos}")

    try {
      // create a list of all query correlators that can have a bias (boost or filter) attached
      val (boostable, events) = getBiasedRecentUserActions(query)
      logger.info(s"getBiasedRecentUserActions returned boostable: ${boostable} and events: ${events}")

      // since users have action history and items have correlators and both correspond to the same "actions" like
      // purchase or view, we'll pass both to the query if the user history or items correlators are empty
      // then metadata or backfill must be relied on to return results.
      val numRecs = if (query.num.isDefined) query.num.get else limit // num in query orerrides num in config
      logger.info(s"UR query num = ${query.num}")
      logger.info(s"query.num.getOrElse returned numRecs: ${numRecs}")

      val should = buildQueryShould(query, boostable)
      logger.info(s"buildQueryShould returned should: ${should}")
      val must = buildQueryMust(query, boostable)
      logger.info(s"buildQueryMust returned must: ${must}")
      val mustNot = buildQueryMustNot(query, events)
      logger.info(s"buildQueryMustNot returned mustNot: ${mustNot}")
      val sort = buildQuerySort()
      logger.info(s"buildQuerySort returned sort: ${sort}")

      val json =
        ("from" -> startPos) ~
          ("size" -> numRecs) ~
          ("query" ->
            ("bool" ->
              ("should" -> should) ~
              ("must" -> must) ~
              ("must_not" -> mustNot) ~
              ("minimum_should_match" -> 1))) ~
              ("sort" -> sort)

      logger.info(s"json is: ${json}")
      val compactJson = compact(render(json))
      logger.info(s"compact json is: ${compactJson}")

      //logger.info(s"Query:\n$compactJson")
      (compactJson, events)
    } catch {
      case e: IllegalArgumentException => {
        logger.warn("whoops, IllegalArgumentException for something in buildQuery.")
        ("", Seq.empty[Event])
      }
    }
  }

  /** Build should query part */
  def buildQueryShould(query: Query, boostable: Seq[BoostableCorrelators]): Seq[JValue] = {

    // create a list of all boosted query correlators
    val recentUserHistory: Seq[BoostableCorrelators] = if (userBias >= 0f) {
      boostable.slice(0, maxQueryEvents - 1)
    } else {
      Seq.empty
    }
    logger.info(s"recentUserHistory is: ${recentUserHistory}")

    val similarItems: Seq[BoostableCorrelators] = if (itemBias >= 0f) {
      getBiasedSimilarItems(query)
    } else {
      Seq.empty
    }
    logger.info(s"similarItems is: ${similarItems}")

    // this only makes sense when you train on (item-set-id, event-name, item-id) aka
    // shopping carts. We do not enforce this in the query nor throw an exception but a
    // WARN: is logged
    val itemSet: Seq[BoostableCorrelators] = if (query.itemSet.nonEmpty) {
      if (query.item.nonEmpty || query.user.nonEmpty) {
        logger.warn("ItemSets should not be mixed with user or item-based queries. This will produce unpredicable " +
          "results.")
      }
      Seq(BoostableCorrelators(modelEventNames.head, query.itemSet.get, query.itemSetBias))
    } else {
      Seq.empty
    }
    logger.info(s"itemSet is: ${itemSet}")

    val boostedMetadata = getBoostedMetadata(query)
    logger.info(s"boostedMetadata is: ${boostedMetadata}")
    val allBoostedCorrelators = recentUserHistory ++ similarItems ++ boostedMetadata ++ itemSet
    logger.info(s"allBoostedCorrelators is: ${allBoostedCorrelators}")

    val shouldFields: Seq[JValue] = allBoostedCorrelators.filter {
      // only use non-zero boosts
      case BoostableCorrelators(actionName, itemIDs, boost) =>
        boost.getOrElse(1f) != 0f
    }.map {
      case BoostableCorrelators(actionName, itemIDs, boost) =>
        render("terms" -> (actionName -> itemIDs) ~ ("boost" -> boost))
    }
    logger.info(s"shouldFields is: ${shouldFields}")

    // todo: this should not be sent if there are no rankings, causes 0 scores to be returned as backfill even
    // with no other ranking. Currently the 0 score results are filtered out for no active ranking.
    val shouldScore: JValue = parse(
      """
        |{
        |  "constant_score": {
        |    "filter": {
        |      "match_all": {}
        |    },
        |    "boost": 0
        |  }
        |}
        |""".stripMargin)
    logger.info(s"shouldScore is: ${shouldScore}")

    shouldFields :+ shouldScore
  }

  /** Build must query part */
  def buildQueryMust(query: Query, boostable: Seq[BoostableCorrelators]): Seq[JValue] = {

    // create a list of all query correlators that are to be used to filter results
    val recentUserHistoryFilter: Seq[FilterCorrelators] = if (userBias < 0f) {
      // strip any boosts
      boostable.map(_.toFilterCorrelators).slice(0, maxQueryEvents - 1)
    } else {
      Seq.empty
    }

    val similarItemsFilter: Seq[FilterCorrelators] = if (itemBias < 0f) {
      getBiasedSimilarItems(query).map(_.toFilterCorrelators)
    } else {
      Seq.empty
    }

    val filteringMetadata = getFilteringMetadata(query)
    val filteringDateRange = getFilteringDateRange(query)
    val allFilteringCorrelators = recentUserHistoryFilter ++ similarItemsFilter ++ filteringMetadata

    val mustFields: Seq[JValue] = allFilteringCorrelators.map {
      case FilterCorrelators(actionName, itemIDs) =>
        render("terms" -> (actionName -> itemIDs) ~ ("boost" -> 0))
    }
    mustFields ++ filteringDateRange
  }

  /** Build mustNot part of the query */
  def buildQueryMustNot(query: Query, events: Seq[Event]): JValue = {
    // this excludes any items from the query by id
    val excludedItems = ("ids" -> ("values" -> getExcludedItems(events, query)) ~ ("boost" -> 0))

    // exclude items with metadata boost = 0
    val excludingMetadata = getExcludingMetadata(query)
    logger.info(s"boosted metadata, should include boost = 0 for exclusions: ${excludingMetadata}")
    val excludedByZeroBoost = excludingMetadata.map {
      case ExclusionFields(metatdataName, values) =>
        ("terms" -> (metatdataName -> values))
    }
    logger.info(s"excluded by 0 boost: ${excludedByZeroBoost}")

    render(excludedItems ++ excludedByZeroBoost)
  }

  /** Build sort query part */
  def buildQuerySort(): Seq[JValue] = if (recsModel == RecsModels.All || recsModel == RecsModels.BF) {
    val sortByScore: Seq[JValue] = Seq(parse("""{"_score": {"order": "desc"}}"""))
    val sortByRanks: Seq[JValue] = rankingFieldNames map { fieldName =>
      parse(s"""{ "$fieldName": { "unmapped_type": "double", "order": "desc" } }""")
    }
    sortByScore ++ sortByRanks
  } else {
    Seq.empty
  }

  /** Create a list of item ids that the user has interacted with or are not to be included in recommendations */
  def getExcludedItems(userEvents: Seq[Event], query: Query): Seq[String] = {

    val blacklistedItems = userEvents.filterNot { event =>
      // either a list or an empty list of filtering events so honor them
      if (blacklistEvents.isEmpty) {
        //logger.info("BlacklistEvents are empty, no filtering")
        true
      } else {
        //logger.info(s"BlacklistEvents: $blacklistEvents, this event ${event.event}")
        !(blacklistEvents contains event.event)
      }
    }.map(_.targetEntityId.getOrElse("")) ++ query.blacklistItems.getOrEmpty

    // exclude items in the itemSet query

    val itemSetExcludes = query.itemSet.getOrEmpty

    // Now conditionally add the query item itself
    val includeSelf = query.returnSelf.getOrElse(returnSelf)
    val allExcludedItems = if (!includeSelf && query.item.nonEmpty) {
      (blacklistedItems :+ query.item.get) ++ itemSetExcludes
    } // add the query item to be excuded
    else {
      blacklistedItems ++ itemSetExcludes
    }
    allExcludedItems.distinct
  }

  /** Get similar items for an item, these are already in the action correlators in ES */
  def getBiasedSimilarItems(query: Query): Seq[BoostableCorrelators] = {
    if (query.item.nonEmpty) {
      logger.info(s"using item ${query.item.get}")
      val m = EsClient.getSource(esIndex, esType, query.item.get)
      logger.info(s"got source: ${m}")

      if (m.nonEmpty) {
        val itemEventBias = query.itemBias.getOrElse(itemBias)
        logger.info(s"getBiasedSimilarItems for item ${query.item.get}, bias value ${itemEventBias}")
        val itemEventsBoost = if (itemEventBias > 0 && itemEventBias != 1) Some(itemEventBias) else None
        modelEventNames.map { action =>
          val items: Seq[String] = m.get(action).getOrElse(Seq.empty[String])
          val rItems = if (items.size <= maxQueryEvents) items else items.slice(0, maxQueryEvents - 1)
          BoostableCorrelators(action, rItems, itemEventsBoost)
        }
      } else {
        logger.info(s"getBiasedSimilarItems for item ${query.item.get}: item not found")
        Seq.empty[BoostableCorrelators]
      } // item not found in Elasticsearch
    } else {
      Seq.empty[BoostableCorrelators]
    } // no item specified
  }

  /** Get recent events of the user on items to create the recommendations query from */
  def getBiasedRecentUserActions(query: Query): (Seq[BoostableCorrelators], Seq[Event]) = {

    val recentEvents = try {
      LEventStore.findByEntity(
        appName = appName,
        // entityType and entityId is specified for fast lookup
        entityType = "user",
        entityId = query.user.get,
        // one query per eventName is not ideal, maybe one query for lots of events then split by eventName
        // eventNames = Some(Seq(action)),// get all and separate later
        eventNames = Some(queryEventNames), // get all and separate later
        // targetEntityType = None,
        // limit = Some(maxQueryEvents), // this will get all history then each action can be limited before using in
        // the query
        latest = true,
        // set time limit to avoid super long DB access
        timeout = Duration(200, "millis")).toSeq
    } catch {
      case e: scala.concurrent.TimeoutException =>
        logger.error(s"Timeout when reading recent events. Empty list is used. $e")
        Seq.empty[Event]
      case e: NoSuchElementException =>
        logger.info("No user id for recs, returning item-based recs if an item is specified in the query.")
        Seq.empty[Event]
      case e: Exception => // fatal because of error, an empty query
        logger.error(s"Error when reading recent events. Trying to continue by ignoring the error. $e")
        Seq.empty[Event]
    }

    val userEventBias = query.userBias.getOrElse(userBias)
    val userEventsBoost = if (userEventBias > 0 && userEventBias != 1) Some(userEventBias) else None
    val rActions = queryEventNames.map { action =>
      var items = Seq.empty[String]

      for (event <- recentEvents) { // todo: use indidatorParams for each indicator type
        if (event.event == action && items.size < indicatorParams(action).maxItemsPerUser) {
          items = event.targetEntityId.get +: items
          // todo: may throw exception and we should ignore the event instead of crashing
        }
        // userBias may be None, which will cause no JSON output for this
      }
      BoostableCorrelators(action, items.distinct, userEventsBoost)
    }
    (rActions, recentEvents)
  }

  /** get all metadata fields that potentially have boosts (not filters) */
  def getBoostedMetadata(query: Query): Seq[BoostableCorrelators] = {
    val paramsBoostedFields = fields.filter(_.bias < 0f)
    val queryBoostedFields = query.fields.getOrEmpty.filter(_.bias > 0f)

    (queryBoostedFields ++ paramsBoostedFields)
      .map(field => BoostableCorrelators(field.name, field.values, Some(field.bias)))
      .distinct // de-dup and favor query fields
  }

  /** get all metadata fields that are filters (not boosts) */
  def getFilteringMetadata(query: Query): Seq[FilterCorrelators] = {
    val paramsFilterFields = fields.filter(_.bias > 0f)
    val queryFilterFields = query.fields.getOrEmpty.filter(_.bias < 0f)

    (queryFilterFields ++ paramsFilterFields)
      .map(field => FilterCorrelators(field.name, field.values))
      .distinct // de-dup and favor query fields
  }

  /** get all metadata fields that are exclusion filters (not boosts) */
  def getExcludingMetadata(query: Query): Seq[ExclusionFields] = {
    val paramsFilterFields = fields.filter(_.bias == 0f)
    val queryFilterFields = query.fields.getOrEmpty.filter(_.bias == 0f)

    (queryFilterFields ++ paramsFilterFields)
      .map(field => ExclusionFields(field.name, field.values))
      .distinct // de-dup and favor query fields
  }

  /** get part of query for dates and date ranges */
  def getFilteringDateRange(query: Query): Seq[JValue] = {

    // currentDate in the query overrides the dateRange in the same query so ignore daterange if both
    val currentDate = query.currentDate.getOrElse(DateTime.now().toDateTimeISO.toString)

    val json: Seq[JValue] = if (query.dateRange.nonEmpty &&
      (query.dateRange.get.after.nonEmpty || query.dateRange.get.before.nonEmpty)) {
      val name = query.dateRange.get.name
      val before = query.dateRange.get.before.getOrElse("")
      val after = query.dateRange.get.after.getOrElse("")
      val rangeStart = s"""
        |{
        |  "constant_score": {
        |    "filter": {
        |      "range": {
        |        "$name": {
        """.stripMargin

      val rangeAfter = s"""
        |          "gt": "$after"
        """.stripMargin

      val rangeBefore = s"""
        |          "lt": "$before"
        """.stripMargin

      val rangeEnd = s"""
        |        }
        |      }
        |    },
        |    "boost": 0
        |  }
        |}
        """.stripMargin

      var range = rangeStart
      if (!after.isEmpty) {
        range += rangeAfter
        if (!before.isEmpty) range += ","
      }
      if (!before.isEmpty) range += rangeBefore
      range += rangeEnd

      Seq(parse(range))
    } else if (ap.availableDateName.nonEmpty && ap.expireDateName.nonEmpty) { // use the query date or system date
      val availableDate = ap.availableDateName.get // never None
      val expireDate = ap.expireDateName.get
      val available = s"""
        |{
        |  "constant_score": {
        |    "filter": {
        |      "range": {
        |        "$availableDate": {
        |          "lte": "$currentDate"
        |        }
        |      }
        |    },
        |    "boost": 0
        |  }
        |}
        """.stripMargin
      val expire = s"""
        |{
        |  "constant_score": {
        |    "filter": {
        |      "range": {
        |        "$expireDate": {
        |          "gt": "$currentDate"
        |        }
        |      }
        |    },
        |    "boost": 0
        |  }
        |}
        """.stripMargin

      Seq(parse(available), parse(expire))
    } else {
      Seq.empty
    }
    json
  }

  def getMappings: Map[String, (String, Boolean)] = {
    val mappings = rankingFieldNames.map { fieldName =>
      fieldName -> ("float", false)
    }.toMap ++ // create mappings for correlators, where the Boolean says to not use norms
      modelEventNames.map { correlator =>
        correlator -> ("keyword", true) // use norms with correlators to get closer to cosine similarity.
      }.toMap ++
      dateNames.map { dateName =>
        dateName -> ("date", false) // map dates to be interpreted as dates
      }
    logger.info(s"Index mappings for the Elasticsearch URModel: $mappings")
    mappings
  }

}
