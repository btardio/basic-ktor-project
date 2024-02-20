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
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SolrStartup {
	public static final String SOLR_CONNECT_IP = System.getenv("SOLR_CONNECT_IP")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			"solr1:8983" : System.getenv("SOLR_CONNECT_IP");
	// note: delete all {'delete': {'query': '*:*'}}

	static public void createCollection(int numShards, int numReplicas, String collectionName, String zooHost) throws SolrServerException, IOException {
		try (SolrClient solr = new CloudSolrClient.Builder().withZkHost(zooHost).build()) {
			List<String> existingCollectionNames = CollectionAdminRequest.listCollections(solr);
			if (!existingCollectionNames.contains(collectionName)) {
				solr.request(CollectionAdminRequest.createCollection(collectionName, numShards, numReplicas));
			}
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
		} catch( BaseHttpSolrClient.RemoteExecutionException e ) {
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

		createCollection(3,1, "coordinates_after_webserver", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_webserver").build();
		createSchema(solrClient);

		createCollection(3,1, "coordinates_after_collector", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_collector").build();
		createSchema(solrClient);

		createCollection(3,1, "coordinates_after_analyzer", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_analyzer").build();
		createSchema(solrClient);

		createCollection(3,1, "schedules", zooHost);
		solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/schedules").build();
		createSchema(solrClient);



	}
}
