package kmeans.webserver

//////


////// TODO /////////



/// solve this:
// restart application/service if messages ready gets to large
// curl -i -u guest:guest
// http://localhost:15672/api/queues/%2f/collector-queue-ip-10-0-1-119.us-west-1.compute.internal-b
// docker container restart


///// app hangs after it cant find a replica, need to restart it if solr isnt ready
//Exception in thread "main" org.apache.solr.client.solrj.impl.HttpSolrClient$RemoteSolrException: Error from server at http://10.0.1.119:8983/solr/coordinates_after_webserver: No active replicas found for collection: coordinates_after_webserver
////////////////////////////////////////////////////////////////

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
import java.util.*
import kotlin.jvm.optionals.getOrElse


// WebServer -> Collector -> Analyzer -> WebServer

val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "collector-exchange")

val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "collector-queue-webserver-app")

val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")

val SOLR_CONNECT_IP = getEnvStr("SOLR_CONNECT_IP", "solr1:8983")
val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-webserver-app")

//val EMBEDDED_NETTY_PORT = getEnvInt("BASIC_SERVER_PORT_MAP", 8888)

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




                call.respondText(ObjectMapper().writeValueAsString(out))
            }

            // todo : select only json data, this will contain number of coordinates to make

            // todo : select only json data, this will contain number of coordinates to make

            // todo: add ajax endpoint for all running jobs and their status


            // todo: add click a done job and return the points


            // todo: add start a job given a picture

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
//        queueName = REGISTRATION_REQUEST_QUEUE,
            queueName = WEBSERVER_QUEUE,
            //exchangeName = NOTIFICATION_EXCHANGE,
            exchangeName = null,
            jedis = jedis
        )
    }
}
