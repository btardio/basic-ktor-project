package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SolrEntityCoordinateJsonData {

	@JsonProperty("coordinates")
	List<Coordinate> coordinates = new ArrayList<>();

	@JsonProperty("numPoints")
	int numPoints = 0;

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

	public List<Coordinate> getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(List<Coordinate> coordinates) {
		this.coordinates = coordinates;
		this.numPoints = coordinates.size();
	}
}
