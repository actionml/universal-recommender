package org.template

import java.util

import io.prediction.controller.P2LAlgorithm
import io.prediction.controller.Params
import io.prediction.data.storage.{elasticsearch, StorageClientConfig, Event, BiMap}
import io.prediction.data.store.LEventStore
import org.apache.mahout.math.cf.SimilarityAnalysis
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchRequest}
import org.elasticsearch.index.query.QueryBuilder
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.convert.wrapAsScala._
import scala.collection.convert.decorateAll._
import scala.collection.convert.Wrappers
import grizzled.slf4j.Logger
/*
“fields”: [
   {
     “name”: ”fieldname”
     “values”: [“series”, ...],// values in the field query
     “bias”: -maxFloat..maxFloat// negative means a filter, positive is a boost
   }, ...
 ]
*/

/** Instantiated from engine.json */
case class MMRAlgorithmParams(
    appName: String,// filled in from engine.json
    indexName: String = "mmrindex", // can optionally be used to specify the elasticsearch index name
    typeName: String = "items", // can optionally be used to specify the elasticsearch type name
    eventNames: List[String],// names used to ID all user actions
    blacklist: Option[List[String]],
    backfill: Option[String],// popular or trending
    maxQueryActions: Option[Int] = Some(20),//default = DefaultMaxQueryItems
    num: Option[Int] = Some(20),
    userBias: Option[Float] = None,// will cause the default search engine boost of 1.0
    itemBias: Option[Float] = None,// will cause the default search engine boost of 1.0
    fields: Option[List[Field]] = None,
    seed: Option[Long] = Some(System.currentTimeMillis()))
  extends Params //fixed default make it reproducable unless supplied

/** Creates cooccurrence, cross-cooccurrence and eventually content indicators with
  * [[org.apache.mahout.math.cf.SimilarityAnalysis]] The analysis part of the recommender is
  * done here but the algorithm can predict only when the coocurrence data is indexed in a
  * search engine like Elasticsearch. This is done in MMRModel.save.
  *
  * @param ap taken from engine.json to describe limits and event types
  */
class MMRAlgorithm(val ap: MMRAlgorithmParams)
  extends P2LAlgorithm[PreparedData, MMRModel, Query, PredictedResult] {

  case class BoostableIndicators(actionName: String, itemIDs: Seq[String], boost: Float)
  case class FilterIndicators(actionName: String, itemIDs: Seq[String])

  @transient lazy val logger = Logger[this.type]

  def train(sc: SparkContext, data: PreparedData): MMRModel = {
    // No one likes empty training data.
    require(data.actions.take(1).nonEmpty,
      s"Primary action in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")

    logger.info("Actions read now creating indicators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.actions.map(_._2).toArray) // strip action names
      .map(_.asInstanceOf[IndexedDatasetSpark]) // we know this is a Spark version of IndexedDataset
    val cooccurrenceIndicators = cooccurrenceIDSs.zip(data.actions.map(_._1)).map(_.swap) //add back the actionNames

    logger.info("Indicators created now putting into MMRModel")
    new MMRModel(cooccurrenceIndicators, data.fieldsRDD, ap.indexName)
  }

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
    *          }
    *        ],
    *        "must": [
    *          {
    *            "terms": {
    *              "category": ["cat1"]
    *            }
    *          }
    *        ]
    *      }
    *    }
    *  }
    *
    * @param model <strong>Ignored!</strong> since the model is already in Elasticsearch
    * @param query contains the user id to recommend for
    * @return
    */
  def predict(model: MMRModel, query: Query): PredictedResult = {
    logger.info(s"Query received, user id: ${query.user}, item id: ${query.item}")

    val queryAndBlacklist = buildQuery(ap, query)
    val recs = esClient.search(queryAndBlacklist._1, ap.indexName)
    removeBlacklisted(recs, query, queryAndBlacklist._2)
  }

  def removeBlacklisted(recs: PredictedResult, query: Query, queryBlacklist: List[Event] ): PredictedResult = {
    val eventFilteredItemScores = recs.itemScores.filterNot { itemScore =>
      queryBlacklist.filter { event => //if some rec id == something blacklisted
        if (event.entityId == itemScore.item) {
          val breakpoint = 0
          true
        } else false
      }.nonEmpty //if some rec id == something blacklisted then don't return the itemScore
    }

    val queryFilteredItemScores = eventFilteredItemScores.filterNot { itemScore =>
      query.blacklist.getOrElse(List.empty[String]).contains(itemScore.item)
    } //if some rec id == something blacklisted in query then don't return the itemScore
    PredictedResult(queryFilteredItemScores)
  }

  def buildQuery(ap: MMRAlgorithmParams, query: Query): (String, List[Event]) = {

    // there may be default query values in the engine.json so build the qurey from there
    // then overwrite with data in the specific query if needed.
    // In order to match the form of the Elasticsearch query, indicators are broken into
    // one with possible boosts and ones that filter results

    try{ // require the minimum of a user or item, if not then return nothing
      require( query.item.nonEmpty || query.user.nonEmpty, "Warning: a query must include either a user or item id")

      // create a List[BoostableIndicators] of all query indicators that can have a boost attached
      val alluserEvents = getBiasedRecentUserActions(query)

      val recentUserHistory = if ( ap.userBias.getOrElse(1f) >= 0f )
        alluserEvents._1.slice(0, ap.maxQueryActions.get - 1)
      else List.empty[BoostableIndicators]

      val similarItems = if ( ap.itemBias.getOrElse(1f) >= 0f )
        getBiasedSimilarItems(query)
      else List.empty[BoostableIndicators]

      val boostedMetadata = getBoostedMetadata(query)

      val allBoostedIndicators = recentUserHistory ++ similarItems ++ boostedMetadata

      // create a List[FilteredIndicators] of all query indicators that are to be used to filter results
      val recentUserHistoryFilter = if ( ap.userBias.getOrElse(1f) < 0f ) {
        // strip any boosts
        alluserEvents._1.map { i =>
          FilterIndicators(i.actionName, i.itemIDs)
        }.slice(0, ap.maxQueryActions.get - 1)
      } else List.empty[FilterIndicators]

      val similarItemsFilter = if ( ap.itemBias.getOrElse(1f) < 0f ) {
        getBiasedSimilarItems(query).map { i =>
          FilterIndicators(i.actionName, i.itemIDs)
        }.toList
      } else List.empty[FilterIndicators]

      val filteringMetadata = getFilteringMetadata(query)

      val allFilteringIndicators = recentUserHistoryFilter ++ similarItemsFilter ++ filteringMetadata

      //val indicatorArray = allIndicators.toArray

      // since users have action history and items have indicators and both correspond to the same "actions" like
      // purchase or view, we'll pass both to the query if the user history or items indicators are empty
      // then metadata must be relied on to return results.

      val numRecs = if ( query.num.nonEmpty ) query.num.get
      else ap.num.get
      
      val json =
        (
          ("size" -> numRecs) ~
          ("query"->
            ("bool"->
              ("should"->
                allBoostedIndicators.map { i =>
                  ("terms" -> (i.actionName -> i.itemIDs) ~ ("boost" -> i.boost))}

              ) ~
              ("must"->
                allFilteringIndicators.map { i =>
                  ("terms" -> (i.actionName -> i.itemIDs))}
              )
            )
          )
        )
      val j = compact(render(json))
      logger.info(s"Query: \n${j}\n")
      (compact(render(json)), alluserEvents._2)
    } catch {
      case e: IllegalArgumentException =>
        ("", List.empty[Event])
    }
  }

  /** Get similar items for an item, these are already in the action indicators in ES */
  def getBiasedSimilarItems(query: Query): Seq[BoostableIndicators] = {
    if (query.item.nonEmpty) {
      val m = esClient.getSource(ap.indexName, ap.typeName, query.item.get)

      val itemEventBias = query.userBias.getOrElse(ap.userBias.getOrElse(1f))
      val userEventsBoost = if (itemEventBias > 0) itemEventBias else 1
      ap.eventNames.map { action =>
        val items = try {
          m.get(action).asInstanceOf[util.ArrayList[String]].toList
        } catch {
          case cce: ClassCastException =>
            logger.warn(s"Bad value in item ${query.item} corresponding to key: ${action} that was not a List[String]" +
              " ignored.")
            List.empty[String]
        }
        val rItems = if (items.size <= ap.maxQueryActions.get) items else items.slice(0, ap.maxQueryActions.get - 1)
        BoostableIndicators(action, rItems, userEventsBoost)
      }
    } else List.empty[BoostableIndicators]
  }

  /** Get recent events of the user on items to create the recommendations query from */
  def getBiasedRecentUserActions(
    query: Query): (Seq[BoostableIndicators], List[Event]) = {

    val recentEvents = try {
      LEventStore.findByEntity(
        appName = ap.appName,
        // entityType and entityId is specified for fast lookup
        entityType = "user",
        entityId = query.user.get,
        // one query per eventName is not ideal, maybe one query for lots of events then split by eventName
        //eventNames = Some(Seq(action)),// get all and separate later
        targetEntityType = Some(Some("item")),
        // limit = Some(maxQueryActions), // this will get all history then each action can be limited before using in
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
    val userEventsBoost = if (userEventBias > 0) userEventBias else 1
    val rActions = ap.eventNames.map { action =>
      var items = List[String]()

      for ( event <- recentEvents )
        if (event.event == action && items.size < ap.maxQueryActions.get) {
          items = event.targetEntityId.get :: items
          // todo: may throw exception and we should ignore the event instead of crashing
        }
      BoostableIndicators(action, items, userEventsBoost)// todo: userBias may be None!
    }
    (rActions, recentEvents)
  }

  def getBoostedMetadata( query: Query ): List[BoostableIndicators] = {
    val paramsBoostedFields = ap.fields.getOrElse(List.empty[Field]).filter( field => field.bias <= 0 ).map { field =>
      BoostableIndicators(field.name, field.values, field.bias)
    }
    
    val queryBoostedFields = query.fields.getOrElse(List.empty[Field]).filter { field =>
      field.bias >=  0f
    }.map { field =>
      BoostableIndicators(field.name, field.values, field.bias)
    }

    paramsBoostedFields ++ queryBoostedFields
  }

  def getFilteringMetadata( query: Query ): List[FilterIndicators] = {
    val paramsFilterFields = ap.fields.getOrElse(List.empty[Field]).filter( field => field.bias > 0 ).map { field =>
      FilterIndicators(field.name, field.values)
    }

    val queryFilterFields = query.fields.getOrElse(List.empty[Field]).filter { field =>
      field.bias <  0f
    }.map { field =>
      FilterIndicators(field.name, field.values)
    }
    
    paramsFilterFields ++ queryFilterFields
  }

}