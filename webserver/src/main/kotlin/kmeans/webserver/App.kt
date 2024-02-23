package kmeans.webserver




////// TODO /////////
///// app hangs after it cant find a replica, need to restart it if solr isnt ready
//Exception in thread "main" org.apache.solr.client.solrj.impl.HttpSolrClient$RemoteSolrException: Error from server at http://10.0.1.119:8983/solr/coordinates_after_webserver: No active replicas found for collection: coordinates_after_webserver
////////////////////////////////////////////////////////////////




//import kmeans.webserver.SolrStartup.createCollection
//import kmeans.webserver.SolrStartup.createSchema

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import kmeans.`env-support`.getEnvStr
import kmeans.solrSupport.SolrEntity
import kmeans.solrSupport.SolrEntityCoordinateJsonData
import kmeans.solrSupport.SolrEntityScheduledRunJsonData
import kmeans.solrSupport.SolrStartup.solrInitialize
import kotlinx.coroutines.runBlocking
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.slf4j.LoggerFactory
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

private val logger = LoggerFactory.getLogger("kmeans.collector.App")

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String,
    counter: Counter
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(
        queueName,
        false,
        WebserverCsmr(channel, connectionFactory, counter)
    );
}


suspend fun listenAndPublish(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String?,
    counter: Counter
) {

    logger.info("listening for notifications " + queueName)
    listenForNotificationRequests(
        connectionFactory,
        queueName,
        counter
    )
}


private operator fun SolrDocument.component1(): SolrDocument {
    return this;
}

val counter: Counter = Counter.builder()
    .name("my_count_total")
    .help("example counter")
    .labelNames("status")
    .register()

fun main() {

    runBlocking {

        val server: HTTPServer = HTTPServer.builder()
            .port(Integer.valueOf("65409"))
            .buildAndStart()


        solrInitialize(ZOO_LOCAL)
//
//        var solrClient: HttpSolrClient = HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/sanesystem").build();
//
//        // sane checks stay
//
//        // we have a collection
//        createCollection(1,1, "sanesystem")
//
//        // we have a schema
//        createSchema(solrClient)
//
//        val uuid: UUID = UUID.randomUUID()
//
//        val currentTime = Date().getTime().toString()
//
//        // we can write
//        solrClient.addBean(
//            SolrEntity(
//                currentTime,
//                currentTime,
//                "{" + Date().toString() + "}"
//            )
//        )
//        solrClient.commit()
//
////            // not sane            .withCql("CREATE TABLE sanity (uuid varchar, value varchar, PRIMARY KEY (uuid));")
//
//        // we can read
//        val query = SolrQuery()
//        query.set("q", "schedule_uuid:" + currentTime)
//        val response: QueryResponse = solrClient.query(query)
//
//        if ( response.results.size != 1 ) {
//            throw Exception("Error, not sane.")
//        }
//
//
//        createCollection(3,1, "coordinates")
//        solrClient = HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates").build()
//        createSchema(solrClient);
//
//        createCollection(3,1, "schedules");
//        solrClient = HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/schedules").build()
//        createSchema(solrClient);

        val connectionFactory = ConnectionFactory();

        connectionFactory.setHost(RABBIT_URL);

        val conn = connectionFactory.newConnection();

        val ch = conn.createChannel();

        ch.exchangeDeclare(
            COLLECTOR_EXCHANGE,
            "x-consistent-hash",
            false,
            false,
            null
        )
//        ch.queueDeclare(
//            COLLECTOR_QUEUE,
//            false,
//            false,
//            false,
//            null,
//
//            )
//        ch.queueBind(
//            COLLECTOR_QUEUE,
//            COLLECTOR_EXCHANGE,
//            getEnvStr("ROUNDTRIP_REQUEST_CONSISTENT_HASH_ROUTING", "11")
//        )


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
            counter,
        )

        embeddedServer(Netty, port = 8888) {
            routing {
                get("/") {
                    call.respondText("~~~response OK")
                }
                get("/startKmeans/{filename}") {
                    val filename = call.parameters["filename"]

                    val cf = connectionFactory.newConnection().createChannel()

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

                    }

//                    scheduledRun.setStartTime(Timestamp(Date().time))
//                    scheduledRun.setNumberPoints(numPointsAsInt)
//                    scheduledRun.setStatus("started");
                    //coordinateList.setSchedule_uuid(scheduledRun.getSchedule_uuid())
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
                    cf.close()
//                    cf.close()


                    // save schedule run, create collection


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


                    call.respondText(
                        "OK, Templeton, scheduling a new kmeans run using " + filename + "<BR>" +
                                "Your schedule run ID: " + scheduleUUID.toString()
                    )

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
                        counter.labelValues("get_all_schedules_fail").inc()
                    }



                }
                get("/getFinishedSchedule/{coordinateId}") {

                    // get coordinates entry in solr
                    val query = SolrQuery()

                    query.set("q", "coordinate_uuid:" + call.parameters["coordinateId"])
                    val solrClient: SolrClient =
                        HttpSolrClient.Builder("http://" + SOLR_CONNECT_IP + "/solr/coordinates_after_analyzer").build()

                    try {
//                        var response: QueryResponse? = null
//                        response = solrClient.query(query)


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
                        counter.labelValues("get_finished_schedule_fail").inc()
                    }
                }



                // todo : select only json data, this will contain number of coordinates to make

                // todo : select only json data, this will contain number of coordinates to make

                // todo: add ajax endpoint for all running jobs and their status


                // todo: add click a done job and return the points


                // todo: add start a job given a picture

            }
        }.start(wait = true)
    }

}
