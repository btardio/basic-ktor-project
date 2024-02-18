package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.solr.client.solrj.beans.Field;

import java.util.Date;
import java.util.UUID;

public class SolrEntity {

	@Field("schedule_uuid")
	String schedule_uuid;
	@Field("coordinate_uuid")
	String coordinate_uuid;
	@Field("jsonData")
	String jsonData;
	@Field("timestamp")
	long timestamp = new Date().getTime();

	@Override
	public String toString() {
		return "SolrEntity{" +
				"schedule_uuid='" + schedule_uuid + '\'' +
				", coordinate_uuid='" + coordinate_uuid + '\'' +
				", jsonData='" + jsonData + '\'' +
				", timestamp=" + timestamp +
				'}';
	}

	public SolrEntity(String schedule_uuid, String coordinate_uuid, String jsonData, long timestamp) {
		this.schedule_uuid = schedule_uuid;
		this.coordinate_uuid = coordinate_uuid;
		this.jsonData = jsonData;
		this.timestamp = timestamp;
	}

	public SolrEntity(){
		super();
	}

	public SolrEntity(String schedule_uuid, String coordinate_uuid, String jsonData) {
		this.schedule_uuid = schedule_uuid;
		this.coordinate_uuid = coordinate_uuid;
		this.jsonData = jsonData;
	}

	public SolrEntity(UUID schedule_uuid, UUID coordinate_uuid, String jsonData) {
		this.schedule_uuid = schedule_uuid.toString();
		this.coordinate_uuid = coordinate_uuid.toString();
		this.jsonData = jsonData;
	}

//	public SolrEntity(UUID schedule_uuid, String jsonData) {
//		this.schedule_uuid = schedule_uuid.toString();
//		this.jsonData = jsonData;
//	}

	public String getCoordinate_uuid() {
		return coordinate_uuid;
	}

	public void setCoordinate_uuid(String coordinate_uuid) {
		this.coordinate_uuid = coordinate_uuid;
	}

	public void setSchedule_uuid(String schedule_uuid) {
		this.schedule_uuid = schedule_uuid;
	}

	public void setJsonData(String jsonData) {
		this.jsonData = jsonData;
	}

	public String getSchedule_uuid() {
		return this.schedule_uuid;
	}


	public String getJsonData() {
		return this.jsonData;
	}

}
