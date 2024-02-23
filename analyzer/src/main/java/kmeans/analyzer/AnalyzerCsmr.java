package kmeans.analyzer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import kmeans.rabbitSupport.LazyInitializedSingleton;
import kmeans.rabbitSupport.RabbitMessageStartRun;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import kmeans.scalasupport.IndexedColorFilter;
import org.apache.solr.client.solrj.response.QueryResponse;
import kmeans.solrSupport.SolrEntity;

import kmeans.solrSupport.SolrEntityCoordinateJsonData;
//import kmeans.scalasupport.IndexedColorFilter;
//import kmeans.analyzer.IndexedColorFilter;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kmeans.solrSupport.SolrUtility;
import static java.lang.System.exit;


public class AnalyzerCsmr  implements Consumer {
	public static final String ANALYZER_EXCHANGE = System.getenv("ANALYZER_EXCHANGE").isEmpty() ?
			"analyzer-exchange" : System.getenv("ANALYZER_EXCHANGE");

	// TODO change the connect ip to round robin
	public static final String SOLR_CONNECT_IP = System.getenv("SOLR_CONNECT_IP")==null || System.getenv("SOLR_CONNECT_IP").isEmpty() ?
			"solr1:8983" : System.getenv("SOLR_CONNECT_IP");

	private final Channel ch;
	private final String exchangeName;
	private final ConnectionFactory connectionFactory;

	private static final Logger log = LoggerFactory.getLogger(AnalyzerCsmr.class);

	public AnalyzerCsmr(Channel ch, String exchangeName, ConnectionFactory connectionFactory) {
		this.ch = ch;
		this.exchangeName = exchangeName;
		this.connectionFactory = connectionFactory;
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

		ObjectMapper objectMapper = new ObjectMapper();
		//log.error(new String(body, StandardCharsets.UTF_8));

		if (body != null) {

			// read the message

			RabbitMessageStartRun rabbitMessageStartRun = objectMapper.readValue(body, RabbitMessageStartRun.class);
			//log.error(rabbitMessageStartRun.toString());
			Channel cfA = LazyInitializedSingleton.getInstance(connectionFactory);

			// get coordinates entry in solr
			SolrQuery query = new SolrQuery();

			// todo : select only json data, this will contain number of coordinates to make
			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder(
					"http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_collector").build();

            SolrUtility.pingCollection(solrClient, "coordinates_after_collector");

			QueryResponse response = null;

			try {
				response = solrClient.query(query);
			} catch (SolrServerException | SolrException e) {
				log.error("Exception querying coordinates_after_collector.", e);
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
				return;
			} finally {
                solrClient.close();
            }

			// if its not fuond it will be
			if (response.getResults().getNumFound() < 1L) {
				log.error(response.getResults().getNumFound() + "Records found on coordinates_after_collector.");
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

				solrClient = new HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_analyzer").build();
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
					log.error("Coordinates after analyzer commit failure.", e);
					// exceptions put back on the exchange

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
//					try {
//						cfA.close();
//					} catch (TimeoutException ee) {
//
//					}
//					if (envelope != null) {
//						this.ch.basicAck(envelope.getDeliveryTag(), false);
//					};
                    if (envelope != null) {
                        this.ch.basicAck(envelope.getDeliveryTag(), false);
                    }
					return;
				} finally {
                    solrClient.close();
                }



				// publish to webserver exchange
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


			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
			}
			;







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