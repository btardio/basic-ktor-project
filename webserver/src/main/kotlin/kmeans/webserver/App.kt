package kmeans.webserver


//import kmeans.webserver.SolrStartup.createCollection
//import kmeans.webserver.SolrStartup.createSchema
import com.rabbitmq.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kmeans.`env-support`.getEnvStr
import kmeans.solrSupport.SolrEntity
import com.fasterxml.jackson.databind.ObjectMapper;
import kmeans.solrSupport.SolrEntityCoordinateJsonData
import kmeans.solrSupport.SolrEntityScheduledRunJsonData
import kotlinx.coroutines.runBlocking
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.slf4j.LoggerFactory
import java.util.*

import kmeans.solrSupport.SolrStartup.*

// WebServer -> Collector -> Analyzer -> WebServer

val COLLECTOR_EXCHANGE = getEnvStr("COLLECTOR_EXCHANGE", "collector-exchange")

val COLLECTOR_QUEUE = getEnvStr("COLLECTOR_QUEUE", "collector-queue-webserver-app")

val WEBSERVER_EXCHANGE = getEnvStr("WEBSERVER_EXCHANGE", "webserver-exchange")



val WEBSERVER_QUEUE = getEnvStr("WEBSERVER_QUEUE", "webserver-queue-webserver-app")

//val EMBEDDED_NETTY_PORT = getEnvInt("BASIC_SERVER_PORT_MAP", 8888)

val CASSANDRA_SEEDS = getEnvStr("CASSANDRA_SEEDS", "127.0.0.1")

val RABBIT_URL = getEnvStr("RABBIT_URL", "rabbit")

private val logger = LoggerFactory.getLogger("kmeans.collector.App")

private fun listenForNotificationRequests(
    connectionFactory: ConnectionFactory,
    queueName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(queueName,
        false,
        WebserverCsmr(channel, connectionFactory)
    );
}


suspend fun listenAndPublish(
    connectionFactory: ConnectionFactory,
    queueName: String,
    exchangeName: String?,
) {

    logger.info("listening for notifications " + queueName)
    listenForNotificationRequests(
        connectionFactory,
        queueName
    )
}



fun main() {

    runBlocking {
        solrInitialize()
//
//        var solrClient: HttpSolrClient = HttpSolrClient.Builder("http://solr1:8983/solr/sanesystem").build();
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
//        solrClient = HttpSolrClient.Builder("http://solr1:8983/solr/coordinates").build()
//        createSchema(solrClient);
//
//        createCollection(3,1, "schedules");
//        solrClient = HttpSolrClient.Builder("http://solr1:8983/solr/schedules").build()
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
        )

        embeddedServer(Netty, port = 8123) {
            routing {
                get("/") {
                    call.respondText("~~~response OK")
                }
                get("/startKmeans/{numberPoints}") {
                    val numberPoints = call.parameters["numberPoints"]

                    val cf = connectionFactory.newConnection().createChannel()

                    var numPointsAsInt = Integer.parseInt(numberPoints)

                    // todo: add a field for number of coordinates
                    var coordinateList = SolrEntityCoordinateJsonData()
                    coordinateList.setNumPoints(numberPoints);

                    var scheduledRun = SolrEntityScheduledRunJsonData()

                    try {
                        if (numberPoints != null) {
                            scheduledRun.setNumberPoints(numberPoints.toInt())
                            scheduledRun.setNumberPoints(Integer.valueOf(numberPoints))
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
                    var solrClient = HttpSolrClient.Builder("http://solr1:8983/solr/schedules").build();
                    solrClient.addBean(
                        SolrEntity(
                            scheduleUUID,
                            coordinateUUID,
                            ObjectMapper().writeValueAsString(scheduledRun)
                        )
                    )
                    solrClient.commit()

                    // save coordinate
                    solrClient = HttpSolrClient.Builder("http://solr1:8983/solr/coordinates_after_webserver").build();
                    solrClient.addBean(
                        SolrEntity(
                            scheduleUUID,
                            coordinateUUID,
                            ObjectMapper().writeValueAsString(coordinateList)
                        )
                    )
                    solrClient.commit()


                    // save coodrinates, use collection coordinates






                    call.respondText("OK, Templeton, scheduling a new kmeans run using " + numberPoints + "<BR>" +
                    "Your schedule run ID: " + scheduleUUID.toString())
                }

                // todo: add ajax endpoint for all running jobs and their status


                // todo: add click a done job and return the points


                // todo: add start a job given a picture

            }
        }.start(wait = true)
    }

}
