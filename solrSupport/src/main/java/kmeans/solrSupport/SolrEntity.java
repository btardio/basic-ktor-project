package kmeans.solrSupport;

import org.apache.solr.client.solrj.beans.Field;

import java.util.UUID;


public class SolrEntity {

	@Field("uuid")
	String uuid;
	@Field("jsonData")
	String jsonData;

	@Override
	public String toString() {
		return "ExampleClass{" +
				", uuid='" + uuid + '\'' +
				", jsonData='" + jsonData + '\'' +
				'}';
	}

	public SolrEntity(String uuid, String jsonData) {
		this.uuid = uuid;
		this.jsonData = jsonData;
	}

	public SolrEntity(UUID uuid, String jsonData) {
		this.uuid = uuid.toString();
		this.jsonData = jsonData;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public void setJsonData(String jsonData) {
		this.jsonData = jsonData;
	}

	public String getUuid() {
		return this.uuid;
	}

	public String getJsonData() {
		return this.jsonData;
	}

}
