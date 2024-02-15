package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SolrEntityCoordinateJsonData {

	@JsonProperty("coordinates")
	List<Coordinate> coordinates = new ArrayList<>();

	public List<Coordinate> getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(List<Coordinate> coordinates) {
		this.coordinates = coordinates;
	}
}
