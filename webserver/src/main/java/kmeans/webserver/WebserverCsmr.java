package kmeans.webserver;


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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;



public class WebserverCsmr implements Consumer {
	public static final String WEBSERVER_EXCHANGE = System.getenv("WEBSERVER_EXCHANGE").isEmpty() ?
			"webserver-exchange" : System.getenv("WEBSERVER_EXCHANGE");

	public static final String SOLR_CONNECT_IP = System.getenv("SOLR_CONNECT_IP")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			"solr1:8983" : System.getenv("SOLR_CONNECT_IP");

	private final Channel ch;

	private final ConnectionFactory connectionFactory;

	private static final Logger log = LoggerFactory.getLogger(WebserverCsmr.class);
	private final Map<String, Counter> counter;

	public WebserverCsmr(Channel ch,
						 ConnectionFactory connectionFactory,
						 Map<String, Counter> counter) {
		this.ch = ch;
		this.connectionFactory = connectionFactory;
		this.counter = counter;
	}

//	java.util.List<Coordinate> convert(Object seq) {
//		return null;
//	}

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
		counter.get("rabbits_consumed").inc();
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
			SolrClient solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_analyzer").build();

			SolrUtility.pingCollection(solrClient, "coordinates_after_analyzer");

			QueryResponse response = null;
			//log.error(String.valueOf(response));
			try {
				response = solrClient.query(query);
			} catch (SolrServerException | SolrException e) {
				counter.get("exception_unknown_republish").inc();
				log.error("Exception querying coordinates_after_analyzer.", e);
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if (numTries < 100) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							WEBSERVER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
				}
			} finally {
				solrClient.close();
			}

			// if its not fuond it will be
			if (response.getResults().getNumFound() != 1L) {
				counter.get("not_found_expected_coordinates").inc();
				log.error(response.getResults().getNumFound() + "Records found on coordinates_after_analyzer.");
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

				solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/schedules").build();
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
				counter.get("processed_coordinates_after_read").inc();
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
					counter.get("failed_writing_coordinates_after_read").inc();
//					counter.get("get_all_schedules_fail").inc();
					cfA.basicPublish(
							WEBSERVER_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);


//					try {
//						cfA.close();
//					} catch (TimeoutException ee) {
//
//					}
					counter.get("succeeded_writing_coordinates_after_read").inc();
					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					};

				} finally {
					solrClient.close();
				}


				// publish to webserver exchange

			}
//
//			try {
//				cfA.close();
//			} catch (TimeoutException e) {
//
//			}


			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
			};







				//Path f = new kmeans.analyzer.Path();
				//log.error(f.process().apply("som input")); //prints processed

						//new IndexedColorFilter(coordinateList.getCoordinates()).getResult());

//				Random r = new Random();
//
//				List<Coordinate> listOfNewCoordinates = new ArrayList<>();
//
//				for ( int i = 0; i < coordinates.getNumPoints(); i ++ ){
//					listOfNewCoordinates.add(new Coordinate(
//							100 * r.nextDouble(),
//							100 * r.nextDouble(),
//							100 * r.nextDouble()));
//				}
//
////				double randomValue 100 * r.nextDouble()
//				coordinateList.setCoordinates(listOfNewCoordinates);
//
//				// save coordinate
//				solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_collector").build();
//				try {
//					solrClient.addBean(
//							new SolrEntity(
//									rabbitMessageStartRun.getSolrEntityScheduledRun_UUID(),
//									rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID(),
//									new ObjectMapper().writeValueAsString(coordinateList)
//							)
//					);
//					solrClient.commit();
//				} catch (SolrServerException e) {
//					int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
//					if (numTries < 100) {
//						rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
//						cfA.basicPublish(
//								COLLECTOR_EXCHANGE,
//								UUID.randomUUID().toString(),
//								MessageProperties.PERSISTENT_BASIC,
//								objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
//						);
//					}
//					try {
//						cfA.close();
//					} catch (TimeoutException ee) {
//
//					}
//					if (envelope != null) {
//						this.ch.basicAck(envelope.getDeliveryTag(), false);
//					}
//					;
//					return;
//				}
//
//
//				// do some processing with the coordinates
//
//				response.getResults().get(0).get("jsonData");
//
//				// save back to solr
//
//
//				// signal that we're done and next goes
//
//				cfA.basicPublish(
//						exchangeName,
//						UUID.randomUUID().toString(),
//						MessageProperties.PERSISTENT_BASIC,
//						objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
//				);
//			}
//
//			try {
//				cfA.close();
//			} catch (TimeoutException e) {
//
//			}
//
//
//			if (envelope != null) {
//				this.ch.basicAck(envelope.getDeliveryTag(), false);
//			}
//			;


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