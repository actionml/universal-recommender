package com.finderbots

import io.prediction.controller.PPreparator
import io.prediction.data.storage.PropertyMap
import org.apache.mahout.math.indexeddataset.{IndexedDataset, BiDictionary}
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.mahout.sparkbindings
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

class Preparator
  extends PPreparator[TrainingData, PreparedData] {

  /** Create [[org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark]] rdd backed
    * "distributed row matrices" from the input string keyed rdds.
    * @param sc Spark context
    * @param trainingData list of (actionName, actionRDD)
    * @return list of (indicatorName, indicatorIndexedDataset)
    */
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    // now that we have all actions in separate RDDs we must merge any user dictionaries and
    // make sure the same user ids map to the correct events
    var userDictionary: Option[BiDictionary] = None
    val indexedDatasets = trainingData.actions.map{ case(eventName, eventIDS) =>
      val ids = IndexedDatasetSpark(eventIDS, userDictionary)(sc) // passing in previous row dictionary will use the values if they exist
      // and append any new ids, so after all are constructed we have all user ids in the last dictionary
      userDictionary = Some(ids.rowIDs)
      (eventName, ids)
    }
    // now make sure all matrices have identical row space since this corresponds to all users
    val rowAdjustedIds = indexedDatasets.map { case(eventName, eventIDS) =>
      val numUsers = userDictionary.get.size
      (eventName, eventIDS.create(eventIDS.matrix, userDictionary.get, eventIDS.columnIDs).newRowCardinality(numUsers))
    }

    new PreparedData(rowAdjustedIds, trainingData.fieldsRDD)
  }

}

class PreparedData(
  val actions: List[(String, IndexedDataset)],
  val fieldsRDD: RDD[(String, PropertyMap)]
) extends Serializable