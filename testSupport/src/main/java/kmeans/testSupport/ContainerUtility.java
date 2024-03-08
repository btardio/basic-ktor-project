package kmeans.testSupport;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class ContainerUtility {

    public static final SolrContainer solrContainer;
    public static final RabbitMQContainer rabbitContainer;

    public static final GenericContainer redisContainer;

    static {
        solrContainer = new SolrContainer("solr:8.7.0");
        solrContainer.start();
//
        rabbitContainer = new RabbitMQContainer("rabbitmq:3.12-management-alpine");
        rabbitContainer.addExposedPort(15672);
        rabbitContainer.start();

        redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine"))
                .withExposedPorts(6379);
    }

}



//                .withImagePullPolicy(
//                new AbstractImagePullPolicy() {
//                    @Override
//                    protected boolean shouldPullCached(DockerImageName imageName, ImageData localImageData) {
//                        // Set to true if you change the exchange type direct -> x-consistent-hash etc
//                        return false;
//                    }
//                }