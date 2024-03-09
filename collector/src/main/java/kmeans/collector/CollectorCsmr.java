package kmeans.collector;

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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import kmeans.solrSupport.Coordinate;

import kmeans.solrSupport.SolrEntity;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import kmeans.solrSupport.SolrUtility;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrException;




import redis.clients.jedis.JedisPooled;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.function.Supplier;

public class CollectorCsmr implements Consumer {
	public static final String COLLECTOR_EXCHANGE = System.getenv("COLLECTOR_EXCHANGE") == null || System.getenv("COLLECTOR_EXCHANGE").isEmpty() ? "webserver-exchange" : System.getenv("COLLECTOR_EXCHANGE");

//	public static final String COLLECTOR_QUEUE = System.getenv("COLLECTOR_QUEUE").isEmpty() ? "webserver-queue" : System.getenv("COLLECTOR_QUEUE");

//	public static final String SOLR_CONNECT_IP = System.getenv("SOLR_CONNECT_IP")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
//			"solr1:8983" : System.getenv("SOLR_CONNECT_IP");
	private final Channel ch;
	private final String exchangeName;
	private final ConnectionFactory connectionFactory;

	private final JedisPooled jedis;

	private final String solrUri;

	private final Supplier<String> routingSupplier;

	public CollectorCsmr(Channel ch,
						 String exchangeName,
						 ConnectionFactory connectionFactory,
						 String solrUri,
						 Supplier<String> routingSupplier,
						 JedisPooled jedis) {

		this.ch = ch;
		this.exchangeName = exchangeName;
		this.connectionFactory = connectionFactory;
		this.jedis = jedis;
		this.solrUri = solrUri;
		this.routingSupplier = routingSupplier;
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

			RabbitMessageStartRun rabbitMessageStartRun = objectMapper.readValue(body, RabbitMessageStartRun.class);

			String timestamp = String.valueOf(new Date().getTime() / 10L);
			jedis.set(timestamp, "{\"source\":\"collector\",\"message\":" +
					"\"Started CollectorCsmr\"" +
					",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
			jedis.expire(timestamp, 900);

			Channel cfA = LazyInitializedSingleton.getInstance(connectionFactory);

			SolrQuery query = new SolrQuery();

			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder("http://" + solrUri + "/solr/coordinates_after_webserver").build();

			SolrUtility.pingCollection(solrClient, "coordinates_after_webserver");

			QueryResponse response = null;

			try {
				response = solrClient.query(query);
			} catch (SolrServerException | SolrException e) {

				timestamp = String.valueOf(new Date().getTime() / 10L);
				jedis.set(timestamp, "coordinates_after_webserver query failure." + e.getMessage());
				jedis.expire(timestamp, 900);
				jedis.set(timestamp, "{\"source\":\"collector\",\"message\":" +
						"\"coordinates_after_webserver query failure " + e.getMessage() + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							COLLECTOR_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					};
				}
				return;
			}

			// if its not fuond it will be
			if (response.getResults().getNumFound() < 1L) {
				//counter.get("not_found_expected_coordinates").labelValues("default").inc();
				//log.error(response.getResults().getNumFound() + "Records found on coordinates_after_webserver.");
				timestamp = String.valueOf(new Date().getTime() / 10L);
				jedis.set(timestamp, "Record not found on coordinates_after_webserver.");
				jedis.set(timestamp, "{\"source\":\"collector\",\"message\":" +
						"\"Record not found on coordinates_after_webserver. " + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
				jedis.expire(timestamp, 900);
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							COLLECTOR_EXCHANGE,
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

				solrClient = new HttpSolrClient.Builder("http://" + solrUri + "/solr/coordinates_after_collector").build();
				SolrUtility.pingCollection(solrClient, "coordinates_after_collector");

				assert(((List)response.getResults().get(0).getFieldValue("jsonData")).size() == 1);

				SolrEntityCoordinateJsonData coordinates =
						objectMapper.readValue(
								((List)response.getResults().get(0).getFieldValue("jsonData")).get(0).toString(),
								SolrEntityCoordinateJsonData.class);



				SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();

				List<Coordinate> listOfNewCoordinates = new ArrayList<>();



				//// start image to xyz

				BufferedImage image = null;

				try {
					image = ImageIO.read(new URL("http://apache/" + coordinates.getFilename()));
				} catch (Exception e) {
					image = new BufferedImage(3,3, BufferedImage.TYPE_INT_RGB);
				}

				int height = image.getHeight();
				int width = image.getWidth();

				for ( int i = 0; i < height; i++ ) {
					for ( int j = 0 ; j < width; j++ ) {
						int javaRGB = image.getRGB(j, i);
						Double javaRed = (double) ((javaRGB >> 16) & 0xFF);
						Double javaGreen = (double) ((javaRGB >> 8) & 0xFF);
						Double javaBlue = (double) ((javaRGB >> 0) & 0xFF);
						listOfNewCoordinates.add(new Coordinate(
								javaRed / 255D,
								javaGreen / 255D,
								javaBlue / 255D));
					}
				}
				// end image to xyz

				coordinateList.setCoordinates(listOfNewCoordinates);
				coordinateList.setFilename(coordinates.getFilename());
				coordinateList.setWidth(width);
				coordinateList.setHeight(height);

				// save coordinate
				//counter.get("processed_coordinates_after_read").labelValues("default").inc();
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
					timestamp = String.valueOf(new Date().getTime() / 10L);

					jedis.set(timestamp, "{\"source\":\"collector\",\"message\":" +
							"\"Coordinates after collector commit failure. " + e.getMessage() + "\"" +
							",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");

					jedis.expire(timestamp, 900);
					//counter.get("failed_writing_coordinates_after_read").labelValues("default").inc();
					//log.error("Coordinates after collector commit failure.", e);
					int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
					if (numTries < 100) {
						rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
						cfA.basicPublish(
								COLLECTOR_EXCHANGE,
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
					;
					return;
				} finally {
                    solrClient.close();
                }

				cfA.basicPublish(
						exchangeName,
						routingSupplier.get(),
						MessageProperties.PERSISTENT_BASIC,
						objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
				);
			}

			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
				timestamp = String.valueOf(new Date().getTime() / 10L);
				jedis.set(timestamp, "{\"source\":\"collector\",\"message\":" +
						"\"Finished CollectorCsmr " + "\"" +
						",\"scheduleid\":" + "\"" + rabbitMessageStartRun.getSolrEntityScheduledRun_UUID() + "\"}");
				jedis.expire(timestamp, 900);
			}
		}
		jedis.set("collector", "OK");
		jedis.expire("collector", 180);
	}
}


