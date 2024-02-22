package kmeans.analyzer

import com.rabbitmq.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kmeans.`env-support`.getEnvInt
import kmeans.`env-support`.getEnvStr
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

import com.rabbitmq.client.ConnectionFactory
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import kmeans.solrSupport.SolrStartup.*

// WebServer -> Collector -> Analyzer -> WebServer


val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-analyzer-app")

val ANALYZER_EXCHANGE = getEnvStr("ANALYZER_EXCHANGE", "analyzer-exchange")
val ANALYZER_QUEUE = getEnvStr("ANALYZER_QUEUE", "analyzer-queue-analyzer-app")

val EMBEDDED_NETTY_PORT = getEnvInt("DATA_ANALYZER_PORT_MAP", 8887)

val CASSANDRA_SEEDS = getEnvStr("CASSANDRA_SEEDS", "127.0.0.1")
val ZOO_LOCAL = getEnvStr("ZOO_LOCAL", "zoo1:2181")
val RABBIT_URL = getEnvStr("RABBIT_URL", "127.0.0.1")

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
        AnalyzerCsmr(channel, exchangeName, connectionFactory)
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

val counter: Counter = Counter.builder()
    .name("my_count_total")
    .help("example counter")
    .labelNames("status")
    .register()

fun main() {



    runBlocking {

        val server: HTTPServer = HTTPServer.builder()
            .port(Integer.valueOf("65400"))
            .buildAndStart()

        solrInitialize(ZOO_LOCAL)




        val connectionFactory = ConnectionFactory();

        connectionFactory.setHost(RABBIT_URL);

        val conn = connectionFactory.newConnection();

        val ch = conn.createChannel();

//        ch.exchangeDeclare(
//            WEBSERVER_EXCHANGE,
//            "x-consistent-hash",
//            false,
//            false,
//            null
//        )
//        ch.queueDeclare(
//            WEBSERVER_QUEUE,
//            false,
//            false,
//            false,
//            null,
//
//            )
//        ch.queueBind(
//            WEBSERVER_QUEUE,
//            WEBSERVER_EXCHANGE,
//            getEnvStr("ROUNDTRIP_REQUEST_CONSISTENT_HASH_ROUTING", "11")
//        )




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



        embeddedServer(Netty, port = EMBEDDED_NETTY_PORT) {
            routing {
                get("/") {
                    call.respondText("response OK")
                }
            }
        }.start(wait = true)

    }





}

