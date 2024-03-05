package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.UUID;

public class SolrEntityScheduledRunJsonData {

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


}
