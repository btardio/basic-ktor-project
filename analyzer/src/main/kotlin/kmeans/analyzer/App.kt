package kmeans.analyzer

import com.rabbitmq.client.*
import kmeans.`env-support`.getEnvInt
import kmeans.`env-support`.getEnvStr

import org.slf4j.LoggerFactory
import kotlinx.coroutines.runBlocking

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "collector-exchange")
val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "collector-queue-webserver-app")

val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-webserver-app")

val EMBEDDED_NETTY_PORT = getEnvInt("DATA_ANALYZER_PORT_MAP", 8887)

private val logger = LoggerFactory.getLogger("kmeans.analyzer.App")

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


fun main() {


    runBlocking {
        val rabbitUrl = "rabbit";
        //    val rabbitUrl = System.getenv("RABBIT_URL")?.let(::URI)
//        ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
//    val databaseUrl = System.getenv("DATABASE_URL")
//        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")

        val connectionFactory = ConnectionFactory();
        connectionFactory.setHost("host.docker.internal");

        val conn = connectionFactory.newConnection();

        val ch = conn.createChannel();

        ch.exchangeDeclare(
            COLLECTOR_EXCHANGE,
            "x-consistent-hash",
            false,
            false,
            null
        )
        ch.queueDeclare(
            COLLECTOR_QUEUE,
            false,
            false,
            false,
            null,

            )
        ch.queueBind(
            COLLECTOR_QUEUE,
            COLLECTOR_EXCHANGE,
            getEnvStr("ROUNDTRIP_REQUEST_CONSISTENT_HASH_ROUTING", "11")
        )




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
            getEnvStr("ROUNDTRIP_NOTIFICATION_CONSISTENT_HASH_ROUTING", "83")
        )



        listenAndPublish(
            connectionFactory = connectionFactory,
//        queueName = REGISTRATION_REQUEST_QUEUE,
            queueName = WEBSERVER_QUEUE,
            //exchangeName = NOTIFICATION_EXCHANGE,
            exchangeName = COLLECTOR_EXCHANGE,
        )



        embeddedServer(Netty, port = EMBEDDED_NETTY_PORT) {
            routing {
                get("/") {
                    call.respondText("response OK")
                }
            }
        }.start(wait = true)

    }

    class Csmr(ch: Channel, exchangeName: String) : Consumer {

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


//
//@Serializable
//data class ConfirmationMessage(
//    val email: String,
//    val notificationSent: Boolean,
//    val messageSaved: Boolean
//)
//
//@Serializable
//data class ConfirmationMessageA(
//    val email: String,
//    @Serializable(with = UUIDSerializer::class)
//    val confirmationCode: UUID,
//    var notificationSent: Boolean,
//    val messageSaved: Boolean
//)
//
//@Serializable
//data class LegacyConfirmationMessage(
//    val email: String,
//    @Serializable(with = UUIDSerializer::class)
//    val confirmationCode: UUID,
//)


}


//
//import com.rabbitmq.client.*
//import kmeans.`env-support`.getEnvStr
//
//import org.slf4j.LoggerFactory
//import kotlinx.coroutines.runBlocking
//
//class App
//
//private val logger = LoggerFactory.getLogger(App::class.java)
//
//// WebServer -> Collector -> Analyzer -> WebServer
//
//val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "collector-exchange")
//val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "collector-queue-webserver-app")
//
//val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")
//val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-webserver-app")
//
//
//fun main() = {}
//
//    //runBlocking {
////    val rabbitUrl = "rabbit";
////    //    val rabbitUrl = System.getenv("RABBIT_URL")?.let(::URI)
//////        ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
//////    val databaseUrl = System.getenv("DATABASE_URL")
//////        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")
////
////    val connectionFactory = ConnectionFactory();
////    connectionFactory.setHost("host.docker.internal");
////
////    val conn = connectionFactory.newConnection();
////
////    val ch = conn.createChannel();
////
////    ch.exchangeDeclare(
////        COLLECTOR_EXCHANGE,
////        "x-consistent-hash",
////        false,
////        false,
////        null
////    )
////    ch.queueDeclare(
////        COLLECTOR_QUEUE,
////        false,
////        false,
////        false,
////        null,
////
////        )
////    ch.queueBind(
////        COLLECTOR_QUEUE,
////        COLLECTOR_EXCHANGE,
////        getEnvStr("ROUNDTRIP_REQUEST_CONSISTENT_HASH_ROUTING", "11")
////    )
////
////
////
////
////    ch.exchangeDeclare(
////        WEBSERVER_EXCHANGE,
////        "x-consistent-hash",
////        false,
////        false,
////        null
////    )
////    ch.queueDeclare(
////        WEBSERVER_QUEUE,
////        false,
////        false,
////        false,
////        null,
////
////        )
////    ch.queueBind(
////        WEBSERVER_QUEUE,
////        WEBSERVER_EXCHANGE,
////        getEnvStr("ROUNDTRIP_NOTIFICATION_CONSISTENT_HASH_ROUTING", "83")
////    )
////
////
////
////    listenAndPublish(
////        connectionFactory = connectionFactory,
//////        queueName = REGISTRATION_REQUEST_QUEUE,
////        queueName = WEBSERVER_QUEUE,
////        //exchangeName = NOTIFICATION_EXCHANGE,
////        exchangeName = COLLECTOR_EXCHANGE,
////    )
////}
////
////suspend fun listenAndPublish(
////    connectionFactory: ConnectionFactory,
////    queueName: String,
////    exchangeName: String,
////) {
////
////    logger.info("listening for notifications " + queueName)
////    listenForNotificationRequests(
////        connectionFactory,
////        queueName,
////        exchangeName
////    )
////}
////
////class Csmr(ch: Channel, exchangeName: String): Consumer {
////
////    val ch: Channel = ch
////    val exchange: String = exchangeName
////
////    override fun handleConsumeOk(consumerTag: String?) {
////
////    }
////
////    override fun handleCancelOk(consumerTag: String?) {
////
////    }
////
////    override fun handleCancel(consumerTag: String?) {
////
////    }
////
////    override fun handleShutdownSignal(consumerTag: String?, sig: ShutdownSignalException?) {
////        sig?.let {
//////            throw it
////        }
////    }
////
////    override fun handleRecoverOk(consumerTag: String?) {
////
////    }
////
////    override fun handleDelivery(
////        consumerTag: String?,
////        envelope: Envelope?,
////        properties: AMQP.BasicProperties?,
////        body: ByteArray?
////    ) {
////
////        val email = body?.decodeToString().orEmpty()
////
////
////        var messageType = 0;
////            // if the message is malformed, try to make it formed - for tests
//////
//////            ch.basicPublish(
//////                exchange,
//////                "42",
//////                MessageProperties.PERSISTENT_BASIC,
//////                Json.encodeToString<ConfirmationMessageA>(
//////                    ConfirmationMessageA(
//////                        email = Json.decodeFromString<LegacyConfirmationMessage>(email).email,
//////                        confirmationCode = Json.decodeFromString<LegacyConfirmationMessage>(email).confirmationCode,
//////                        notificationSent = false,
//////                        messageSaved = true // this actually hasn't saved
//////                    )
//////                ).toByteArray()
//////            )
//////            if (envelope != null) {
//////                ch.basicAck(envelope.deliveryTag, false)
//////            }
//////
////
//////                ch.basicPublish(
//////                    exchange,
//////                    UUID.randomUUID().toString(),
//////                    MessageProperties.PERSISTENT_BASIC,
//////                    Json.encodeToString<ConfirmationMessageA>(deserialized).toByteArray()
//////                )
//////            }
//////            if (envelope != null) {
//////                ch.basicAck(envelope.deliveryTag, false)
//////            }
////
//////        }
//////
//////        if (messageType == 3) {
//////
//////            var deserialized: ConfirmationMessage = Json.decodeFromString<ConfirmationMessage>(email);
//////
//////            ch.basicPublish(
//////                exchange,
//////                UUID.randomUUID().toString(),
//////                MessageProperties.PERSISTENT_BASIC,
//////                Json.encodeToString<ConfirmationMessage>(deserialized).toByteArray()
//////            )
//////            if (envelope != null) {
//////                ch.basicAck(envelope.deliveryTag, false)
//////            }
//////        }
////    }
////}
////
////private fun listenForNotificationRequests(
////    connectionFactory: ConnectionFactory,
////    queueName: String,
////    exchangeName: String
////) {
////    val channel = connectionFactory.newConnection().createChannel()
////
////    channel.basicConsume(queueName,
////        false,
////        Csmr(channel, exchangeName)
////    );
////}
////
//////
//////@Serializable
//////data class ConfirmationMessage(
//////    val email: String,
//////    val notificationSent: Boolean,
//////    val messageSaved: Boolean
//////)
//////
//////@Serializable
//////data class ConfirmationMessageA(
//////    val email: String,
//////    @Serializable(with = UUIDSerializer::class)
//////    val confirmationCode: UUID,
//////    var notificationSent: Boolean,
//////    val messageSaved: Boolean
//////)
//////
//////@Serializable
//////data class LegacyConfirmationMessage(
//////    val email: String,
//////    @Serializable(with = UUIDSerializer::class)
//////    val confirmationCode: UUID,
//////)