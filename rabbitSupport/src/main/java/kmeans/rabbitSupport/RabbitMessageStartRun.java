package kmeans.rabbitSupport;

import com.fasterxml.jackson.annotation.JsonProperty;
import kmeans.solrSupport.SolrEntityCoordinateJsonData;
import kmeans.solrSupport.SolrEntityScheduledRunJsonData;

public class RabbitMessageStartRun {
	@JsonProperty("solrEntityScheduledRun_UUID")
	String solrEntityScheduledRun_UUID;

	@JsonProperty("solrEntityCoordinatesList_UUID")
	String solrEntityCoordinatesList_UUID;

	@JsonProperty("numTriesFindingSolrRecord")
	public int numTriesFindingSolrRecord = 0;

	// number of treis to find in solr
	public int getNumTriesFindingSolrRecord() {
		return numTriesFindingSolrRecord;
	}

	public void setNumTriesFindingSolrRecord(int numTriesFindingSolrRecord) {
		this.numTriesFindingSolrRecord = numTriesFindingSolrRecord;
	}

	public RabbitMessageStartRun() {

	}
	public RabbitMessageStartRun(String solrEntityScheduledRun_UUID, String solrEntityCoordinatesList_UUID) {
		this.solrEntityScheduledRun_UUID = solrEntityScheduledRun_UUID;
		this.solrEntityCoordinatesList_UUID = solrEntityCoordinatesList_UUID;
	}
	public String getSolrEntityScheduledRun_UUID() {
		return solrEntityScheduledRun_UUID;
	}

	public void setSolrEntityScheduledRun_UUID(String solrEntityScheduledRun_UUID) {
		this.solrEntityScheduledRun_UUID = solrEntityScheduledRun_UUID;
	}

	public String getSolrEntityCoordinatesList_UUID() {
		return solrEntityCoordinatesList_UUID;
	}

	public void setSolrEntityCoordinatesList_UUID(String solrEntityCoordinatesList_UUID) {
		this.solrEntityCoordinatesList_UUID = solrEntityCoordinatesList_UUID;
	}
}
