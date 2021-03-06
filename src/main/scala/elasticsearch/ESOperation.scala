package elasticsearch

/**
  * Created by Ayan on 11/3/2016.
  */
import org.elasticsearch.node.NodeBuilder._
import org.elasticsearch.client.Client._
import org.elasticsearch.node.Node._
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest

import scala.io.Source
import org.elasticsearch.common.settings.{ImmutableSettings, Settings}
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.index.query.{QueryBuilder, QueryBuilders}
import org.elasticsearch.index.query.FilterBuilders._
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.Client
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.{XContentBuilder, XContentFactory}
import org.json.JSONArray
import org.json.JSONObject;


trait ESOperation {

  /**
    * This is getClient method which returns java API client
    *
    * @return
    */
  def getClient(): Client = {
    val node = nodeBuilder().local(true).node()
    val client = node.client()
    //val client = new TransportClient();
    //client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
    client
  }


  def insertOrUpdateAvailabilityRecord(indexName: String,indexType: String,instance :String,status :String,time :String,client: Client): Any = {

    val queryBuilder : QueryBuilder= QueryBuilders.matchQuery("instance",instance)
    val indexExists = client.admin().indices().prepareExists(indexName) .execute().actionGet().isExists()
    val searchResponse :SearchResponse =client.prepareSearch(indexName).setTypes(indexType).setSearchType(SearchType.QUERY_AND_FETCH)
      .setQuery(queryBuilder)
      .execute().actionGet()
    //println("searchResponse :"+searchResponse.toString)
    var fieldValue :Any = None: Option[Any]
    if(searchResponse.getHits.getHits.size>0)
    {
      println("Old record exists for instance "+instance+"")
      val searchHits = searchResponse.getHits.getHits
      //println("searchHits(0).sourceAsMap():"+searchHits(0).sourceAsMap())
      fieldValue = searchHits(0).sourceAsMap().get("status")
      println("status from Kafka "+status+",status from ES "+fieldValue)
    }
    else
    {
      println("Old record does NOT exists for instance "+instance+",inserting new records")
      val builder :XContentBuilder= XContentFactory.jsonBuilder().startObject()
      builder.field("instance", instance)
      builder.field("status", status)
      builder.field("time", time)
      builder.endObject()

      client.prepareIndex(indexName,indexType).setSource(builder).execute()

    }
  }

  def availabilityRecordExists(indexName: String,indexType: String, id: String,client: Client): Boolean = {

    val response :GetResponse  = client.prepareGet(indexName,indexType,id).execute().actionGet();
    print("record present:"+response.isExists)
    return response.isExists
  }

  //def getAvailabilityRecordField(indexName: String,indexType: String, termFieldName: String,termFieldValue: String,fieldName:String,client: Client): String = {
  def getAvailabilityRecordField(indexName: String,indexType: String, termFieldName: String,termFieldValue: String,fieldName:String,client: Client): Any = {

    //val qb : QueryBuilder= QueryBuilders.boolQuery().must(QueryBuilders.termQuery("time", "anid"))
    val qb : QueryBuilder= QueryBuilders.matchQuery(termFieldName,termFieldValue)

    //val builder :FilteredQueryBuilder=QueryBuilders.filteredQuery(QueryBuilders.termQuery("test","test"),FilterBuilders.termFilter("test","test"));
    val searchResponse :SearchResponse =client.prepareSearch(indexName).setTypes(indexType).setSearchType(SearchType.QUERY_AND_FETCH)
      .setQuery(qb)
      .execute().actionGet()
    //println("searchResponse :"+searchResponse.toString)
    var fieldValue :Any = None: Option[Any]
    if(searchResponse.getHits.getHits.size>0)
    {
      val searchHits = searchResponse.getHits.getHits
      //println("searchHits(0).sourceAsMap():"+searchHits(0).sourceAsMap())
      fieldValue = searchHits(0).sourceAsMap().get(fieldName)
    }
    println(fieldName+":"+fieldValue)
  }


  /**
    * This is insertBulkDocument method which takes each document from file and insert into index
    *
    * @param client
    * @return
    */
  def insertBulkDocument(client: Client): BulkResponse = {
    val bulkJson = Source.fromFile("src/main/resources/bulk.json").getLines().toList
    val bulkRequest = client.prepareBulk()
    for (i <- 0 until bulkJson.size) {
      bulkRequest.add(client.prepareIndex("twitter", "tweet", (i + 1).toString).setSource(bulkJson(i)))
    }
    bulkRequest.execute().actionGet()
  }

  /**
    * This is update index method which updates particular document by add one more field
    *
    * @param client
    * @param indexName
    * @param typeName
    * @param id
    * @return
    */
  def updateIndex(client: Client, indexName: String, typeName: String, id: String): UpdateResponse = {

    val updateRequest = new UpdateRequest(indexName, typeName, id)
      .doc(jsonBuilder()
        .startObject()
        .field("gender", "male")
        .endObject())
    client.update(updateRequest).get()
  }

  /**
    * This is sortByTimeStamp method provides sorted document on the basis of time stamp
    *
    * @param client
    * @param indexName
    * @return
    */
  def sortByTimeStamp(client: Client, indexName: String): SearchResponse = {
    val filter = andFilter(rangeFilter("post_date").from("2015-05-13") to ("2015-05-19"))
    val sortedSearchResponse = client.prepareSearch().setIndices(indexName)
      .setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), filter))
      .setSize(2).addSort("post_date", SortOrder.DESC).execute().actionGet()
    sortedSearchResponse
  }

  /**
    * This is deleteDocumentById method which removes particular document from index
    *
    * @param client
    * @param indexName
    * @param typeName
    * @param id
    * @return
    */
  def deleteDocumentById(client: Client, indexName: String, typeName: String, id: String): DeleteResponse = {

    val delResponse = client.prepareDelete("twitter", "tweet", "1")
      .execute()
      .actionGet()
    delResponse
  }

  /**
    * This is deleteIndex method which takes client and index as parameter and delete index from node
    *
    * @param client
    * @param indexName
    * @return
    */
  def deleteIndex(client: Client, indexName: String): Boolean = {

    val deleteIndexRequest = new DeleteIndexRequest(indexName)
    val deleteIndexResponse = client.admin().indices().delete(deleteIndexRequest).actionGet()
    deleteIndexResponse.isAcknowledged()
  }

}