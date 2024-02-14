package kmeans.webserver


//import kmeans.webserver.SolrStartup.createCollection
//import kmeans.webserver.SolrStartup.createSchema
import com.rabbitmq.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kmeans.`env-support`.getEnvStr
import kmeans.solrSupport.SolrEntity
import com.fasterxml.jackson.databind.ObjectMapper;
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.response.QueryResponse
import org.slf4j.LoggerFactory
import java.sql.Timestamp
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
    queueName: String,
    exchangeName: String
) {
    val channel = connectionFactory.newConnection().createChannel()

    channel.basicConsume(queueName,
        false,
        WebserverCsmr(channel, exchangeName)
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



//        listenAndPublish(
//            connectionFactory = connectionFactory,
////        queueName = REGISTRATION_REQUEST_QUEUE,
//            queueName = WEBSERVER_QUEUE,
//            //exchangeName = NOTIFICATION_EXCHANGE,
//            exchangeName = COLLECTOR_EXCHANGE,
//        )

        embeddedServer(Netty, port = 8123) {
            routing {
                get("/") {
                    call.respondText("~~~response OK")
                }
                get("/startKmeans/{numberPoints}") {
                    val numberPoints = call.parameters["numberPoints"]


                    val cf = connectionFactory.newConnection().createChannel()

                    var numPointsAsInt = Integer.parseInt(numberPoints)

                    var coordinateList = SolrEntityCoordinate()

                    var scheduledRun = SolrEntityScheduledRun(coordinateList)

                    scheduledRun.setStartTime(Timestamp(Date().time))
                    scheduledRun.setNumberPoints(numPointsAsInt)
                    scheduledRun.setStatus("started");
                    coordinateList.setSchedule_uuid(scheduledRun.getSchedule_uuid())

                    var sendingMessage: RabbitMessageStartRun = RabbitMessageStartRun(scheduledRun, coordinateList);

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
                            scheduledRun.getSchedule_uuid(),
                            scheduledRun.getCoordinate_uuid(),
                            ObjectMapper().writeValueAsString(scheduledRun)
                        )
                    )
                    solrClient.commit()

                    // save coordinate
                    solrClient = HttpSolrClient.Builder("http://solr1:8983/solr/coordinates").build();
                    solrClient.addBean(
                        SolrEntity(
                            scheduledRun.getSchedule_uuid(),
                            scheduledRun.getCoordinate_uuid(),
                            ObjectMapper().writeValueAsString(coordinateList)
                        )
                    )
                    solrClient.commit()


                    // save coodrinates, use collection coordinates






                    call.respondText("OK, Templeton, scheduling a new kmeans run using " + numberPoints + "<BR>" +
                    "Your schedule run ID: " + scheduledRun.getSchedule_uuid())
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

class UUIDSerializer: KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}