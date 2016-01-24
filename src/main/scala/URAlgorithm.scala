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

package org.template

import java.util
import io.prediction.controller.P2LAlgorithm
import io.prediction.controller.Params
import io.prediction.data
import io.prediction.data.storage.{PropertyMap, Event}
import io.prediction.data.store.LEventStore
import org.apache.mahout.math.cf.SimilarityAnalysis
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import org.json4s
import org.json4s.JsonAST
import org.json4s.JsonAST._
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration.Duration
import org.apache.spark.SparkContext
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.convert.wrapAsScala._
import grizzled.slf4j.Logger
import org.elasticsearch.spark._

/** Setting the option in the params case class doesn't work as expected when the param is missing from
  * engine.json so set these for use in the algorithm when they are not present in the engine.json
  */
object defaultURAlgorithmParams {
  val DefaultMaxEventsPerEventType = 500
  val DefaultNum = 20
  val DefaultMaxCorrelatorsPerEventType = 50
  val DefaultMaxQueryEvents = 100 // default number of user history events to use in recs query

  val DefaultExpireDateName = "expireDate" // default name for the expire date property of an item
  val DefaultAvailableDateName = "availableDate" //defualt name for and item's available after date
  val DefaultDateName = "date" // when using a date range in the query this is the name of the item's date
  val DefaultRecsModel = "all" // use CF + backfill
  val DefaultBackfillParams = BackfillField()
  val DefaultBackfillFieldName = "popRank"
  val DefaultBackfillType = "popular"
  val DefaultBackfillDuration = 259200
}

case class BackfillField(
  name: Option[String] = Some(defaultURAlgorithmParams.DefaultBackfillFieldName),
  backfillType: Option[String] = Some(defaultURAlgorithmParams.DefaultBackfillType), // may be 'hot', or 'trending' also
  eventNames: Option[List[String]] = None, // None means use the algo eventNames list, otherwise a list of events
  offsetDate: Option[String] = None, // used only for tests, specifies the offset date to start the duration so the most
  // recent date for events going back by from the more recent offsetDate - duration
  duration: Option[Int] = Some(defaultURAlgorithmParams.DefaultBackfillDuration)) // number of seconds worth of events
  // to use in calculation of backfill

/** Instantiated from engine.json */
case class URAlgorithmParams(
  appName: String, // filled in from engine.json
  indexName: String, // can optionally be used to specify the elasticsearch index name
  typeName: String, // can optionally be used to specify the elasticsearch type name
  recsModel: Option[String] = Some(defaultURAlgorithmParams.DefaultRecsModel), // "all", "collabFiltering", "backfill"
  eventNames: List[String], // names used to ID all user actions
  blacklistEvents: Option[List[String]] = None,// None means use the primary event, empty array means no filter
  // number of events in user-based recs query
  maxQueryEvents: Option[Int] = Some(defaultURAlgorithmParams.DefaultMaxQueryEvents),
  maxEventsPerEventType: Option[Int] = Some(defaultURAlgorithmParams.DefaultMaxEventsPerEventType),
  maxCorrelatorsPerEventType: Option[Int] = Some(defaultURAlgorithmParams.DefaultMaxCorrelatorsPerEventType),
  num: Option[Int] = Some(defaultURAlgorithmParams.DefaultNum), // default max # of recs requested
  userBias: Option[Float] = None, // will cause the default search engine boost of 1.0
  itemBias: Option[Float] = None, // will cause the default search engine boost of 1.0
  returnSelf: Option[Boolean] = None, // query building logic defaults this to false
  fields: Option[List[Field]] = None, //defaults to no fields
  // leave out for default or popular
  backfillField: Option[BackfillField] = None,
  // name of date property field for when the item is avalable
  availableDateName: Option[String] = Some(defaultURAlgorithmParams.DefaultAvailableDateName),
  // name of date property field for when an item is no longer available
  expireDateName: Option[String] = Some(defaultURAlgorithmParams.DefaultExpireDateName),
  // used as the subject of a dateRange in queries, specifies the name of the item property
  dateName: Option[String] = Some(defaultURAlgorithmParams.DefaultDateName),
  seed: Option[Long] = None) // seed is not used presently
  extends Params //fixed default make it reproducible unless supplied

/** Creates cooccurrence, cross-cooccurrence and eventually content correlators with
  * [[org.apache.mahout.math.cf.SimilarityAnalysis]] The analysis part of the recommender is
  * done here but the algorithm can predict only when the coocurrence data is indexed in a
  * search engine like Elasticsearch. This is done in URModel.save.
  *
  * @param ap taken from engine.json to describe limits and event types
  */
class URAlgorithm(val ap: URAlgorithmParams)
  extends P2LAlgorithm[PreparedData, URModel, Query, PredictedResult] {

  case class BoostableCorrelators(actionName: String, itemIDs: Seq[String], boost: Option[Float])
  case class FilterCorrelators(actionName: String, itemIDs: Seq[String])

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): URModel = {

    val dateNames = Some(List(ap.dateName.getOrElse(""), ap.availableDateName.getOrElse(""),
      ap.expireDateName.getOrElse(""))) // todo: return None if all are empty?
    //logger.info(s"backfill: ${ap.backfillField.toString}")
    val backfillFieldName = ap.backfillField.getOrElse(BackfillField()).name
      .getOrElse(defaultURAlgorithmParams.DefaultBackfillFieldName)

    ap.recsModel.getOrElse(defaultURAlgorithmParams.DefaultRecsModel) match {
      case "all" => calcAll(sc, data, dateNames, backfillFieldName)
      case "collabFiltering" => calcAll(sc, data, dateNames, backfillFieldName, popular = false )
      case "backfill" => calcPop(sc, data, dateNames, backfillFieldName)
      // error, throw an exception
      case _ => throw new IllegalArgumentException("Bad recsModel in engine definition params, possibly a bad json value.")
    }
  }

  /** Calculates recs model as well as popularity model */
  def calcAll(
    sc: SparkContext,
    data: PreparedData,
    dateNames: Option[List[String]] = None,
    backfillFieldName: String,
    popular: Boolean = true):
  URModel = {

    // No one likes empty training data.
    require(data.actions.take(1).nonEmpty,
      s"Primary action in PreparedData cannot be empty." +
        " Please check if DataSource generates TrainingData" +
        " and Preprator generates PreparedData correctly.")

    val backfillParams = ap.backfillField.getOrElse(defaultURAlgorithmParams.DefaultBackfillParams)
    val nonDefaultMappings = Map(backfillParams.name.getOrElse(defaultURAlgorithmParams.DefaultBackfillFieldName) -> "float")
    logger.info("Actions read now creating correlators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.actions.map(_._2).toArray,
      randomSeed = ap.seed.getOrElse(System.currentTimeMillis()).toInt,
      maxInterestingItemsPerThing = ap.maxCorrelatorsPerEventType
        .getOrElse(defaultURAlgorithmParams.DefaultMaxCorrelatorsPerEventType),
      maxNumInteractions = ap.maxEventsPerEventType.getOrElse(defaultURAlgorithmParams.DefaultMaxEventsPerEventType))
      .map(_.asInstanceOf[IndexedDatasetSpark]) // strip action names
    val cooccurrenceCorrelators = cooccurrenceIDSs.zip(data.actions.map(_._1)).map(_.swap) //add back the actionNames

    val popModel = if (popular) {
      val duration = ap.backfillField.getOrElse(defaultURAlgorithmParams.DefaultBackfillParams).duration
      val backfillEvents = backfillParams.eventNames.getOrElse(List(ap.eventNames.head))
      val end = ap.backfillField.getOrElse(defaultURAlgorithmParams.DefaultBackfillParams)
        .offsetDate
      PopModel.calc(
        Some(backfillParams.backfillType.getOrElse(defaultURAlgorithmParams.DefaultBackfillType)),
        backfillEvents,
        ap.appName,
        duration.getOrElse(defaultURAlgorithmParams.DefaultBackfillDuration),
        end)(sc)
    } else None

    val allPropertiesRDD = if (popModel.nonEmpty) {
      data.fieldsRDD.cogroup[Float](popModel.get).map { case (item, pms) =>
        val pm = if (pms._1.nonEmpty && pms._2.nonEmpty) {
          val newPM = pms._1.head.fields + (backfillFieldName -> JDouble(pms._2.head))
          PropertyMap(newPM, pms._1.head.firstUpdated, DateTime.now())
        } else if (pms._2.nonEmpty) PropertyMap(Map(backfillFieldName -> JDouble(pms._2.head)), DateTime.now(), DateTime.now())
        else PropertyMap( Map.empty[String, JValue], DateTime.now, DateTime.now) // some error????
        (item, pm)
      }
    } else data.fieldsRDD

    logger.info("Correlators created now putting into URModel")
    new URModel(
      Some(cooccurrenceCorrelators),
      Some(allPropertiesRDD),
      ap.indexName,
      dateNames,
      typeMappings = Some(nonDefaultMappings))
  }

  /** This function creates a URModel from an existing index in Elasticsearch + new popularity ranking
    * It is used when you want to re-calc the popularity model between training on useage data. It leaves
    * the part of the model created from usage data alone and only modifies the popularity ranking.
    */
  def calcPop(
    sc: SparkContext,
    data: PreparedData,
    dateNames: Option[List[String]] = None,
    backfillFieldName: String = ""): URModel = {

    val backfillParams = ap.backfillField.getOrElse(defaultURAlgorithmParams.DefaultBackfillParams)
    val backfillEvents = backfillParams.eventNames.getOrElse(List(ap.eventNames.head))//default to first/primary event
    val end = ap.backfillField.getOrElse(defaultURAlgorithmParams.DefaultBackfillParams).offsetDate
    val popModel = PopModel.calc(
      Some(backfillParams.backfillType.getOrElse(defaultURAlgorithmParams.DefaultBackfillType)),
      backfillEvents,
      ap.appName,
      backfillParams.duration.getOrElse(defaultURAlgorithmParams.DefaultBackfillDuration),
      end)(sc)
    val popRDD = if (popModel.nonEmpty) {
      val model = popModel.get.map { case (item, rank)  =>
        val newPM = Map(backfillFieldName -> JDouble(rank))
        (item, PropertyMap(newPM, DateTime.now, DateTime.now))
      }
      Some(model)
    } else None

    val propertiesRDD = if (popModel.nonEmpty) {
      val currentMetadata = esClient.getRDD(sc, ap.indexName, ap.typeName)
      if (currentMetadata.nonEmpty) { // may be an empty index so ignore
        Some(popModel.get.cogroup[collection.Map[String, AnyRef]](currentMetadata.get)
          .map { case (item, (ranks, pms)) =>
            if (ranks.nonEmpty) pms.head + (backfillFieldName -> ranks.head)
            else if (pms.nonEmpty) pms.head
            else Map.empty[String, AnyRef] // could happen if only calculating popularity, which may leave out items with
            // no events
          })
      } else None
    } else None

    // returns the existing model plus new popularity ranking
    new URModel(
      None,
      None,
      ap.indexName,
      None,
      propertiesRDD = propertiesRDD,
      typeMappings = Some(Map(backfillFieldName -> "float")))
  }

  var queryEventNames = List.empty[String] // if passed in with the query overrides the engine.json list--used in MAP@k
  //testing, this only effects which events are used in queries.

  /** Return a list of items recommended for a user identified in the query
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
    *           "constant_score": {// date in query must fall between the expire and avqilable dates of an item
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
    * @param model <strong>Ignored!</strong> since the model is already in Elasticsearch
    * @param query contains query spec
    * @todo Need to prune that query to minimum required for data include, for instance no need for the popularity
    *       ranking if no PopModel is being used, same for "must" clause and dates.
    */
  def predict(model: URModel, query: Query): PredictedResult = {
    //logger.info(s"Query received, user id: ${query.user}, item id: ${query.item}")
    logger.info(s"Query BackFillField: ${ap.backfillField}")

    queryEventNames = query.eventNames.getOrElse(ap.eventNames) // eventNames in query take precedence for the query
    // part of their use
    val backfillFieldName = ap.backfillField.getOrElse(BackfillField()).name
    logger.info(s"PopModel using fieldName: ${backfillFieldName}")
    val queryAndBlacklist = buildQuery(ap, query, backfillFieldName.getOrElse(defaultURAlgorithmParams.DefaultBackfillFieldName))
    val recs = esClient.search(queryAndBlacklist._1, ap.indexName)
    // should have all blacklisted items excluded
    // todo: need to add dithering, mean, sigma, seed required, make a seed that only changes on some fixed time
    // period so the recs ordering stays fixed for that time period.
    recs
  }

  /** Build a query from default algorithms params and the query itself taking into account defaults */
  def buildQuery(ap: URAlgorithmParams, query: Query, backfillFieldName: String = ""): (String, List[Event]) = {

    try{ // require the minimum of a user or item, if not then return popular if any
      //require( query.item.nonEmpty || query.user.nonEmpty, "Warning: a query must include either a user or item id")

      // create a list of all query correlators that can have a bias (boost or filter) attached
      val alluserEvents = getBiasedRecentUserActions(query)

      // create a list of all boosted query correlators
      val recentUserHistory = if ( ap.userBias.getOrElse(1f) >= 0f )
        alluserEvents._1.slice(0, ap.maxQueryEvents.getOrElse(defaultURAlgorithmParams.DefaultMaxQueryEvents) - 1)
      else List.empty[BoostableCorrelators]

      val similarItems = if ( ap.itemBias.getOrElse(1f) >= 0f )
        getBiasedSimilarItems(query)
      else List.empty[BoostableCorrelators]

      val boostedMetadata = getBoostedMetadata(query)

      val allBoostedCorrelators = recentUserHistory ++ similarItems ++ boostedMetadata

      // create a lsit of all query correlators that are to be used to filter results
      val recentUserHistoryFilter = if ( ap.userBias.getOrElse(1f) < 0f ) {
        // strip any boosts
        alluserEvents._1.map { i =>
          FilterCorrelators(i.actionName, i.itemIDs)
        }.slice(0, ap.maxQueryEvents.getOrElse(defaultURAlgorithmParams.DefaultMaxQueryEvents) - 1)
      } else List.empty[FilterCorrelators]

      val similarItemsFilter = if ( ap.itemBias.getOrElse(1f) < 0f ) {
        getBiasedSimilarItems(query).map { i =>
          FilterCorrelators(i.actionName, i.itemIDs)
        }.toList
      } else List.empty[FilterCorrelators]

      val filteringMetadata = getFilteringMetadata(query)

      val filteringDateRange = getFilteringDateRange(query)

      val allFilteringCorrelators = recentUserHistoryFilter ++ similarItemsFilter ++ filteringMetadata

      // since users have action history and items have correlators and both correspond to the same "actions" like
      // purchase or view, we'll pass both to the query if the user history or items correlators are empty
      // then metadata or backfill must be relied on to return results.

      val numRecs = query.num.getOrElse(ap.num.getOrElse(defaultURAlgorithmParams.DefaultNum))

      val shouldFields: Option[List[JValue]] = if (allBoostedCorrelators.isEmpty) None
      else {
        Some(allBoostedCorrelators.map { i =>
          render(("terms" -> (i.actionName -> i.itemIDs) ~ ("boost" -> i.boost)))
        }.toList)
      }
      val popModelSort = List(parse(
        """
          |{
          |  "constant_score": {
          |    "filter": {
          |      "match_all": {}
          |    },
          |    "boost": 0
          |  }
          |}
          |""".stripMargin))

      val should: List[JValue] = if (shouldFields.isEmpty) popModelSort else shouldFields.get ::: popModelSort


      val mustFields: List[JValue] = allFilteringCorrelators.map { i =>
        render(("terms" -> (i.actionName -> i.itemIDs) ~ ("boost" -> 0)))}.toList
      val must: List[JValue] = mustFields ::: filteringDateRange

      val mustNotFields: JValue = render(("ids" -> ("values" -> getExcludedItems (alluserEvents._2, query)) ~ ("boost" -> 0)))
      val mustNot: JValue = mustNotFields

      val popQuery = if (ap.recsModel.getOrElse("all") == "all" ||
        ap.recsModel.getOrElse("all") == "backfill") {
        Some(List(
          parse( """{"_score": {"order": "desc"}}"""),
          parse(
            s"""
               |{
               |    "${backfillFieldName}": {
               |      "unmapped_type": "double",
               |      "order": "desc"
               |    }
               |}""".stripMargin)))
      } else None


      val json =
        (
          ("size" -> numRecs) ~
            ("query"->
              ("bool"->
                ("should"-> should) ~
                  ("must"-> must) ~
                  ("must_not"-> mustNot) ~
                  ("minimum_should_match" -> 1))
              ) ~
            ("sort" -> popQuery))
      val j = compact(render(json))
      logger.info(s"Query: \n${j}\n")
      (compact(render(json)), alluserEvents._2)
    } catch {
      case e: IllegalArgumentException =>
        ("", List.empty[Event])
    }
  }

  /** Create a list of item ids that the user has interacted with or are not to be included in recommendations */
  def getExcludedItems(userEvents: List[Event], query: Query): List[String] = {

    val blacklistedItems = userEvents.filter { event =>
      if (ap.blacklistEvents.nonEmpty) {
        // either a list or an empty list of filtering events so honor them
        if (ap.blacklistEvents.get == List.empty[String]) false // no filtering events so all are allowed
        else ap.blacklistEvents.get.contains(event.event) // if its filtered remove it, else allow
      } else ap.eventNames(0).equals(event.event) // remove the primary event if nothing specified
    }.map (_.targetEntityId.getOrElse("")) ++ query.blacklistItems.getOrElse(List.empty[String])
      .distinct

    // Now conditionally add the query item itself
    val includeSelf = query.returnSelf.getOrElse(ap.returnSelf.getOrElse(false))
    val allExcludedItems = if ( !includeSelf && query.item.nonEmpty )
      blacklistedItems :+ query.item.get // add the query item to be excuded
    else
      blacklistedItems
    allExcludedItems.distinct
  }

  /** Get similar items for an item, these are already in the action correlators in ES */
  def getBiasedSimilarItems(query: Query): Seq[BoostableCorrelators] = {
    if (query.item.nonEmpty) {
      val m = esClient.getSource(ap.indexName, ap.typeName, query.item.get)

      if (m != null) {
        val itemEventBias = query.itemBias.getOrElse(ap.itemBias.getOrElse(1f))
        val itemEventsBoost = if (itemEventBias > 0 && itemEventBias != 1) Some(itemEventBias) else None
        ap.eventNames.map { action =>
          val items = try {
            if (m.containsKey(action) && m.get(action) != null) m.get(action).asInstanceOf[util.ArrayList[String]].toList
            else List.empty[String]
          } catch {
            case cce: ClassCastException =>
              logger.warn(s"Bad value in item ${query.item} corresponding to key: ${action} that was not a List[String]" +
                " ignored.")
              List.empty[String]
          }
          val rItems = if (items.size <= ap.maxQueryEvents.getOrElse(defaultURAlgorithmParams.DefaultMaxQueryEvents))
            items else items.slice(0, ap.maxQueryEvents.getOrElse(defaultURAlgorithmParams.DefaultMaxQueryEvents) - 1)
          BoostableCorrelators(action, rItems, itemEventsBoost)
        }
      } else List.empty[BoostableCorrelators] // no similar items
    } else List.empty[BoostableCorrelators] // no item specified
  }

  /** Get recent events of the user on items to create the recommendations query from */
  def getBiasedRecentUserActions(
    query: Query): (Seq[BoostableCorrelators], List[Event]) = {

    val recentEvents = try {
      LEventStore.findByEntity(
        appName = ap.appName,
        // entityType and entityId is specified for fast lookup
        entityType = "user",
        entityId = query.user.get,
        // one query per eventName is not ideal, maybe one query for lots of events then split by eventName
        //eventNames = Some(Seq(action)),// get all and separate later
        eventNames = Some(queryEventNames),// get all and separate later
        targetEntityType = None,
        // limit = Some(maxQueryEvents), // this will get all history then each action can be limited before using in
        // the query
        latest = true,
        // set time limit to avoid super long DB access
        timeout = Duration(200, "millis")
      ).toList
    } catch {
      case e: scala.concurrent.TimeoutException =>
        logger.error(s"Timeout when read recent events." +
          s" Empty list is used. ${e}")
        List.empty[Event]
      case e: NoSuchElementException => // todo: bad form to use an exception to check if there is a user id
        logger.info("No user id for recs, returning similar items for the item specified")
        List.empty[Event]
      case e: Exception => // fatal because of error, an empty query
        logger.error(s"Error when read recent events: ${e}")
        throw e
    }

    val userEventBias = query.userBias.getOrElse(ap.userBias.getOrElse(1f))
    val userEventsBoost = if (userEventBias > 0 && userEventBias != 1) Some(userEventBias) else None
    //val rActions = ap.eventNames.map { action =>
    val rActions = queryEventNames.map { action =>
      var items = List[String]()

      for ( event <- recentEvents )
        if (event.event == action && items.size <
          ap.maxQueryEvents.getOrElse(defaultURAlgorithmParams.DefaultMaxQueryEvents)) {
          items = event.targetEntityId.get :: items
          // todo: may throw exception and we should ignore the event instead of crashing
        }
      // userBias may be None, which will cause no JSON output for this
      BoostableCorrelators(action, items.distinct, userEventsBoost)
    }
    (rActions, recentEvents)
  }

  /** get all metadata fields that potentially have boosts (not filters) */
  def getBoostedMetadata( query: Query ): List[BoostableCorrelators] = {
    val paramsBoostedFields = ap.fields.getOrElse(List.empty[Field]).filter( field => field.bias < 0 ).map { field =>
      BoostableCorrelators(field.name, field.values, Some(field.bias))
    }

    val queryBoostedFields = query.fields.getOrElse(List.empty[Field]).filter { field =>
      field.bias >=  0f
    }.map { field =>
      BoostableCorrelators(field.name, field.values, Some(field.bias))
    }

    (queryBoostedFields ++ paramsBoostedFields).distinct  // de-dup and favor query fields
  }

  /** get all metadata fields that are filters (not boosts) */
  def getFilteringMetadata( query: Query ): List[FilterCorrelators] = {
    val paramsFilterFields = ap.fields.getOrElse(List.empty[Field]).filter( field => field.bias >= 0 ).map { field =>
      FilterCorrelators(field.name, field.values)
    }

    val queryFilterFields = query.fields.getOrElse(List.empty[Field]).filter { field =>
      field.bias <  0f
    }.map { field =>
      FilterCorrelators(field.name, field.values)
    }

    (queryFilterFields ++ paramsFilterFields).distinct // de-dup and favor query fields
  }

  /** get part of query for dates and date ranges */
  def getFilteringDateRange( query: Query ): List[JValue] = {

    var json: List[JValue] = List.empty[JValue]
    // currentDate in the query overrides the dateRange in the same query so ignore daterange if both
    val currentDate = query.currentDate.getOrElse(DateTime.now().toDateTimeISO.toString)

    if (query.dateRange.nonEmpty &&
      (query.dateRange.get.after.nonEmpty || query.dateRange.get.before.nonEmpty)) {
      val name = query.dateRange.get.name
      val before = query.dateRange.get.before.getOrElse("")
      val after = query.dateRange.get.after.getOrElse("")
      val rangeStart = s"""
                          |{
                          |  "constant_score": {
                          |    "filter": {
                          |      "range": {
                          |        "${name}": {
        """.stripMargin

      val rangeAfter = s"""
                          |          "gt": "${after}"
        """.stripMargin

      val rangeBefore = s"""
                           |          "lt": "${before}"
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

      json = json :+ parse(range)
    } else if (ap.availableDateName.nonEmpty && ap.expireDateName.nonEmpty) {// use the query date or system date
    val availableDate = ap.availableDateName.get // never None
    val expireDate = ap.expireDateName.get

      val available = s"""
                         |{
                         |  "constant_score": {
                         |    "filter": {
                         |      "range": {
                         |        "${availableDate}": {
                         |          "lte": "${currentDate}"
                         |        }
                         |      }
                         |    },
                         |    "boost": 0
                         |  }
                         |}
        """.stripMargin

      json = json :+ parse(available)
      val expire = s"""
                      |{
                      |  "constant_score": {
                      |    "filter": {
                      |      "range": {
                      |        "${expireDate}": {
                      |          "gt": "${currentDate}"
                      |        }
                      |      }
                      |    },
                      |    "boost": 0
                      |  }
                      |}
        """.stripMargin
      json = json :+ parse(expire)
    } else {
      logger.info("Misconfigured date information, either your engine.json date settings or your query's dateRange is incorrect.\nIngoring date information for this query.")
    }
    json
  }

}