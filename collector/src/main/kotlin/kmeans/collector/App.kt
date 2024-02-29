package kmeans.collector

import com.rabbitmq.client.ConnectionFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
//import io.prometheus.metrics.core.metrics.Counter
//import io.prometheus.metrics.exporter.httpserver.HTTPServer
import kmeans.support.getEnvInt
import kmeans.support.getEnvStr
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kmeans.solrSupport.SolrStartup.*
import kmeans.support.ContextCloseExit


// WebServer -> Collector -> Analyzer -> WebServer

val ANALYZER_EXCHANGE = getEnvStr("ANALYZER_EXCHANGE", "collector-exchange")
val ANALYZER_QUEUE = getEnvStr("ANALYZER_QUEUE", "collector-queue-webserver-app")

// TODO: are these needed?
val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "webserver-exchange")
val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "webserver-queue-webserver-app")

val EMBEDDED_NETTY_PORT = getEnvInt("DATA_COLLECTOR_PORT_MAP", 8886)

val CASSANDRA_SEEDS = getEnvStr("CASSANDRA_SEEDS", "127.0.0.1")
val ZOO_LOCAL = getEnvStr("ZOO_LOCAL", "zoo1:2181")
val RABBIT_URL = getEnvStr("RABBIT_URL", "127.0.0.1")

private val logger = LoggerFactory.getLogger("kmeans.collector.App")

val collectorCounter: Counter = Counter.builder()
    .name("collectorCounter")
    .labelNames("rabbits_consumed", "rabbits_acknowledged", "rabbits_published")
    .register()

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(queueName,
        false,
        CollectorCsmr(channel, exchangeName, connectionFactory)
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

//val counter: Counter = Counter.builder()
//    .name("my_count_total")
//    .help("example counter")
//    .labelNames("status")
//    .register()

fun main() {

    JvmMetrics.builder().register();

    val prometheus: HTTPServer = HTTPServer.builder()
        .port(Integer.valueOf("65409"))
        .buildAndStart()
    collectorCounter.inc()
    runBlocking {

//        val server: HTTPServer = HTTPServer.builder()
//            .port(Integer.valueOf("65403"))
//            .buildAndStart()


        try {
            solrInitialize(ZOO_LOCAL)
        } catch ( e: Exception ) {
            logger.error("solrInitialize", e)
            ContextCloseExit.closeContextExit(-1)
        }


        val connectionFactory = ConnectionFactory();

        connectionFactory.setHost(RABBIT_URL);

        val conn = connectionFactory.newConnection();

        val ch = conn.createChannel();

        ch.exchangeDeclare(
            ANALYZER_EXCHANGE,
            "x-consistent-hash",
            false,
            false,
            null
        )
//        ch.queueDeclare(
//            ANALYZER_QUEUE,
//            false,
//            false,
//            false,
//            null,
//
//            )
//        ch.queueBind(
//            ANALYZER_QUEUE,
//            ANALYZER_EXCHANGE,
//            getEnvStr("ROUNDTRIP_REQUEST_CONSISTENT_HASH_ROUTING", "11")
//        )




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
            getEnvStr("ROUNDTRIP_NOTIFICATION_CONSISTENT_HASH_ROUTING", "83")
        )



        listenAndPublish(
            connectionFactory = connectionFactory,
//        queueName = REGISTRATION_REQUEST_QUEUE,
            queueName = COLLECTOR_QUEUE,
            //exchangeName = NOTIFICATION_EXCHANGE,
            exchangeName = ANALYZER_EXCHANGE,
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