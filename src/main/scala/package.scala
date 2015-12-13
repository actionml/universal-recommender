/*
 * Licensed to ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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

import grizzled.slf4j.Logger
import scala.collection.JavaConversions._
import org.apache.mahout.sparkbindings.SparkDistributedContext
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.mahout.sparkbindings._
import org.apache.spark.rdd.RDD

/** Utility conversions for IndexedDatasetSpark */
package object conversions {

  implicit class IndexedDatasetConversions(val indexedDataset: IndexedDatasetSpark) {
    def toStringMapRDD(actionName: String): RDD[(String, Map[String, Seq[String]])] = {
      @transient lazy val logger = Logger[this.type]

      //val matrix = indexedDataset.matrix.checkpoint()
      val rowIDDictionary = indexedDataset.rowIDs
      implicit val sc = indexedDataset.matrix.context.asInstanceOf[SparkDistributedContext].sc
      val rowIDDictionary_bcast = sc.broadcast(rowIDDictionary)

      val columnIDDictionary = indexedDataset.columnIDs
      val columnIDDictionary_bcast = sc.broadcast(columnIDDictionary)

      // may want to mapPartition and create bulk updates as a slight optimization
      // creates an RDD of (itemID, Map[correlatorName, list-of-correlator-values])
      indexedDataset.matrix.rdd.map[(String, Map[String, Seq[String]])] { case (rowNum, itemVector) =>

        // turn non-zeros into list for sorting
        var itemList = List[(Int, Double)]()
        for (ve <- itemVector.nonZeroes) {
          val v = ve
          itemList = itemList :+(ve.index, ve.get)
        }
        //sort by highest strength value descending(-)
        val vector = itemList.sortBy { elem => -elem._2 }

        val itemID = rowIDDictionary_bcast.value.inverse.getOrElse(rowNum, "INVALID_ITEM_ID")
        try {

          require(itemID != "INVALID_ITEM_ID", s"Bad row number in  matrix, skipping item ${rowNum}")
          require(vector.nonEmpty, s"No values so skipping item ${rowNum}")

          // create a list of element ids
          val values = vector.map { item =>
            columnIDDictionary_bcast.value.inverse.getOrElse(item._1, "") // should always be in the dictionary
          }

          (itemID, Map(actionName -> values))

        } catch {
          case cce: IllegalArgumentException => //non-fatal, ignore line
            null.asInstanceOf[(String, Map[String, Seq[String]])]
        }

      }.filter(_ != null)
    }
  }

}
