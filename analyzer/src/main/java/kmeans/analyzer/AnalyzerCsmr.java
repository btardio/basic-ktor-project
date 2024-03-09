package kmeans.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import kmeans.rabbitSupport.LazyInitializedSingleton;
import kmeans.rabbitSupport.RabbitMessageStartRun;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import kmeans.scalasupport.IndexedColorFilter;
import org.apache.solr.client.solrj.response.QueryResponse;
import kmeans.solrSupport.SolrEntity;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrException;
import kmeans.solrSupport.SolrUtility;
import redis.clients.jedis.JedisPooled;


public class AnalyzerCsmr  implements Consumer {
	public static final String ANALYZER_EXCHANGE = System.getenv("ANALYZER_EXCHANGE") == null || System.getenv("ANALYZER_EXCHANGE").isEmpty() ?
			"analyzer-exchange" : System.getenv("ANALYZER_EXCHANGE");

	private final Channel ch;
	private final String exchangeName;
	private final ConnectionFactory connectionFactory;

	private final String solrUri;
	private final JedisPooled jedis;

	public Supplier<String> routingSupplier;

	public AnalyzerCsmr(Channel ch,
						String exchangeName,
						ConnectionFactory connectionFactory,
						String solrUri,
						Supplier<String> routingSupplier,
						JedisPooled jedis) {

		this.ch = ch;
		this.exchangeName = exchangeName;
		this.connectionFactory = connectionFactory;
		this.solrUri = solrUri;
		this.routingSupplier = routingSupplier;
		this.jedis = jedis;
	}

	@Override
	public void handleConsumeOk(String consumerTag) {

	}

	@Override
	public void handleCancelOk(String consumerTag) {

	}

	@Override
	public void handleCancel(String consumerTag) throws IOException {

	}

	@Override
	public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {

	}

	@Override
	public void handleRecoverOk(String consumerTag) {

	}

	@Override
	public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();

		if (body != null) {

			// read the message

			RabbitMessageStartRun rabbitMessageStartRun = objectMapper.readValue(body, RabbitMessageStartRun.class);

			String timestamp = String.valueOf(new Date().getTime() / 10L);
			jedis.set(timestamp, "{\"source\":\"analyzer\",\"message\":" +
					"\"Started AnalyzerCsmr " + "\"" +
					",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

			//log.error(rabbitMessageStartRun.toString());
			Channel cfA = LazyInitializedSingleton.getInstance(connectionFactory);

			// get coordinates entry in solr
			SolrQuery query = new SolrQuery();

			// todo : select only json data, this will contain number of coordinates to make
			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder(
					"http://" + solrUri + "/solr/coordinates_after_collector").build();

            SolrUtility.pingCollection(solrClient, "coordinates_after_collector");

			QueryResponse response = null;

			try {
				response = solrClient.query(query);
			} catch (SolrServerException | SolrException e) {
				//log.error("Exception querying coordinates_after_collector.", e);
				timestamp = String.valueOf(new Date().getTime() / 10L);

				jedis.set(timestamp, "{\"source\":\"analyzer\",\"message\":" +
						"\"Exception querying coordinates_after_collector " + e.getMessage() + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

				jedis.expire(timestamp, 900);
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							ANALYZER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
				}
				if (envelope != null) {
					this.ch.basicAck(envelope.getDeliveryTag(), false);
				};
				return;
			} finally {
                solrClient.close();
            }

			timestamp = String.valueOf(new Date().getTime() / 10L);

			// if its not fuond it will be
			if (response.getResults().getNumFound() < 1L) {
				timestamp = String.valueOf(new Date().getTime() / 10L);

				jedis.set(timestamp, "{\"source\":\"analyzer\",\"message\":" +
						"\"Records not found on coordinates_after_collector " + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

				jedis.expire(timestamp, 900);
				//counter.get("not_found_expected_coordinates").labelValues("default").inc();
				///log.error(response.getResults().getNumFound() + "Records found on coordinates_after_collector.");
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							ANALYZER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
                    if (envelope != null) {
                        this.ch.basicAck(envelope.getDeliveryTag(), false);
                    };
				}
                return;
			} else {

				solrClient = new HttpSolrClient.Builder("http://" + solrUri + "/solr/coordinates_after_analyzer").build();
                SolrUtility.pingCollection(solrClient, "coordinates_after_analyzer");

				assert(((List)response.getResults().get(0).getFieldValue("jsonData")).size() == 1);

				SolrEntityCoordinateJsonData coordinates =
						objectMapper.readValue(
								((List)response.getResults().get(0).getFieldValue("jsonData")).get(0).toString(),
								SolrEntityCoordinateJsonData.class);

				IndexedColorFilter icf = new IndexedColorFilter(coordinates.getCoordinates());

				SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();

				coordinateList.setCoordinates(icf.getResult());
				coordinateList.setFilename(coordinates.getFilename());
				coordinateList.setWidth(coordinates.getWidth());
				coordinateList.setHeight(coordinates.getHeight());
				//counter.get("processed_coordinates_after_read").labelValues("default").inc();
				// save coordinate

				try {
					solrClient.addBean(
							new SolrEntity(
									rabbitMessageStartRun.getSolrEntityScheduledRun_UUID(),
									rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID(),
									new ObjectMapper().writeValueAsString(coordinateList)
							)
					);
					solrClient.commit();
				} catch (SolrServerException | SolrException e) {
					//counter.get("failed_writing_coordinates_after_read").labelValues("default").inc();
					//log.error("Coordinates after analyzer commit failure.", e);
					// exceptions put back on the exchange
					timestamp = String.valueOf(new Date().getTime() / 10L);
					jedis.set(timestamp, "{\"source\":\"analyzer\",\"message\":" +
							"\"Coordinates after analyzer commit failure. " + e.getMessage() + "\"" +
							",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
					jedis.expire(timestamp, 900);
					int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
					if (numTries < 100) {
						rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
						cfA.basicPublish(
								ANALYZER_EXCHANGE,
								UUID.randomUUID().toString(),
								MessageProperties.PERSISTENT_BASIC,
								objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
						);
                        if (envelope != null) {
                            this.ch.basicAck(envelope.getDeliveryTag(), false);
                        };
                        return;
					}
                    if (envelope != null) {
                        this.ch.basicAck(envelope.getDeliveryTag(), false);
                    }
					return;
				} finally {
                    solrClient.close();
                }

				// publish to webserver exchange
				LazyInitializedSingleton.getInstance(connectionFactory).basicPublish(
						exchangeName,
						routingSupplier.get(),
						MessageProperties.PERSISTENT_BASIC,
						objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
				);
			}

// this can go wrong and cause a lot of waste
//
// start delete coordinates from collector collection, no longer needed
//
//			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
//			 solrClient = new HttpSolrClient.Builder(
//					"http://" + solrUri + "/solr/coordinates_after_collector").build();
//
//			SolrUtility.pingCollection(solrClient, "coordinates_after_collector");
//
//			List<String> idsDeleting = response.getResults().stream().map(item -> item.get("id").toString()).toList();
//
//			try {
//				solrClient.deleteById("coordinates_after_collector", idsDeleting);
//			} catch (SolrServerException | SolrException e) {
//				//counter.get("exception_unknown_republish").labelValues("default").inc();
//				//log.error("Exception deleting from coordinates_after_collector.", e);
//				timestamp = String.valueOf(new Date().getTime() / 10L);
//				jedis.set(timestamp, "Exception deleting from coordinates_after_collector." + e.getMessage());
//				jedis.expire(timestamp, 900);
//				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
//				if (numTries < 100) {
//					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
//					cfA.basicPublish(
//							ANALYZER_EXCHANGE,
//							UUID.randomUUID().toString(),
//							MessageProperties.PERSISTENT_BASIC,
//							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
//					);
//				}
//				if (envelope != null) {
//					this.ch.basicAck(envelope.getDeliveryTag(), false);
//				};
//				return;
//			} finally {
//				solrClient.close();
//			}

			// end delete coordinates from collector collection, no longer needed

			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
				timestamp = String.valueOf(new Date().getTime() / 10L);

				jedis.set(timestamp, "{\"source\":\"analyzer\",\"message\":" +
						"\"Finished AnalyzerCsmr " + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

				jedis.expire(timestamp, 900);
			}

			jedis.set("analyzer", "OK");
			jedis.expire("analyzer", 180);

		}
	}
}