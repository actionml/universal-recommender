package org.template

import io.prediction.controller.{EngineFactory, Engine}

/** Constructed by PredictionIO and passed to MMRAlgoritm.predict */
case class Query(
  // todo: this needs to be a good deal more expressive to encompass things like context
  user: String,
  item: String,
  num: Int
) extends Serializable

/** results of a MMRAlgoritm.predict */
case class PredictedResult(
  itemScores: Array[ItemScore]
) extends Serializable

case class ItemScore(
  item: String, // item id
  score: Double // rank, original order returned
) extends Serializable

object RecommendationEngine extends EngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("mmr" -> classOf[MMRAlgorithm]),
      classOf[Serving])
  }
}