package kmeans.webserver

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kmeans.rabbitSupport.LazyInitializedSingleton
import kmeans.solrSupport.SolrEntity
import kmeans.solrSupport.SolrEntityCoordinateJsonData
import kmeans.solrSupport.SolrEntityScheduledRunJsonData
import kmeans.solrSupport.SolrStartup.solrInitialize
import kmeans.support.ContextCloseExit
import kmeans.support.getEnvStr
import kotlinx.coroutines.runBlocking
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.ScanParams.SCAN_POINTER_START
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrElse


// WebServer -> Collector -> Analyzer -> WebServer

val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "collector-exchange")

val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "collector-queue-webserver-app")

val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")

val SOLR_CONNECT_IP = getEnvStr("SOLR_CONNECT_IP", "solr1:8983")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-webserver-app")

val CASSANDRA_SEEDS = getEnvStr("CASSANDRA_SEEDS", "127.0.0.1")
val ZOO_LOCAL = getEnvStr("ZOO_LOCAL", "zoo1:2181")
val RABBIT_URL = getEnvStr("RABBIT_URL", "rabbit")


val connectionFactory = ConnectionFactory();


private val logger = LoggerFactory.getLogger("kmeans.collector.App")

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    jedis: JedisPooled
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(
        queueName,
        false,
        WebserverCsmr(
            channel,
            connectionFactory,
            SOLR_CONNECT_IP,
            jedis)
    );
}


suspend fun listenAndPublish(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String?,
    jedis: JedisPooled
) {

    logger.info("listening for notifications " + queueName)
    listenForNotificationRequests(
        connectionFactory,
        queueName,
        jedis
    )
}

val executorService = Executors.newFixedThreadPool(5)

private operator fun SolrDocument.component1(): SolrDocument {
    return this;
}

fun main() {
    val jedis = JedisPooled("redis", 6379)
    jedis.set("webserver", "OK")
    jedis.expire("webserver", 180);

    connectionFactory.setHost(RABBIT_URL);

    if (Objects.isNull(jedis.get("EHLO"))) {
        try {
            solrInitialize(ZOO_LOCAL)
            jedis.set("EHLO", "HELO")
        } catch (e: Exception) {
            logger.error("solrInitialize", e)
            ContextCloseExit.closeContextExit(-1)
        }
    }
    jedis.set("webserver", "OK")
    jedis.expire("webserver", 180);


    embeddedServer(Netty, port = 8888) {
        routing {
            get("/") {
                call.respondText("~~~response OK")
            }
            get("/startKmeans/{filename}") {
                val filename = call.parameters["filename"]

                val cf = LazyInitializedSingleton.getInstance(connectionFactory)

                // todo: add a field for number of coordinates
                var coordinateList = SolrEntityCoordinateJsonData()
                coordinateList.setNumPoints(0);


                var scheduledRun = SolrEntityScheduledRunJsonData()
                scheduledRun.setNumberPoints(0)

                try {
                    if (filename != null) {
                        coordinateList.setFilename(filename);
                        scheduledRun.setFilename(filename);
                    }
                    scheduledRun.setStatus("started")


                } catch (e: Exception) {
                    call.respondText("" + e.message)
                }

                val scheduleUUID = UUID.randomUUID();
                val coordinateUUID = UUID.randomUUID()
                var sendingMessage: kmeans.rabbitSupport.RabbitMessageStartRun =
                    kmeans.rabbitSupport.RabbitMessageStartRun(scheduleUUID.toString(), coordinateUUID.toString());

                cf.basicPublish(
                    COLLECTOR_EXCHANGE,
                    UUID.randomUUID().toString(),
                    MessageProperties.PERSISTENT_BASIC,
                    ObjectMapper().writeValueAsString(sendingMessage).toByteArray()
                )

                // save schedule run, create collection
                var solrClient = HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/schedules").build();
                solrClient.addBean(
                    SolrEntity(
                        scheduleUUID,
                        coordinateUUID,
                        ObjectMapper().writeValueAsString(scheduledRun)
                    )
                )
                solrClient.commit()

                // save coordinate
                solrClient = HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_webserver").build();
                solrClient.addBean(
                    SolrEntity(
                        scheduleUUID,
                        coordinateUUID,
                        ObjectMapper().writeValueAsString(coordinateList)
                    )
                )
                solrClient.commit()


                // save coodrinates, use collection coordinates

                call.respondText("{\"filename\": \"${filename}\",\"schedule_uuid\": \"${scheduleUUID}\"}")

            }
            get("/getAllSchedules") {


                // get coordinates entry in solr
                val query = SolrQuery()

                query.set("q", "*:*")
                query.set("fq", "timestamp:[" + Date().time.minus(600000L) + " TO " + Date().time + "]")
                query.set("rows", "1000")
                val solrClient: SolrClient =
                    HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/schedules").build()
                var response: QueryResponse? = null
                try {
                    response = solrClient.query(query)

                    if (response != null) {
                        call.respondText(ObjectMapper().writeValueAsString(response.getResults().map {
                            SolrEntity(
                                Optional.ofNullable(it.getFieldValue("schedule_uuid")).getOrElse { "" }.toString(),
                                Optional.ofNullable(it.getFieldValue("coordinate_uuid")).getOrElse { "" }
                                    .toString(),
                                Optional.ofNullable((it.getFieldValue("jsonData") as List<String>).get(0)).getOrElse { "" }.toString(),
                                (Optional.ofNullable(it.getFieldValue("timestamp")).getOrElse { "-1" }
                                    .toString()).toLong()
                            )
                        }
                        ))
                    }
                } catch (e: SolrServerException) {
                    call.respondText("" + e.message)
                    //log.error("solr error", e)
                    //counter.labelValues("get_all_schedules_fail").labelValues("default").inc()
                } catch (e: Exception) {

                }



            }
            get("/getFinishedSchedule/{coordinateId}") {

                // get coordinates entry in solr
                val query = SolrQuery()

                query.set("q", "coordinate_uuid:" + call.parameters["coordinateId"])
                val solrClient: SolrClient =
                    HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_analyzer").build()

                try {
                    call.respondText(ObjectMapper().writeValueAsString(solrClient.query(query).getResults().map {
                        assert( (it.getFieldValue("jsonData") as List<String>).size == 1)
                        SolrEntity(
                            Optional.ofNullable(it.getFieldValue("schedule_uuid")).getOrElse { "" }.toString(),
                            Optional.ofNullable(it.getFieldValue("coordinate_uuid")).getOrElse { "" }
                                .toString(),
                            Optional.ofNullable((it.getFieldValue("jsonData") as List<String>).get(0)).getOrElse { "" }.toString(),
                            (Optional.ofNullable(it.getFieldValue("timestamp")).getOrElse { "-1" }
                                .toString()).toLong()
                        )
                    }.map {
                        ObjectMapper().readValue<SolrEntityCoordinateJsonData>(
                            it.jsonData,
                            SolrEntityCoordinateJsonData::class.java
                        );
                    }.map {
                        it.coordinates
                    }
                    ))

                } catch (e: SolrServerException) {
                    call.respondText("" + e.message)
                    //counter.labelValues("get_finished_schedule_fail").labelValues("default").inc()
                }
            }
// note: this is commented out but can be uncommented, it's a little bit of a drain on the system
//
            get("/metricsDump") {
                //jedis.scan('0 MATCH '17*' COUNT 1000');
                var out = mutableMapOf<String, String>()
                val scanParams = ScanParams().count(1000).match("17*")
                var cur = SCAN_POINTER_START
                do {
                    val scanResult = jedis.scan(cur, scanParams)

                    scanResult.result.stream().forEach { item: String? ->
                        out.put(item.toString(), jedis.get(item))
                    }

                    cur = scanResult.cursor
                } while (cur != SCAN_POINTER_START)

                call.respondText(URLEncoder.encode(ObjectMapper().writeValueAsString(out), StandardCharsets.UTF_8.toString()))
            }

            get("/metrics") {
                val map = mutableMapOf<String, String>();
                var responseA: CompletableFuture<HttpResponse<String>>? = null
                var responseB: CompletableFuture<HttpResponse<String>>? = null
                var responseC: CompletableFuture<HttpResponse<String>>? = null
                var responseD: CompletableFuture<HttpResponse<String>>? = null
                var responseE: CompletableFuture<HttpResponse<String>>? = null

                try {

                    responseA = HttpClient.newBuilder()
                        .executor(executorService)
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()
                        .sendAsync(
                            HttpRequest.newBuilder()
                                .uri(URI("http://A.lf.lll:8888/metricsDump"))
                                .GET()
                                .build(), HttpResponse.BodyHandlers.ofString()
                        )
                } catch (e: Exception) {

                }

                try {
                    responseB = HttpClient.newBuilder()
                        .executor(executorService)
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()
                        .sendAsync(
                            HttpRequest.newBuilder()
                                .uri(URI("http://B.lf.lll:8888/metricsDump"))
                                .GET()
                                .build(), HttpResponse.BodyHandlers.ofString())
                } catch (e: Exception) {

                }

                try {
                    responseC = HttpClient.newBuilder()
                        .executor(executorService)
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()
                        .sendAsync(
                            HttpRequest.newBuilder()
                                .uri(URI("http://C.lf.lll:8888/metricsDump"))
                                .GET()
                                .build(), HttpResponse.BodyHandlers.ofString())
                } catch (e: Exception) {

                }

                try {
                    responseD = HttpClient.newBuilder()
                        .executor(executorService)
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()
                        .sendAsync(
                            HttpRequest.newBuilder()
                                .uri(URI("http://D.lf.lll:8888/metricsDump"))
                                .GET()
                                .build(), HttpResponse.BodyHandlers.ofString())
                } catch (e: Exception) {

                }

                try {
                    responseE = HttpClient.newBuilder()
                        .executor(executorService)
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()
                        .sendAsync(
                            HttpRequest.newBuilder()
                                .uri(URI("http://E.lf.lll:8888/metricsDump"))
                                .GET()
                                .build(), HttpResponse.BodyHandlers.ofString()
                        )
                } catch (e: Exception) {

                }

                try {
                    if (responseA != null) {
                        map.put("A.lf.lll", responseA.get().body())
                    }; } catch (e: Exception) { }
                try {
                    if (responseB != null) {
                        map.put("B.lf.lll", responseB.get().body())
                    }; } catch (e: Exception) { }
                try {
                    if (responseC != null) {
                        map.put("C.lf.lll", responseC.get().body())
                    }; } catch (e: Exception) { }
                try {
                    if (responseD != null) {
                        map.put("D.lf.lll", responseD.get().body())
                    }; } catch (e: Exception) { }
                try {
                    if (responseE != null) {
                        map.put("E.lf.lll", responseE.get().body())
                    }; } catch (e: Exception) { }

                call.respondText(ObjectMapper().writeValueAsString(map))
            }
        }
    }.start(wait = false)

    runBlocking {

        val ch = LazyInitializedSingleton.getInstance(connectionFactory)

        ch.exchangeDeclare(
            COLLECTOR_EXCHANGE,
            "x-consistent-hash",
            false,
            false,
            null
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
            queueName = WEBSERVER_QUEUE,
            exchangeName = null,
            jedis = jedis
        )
    }
}
