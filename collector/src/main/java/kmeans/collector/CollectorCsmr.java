package kmeans.collector;

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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import kmeans.solrSupport.Coordinate;
import kmeans.solrSupport.SolrEntity;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class CollectorCsmr implements Consumer {

	public static final String COLLECTOR_EXCHANGE = System.getenv("COLLECTOR_EXCHANGE").isEmpty() ? "webserver-exchange" : System.getenv("COLLECTOR_EXCHANGE");
	private final Channel ch;
	private final String exchangeName;
	private final ConnectionFactory connectionFactory;

	private static final Logger log = LoggerFactory.getLogger(CollectorCsmr.class);

	public CollectorCsmr(Channel ch, String exchangeName, ConnectionFactory connectionFactory) {
		this.ch = ch;
		this.exchangeName = exchangeName;
		this.connectionFactory = connectionFactory;
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
			query.set("q", "coordinate_uuid:" + rabbitMessageStartRun.getSolrEntityCoordinatesList_UUID());
			SolrClient solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/coordinates").build();
			QueryResponse response = null;
			//log.error(String.valueOf(response));
			try {
				response = solrClient.query(query);
			} catch (SolrServerException e) {
				log.error("Error, unexpected.");
			}

			// if its not fuond it will be
			if ( response.getResults().getNumFound() != 1L ) {
				int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
				if ( numTries < 100 ) {
					rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
					cfA.basicPublish(
							COLLECTOR_EXCHANGE,
							UUID.randomUUID().toString(),
							MessageProperties.PERSISTENT_BASIC,
							objectMapper.writeValueAsString(rabbitMessageStartRun).getBytes()
					);
				}
				} else {

				log.error(String.valueOf(response.getResults()));

				SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();
				Random r = new Random();
//				double randomValue 100 * r.nextDouble()
				coordinateList.setCoordinates(List.of(
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0),
						new Coordinate(100 * r.nextDouble(), 100 * r.nextDouble(), 3.0)
				));

				// save coordinate
				solrClient = new HttpSolrClient.Builder("http://solr1:8983/solr/coordinates").build();
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
					int numTries = rabbitMessageStartRun.getNumTriesFindingSolrRecord();
					if ( numTries < 100 ) {
						rabbitMessageStartRun.setNumTriesFindingSolrRecord(numTries + 1);
						cfA.basicPublish(
								COLLECTOR_EXCHANGE,
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

			try {
				cfA.close();
			} catch (TimeoutException e) {

			}



			if (envelope != null) {
				this.ch.basicAck(envelope.getDeliveryTag(), false);
			};





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


