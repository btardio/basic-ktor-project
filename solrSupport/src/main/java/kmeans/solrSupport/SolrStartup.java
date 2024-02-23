package kmeans.solrSupport;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.exit;


public class SolrStartup {
	private static final Logger log = LoggerFactory.getLogger(SolrStartup.class);
	public static final String SOLR_CONNECT_IP = System.getenv("SOLR_CONNECT_IP")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			"solr1:8983" : System.getenv("SOLR_CONNECT_IP");


	public static final Integer COORDINATES_AFTER_WEBSERVER_SHARDS = System.getenv("COORDINATES_AFTER_WEBSERVER_SHARDS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			3 : Integer.valueOf(System.getenv("COORDINATES_AFTER_WEBSERVER_SHARDS"));
	public static final Integer COORDINATES_AFTER_WEBSERVER_REPLICAS = System.getenv("COORDINATES_AFTER_WEBSERVER_REPLICAS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			1 : Integer.valueOf(System.getenv("COORDINATES_AFTER_WEBSERVER_REPLICAS"));
	public static final Integer COORDINATES_AFTER_COLLECTOR_SHARDS = System.getenv("COORDINATES_AFTER_COLLECTOR_SHARDS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			3 : Integer.valueOf(System.getenv("COORDINATES_AFTER_COLLECTOR_SHARDS"));
	public static final Integer COORDINATES_AFTER_COLLECTOR_REPLICAS = System.getenv("COORDINATES_AFTER_COLLECTOR_REPLICAS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			1 : Integer.valueOf(System.getenv("COORDINATES_AFTER_COLLECTOR_REPLICAS"));
	public static final Integer COORDINATES_AFTER_ANALYZER_SHARDS = System.getenv("COORDINATES_AFTER_ANALYZER_SHARDS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			3 : Integer.valueOf(System.getenv("COORDINATES_AFTER_ANALYZER_SHARDS"));
	public static final Integer COORDINATES_AFTER_ANALYZER_REPLICAS = System.getenv("COORDINATES_AFTER_ANALYZER_REPLICAS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			1 : Integer.valueOf(System.getenv("COORDINATES_AFTER_ANALYZER_REPLICAS"));
	public static final Integer SCHEDULES_SHARDS = System.getenv("SCHEDULES_SHARDS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			3 : Integer.valueOf(System.getenv("SCHEDULES_SHARDS"));
	public static final Integer SCHEDULES_REPLICAS = System.getenv("SCHEDULES_REPLICAS")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			1 : Integer.valueOf(System.getenv("SCHEDULES_REPLICAS"));
	
	
	
	
	// note: delete all {'delete': {'query': '*:*'}}

	static public void createCollection(int numShards, int numReplicas, String collectionName, String zooHost) throws SolrServerException, IOException {
		SolrClient solr = null;
		try {
			solr = new CloudSolrClient.Builder().withZkHost(zooHost).build();
			solr.ping();

			SolrPingResponse pingResponse = null;
			try {
				pingResponse = solr.ping();
			} catch (Exception e) {
				log.error("Unable to ping zk host.");
				exit(-1);
			}
			if (pingResponse.getStatus() != 0){
				log.error("Unable to ping zk host.");
				exit(-1);
			};

		} catch ( Exception e ) {
			exit(-1);
		}
		try {
			List<String> existingCollectionNames = CollectionAdminRequest.listCollections(solr);
			if (!existingCollectionNames.contains(collectionName)) {
				solr.request(CollectionAdminRequest.createCollection(collectionName, numShards, numReplicas));
			}
		} catch ( Exception e ){
			exit(-1);
		}

	}

	static public void createSchema(SolrClient client) throws SolrServerException, IOException {
		try {
			Map<String, Object> fieldAttributes = new HashMap<>();

			fieldAttributes.put("name", "schedule_uuid");
			fieldAttributes.put("type", "string");
			fieldAttributes.put("indexed", "true");
			fieldAttributes.put("stored", "true");

			SchemaRequest.AddField addFieldUpdateSchemaRequestUuid = new SchemaRequest.AddField(fieldAttributes);
			addFieldUpdateSchemaRequestUuid.process(client);
		} catch(BaseHttpSolrClient.RemoteExecutionException e ) {
			LoggerFactory.getLogger(SolrStartup.class).info("Failed creating schema, continuing." + "\n" + e.getMessage());
		}
		try {
			Map<String, Object> fieldAttributes = new HashMap<>();

			fieldAttributes.put("name", "coordinate_uuid");
			fieldAttributes.put("type", "string");
			fieldAttributes.put("indexed", "true");
			fieldAttributes.put("stored", "true");

			SchemaRequest.AddField addFieldUpdateSchemaRequestUuid = new SchemaRequest.AddField(fieldAttributes);
			addFieldUpdateSchemaRequestUuid.process(client);
		} catch( BaseHttpSolrClient.RemoteExecutionException e ) {
			LoggerFactory.getLogger(SolrStartup.class).info("Failed creating schema, continuing." + "\n" + e.getMessage());
		}
		try {
			Map<String, Object> fieldAttributes = new HashMap<>();

			fieldAttributes.put("name", "jsonData");
			fieldAttributes.put("type", "text");
			fieldAttributes.put("stored", "true");
			fieldAttributes.put("indexed", "false");
			fieldAttributes.put("docValues", "false");


			SchemaRequest.AddField addFieldUpdateSchemaRequestJsonData = new SchemaRequest.AddField(fieldAttributes);
			addFieldUpdateSchemaRequestJsonData.process(client);
		} catch( BaseHttpSolrClient.RemoteExecutionException e ) {
			LoggerFactory.getLogger(SolrStartup.class).info("Failed creating schema, continuing." + "\n" + e.getMessage());
		}
		try {
			Map<String, Object> fieldAttributes = new HashMap<>();

			fieldAttributes.put("name", "timestamp");
			fieldAttributes.put("type", "plong");
			fieldAttributes.put("stored", "true");

			SchemaRequest.AddField addFieldUpdateSchemaRequestJsonData = new SchemaRequest.AddField(fieldAttributes);
			addFieldUpdateSchemaRequestJsonData.process(client);
		} catch( BaseHttpSolrClient.RemoteExecutionException e ) {
			LoggerFactory.getLogger(SolrStartup.class).info("Failed creating schema, continuing." + "\n" + e.getMessage());
		}

	}

	public static void solrInitialize(String zooHost) throws Exception {

		HttpSolrClient solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/sanesystem").build();

		// sane checks stay

		// we have a collection
		createCollection(1,1, "sanesystem", zooHost);

		// we have a schema
		createSchema(solrClient);

		String currentTime = String.valueOf(new Date().getTime());

		// we can write
		solrClient.addBean(
				new SolrEntity(
						currentTime,
						currentTime,
						"{" + new Date().toString() + "}"
				)
		);
		solrClient.commit();

//            // not sane            .withCql("CREATE TABLE sanity (uuid varchar, value varchar, PRIMARY KEY (uuid));")

		// we can read
		SolrQuery query = new SolrQuery();
		query.set("q", "schedule_uuid:" + currentTime);
		QueryResponse response = solrClient.query(query);

		if ( response.getResults().getNumFound() != 1L ) {
			throw new Exception("Error, not sane.");
		}

		createCollection(COORDINATES_AFTER_WEBSERVER_SHARDS,COORDINATES_AFTER_WEBSERVER_REPLICAS, "coordinates_after_webserver", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_webserver").build();
		createSchema(solrClient);

		createCollection(COORDINATES_AFTER_COLLECTOR_SHARDS,COORDINATES_AFTER_COLLECTOR_REPLICAS, "coordinates_after_collector", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_collector").build();
		createSchema(solrClient);

		createCollection(COORDINATES_AFTER_ANALYZER_SHARDS,COORDINATES_AFTER_ANALYZER_REPLICAS, "coordinates_after_analyzer", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_analyzer").build();
		createSchema(solrClient);

		createCollection(SCHEDULES_SHARDS,SCHEDULES_REPLICAS, "schedules", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/schedules").build();
		createSchema(solrClient);



	}
}
