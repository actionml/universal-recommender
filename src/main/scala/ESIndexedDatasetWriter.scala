package com.finderbots

import _root_.io.prediction.data.storage.StorageClientConfig
import _root_.io.prediction.data.storage.elasticsearch.StorageClient
import org.apache.log4j.Logger
import org.apache.mahout.math.indexeddataset._
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext._
import org.apache.mahout.math.RandomAccessSparseVector
import org.apache.mahout.math.drm.{DrmLike, DrmLikeOps, DistributedContext, CheckpointedDrm}
import org.apache.mahout.sparkbindings._
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentFactory
import scala.collection.JavaConversions._
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.FilterBuilders.termFilter
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

/** Extends the Writer trait to supply the type being written and supplies the writer function */
trait ESIndexedDatasetWriter extends Writer[IndexedDatasetSpark]{

  /** Read in text delimited elements from all URIs in this comma delimited source String.
    * @param mc context for the Spark job
    * @param writeSchema describes the store and details for how to write the dataset.
    * @param dest string the points to dest for [[org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark]]
    * @param indexedDataset what to write
    * @param sort true if written sorted by element strength (default)
    */
  protected def writer(
    mc: DistributedContext,
    writeSchema: Schema,
    dest: String,
    indexedDataset: IndexedDatasetSpark,
    sort: Boolean = true): Unit = {
    @transient lazy val logger = Logger.getLogger(this.getClass.getCanonicalName)
    try {
      // pull out and validate schema params
      // val omitScore = writeSchema("omitScore").asInstanceOf[Boolean] // will throw cast error if wrong type
      val esStorageClient = writeSchema("es").asInstanceOf[StorageClient]
      val indexName = writeSchema("indexName").asInstanceOf[String]
      //instance vars must be put into locally scoped vals when put into closures that are
      //executed but Spark
      val elementDelim = " "

      require (indexedDataset != null ,"No IndexedDataset to write")
      require (!dest.isEmpty,"No destination to write to")
      require (!indexName.isEmpty, "No index name")

      val matrix = indexedDataset.matrix.checkpoint()
      val rowIDDictionary = indexedDataset.rowIDs
      val rowIDDictionary_bcast = mc.broadcast(rowIDDictionary)

      val columnIDDictionary = indexedDataset.columnIDs
      val columnIDDictionary_bcast = mc.broadcast(columnIDDictionary)

      matrix.rdd.map { case (rowID, itemVector) =>

        // turn non-zeros into list for sorting
        var itemList = List[(Int, Double)]()
        for (ve <- itemVector.nonZeroes) {
          itemList = itemList :+ (ve.index, ve.get)
        }
        //sort by highest value descending(-)
        val vector = if (sort) itemList.sortBy { elem => -elem._2 } else itemList

        // construct the data to be written as a doc to ES
        // first get the external docID token
        val doc = if (!vector.isEmpty){
          val docID = rowIDDictionary_bcast.value.inverse.getOrElse(rowID, "INVALID_DOC_ID")
          // for the rest of the row, construct the vector contents of elements (external column ID, strength value)
          var line = ""
          for (item <- vector) {
            // todo: should create a string array for multi-valued field instead of space delimited doc
            line += columnIDDictionary_bcast.value.inverse.getOrElse(item._1, "INVALID_ITEM_ID") + elementDelim
          }
          // drop the last delimiter, not needed to end the line
          (docID, line.dropRight(1))
        } else {//no items so write a line with id but no values, no delimiters
          (rowIDDictionary_bcast.value.inverse.getOrElse(rowID, "INVALID_DOC_ID"),"")
        } // "if" returns a line of text so this must be last in the block

        // create and update and write to ES here
        //implicit val formats = DefaultFormats.lossless

        // todo: use the upsert so indicator fields won't be deleted when the next indicator is written
        // https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-update-api.html#java-update-api-upsert
        // todo: what should be used for the model MMRModelID
        // todo: I think the doc._1, which is the doc ID is actually used as a URI fragment so better escape it
        val indexRequest = new IndexRequest(indexName, "indicators", doc._1)
          .source(XContentFactory.jsonBuilder()
            .startObject()
              .field(dest, doc._2 )
            .endObject())
        val updateRequest = new UpdateRequest(indexName, "indicators", doc._1)
          .doc(XContentFactory.jsonBuilder()
            .startObject()
              .field(dest, doc._2 )
            .endObject())
          .upsert(indexRequest)
        esStorageClient.client.update(updateRequest).get()
      }

    }catch{
      case cce: ClassCastException => {
        logger.error("Schema has illegal values"); throw cce}
    }
  }
}

/**
 * Writes  text delimited files into an IndexedDataset. Classes can be used to supply trait params in their
 * constructor.
 * @param writeSchema describes the parameters for writing to Elasticsearch.
 * @param mc Spark context for reading files
 * @note the destination is supplied to Writer#writeTo
 */
class ElasticsearchIndexedDatasetWriter(val writeSchema: Schema, val sort: Boolean = true)
  (implicit val mc: DistributedContext)
  extends ESIndexedDatasetWriter

