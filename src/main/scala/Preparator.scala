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

import org.apache.predictionio.controller.PPreparator
import org.apache.predictionio.data.storage.PropertyMap
import org.apache.mahout.math.indexeddataset.{IndexedDataset, BiDictionary}
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

class Preparator
  extends PPreparator[TrainingData, PreparedData] {

  /** Create [[org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark]] rdd backed
    * "distributed row matrices" from the input string keyed rdds.
    * @param sc Spark context
    * @param trainingData list of (actionName, actionRDD)
    * @return list of (correlatorName, correlatorIndexedDataset)
    */
  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    // now that we have all actions in separate RDDs we must merge any user dictionaries and
    // make sure the same user ids map to the correct events
    var userDictionary: Option[BiDictionary] = None

    val indexedDatasets = trainingData.actions.map{ case(eventName, eventIDS) =>

      // passing in previous row dictionary will use the values if they exist
      // and append any new ids, so after all are constructed we have all user ids in the last dictionary
      val ids = IndexedDatasetSpark(eventIDS, userDictionary)(sc)
      userDictionary = Some(ids.rowIDs)
      (eventName, ids)
    }

    // now make sure all matrices have identical row space since this corresponds to all users
    val numUsers = userDictionary.get.size
    val numPrimary = indexedDatasets.head._2.matrix.nrow
    // todo: check to see that there are events in primary event IndexedDataset and abort if not.
    val rowAdjustedIds = indexedDatasets.map { case(eventName, eventIDS) =>
      (eventName, eventIDS.create(eventIDS.matrix, userDictionary.get, eventIDS.columnIDs).newRowCardinality(numUsers))
    }

    new PreparedData(rowAdjustedIds, trainingData.fieldsRDD)
  }

}

class PreparedData(
    val actions: List[(String, IndexedDataset)],
    val fieldsRDD: RDD[(String, PropertyMap)])
  extends Serializable