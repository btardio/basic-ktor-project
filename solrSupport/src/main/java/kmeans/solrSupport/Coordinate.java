package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Coordinate {
	@JsonProperty("x")
	BigDecimal x;
	@JsonProperty("y")
	BigDecimal y;
	@JsonProperty("z")
	BigDecimal z;

	public Coordinate() {

	}

	@Override
	public String toString() {
		return "Coordinate{" +
				"x=" + x +
				", y=" + y +
				", z=" + z +
				'}';
	}

	public Coordinate(Double x, Double y, Double z) {
		this.x = new BigDecimal(x).setScale(3, RoundingMode.FLOOR);
		this.y = new BigDecimal(y).setScale(3, RoundingMode.FLOOR);
		this.z = new BigDecimal(z).setScale(3, RoundingMode.FLOOR);
	}

	public BigDecimal getX() {
		return x;
	}

	public void setX(BigDecimal x) {
		this.x = x;
	}

	public BigDecimal getY() {
		return y;
	}

	public void setY(BigDecimal y) {
		this.y = y;
	}

	public BigDecimal getZ() {
		return z;
	}

	public void setZ(BigDecimal z) {
		this.z = z;
	}






	public Double getXD() {
		return x.doubleValue();
	}

//	public void setXD(BigDecimal x) {
//		this.x = x;
//	}

	public double getYD() {
		return y.doubleValue();
	}

//	public void setYD(BigDecimal y) {
//		this.y = y;
//	}

	public double getZD() {
		return z.doubleValue();
	}

//	public void setZD(BigDecimal z) {
//		this.z = z;
//	}
}
