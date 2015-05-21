package com.finderbots

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

/** todo: this needs to be a good deal more expressive to encompass things like context */
case class Query(
  user: String,
  num: Int
) extends Serializable

case class PredictedResult(
  itemScores: Array[ItemRank]
) extends Serializable

case class ItemRank(
  item: String, // item id
  score: Int // rank, original order returned
) extends Serializable

object RecommendationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("mmr" -> classOf[MMRAlgorithm]),
      classOf[Serving])
  }
}