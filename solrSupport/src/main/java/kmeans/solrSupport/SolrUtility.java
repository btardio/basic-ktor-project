package kmeans.solrSupport;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.exit;

public class SolrUtility {
    private static final Logger log = LoggerFactory.getLogger(SolrUtility.class);
    public static void pingCollection(SolrClient client, String collectionName) {
        SolrPingResponse pingResponse = null;
        try {
            pingResponse = client.ping(collectionName);
        } catch (Exception e) {
            log.error("Unable to ping collection " + collectionName, e);
            exit(-1);
        }
        if (pingResponse.getStatus() != 0){
            log.error("Unable to ping collection " + collectionName);
            exit(-1);
        };
    }
}
