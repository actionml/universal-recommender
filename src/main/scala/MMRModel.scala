package com.finderbots
//package org.apache.spark.mllib.recommendation
// This must be the same package as Spark's MatrixFactorizationModel because
// MatrixFactorizationModel's constructor is private and we are using
// its constructor in order to save and load the model
// todo: THIS MUST BE REMOVED FROM SPARK

import com.finderbots.MRAlgorithmParams

import io.prediction.controller.{PersistentModel, IPersistentModel, IPersistentModelLoader}
import io.prediction.data.storage.BiMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

/** Cooccurrence models to save in ES */
class MMRModel(/* something like cooccurrence and ES object? */) {

  def save(id: String, params: MRAlgorithmParams,
    sc: SparkContext): Boolean = {

    //todo: create writer for IndexedDatasetSpark that can save in ES
    true
  }

  override def toString = {
  /*s"userFeatures: [${userFeatures.count()}]" +
    s"(${userFeatures.take(2).toList}...)" +
    s" productFeatures: [${productFeatures.count()}]" +
    s"(${productFeatures.take(2).toList}...)" +
    s" userStringIntMap: [${userStringIntMap.size}]" +
    s"(${userStringIntMap.take(2)}...)" +
    s" itemStringIntMap: [${itemStringIntMap.size}]" +
    s"(${itemStringIntMap.take(2)}...)" */
    s"describe stuff sent to ES"
  }
}

/** Not sure we need a companion object since we seem to need only one constructor AFAIK*/
object MMRModel {
  def apply(id: String, params: MRAlgorithmParams,
    sc: Option[SparkContext]) = {
    new MMRModel(/* something like cooccurrence and ES object? */)
  }
}