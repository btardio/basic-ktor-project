package kmeans.analyzer

import com.rabbitmq.client.*
import java.sql.Timestamp
import java.util.*


private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(
        queueName,
        false,
        AnalyzerCsmr(channel, exchangeName, connectionFactory)
    );
}

class AnalyzerCsmr(ch: Channel, exchangeName: String, connectionFactory: ConnectionFactory) : Consumer {

    val ch: Channel = ch
    val exchange: String = exchangeName

    override fun handleConsumeOk(consumerTag: String?) {

    }

    override fun handleCancelOk(consumerTag: String?) {

    }

    override fun handleCancel(consumerTag: String?) {

    }

    override fun handleShutdownSignal(consumerTag: String?, sig: ShutdownSignalException?) {
        sig?.let {
//            throw it
        }
    }

    override fun handleRecoverOk(consumerTag: String?) {

    }

    override fun handleDelivery(
        consumerTag: String?,
        envelope: Envelope?,
        properties: AMQP.BasicProperties?,
        body: ByteArray?
    ) {
        println("received" + body.toString())

//
//
//        val cf = connectionFactory.newConnection().createChannel()
//
//        var numPointsAsInt = Integer.parseInt(numberPoints)
//
//        var coordinateList = SolrEntityCoordinate()
//
//        var scheduledRun = SolrEntityScheduledRun(coordinateList)
//
//        scheduledRun.setStartTime(Timestamp(Date().time))
//        scheduledRun.setNumberPoints(numPointsAsInt)
//        scheduledRun.setStatus("started");
//        coordinateList.setSchedule_uuid(scheduledRun.getSchedule_uuid())
//
//        var sendingMessage: RabbitMessageStartRun = RabbitMessageStartRun(scheduledRun, coordinateList);
//
//        cf.basicPublish(
//            COLLECTOR_EXCHANGE,
//            UUID.randomUUID().toString(),
//            MessageProperties.PERSISTENT_BASIC,
//            ObjectMapper().writeValueAsString(sendingMessage).toByteArray()
//        )
//        cf.close()

        if (envelope != null) {
            ch.basicAck(envelope.getDeliveryTag(), false)
        };
    }
}
