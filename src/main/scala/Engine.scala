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

import java.util.Date

import org.apache.predictionio.controller.{EngineFactory, Engine}

/** This file contains case classes that are used with reflection to specify how query and config
  * JSON is to be parsed. the Query case class, for instance defines the way a JSON query is to be
  * formed. The same for param case classes.
  */

/** The Query spec with optional values. The only hard rule is that there must be either a user or
  * an item id. All other values are optional. */
case class Query(
    user: Option[String] = None, // must be a user or item id
    userBias: Option[Float] = None, // default: whatever is in algorithm params or 1
    item: Option[String] = None, // must be a user or item id
    itemBias: Option[Float] = None, // default: whatever is in algorithm params or 1
    fields: Option[List[Field]] = None, // default: whatever is in algorithm params or None
    currentDate: Option[String] = None, // if used will override dateRange filter, currentDate must lie between the item's
    // expireDateName value and availableDateName value, all are ISO 8601 dates
    dateRange: Option[DateRange] = None, // optional before and after filter applied to a date field
    blacklistItems: Option[List[String]] = None, // default: whatever is in algorithm params or None
    returnSelf: Option[Boolean] = None,// means for an item query should the item itself be returned, defaults
                                       // to what is in the algorithm params or false
    num: Option[Int] = None, // default: whatever is in algorithm params, which itself has a default--probably 20
    eventNames: Option[List[String]]) // names used to ID all user actions
  extends Serializable

/** Used to specify how Fields are represented in engine.json */
case class Field( // no optional values for fields, whne specified
    name: String, // name of metadata field
    values: Array[String], // fields can have multiple values like tags of a single value as when using hierarchical
    // taxonomies
    bias: Float)// any positive value is a boost, negative is a filter
  extends Serializable

/** Used to specify the date range for a query */
case class DateRange(
    name: String, // name of item property for the date comparison
    before: Option[String], // empty strings means no filter
    after: Option[String]) // both empty should be ignored
  extends Serializable

/** results of a MMRAlgoritm.predict */
case class PredictedResult(
    itemScores: Array[ItemScore])
  extends Serializable

case class ItemScore(
    item: String, // item id
    score: Double )// used to rank, original score returned from teh search engine
  extends Serializable

object RecommendationEngine extends EngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("ur" -> classOf[URAlgorithm]), // IMPORTANT: "ur" must be the "name" of the parameter set in engine.json
      classOf[Serving])
  }
}