package kmeans.webserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import kmeans.solrSupport.*;
import kmeans.testSupport.ContainerUtility;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;
import static kmeans.solrSupport.SolrStartup.createCollection;
import static kmeans.solrSupport.SolrStartup.createSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

public class WebserverCsmrTest extends ContainerUtility {
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
    public void returnsWithoutErrorWebserverCsmr() throws Exception {

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
                "webserver-exchange",
                "direct",
                false,
                false,
                null
        );
        ch.queueDeclare(
                "webserver-queue-webserver-app",
                false,
                false,
                false,
                null
        );
        ch.queueBind(
                "webserver-queue-webserver-app",
                "webserver-exchange",
                "86"
        );
//
//        ch.exchangeDeclare(
//                "analyzer-exchange",
//                "direct",
//                false,
//                false,
//                null
//        );
//        ch.queueDeclare(
//                "analyzer-queue-collector-app",
//                false,
//                false,
//                false,
//                null
//        );
//        ch.queueBind(
//                "analyzer-queue-collector-app",
//                "analyzer-exchange",
//                "80"
//        );

        SolrEntityCoordinateJsonData coordinateList = new SolrEntityCoordinateJsonData();
        List<Coordinate> listOfNewCoordinates = new ArrayList<>();
        listOfNewCoordinates.add(new Coordinate(1.0D,3.0D, 9.0D));
        coordinateList.setCoordinates(listOfNewCoordinates);
        coordinateList.setFilename("a.png");
        coordinateList.setWidth(1);
        coordinateList.setHeight(3);

        solrClient = new HttpSolrClient.Builder("http://" + solrIp + "/solr/coordinates_after_analyzer").build();

        solrClient.addBean(
                new SolrEntity(
                        "111",
                        "222",
                        new ObjectMapper().writeValueAsString(coordinateList)
                )
        );
        solrClient.commit();

        WebserverCsmr webserverCsmr = new WebserverCsmr(
                ch,
                connectionFactory,
                solrContainer.getHost() + ":" + solrContainer.getSolrPort(),
                new JedisPooled(redisContainer.getHost(), 6379)
        );
        ch.basicConsume(
                "webserver-queue-webserver-app",
                false,
                webserverCsmr);

        // create a message ( this would be written by the webserver app )
        assertTrue(ContainerUtility.rabbitContainer.execInContainer(
                        "rabbitmqadmin",
                        "publish",
                        "exchange=webserver-exchange",
                        "routing_key=86",
                        "payload={\"solrEntityScheduledRun_UUID\":\"111\",\"solrEntityCoordinatesList_UUID\":\"222\",\"numTriesFindingSolrRecord\":333}"
                )
                .getStdout().contains("Message published"));


        boolean found = false;
        int counter = 0;
        while (!found && counter < 1000) {
            SolrQuery query = new SolrQuery();

            query.set("q", "schedule_uuid:111");
            solrClient = new HttpSolrClient.Builder("http://" + solrContainer.getHost() + ":" + solrContainer.getSolrPort() +
                    "/solr/schedules").build();

            if (solrClient.query(query).getResults().getNumFound() == 1){
                found = true;
            }
            counter += 1;
        }
        assertTrue(found == true);
    }
}
