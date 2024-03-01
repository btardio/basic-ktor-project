package kmeans.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;

import io.prometheus.metrics.core.metrics.Counter;
import kmeans.rabbitSupport.LazyInitializedSingleton;
import kmeans.rabbitSupport.RabbitMessageStartRun;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.InputStream;

public class CollectorCsmr implements Consumer {

	public static final String COLLECTOR_EXCHANGE = System.getenv("COLLECTOR_EXCHANGE").isEmpty() ? "webserver-exchange" : System.getenv("COLLECTOR_EXCHANGE");
	public static final String SOLR_CONNECT_IP = System.getenv("SOLR_CONNECT_IP")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			"solr1:8983" : System.getenv("SOLR_CONNECT_IP");
	private final Channel ch;
	private final String exchangeName;
	private final ConnectionFactory connectionFactory;

	private static final Logger log = LoggerFactory.getLogger(CollectorCsmr.class);
	private final Map<String, Counter> counter;

	public CollectorCsmr(Channel ch,
						 String exchangeName,
						 ConnectionFactory connectionFactory,
						 Map<String, Counter> counter) {
		this.ch = ch;
		this.exchangeName = exchangeName;
		this.connectionFactory = connectionFactory;
		this.counter = counter;
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
		counter.get("rabbits_consumed").labelValues("default").inc();
		ObjectMapper objectMapper = new ObjectMapper();
		//log.error(new String(body, StandardCharsets.UTF_8));

		if (body != null) {

			// read the message

			RabbitMessageStartRun rabbitMessageStartRun = objectMapper.readValue(body, RabbitMessageStartRun.class);
			//log.error(rabbitMessageStartRun.toString());
			Channel cfA = LazyInitializedSingleton.getInstance(connectionFactory);
			//log.error(rabbitMessageStartRun.toString());


			// get coordinates entry in solr
			SolrQuery query = new SolrQuery();

			// todo : select only json data, this will contain number of coordinates to make
			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_webserver").build();

			SolrUtility.pingCollection(solrClient, "coordinates_after_webserver");

			QueryResponse response = null;
			//log.error(String.valueOf(response));
			try {
				response = solrClient.query(query);
			} catch (SolrServerException | SolrException e) {
				counter.get("exception_unknown_republish").labelValues("default").inc();
				log.error("coordinates_after_webserver query failure.", e);
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
			if (response.getResults().getNumFound() != 1L) {
				counter.get("not_found_expected_coordinates").labelValues("default").inc();
				log.error(response.getResults().getNumFound() + "Records found on coordinates_after_webserver.");
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

				solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_collector").build();
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

				// goal of this evening : instead of random, read a thumbnail url using java client and convert png to 0 to 1 double rgb format
				try {
					image = ImageIO.read(new URL("http://apache/" + coordinates.getFilename()));
				} catch (Exception e) {
					log.error(e.getMessage(), e);
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
				counter.get("processed_coordinates_after_read").labelValues("default").inc();
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
					counter.get("failed_writing_coordinates_after_read").labelValues("default").inc();
					log.error("Coordinates after collector commit failure.", e);
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
//					try {
//						cfA.close();
//					} catch (TimeoutException ee) {
//
//					}
					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					}
					;
					return;
				} finally {
                    solrClient.close();
                }


				// do some processing with the coordinates

				response.getResults().get(0).get("jsonData");

				// save back to solr


				// signal that we're done and next goes

				cfA.basicPublish(
						exchangeName,
						UUID.randomUUID().toString(),
						MessageProperties.PERSISTENT_BASIC,
						objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
				);
			}

//			try {
//				cfA.close();
//			} catch (TimeoutException e) {
//
//			}

			counter.get("succeeded_writing_coordinates_after_read").labelValues("default").inc();
			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
			}
			;


		}
//
//
////
//
//
////        var numPointsAsInt = Integer.parseInt(numberPoints)
////
////        var coordinateList = SolrEntityCoordinate()
////
////        var scheduledRun = SolrEntityScheduledRun(coordinateList)
////
////        scheduledRun.setStartTime(Timestamp(Date().time))
////        scheduledRun.setNumberPoints(numPointsAsInt)
////        scheduledRun.setStatus("started");
////        coordinateList.setSchedule_uuid(scheduledRun.getSchedule_uuid())
////
////        var sendingMessage: RabbitMessageStartRun = RabbitMessageStartRun(scheduledRun, coordinateList);
//
//
//			if (envelope != null) {
//				ch.basicAck(envelope.getDeliveryTag(), false)
//			};

	}
}
//
//	class CollectorCsmr(ch: Channel, exchangeName: String, connectionFactory: ConnectionFactory) : Consumer {
//
//		val ch: Channel = ch
//		val exchange: String = exchangeName
//		val connectionFactory = connectionFactory
//
//		override fun handleConsumeOk(consumerTag: String?) {
//
//		}
//
//		override fun handleCancelOk(consumerTag: String?) {
//
//		}
//
//		override fun handleCancel(consumerTag: String?) {
//
//		}
//
//		override fun handleShutdownSignal(consumerTag: String?, sig: ShutdownSignalException?) {
//			sig?.let {
////            throw it
//			}
//		}
//
//		override fun handleRecoverOk(consumerTag: String?) {
//
//		}
//
//		override fun handleDelivery(
//				consumerTag: String?,
//				envelope: Envelope?,
//				properties: AMQP.BasicProperties?,
//				body: ByteArray?
//    ) {
//
//			println("received" + String(body!!, StandardCharsets.UTF_8))
//
//			if (body != null) {
//				var solrEntity: SolrEntity =
//						ObjectMapper().registerModule(KotlinModule()).readValue(String(body, StandardCharsets.UTF_8))
//
//				val cf = connectionFactory.newConnection().createChannel()
//				cf.basicPublish(
//						kmeans.collector.ANALYZER_EXCHANGE,
//						UUID.randomUUID().toString(),
//						MessageProperties.PERSISTENT_BASIC,
//						ObjectMapper().registerModule(KotlinModule()).writeValueAsString(solrEntity).toByteArray()
//				)
//				cf.close()
//			}
//
//
////
//
//
////        var numPointsAsInt = Integer.parseInt(numberPoints)
////
////        var coordinateList = SolrEntityCoordinate()
////
////        var scheduledRun = SolrEntityScheduledRun(coordinateList)
////
////        scheduledRun.setStartTime(Timestamp(Date().time))
////        scheduledRun.setNumberPoints(numPointsAsInt)
////        scheduledRun.setStatus("started");
////        coordinateList.setSchedule_uuid(scheduledRun.getSchedule_uuid())
////
////        var sendingMessage: RabbitMessageStartRun = RabbitMessageStartRun(scheduledRun, coordinateList);
//
//
//			if (envelope != null) {
//				ch.basicAck(envelope.getDeliveryTag(), false)
//			};
//		}
//	}


