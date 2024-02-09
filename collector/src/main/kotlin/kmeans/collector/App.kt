package kmeans.collector

import com.rabbitmq.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kmeans.`env-support`.getEnvStr

import org.slf4j.LoggerFactory
import kotlinx.coroutines.runBlocking

import kmeans.`env-support`.getEnvInt

// WebServer -> Collector -> Analyzer -> WebServer

val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "collector-exchange")
val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "collector-queue-webserver-app")

val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-webserver-app")

val EMBEDDED_NETTY_PORT = getEnvInt("DATA_COLLECTOR_PORT_MAP", 8886)

private val logger = LoggerFactory.getLogger("kmeans.collector.App")

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(queueName,
        false,
        CollectorCsmr(channel, exchangeName)
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

    println(' ')



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
}