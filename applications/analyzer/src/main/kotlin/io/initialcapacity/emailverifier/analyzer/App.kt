package io.initialcapacity.emailverifier.analyzer

import com.rabbitmq.client.*
import io.initialcapacity.emailverifier.envsupport.getEnvStr
import io.initialcapacity.serializationsupport.UUIDSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

class App

private val logger = LoggerFactory.getLogger(App::class.java)

// WebServer -> Collector -> Analyzer -> WebServer

val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-analyzer-app")

val ANALYZER_EXCHANGE = getEnvStr("ANALYZER_EXCHANGE", "analyzer-exchange")
val ANALYZER_QUEUE = getEnvStr("ANALYZER_QUEUE", "analyzer-queue-analyzer-app")


fun main() = runBlocking {
    val rabbitUrl = "rabbit";
    //System.getenv("RABBIT_URL")?.let(::URI)
       // ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
//    val databaseUrl = System.getenv("DATABASE_URL")
//        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")

    val connectionFactory = ConnectionFactory();
    connectionFactory.setHost("host.docker.internal"); //.setUri(rabbitUrl);

    val conn = connectionFactory.newConnection();

    val ch = conn.createChannel();

    ch.exchangeDeclare(
        WEBSERVER_EXCHANGE,
        "x-consistent-hash",
        false,
        false,
        null
    )
    ch.queueDeclare(
        WEBSERVER_QUEUE,
        false,
        false,
        false,
        null,

        )
    ch.queueBind(
        WEBSERVER_QUEUE,
        WEBSERVER_EXCHANGE,
        getEnvStr("ROUNDTRIP_REQUEST_CONSISTENT_HASH_ROUTING", "11")
    )




    ch.exchangeDeclare(
        ANALYZER_EXCHANGE,
        "x-consistent-hash",
        false,
        false,
        null
    )
    ch.queueDeclare(
        ANALYZER_QUEUE,
        false,
        false,
        false,
        null,

        )
    ch.queueBind(
        ANALYZER_QUEUE,
        ANALYZER_EXCHANGE,
        getEnvStr("ROUNDTRIP_NOTIFICATION_CONSISTENT_HASH_ROUTING", "83")
    )



    listenAndPublish(
        connectionFactory = connectionFactory,
//        queueName = REGISTRATION_REQUEST_QUEUE,
        queueName = ANALYZER_QUEUE,
        //exchangeName = NOTIFICATION_EXCHANGE,
        exchangeName = WEBSERVER_EXCHANGE,
    )
}

suspend fun listenAndPublish(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String,
) {

    logger.info("listening for notifications " + queueName)
    listenForNotificationRequests(
        connectionFactory,
        queueName,
        exchangeName
    )
}

class Csmr(ch: Channel, exchangeName: String): Consumer {

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

        val email = body?.decodeToString().orEmpty()


        var messageType = 0;

        try {
            Json.decodeFromString<ConfirmationMessageA>(email)
            messageType = 1;
        } catch (e: SerializationException) {

        }

        try {
            Json.decodeFromString<ConfirmationMessage>(email)
            messageType = 3;
        } catch (e: SerializationException) {

        }

        try {
            Json.decodeFromString<LegacyConfirmationMessage>(email)
            messageType = 4;
        } catch (e: SerializationException) {

        }

        if (messageType == 4) {
            // if the message is malformed, try to make it formed - for tests

            ch.basicPublish(
                exchange,
                "42",
                MessageProperties.PERSISTENT_BASIC,
                Json.encodeToString<ConfirmationMessageA>(
                    ConfirmationMessageA(
                        email = Json.decodeFromString<LegacyConfirmationMessage>(email).email,
                        confirmationCode = Json.decodeFromString<LegacyConfirmationMessage>(email).confirmationCode,
                        notificationSent = false,
                        messageSaved = true // this actually hasn't saved
                    )
                ).toByteArray()
            )
            if (envelope != null) {
                ch.basicAck(envelope.deliveryTag, false)
            }
        }

        if (messageType == 1) {

            val deserialized: ConfirmationMessageA = Json
                .decodeFromString<ConfirmationMessageA>(email);

            if (!deserialized.notificationSent && deserialized.messageSaved) {
                runBlocking {
//                    notifier.notify(
//                        deserialized.email,
//                        deserialized.confirmationCode
//                    )
                }
                deserialized.notificationSent = true
            }

            if (!deserialized.notificationSent || !deserialized.messageSaved) {
                ch.basicPublish(
                    exchange,
                    UUID.randomUUID().toString(),
                    MessageProperties.PERSISTENT_BASIC,
                    Json.encodeToString<ConfirmationMessageA>(deserialized).toByteArray()
                )
            }
            if (envelope != null) {
                ch.basicAck(envelope.deliveryTag, false)
            }

        }

        if (messageType == 3) {

            var deserialized: ConfirmationMessage = Json.decodeFromString<ConfirmationMessage>(email);

            ch.basicPublish(
                exchange,
                UUID.randomUUID().toString(),
                MessageProperties.PERSISTENT_BASIC,
                Json.encodeToString<ConfirmationMessage>(deserialized).toByteArray()
            )
            if (envelope != null) {
                ch.basicAck(envelope.deliveryTag, false)
            }
        }
    }
}

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(queueName,
        false,
        Csmr(channel, exchangeName));
}


@Serializable
data class ConfirmationMessage(
    val email: String,
    val notificationSent: Boolean,
    val messageSaved: Boolean
)

@Serializable
data class ConfirmationMessageA(
    val email: String,
    @Serializable(with = UUIDSerializer::class)
    val confirmationCode: UUID,
    var notificationSent: Boolean,
    val messageSaved: Boolean
)

@Serializable
data class LegacyConfirmationMessage(
    val email: String,
    @Serializable(with = UUIDSerializer::class)
    val confirmationCode: UUID,
)