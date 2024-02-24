package kmeans.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import kmeans.solrSupport.Coordinate;
import kmeans.solrSupport.SolrEntity;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.jupiter.api.Test;
import kmeans.solrSupport.SolrStartup.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.lang.Thread.sleep;
import static kmeans.solrSupport.SolrStartup.createSchema;
import static kmeans.solrSupport.SolrStartup.solrInitialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.SolrClientUtils.createCollection;

public class AnalyzerCsmrUnitTest extends ContainerUtility {
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
    public void testContainersLoaded() throws SolrServerException, IOException {
        SolrPingResponse response = getSolrClient().ping("dummy");
        assertEquals(response.getStatus(), 0);
        assertTrue(response.jsonStr().contains("zkConnected\":true"));


        assertEquals(ContainerUtility.rabbitContainer.getAdminPassword(), "guest");
        assertEquals(ContainerUtility.rabbitContainer.getAdminUsername(), "guest");
        assert (true);
    }

    @Test
    public void returnsSuccessToWebServerExchange() throws Exception {
//        ContainerUtility.rabbitContainer.getHost()

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setUsername(rabbitContainer.getAdminUsername());
        connectionFactory.setPassword(rabbitContainer.getAdminPassword());
//        connectionFactory.setPort(5671);
        //connectionFactory.enableHostnameVerification();
        connectionFactory.setUri(rabbitContainer.getAmqpUrl());
        //connectionFactory.setHost(ContainerUtility.rabbitContainer.getHost());
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


        //        (solrContainer.getHost() + ":" + solrContainer.getZookeeperPort());

        ch.exchangeDeclare(
                "analyzer-exchange",
                "direct",
                false,
                false,
                null
        );
        ch.queueDeclare(
                "analyzer-queue-analyzer-app",
                false,
                false,
                false,
                null
        );
        ch.queueBind(
                "analyzer-queue-analyzer-app",
                "analyzer-exchange",
                "83"
        );

        ch.exchangeDeclare(
                "webserver-exchange",
                "direct",
                false,
                false,
                null
        );
        ch.queueDeclare(
                "webserver-queue-analyzer-app",
                false,
                false,
                false,
                null
        );
        ch.queueBind(
                "webserver-queue-analyzer-app",
                "webserver-exchange",
                "81"
        );

        SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();
        List<Coordinate> listOfNewCoordinates = new ArrayList<>();
        listOfNewCoordinates.add(new Coordinate(1.0D,3.0D, 9.0D));
        coordinateList.setCoordinates(listOfNewCoordinates);
        coordinateList.setFilename("a.png");
        coordinateList.setWidth(1);
        coordinateList.setHeight(3);

        solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/coordinates_after_collector").build();

        solrClient.addBean(
                new SolrEntity(
                        "111",
                        "222",
                        new ObjectMapper().writeValueAsString(coordinateList)
                )
        );
        solrClient.commit();

        AnalyzerCsmr analyzerCsmr = new AnalyzerCsmr(
                ch,
                "webserver-exchange",
                connectionFactory,
                solrContainer.getHost() + ":" + solrContainer.getSolrPort(),
                "81");
        ch.basicConsume(
                "analyzer-queue-analyzer-app",
                false,
                analyzerCsmr);
        assertTrue(ContainerUtility.rabbitContainer.execInContainer(
                "rabbitmqadmin",
                "publish",
                "exchange=analyzer-exchange",
                "routing_key=83",
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
        ch.basicConsume("webserver-queue-analyzer-app", false, new DefaultConsumer(ch) {
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
