package kmeans.solrSupport;

import ch.qos.logback.classic.LoggerContext;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.exit;
import static kmeans.support.ContextCloseExit.closeContextExit;

public class SolrUtility {


    private static final Logger log = LoggerFactory.getLogger(SolrUtility.class);

    public static void pingCollection(SolrClient client, String collectionName) {

        SolrPingResponse pingResponse = null;
        try {
            pingResponse = client.ping();
        } catch (Exception e) {
            log.error("Unable to ping collection " + collectionName, e);

            closeContextExit(-1);
        }
        if (pingResponse.getStatus() != 0){
            log.error("Unable to ping collection " + collectionName);
            closeContextExit(-1);
        };
    }
}
