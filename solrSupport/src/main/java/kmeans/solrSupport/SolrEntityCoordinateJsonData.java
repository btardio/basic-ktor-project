package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SolrEntityCoordinateJsonData {

	@JsonProperty("numPoints")
	int numPoints = 0;

	@JsonProperty("filename")
	String filename;


	@JsonProperty("height")
	Integer height = 0;

	@JsonProperty("width")
	Integer width = 0;


	@JsonProperty("coordinates")
	List<Coordinate> coordinates = new ArrayList<>();

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

	public int getNumPoints() {
		return numPoints;
	}

	public void setNumPoints(int numPoints) {
		this.numPoints = numPoints;
	}

	public void setNumPoints(String numPoints) {
		try {
			this.numPoints = Integer.parseInt(numPoints);
		} catch ( NumberFormatException e) {
			this.numPoints = 0;
		}
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public List<Coordinate> getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(List<Coordinate> coordinates) {
		this.coordinates = coordinates;
		this.numPoints = coordinates.size();
	}
}
