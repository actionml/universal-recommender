package org.template

import io.prediction.controller.{EngineFactory, Engine}


/** Constructed by PredictionIO and passed to MMRAlgoritm.predict */
case class Query(
    user: Entity,
    item: Entity,
    fields: List[Field],
    blacklist: List[String],
    num: Int)
  extends Serializable

case class Entity(id: Option[String] = None, bias: Option[Float] = None)

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