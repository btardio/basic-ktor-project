package kmeans.analyzer

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
import kmeans.solrSupport.SolrStartup.solrInitialize
import kmeans.support.ContextCloseExit
import kmeans.support.getEnvInt
import kmeans.support.getEnvStr

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPooled
import java.util.*
import java.util.Map
import java.util.Objects.isNull

// WebServer -> Collector -> Analyzer -> WebServer


val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-analyzer-app")

val ANALYZER_EXCHANGE = getEnvStr("ANALYZER_EXCHANGE", "analyzer-exchange")
val ANALYZER_QUEUE = getEnvStr("ANALYZER_QUEUE", "analyzer-queue-analyzer-app")

val EMBEDDED_NETTY_PORT = getEnvInt("DATA_ANALYZER_PORT_MAP", 8887)

val CASSANDRA_SEEDS = getEnvStr("CASSANDRA_SEEDS", "127.0.0.1")
val ZOO_LOCAL = getEnvStr("ZOO_LOCAL", "zoo1:2181")
val RABBIT_URL = getEnvStr("RABBIT_URL", "127.0.0.1")
val SOLR_CONNECT_IP = getEnvStr("SOLR_CONNECT_IP", "solr1:8983")

private val logger = LoggerFactory.getLogger("kmeans.analyzer.App")

//log.error(new String(body, StandardCharsets.UTF_8));
var analyzerCounter: kotlin.collections.Map<String, Counter> = Map.of(
    "rabbits_consumed",
    Counter.builder().name("rabbits_consumed").help("~").labelNames("sum").register(),
    "rabbits_acknowledged",
    Counter.builder().name("rabbits_acknowledged").help("~").labelNames("sum").register(),
    "rabbits_published",
    Counter.builder().name("rabbits_published").help("~").labelNames("sum").register(),
    "not_found_expected_coordinates",
    Counter.builder().name("not_found_expected_coordinates").help("~").labelNames("sum").register(),
    "exception_unknown_republish",
    Counter.builder().name("exception_unknown_republish").help("~").labelNames("sum").register(),
    "processed_coordinates_after_read",
    Counter.builder().name("processed_coordinates_after_read").help("~").labelNames("sum").register(),
    "failed_writing_coordinates_after_read",
    Counter.builder().name("failed_writing_coordinates_after_read").help("~").labelNames("sum").register(),
    "succeeded_writing_coordinates_after_read",
    Counter.builder().name("succeeded_writing_coordinates_after_read").help("~").labelNames("sum").register(),
)
//
//val analyzerCounter: Counter = Counter.builder()
//    .name("analyzerCounter")
//    .labelNames(
//        "rabbits_consumed",
//        "rabbits_acknowledged",
//        "rabbits_published",
//        "not_found_expected_coordinates",
//        "exception_unknown_republish",
//        "processed_coordinates_after_read",
//        "failed_writing_coordinates_after_read",
//        "succeeded_writing_coordinates_after_read"
//    )
//    .register()

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String,
    counter: kotlin.collections.Map<String, Counter>,
    jedis: JedisPooled
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(
        queueName,
        false,
        AnalyzerCsmr(channel,
            exchangeName,
            connectionFactory,
            SOLR_CONNECT_IP,
            UUID.randomUUID().toString(),
            counter,
            jedis)
    );
}


suspend fun listenAndPublish(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String,
    counter: kotlin.collections.Map<String, Counter>,
    jedis: JedisPooled
) {

    logger.info("listening for notifications " + queueName)
    listenForNotificationRequests(
        connectionFactory,
        queueName,
        exchangeName,
        counter,
        jedis
    )
}

//val counter: Counter = Counter.builder()
//    .name("my_count_total")
//    .help("example counter")
//    .labelNames("status")
//    .register()

fun main() {

    val jedis = JedisPooled("redis", 6379)
    jedis.expire(ANALYZER_QUEUE, 13);
    JvmMetrics.builder().register();
    val prometheus: HTTPServer = HTTPServer.builder()
        .port(Integer.valueOf("65400"))
        .buildAndStart()


    runBlocking {

//        val server: HTTPServer = HTTPServer.builder()
//            .port(Integer.valueOf("65400"))
//            .buildAndStart()

        if ( isNull(jedis.get("EHLO")) ) {
            try {
                solrInitialize(ZOO_LOCAL)
                jedis.set("EHLO", "HELO")
            } catch (e: Exception) {
                logger.error("solrInitialize", e)
                ContextCloseExit.closeContextExit(-1)
            }
        }
        jedis.expire(ANALYZER_QUEUE, 13);

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
            counter = analyzerCounter,
            jedis = jedis
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

