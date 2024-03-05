package kmeans.analyzer;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.images.AbstractImagePullPolicy;
import org.testcontainers.images.ImageData;
import org.testcontainers.utility.DockerImageName;

abstract class ContainerUtility {

    public static final SolrContainer solrContainer;
    public static final RabbitMQContainer rabbitContainer;

    static {
        solrContainer = new SolrContainer("solr:8.7.0");
        solrContainer.start();
//
        rabbitContainer = new RabbitMQContainer("rabbitmq:3.12-management-alpine");
        rabbitContainer.addExposedPort(15672);
        rabbitContainer.start();
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