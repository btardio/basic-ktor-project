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

	// note: delete all {'delete': {'query': '*:*'}}

	static public void createCollection(int numShards, int numReplicas, String collectionName) throws SolrServerException, IOException {
		try (SolrClient solr = new CloudSolrClient.Builder().withZkHost("zoo1:2181").build()) {
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
			fieldAttributes.put("type", "string");
			fieldAttributes.put("stored", "true");

			SchemaRequest.AddField addFieldUpdateSchemaRequestJsonData = new SchemaRequest.AddField(fieldAttributes);
			addFieldUpdateSchemaRequestJsonData.process(client);
		} catch( BaseHttpSolrClient.RemoteExecutionException e ) {
			LoggerFactory.getLogger(SolrStartup.class).info("Failed creating schema, continuing." + "\n" + e.getMessage());
		}

	}

	public static void solrInitialize() throws Exception {

		HttpSolrClient solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/sanesystem").build();

		// sane checks stay

		// we have a collection
		createCollection(1,1, "sanesystem");

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

		createCollection(3,1, "coordinates");
		solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/coordinates").build();
		createSchema(solrClient);

		createCollection(3,1, "schedules");
		solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/schedules").build();
		createSchema(solrClient);
	}
}
