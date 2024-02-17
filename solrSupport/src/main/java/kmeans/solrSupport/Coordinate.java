package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class Coordinate {
	@JsonProperty("x")
	Double x;
	@JsonProperty("y")
	Double y;
	@JsonProperty("z")
	Double z;

	public Coordinate() {

	}

	public Coordinate(Double x, Double y, Double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Double getX() {
		return x;
	}

	public void setX(Double x) {
		this.x = x;
	}

	public Double getY() {
		return y;
	}

	public void setY(Double y) {
		this.y = y;
	}

	public Double getZ() {
		return z;
	}

	public void setZ(Double z) {
		this.z = z;
	}
}
