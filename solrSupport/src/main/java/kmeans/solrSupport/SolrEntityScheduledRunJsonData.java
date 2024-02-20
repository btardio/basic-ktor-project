package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.UUID;

public class SolrEntityScheduledRunJsonData {
//	UUID schedule_uuid = UUID.randomUUID();
//	UUID coordinates_uuid;
//	Timestamp startTime;
//
//	Timestamp endTime;
//
//
//
	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public int getNumberPoints() {
		return numberPoints;
	}

	public void setNumberPoints(int numberPoints) {
		this.numberPoints = numberPoints;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@JsonProperty("numberPoints")
	int numberPoints;
//
	@JsonProperty("status")
	String status;

	@JsonProperty("filename")
	String filename;

	@JsonProperty("height")
	Integer height = 0;

	@JsonProperty("width")
	Integer width = 0;

	public Integer getHeight() {
		return height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public Integer getWidth() {
		return width;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}
//
//
//	public SolrEntityScheduledRunJsonData(SolrEntityCoordinateJsonData coordinates) {
//		this.coordinates_uuid = coordinates.getCoordinate_uuid();
//	}
////
//	public UUID getSchedule_uuid() {
//		return schedule_uuid;
//	}
//
//	public UUID getCoordinate_uuid() {
//		return coordinates_uuid;
//	}



	//
//	public void setUuid(UUID uuid) {
//		this.schedule_uuid = uuid;
//	}
//
//	public Timestamp getStartTime() {
//		return startTime;
//	}
//
//	public void setStartTime(Timestamp startTime) {
//		this.startTime = startTime;
//	}
//
//	public Timestamp getEndTime() {
//		return endTime;
//	}
//
//	public void setEndTime(Timestamp endTime) {
//		this.endTime = endTime;
//	}
//
//
//	public UUID getCoordinatesUUID() {
//		return coordinates_uuid;
//	}
//
//	public void setCoordinatesUUID(UUID coordinatesUUID) {
//		this.coordinates_uuid = coordinatesUUID;
//	}
//
//	public int getNumberPoints() {
//		return numberPoints;
//	}
//
//	public void setNumberPoints(int numberPoints) {
//		this.numberPoints = numberPoints;
//	}
//
//
//	public String getStatus() {
//		return status;
//	}
//
//	public void setStatus(String status) {
//		this.status = status;
//	}

}
