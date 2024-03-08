package kmeans.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import kmeans.solrSupport.Coordinate;
import kmeans.solrSupport.SolrEntity;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import kmeans.testSupport.ContainerUtility;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;
import static kmeans.solrSupport.SolrStartup.createSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.SolrClientUtils.createCollection;


public class CollectorCsmrUnitTest extends ContainerUtility {
    private SolrClient client = null;

    public static final int DEFAULT_AMQPS_PORT = 5671;

    public static final int DEFAULT_AMQP_PORT = 5672;

    public static final int DEFAULT_HTTPS_PORT = 15671;

    public static final int DEFAULT_HTTP_PORT = 15672;

    public boolean found = false;

    private SolrClient getSolrClient() {

        if (client == null) {
            client =
                    new Http2SolrClient.Builder("http://" +
                            ContainerUtility.solrContainer.getHost() +
                            ":" +
                            ContainerUtility.solrContainer.getSolrPort() +
                            "/solr")
                            .build();
        }
        return client;
    }

    @Test
    @Ignore
    public void testContainersLoaded() throws SolrServerException, IOException {
        SolrPingResponse response = getSolrClient().ping("dummy");
        assertEquals(response.getStatus(), 0);
        assertTrue(response.jsonStr().contains("zkConnected\":true"));


        assertEquals(ContainerUtility.rabbitContainer.getAdminPassword(), "guest");
        assertEquals(ContainerUtility.rabbitContainer.getAdminUsername(), "guest");


        assert (true);
    }

    @Test
    @Ignore
    public void returnsSuccessToAnalyzerExchange() throws Exception {

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setUsername(rabbitContainer.getAdminUsername());
        connectionFactory.setPassword(rabbitContainer.getAdminPassword());

        connectionFactory.setUri(rabbitContainer.getAmqpUrl());

        Connection conn = connectionFactory.newConnection();
        Channel ch = conn.createChannel();

        String solrIp = solrContainer.getHost() + ":" + solrContainer.getSolrPort();

        createCollection(solrContainer.getHost(), solrContainer.getSolrPort(), "coordinates_after_webserver", null);
        createCollection(solrContainer.getHost(), solrContainer.getSolrPort(), "coordinates_after_collector", null);
        createCollection(solrContainer.getHost(), solrContainer.getSolrPort(), "coordinates_after_analyzer", null);
        createCollection(solrContainer.getHost(), solrContainer.getSolrPort(), "schedules", null);

        SolrClient solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/coordinates_after_webserver").build();
        createSchema(solrClient);

        solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/coordinates_after_collector").build();
        createSchema(solrClient);

        solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/coordinates_after_analyzer").build();
        createSchema(solrClient);

        solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/schedules").build();
        createSchema(solrClient);

        ch.exchangeDeclare(
                "collector-exchange",
                "direct",
                false,
                false,
                null
        );
        ch.queueDeclare(
                "collector-queue-collector-app",
                false,
                false,
                false,
                null
        );
        ch.queueBind(
                "collector-queue-collector-app",
                "collector-exchange",
                "86"
        );

        ch.exchangeDeclare(
                "analyzer-exchange",
                "direct",
                false,
                false,
                null
        );
        ch.queueDeclare(
                "analyzer-queue-collector-app",
                false,
                false,
                false,
                null
        );
        ch.queueBind(
                "analyzer-queue-collector-app",
                "analyzer-exchange",
                "80"
        );

        SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();
        coordinateList.setFilename("a.png");

        solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/coordinates_after_webserver").build();

        solrClient.addBean(
                new SolrEntity(
                        "111",
                        "222",
                        new ObjectMapper().writeValueAsString(coordinateList)
                )
        );
        solrClient.commit();

        CollectorCsmr collectorCsmr = new CollectorCsmr(
                ch,
                "analyzer-exchange",
                connectionFactory,
                solrContainer.getHost() + ":" + solrContainer.getSolrPort(),
                () -> "80",
                new JedisPooled(redisContainer.getHost(), 6379)
                );
        ch.basicConsume(
                "collector-queue-collector-app",
                false,
                collectorCsmr);

        // create a message ( this would be written by the webserver app )
        assertTrue(ContainerUtility.rabbitContainer.execInContainer(
                "rabbitmqadmin",
                "publish",
                "exchange=collector-exchange",
                "routing_key=86",
                //"properties='{\"content_type\":\"application/json\"}'",
                "payload={\"solrEntityScheduledRun_UUID\":\"111\",\"solrEntityCoordinatesList_UUID\":\"222\",\"numTriesFindingSolrRecord\":333}"
                //"payload_encoding='string'"
                        )
                        .getStdout().contains("Message published"));
//        ""
//                    "rabbitmqadmin publish " +
//                            "exchange=analyzer-exchange " +
//                            "routing_key=83 " +



        // Queue Consume //



        ;
        ch.basicConsume("analyzer-queue-collector-app", false, new DefaultConsumer(ch) {
            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) throws IOException
            {
                String message = new String(body, "UTF-8");
                System.out.println(" [] '" + message + "'");
                found = true;
            }
        });

        while (!found)
        {
            System.out.println("Waiting.");
            sleep(1000);
        }

    }
}
