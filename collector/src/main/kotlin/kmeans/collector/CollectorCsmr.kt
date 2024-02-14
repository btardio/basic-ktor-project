package kmeans.collector


import com.rabbitmq.client.*
import java.nio.charset.StandardCharsets
import java.util.*
//import kmeans.solrSupport.SolrEntity
////import com.fasterxml.jackson.databind.ObjectMapper;
////import org.codehaus.jackson.map.ObjectMapper
//import com.fasterxml.jackson.module.kotlin.KotlinModule
//import com.fasterxml.jackson.databind.ObjectMapper

//private fun listenForNotificationRequests(
//    connectionFactory: ConnectionFactory,
//    queueName: String,
//    exchangeName: String
//) {
//    val channel = connectionFactory.newConnection().createChannel()
//
//    channel.basicConsume(
//        queueName,
//        false,
//        CollectorCsmr(channel, exchangeName, connectionFactory)
//    );
//}
//
//class CollectorCsmr(ch: Channel, exchangeName: String, connectionFactory: ConnectionFactory) : Consumer {
//
//    val ch: Channel = ch
//    val exchange: String = exchangeName
//    val connectionFactory = connectionFactory
//
//    override fun handleConsumeOk(consumerTag: String?) {
//
//    }
//
//    override fun handleCancelOk(consumerTag: String?) {
//
//    }
//
//    override fun handleCancel(consumerTag: String?) {
//
//    }
//
//    override fun handleShutdownSignal(consumerTag: String?, sig: ShutdownSignalException?) {
//        sig?.let {
////            throw it
//        }
//    }
//
//    override fun handleRecoverOk(consumerTag: String?) {
//
//    }
//
//    override fun handleDelivery(
//        consumerTag: String?,
//        envelope: Envelope?,
//        properties: AMQP.BasicProperties?,
//        body: ByteArray?
//    ) {
//
//        println("received" + String(body!!, StandardCharsets.UTF_8))
//
//        if (body != null) {
//            var solrEntity: SolrEntity =
//                ObjectMapper().registerModule(KotlinModule()).readValue(String(body, StandardCharsets.UTF_8))
//
//            val cf = connectionFactory.newConnection().createChannel()
//            cf.basicPublish(
//                kmeans.collector.ANALYZER_EXCHANGE,
//                UUID.randomUUID().toString(),
//                MessageProperties.PERSISTENT_BASIC,
//                ObjectMapper().registerModule(KotlinModule()).writeValueAsString(solrEntity).toByteArray()
//            )
//            cf.close()
//        }
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
//        if (envelope != null) {
//            ch.basicAck(envelope.getDeliveryTag(), false)
//        };
//    }
//}
