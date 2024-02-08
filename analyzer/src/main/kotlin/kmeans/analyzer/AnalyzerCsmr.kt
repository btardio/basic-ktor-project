package kmeans.analyzer

import com.rabbitmq.client.*


private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(
        queueName,
        false,
        AnalyzerCsmr(channel, exchangeName)
    );
}

class AnalyzerCsmr(ch: Channel, exchangeName: String) : Consumer {

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

    }
}
