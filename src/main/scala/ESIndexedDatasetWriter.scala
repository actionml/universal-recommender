package com.finderbots

import _root_.io.prediction.data.storage.{elasticsearch, StorageClientConfig}
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
import org.elasticsearch.client.transport.TransportClient
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

/** Extends the Writer trait to supply the type being written and the writer function */
trait ESIndexedDatasetWriter extends Writer[IndexedDatasetSpark]{

  /** Read in text delimited elements from all URIs in this comma delimited source String.
    * @param mc context for the Spark job
    * @param writeSchema describes the store and details for how to write the dataset.
    * @param dest string the points to the Elasticsearch field
    * @param indexedDataset what to write
    * @param sort true if written sorted by element strength (default = true)
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
      //val esClient = writeSchema("esClient").asInstanceOf[TransportClient]
      val parallel = writeSchema("parallel").asInstanceOf[Boolean]
      val test = writeSchema("test").asInstanceOf[Boolean]
      val properties = writeSchema("properties").asInstanceOf[Map[String, String]]
      val indexName = writeSchema("indexName").asInstanceOf[String]
      val elementDelim = " " // todo: should be an array of strings eventually, not a space delimited string of IDs

      require (indexedDataset != null ,"No IndexedDataset to write")
      require (!dest.isEmpty,"No destination to write to")
      require (!indexName.isEmpty, "No index name")

      val matrix = indexedDataset.matrix.checkpoint()
      val rowIDDictionary = indexedDataset.rowIDs
      val rowIDDictionary_bcast = mc.broadcast(rowIDDictionary)

      val columnIDDictionary = indexedDataset.columnIDs
      val columnIDDictionary_bcast = mc.broadcast(columnIDDictionary)

      // may want to mapBlock and create bulk updates as a slight optimization
      matrix.rdd.map { case (rowID, itemVector) =>

        // turn non-zeros into list for sorting
        var itemList = List[(Int, Double)]()
        for (ve <- itemVector.nonZeroes) {
          itemList = itemList :+ (ve.index, ve.get)
        }
        //sort by highest strength value descending(-)
        val vector = if (sort) itemList.sortBy { elem => -elem._2 } else itemList

        // construct the data to be written as a doc to ES
        // first get the external docID token
        val doc = if (vector.nonEmpty){
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
        val esClient = new elasticsearch.StorageClient(StorageClientConfig(parallel, test, properties)).client
        val indexRequest = new IndexRequest(indexName, "indicators", doc._1)
          .source(XContentFactory.jsonBuilder() // not using json4s here in case of executor overhead
          .startObject()
          .field(dest, doc._2 ) // todo: should be a list of String?
          .endObject())
        val updateRequest = new UpdateRequest(indexName, "indicators", doc._1)
          .doc(XContentFactory.jsonBuilder()
          .startObject()
          .field(dest, doc._2 )
          .endObject())
          .upsert(indexRequest)
        esClient.update(updateRequest).get()
     }

    }catch{
      case cce: ClassCastException => {
        logger.error("Schema has illegal values"); throw cce}
    }
  }

}

/** Writes IndexedDataset to Elasticsearch. Classes can be used to supply trait params in their
  * constructor.
  * @param writeSchema describes the parameters for writing to Elasticsearch.
  * @param mc [[org.apache.mahout.sparkbindings.SparkDistributedContext]] for distributed writing
  * @note the destination is supplied to Writer#writeTo
 */
class ElasticsearchIndexedDatasetWriter(val writeSchema: Schema, val sort: Boolean = true)
  (implicit val mc: SparkDistributedContext)
  extends ESIndexedDatasetWriter

