package kmeans.collector;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import org.codehaus.jackson.map.ObjectMapper;
import kmeans.solrSupport.SolrEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class CollectorCsmr implements Consumer {

	private final Channel ch;
	private final String exchangeName;
	private final ConnectionFactory connectionFactory;

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

//			println("received" + String(body, StandardCharsets.UTF_8))
//
		if (body != null) {
			SolrEntity solrEntity = objectMapper.readValue(body, SolrEntity.class);

			Channel cf = null;
			try {
				cf = this.connectionFactory.newConnection().createChannel();
			} catch (TimeoutException e) {
				throw new RuntimeException(e);
			}
			cf.basicPublish(
					exchangeName,
					UUID.randomUUID().toString(),
					MessageProperties.PERSISTENT_BASIC,
					objectMapper.writeValueAsString((Object) solrEntity).getBytes()
			);
			try {
				cf.close();
			} catch (TimeoutException e) {
				throw new RuntimeException(e);
			}
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


