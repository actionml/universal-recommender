/*
 * Copyright ActionML, LLC under one or more
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
import org.json4s._

/** Utility conversions for IndexedDatasetSpark */
package object conversions {

  type UserID = String
  type ActionID = String
  type ItemID = String
  // Item properties (fieldName, fieldValue)
  type ItemProps = Map[String, JValue]

  def drawActionML(implicit logger: Logger): Unit = {
    val actionML =
      """
        |
        |               _   _             __  __ _
        |     /\       | | (_)           |  \/  | |
        |    /  \   ___| |_ _  ___  _ __ | \  / | |
        |   / /\ \ / __| __| |/ _ \| '_ \| |\/| | |
        |  / ____ \ (__| |_| | (_) | | | | |  | | |____
        | /_/    \_\___|\__|_|\___/|_| |_|_|  |_|______|
        |
        |
      """.stripMargin

    logger.info(actionML)
  }

  def drawInfo(title: String, dataMap: Seq[(String, Any)])(implicit logger: Logger): Unit = {
    val leftAlignFormat = "║ %-30s%-28s ║"

    val line = "═" * 60

    val preparedTitle = "║ %-58s ║".format(title)
    val data = dataMap.map {
      case (key, value) =>
        leftAlignFormat.format(key, value)
    } mkString "\n"

    logger.info(
      s"""
         |╔$line╗
         |$preparedTitle
         |$data
         |╚$line╝
         |""".stripMargin)

  }

  implicit class OptionCollection[T](collectionOpt: Option[Seq[T]]) {
    def getOrEmpty: Seq[T] = {
      collectionOpt.getOrElse(Seq.empty[T])
    }
  }

  implicit class IndexedDatasetConversions(val indexedDataset: IndexedDatasetSpark) {
    def toStringMapRDD(actionName: ActionID): RDD[(ItemID, ItemProps)] = {
      @transient lazy val logger = Logger[this.type]

      //val matrix = indexedDataset.matrix.checkpoint()
      val rowIDDictionary = indexedDataset.rowIDs
      implicit val sc = indexedDataset.matrix.context.asInstanceOf[SparkDistributedContext].sc
      val rowIDDictionary_bcast = sc.broadcast(rowIDDictionary)

      val columnIDDictionary = indexedDataset.columnIDs
      val columnIDDictionary_bcast = sc.broadcast(columnIDDictionary)

      // may want to mapPartition and create bulk updates as a slight optimization
      // creates an RDD of (itemID, Map[correlatorName, list-of-correlator-values])
      indexedDataset.matrix.rdd.map[(ItemID, ItemProps)] {
        case (rowNum, itemVector) =>

          // turn non-zeros into list for sorting
          var itemList = List[(Int, Double)]()
          for (ve <- itemVector.nonZeroes) {
            itemList = itemList :+ (ve.index, ve.get)
          }
          //sort by highest strength value descending(-)
          val vector = itemList.sortBy { elem => -elem._2 }

          val itemID = rowIDDictionary_bcast.value.inverse.getOrElse(rowNum, "INVALID_ITEM_ID")
          try {

            require(itemID != "INVALID_ITEM_ID", s"Bad row number in  matrix, skipping item $rowNum")
            require(vector.nonEmpty, s"No values so skipping item $rowNum")

            // create a list of element ids
            val values = JArray(vector.map { item =>
              JString(columnIDDictionary_bcast.value.inverse.getOrElse(item._1, "")) // should always be in the dictionary
            })

            (itemID, Map(actionName -> values))

          } catch {
            case cce: IllegalArgumentException => //non-fatal, ignore line
              null.asInstanceOf[(ItemID, ItemProps)]
          }

      }.filter(_ != null)
    }
  }

}
