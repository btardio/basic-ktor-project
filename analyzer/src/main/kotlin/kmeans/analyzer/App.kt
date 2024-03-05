package kmeans.analyzer

import com.rabbitmq.client.ConnectionFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String,
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
            jedis)
    );

}


suspend fun listenAndPublish(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String,
    jedis: JedisPooled
) {

    logger.info("listening for notifications " + queueName)
    listenForNotificationRequests(
        connectionFactory,
        queueName,
        exchangeName,
        jedis
    )
}

fun main() {

    val jedis = JedisPooled("redis", 6379)
    jedis.set("analyzer", "OK")
    jedis.expire("analyzer", 180);

    runBlocking {

        if ( isNull(jedis.get("EHLO")) ) {
            try {
                solrInitialize(ZOO_LOCAL)
                jedis.set("EHLO", "HELO")
            } catch (e: Exception) {
                logger.error("solrInitialize", e)
                ContextCloseExit.closeContextExit(-1)
            }
        }
        jedis.set("analyzer", "OK")
        jedis.expire("analyzer", 180);

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
            queueName = ANALYZER_QUEUE,
            exchangeName = WEBSERVER_EXCHANGE,
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

