package org.template

import io.prediction.controller.{EngineFactory, Engine}


/** Constructed by PredictionIO and passed to MMRAlgoritm.predict */
case class Query(
    user: Option[String] = None,
    userBias: Option[Float] = None,
    item: Option[String] = None,
    itemBias: Option[Float] = None,
    fields: Option[List[Field]] = None,
    blacklist: Option[List[String]] = None,
    returnSelf: Option[Boolean] = None,// means use the query default
    num: Option[Int] = None)
  extends Serializable


case class Field(
    name: String,
    values: Array[String],
    bias: Float)
  extends Serializable

/** results of a MMRAlgoritm.predict */
case class PredictedResult(
    itemScores: Array[ItemScore])
  extends Serializable

case class ItemScore(
    item: String, // item id
    score: Double )// rank, original order returned
  extends Serializable

object RecommendationEngine extends EngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("mmr" -> classOf[MMRAlgorithm]),
      classOf[Serving])
  }
}