package kmeans.webserver;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import kmeans.rabbitSupport.LazyInitializedSingleton;
import kmeans.rabbitSupport.RabbitMessageStartRun;
import kmeans.solrSupport.SolrEntity;
import kmeans.solrSupport.SolrEntityScheduledRunJsonData;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import kmeans.solrSupport.SolrUtility;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class WebserverCsmr implements Consumer {
	public static final String WEBSERVER_EXCHANGE = System.getenv("WEBSERVER_EXCHANGE") == null || System.getenv("WEBSERVER_EXCHANGE").isEmpty() ?
			"webserver-exchange" : System.getenv("WEBSERVER_EXCHANGE");

	private final Channel ch;

	private final ConnectionFactory connectionFactory;

	private final JedisPooled jedis;

	private final String solrUri;

	public WebserverCsmr(Channel ch,
						 ConnectionFactory connectionFactory,
						 String solrUri,
						 JedisPooled jedis) {



		this.ch = ch;
		this.connectionFactory = connectionFactory;
		this.jedis = jedis;
		this.solrUri = solrUri;
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
		//counter.get("rabbits_consumed").labelValues("default").inc();
		ObjectMapper objectMapper = new ObjectMapper();
		//log.error(new String(body, StandardCharsets.UTF_8));

		if (body != null) {

			// read the message

			RabbitMessageStartRun rabbitMessageStartRun = objectMapper.readValue(body, RabbitMessageStartRun.class);
			String timestamp = String.valueOf(new Date().getTime() / 10L);
			jedis.set(timestamp, "{\"source\":\"webserver\",\"message\":" +
					"\"Started WebserverCsmr\"" +
					",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
			jedis.expire(timestamp, 900);

			//log.error(rabbitMessageStartRun.toString());
			Channel cfA = LazyInitializedSingleton.getInstance(connectionFactory);

			SolrQuery query = new SolrQuery();

			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder("http://" + solrUri + "/solr/coordinates_after_analyzer").build();

			SolrUtility.pingCollection(solrClient, "coordinates_after_analyzer");

			QueryResponse response = null;
			//log.error(String.valueOf(response));
			try {
				response = solrClient.query(query);
			} catch (SolrServerException | SolrException e) {
				//counter.get("exception_unknown_republish").labelValues("default").inc();
				//log.error("Exception querying coordinates_after_analyzer.", e);
				timestamp = String.valueOf(new Date().getTime() / 10L);
				jedis.set(timestamp, "{\"source\":\"webserver\",\"message\":" +
						"\"Exception querying coordinates_after_analyzer." + e.getMessage() + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
				jedis.expire(timestamp, 900);
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							WEBSERVER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					};
				}
			} finally {
				solrClient.close();
			}

			timestamp = String.valueOf(new Date().getTime() / 10L);

			jedis.expire(timestamp, 900);

			// if its not fuond it will be
			if (response.getResults().getNumFound() < 1L) {
				//counter.get("not_found_expected_coordinates").labelValues("default").inc();
				//log.error(response.getResults().getNumFound() + "Records found on coordinates_after_analyzer.");
				timestamp = String.valueOf(new Date().getTime() / 10L);

				jedis.set(timestamp, "{\"source\":\"webserver\",\"message\":" +
						"\"Records not found on coordinates_after_analyzer." + "" + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

				jedis.expire(timestamp, 900);
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							WEBSERVER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					};
				}
			} else {

				solrClient = new HttpSolrClient.Builder("http://" + solrUri + "/solr/schedules").build();
				SolrUtility.pingCollection(solrClient, "schedules");

				assert(((List)response.getResults().get(0).getFieldValue("jsonData")).size() == 1);

				SolrEntityCoordinateJsonData coordinates =
						objectMapper.readValue(
								((List)response.getResults().get(0).getFieldValue("jsonData")).get(0).toString(),
								SolrEntityCoordinateJsonData.class);

				// write in the schedule table that status is "done"

				SolrEntityScheduledRunJsonData scheduledRunJson = new SolrEntityScheduledRunJsonData();
				scheduledRunJson.setStatus("finished");
				scheduledRunJson.setHeight(coordinates.getHeight());
				scheduledRunJson.setWidth(coordinates.getWidth());
				scheduledRunJson.setNumberPoints(coordinates.getNumPoints());
				scheduledRunJson.setFilename(coordinates.getFilename());


				// save schedule run, create collection
				//counter.get("processed_coordinates_after_read").labelValues("default").inc();
				try {
					solrClient.addBean(
							new SolrEntity(
									rabbitMessageStartRun.getSolrEntityScheduledRun_UUID(),
									rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID(),
									new ObjectMapper().writeValueAsString(scheduledRunJson)
							)
					);
					solrClient.commit();
				} catch (SolrServerException | SolrException e) {
					//counter.get("failed_writing_coordinates_after_read").labelValues("default").inc();
//					counter.get("get_all_schedules_fail").labelValues("default").inc();
					timestamp = String.valueOf(new Date().getTime() / 10L);

					jedis.set(timestamp, "{\"source\":\"webserver\",\"message\":" +
							"\"failed_writing_schedule_complete_after_read." + e.getMessage() + "\"" +
							",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

					jedis.expire(timestamp, 900);
					cfA.basicPublish(
							WEBSERVER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);

					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					};

				} finally {

					solrClient.close();
				}
			}

			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
				timestamp = String.valueOf(new Date().getTime() / 10L);
				jedis.set(timestamp, "{\"source\":\"webserver\",\"message\":" +
						"\"Finished WebserverCsmr\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
				jedis.expire(timestamp, 900);
			};
			jedis.set("webserver", "OK");
			jedis.expire("webserver", 180);

		}
	}
}