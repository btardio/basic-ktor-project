package kmeans.analyzer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;

import kmeans.rabbitSupport.RabbitMessageStartRun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import kmeans.scalasupport.IndexedColorFilter;
import kmeans.solrSupport.SolrEntity;

import kmeans.solrSupport.SolrEntityCoordinateJsonData;
//import kmeans.scalasupport.IndexedColorFilter;
//import kmeans.analyzer.IndexedColorFilter;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AnalyzerCsmr  implements Consumer {
	public static final String ANALYZER_EXCHANGE = System.getenv("ANALYZER_EXCHANGE").isEmpty() ?
			"analyzer-exchange" : System.getenv("ANALYZER_EXCHANGE");
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
		log.error(new String(body, StandardCharsets.UTF_8));

		if (body != null) {

			// read the message

			RabbitMessageStartRun rabbitMessageStartRun = objectMapper.readValue(body, RabbitMessageStartRun.class);
			log.error(rabbitMessageStartRun.toString());
			Channel cfA = null;
			try {
				cfA = this.connectionFactory.newConnection().createChannel();
			} catch (TimeoutException e) {
				throw new RuntimeException(e);
			}
			log.error(rabbitMessageStartRun.toString());


			// get coordinates entry in solr
			SolrQuery query = new SolrQuery();

			// todo : select only json data, this will contain number of coordinates to make
			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/coordinates_after_collector").build();
			QueryResponse response = null;
			//log.error(String.valueOf(response));
			try {
				response = solrClient.query(query);
			} catch (SolrServerException e) {
				log.error("Error, unexpected.");
			}

			// if its not fuond it will be
			if (response.getResults().getNumFound() != 1L) {
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
			} else {


				assert(((List)response.getResults().get(0).getFieldValue("jsonData")).size() == 1);

				SolrEntityCoordinateJsonData coordinates =
						objectMapper.readValue(
								((List)response.getResults().get(0).getFieldValue("jsonData")).get(0).toString(),
								SolrEntityCoordinateJsonData.class);

				IndexedColorFilter icf = new IndexedColorFilter(coordinates.getCoordinates());

				SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();

				coordinateList.setCoordinates(icf.getResult());

				// save coordinate
				solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/coordinates_after_analyzer").build();
				try {
					solrClient.addBean(
							new SolrEntity(
									rabbitMessageStartRun.getSolrEntityScheduledRun_UUID(),
									rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID(),
									new ObjectMapper().writeValueAsString(coordinateList)
							)
					);
					solrClient.commit();
				} catch (SolrServerException e) {

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
					}
					try {
						cfA.close();
					} catch (TimeoutException ee) {

					}
					if (envelope != null) {
						this.ch.basicAck(envelope.getDeliveryTag(), false);
					};
					return;
				}



				// publish to webserver exchange
				cfA.basicPublish(
						exchangeName,
						UUID.randomUUID().toString(),
						MessageProperties.PERSISTENT_BASIC,
						objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
				);
			}

			try {
				cfA.close();
			} catch (TimeoutException e) {

			}


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
//				solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/coordinates_after_collector").build();
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