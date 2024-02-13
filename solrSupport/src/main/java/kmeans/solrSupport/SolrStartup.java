package kmeans.solrSupport;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SolrStartup {

	// note: delete all {'delete': {'query': '*:*'}}

	static public void createCollection(int numShards, int numReplicas) throws SolrServerException, IOException {
		try (SolrClient solr = new CloudSolrClient.Builder().withZkHost("zoo1:2181").build()) {
			List<String> existingCollectionNames = CollectionAdminRequest.listCollections(solr);
			if (!existingCollectionNames.contains("XYZ")) {
				solr.request(CollectionAdminRequest.createCollection("XYZ", numShards, numReplicas));
			}
		}
	}

	static public void createSchema(SolrClient client) throws SolrServerException, IOException {
		try {
			Map<String, Object> fieldAttributes = new HashMap<>();

			fieldAttributes.put("name", "uuid");
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
}
