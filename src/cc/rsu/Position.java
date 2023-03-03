package cc.rsu;

import java.util.Objects;
/***
 * Position class to represent latitude, longitude, and altitude coordinates
 */
public class Position {
	private double latitude;
	private double longitude;
	private double altitude = 0;

	@Override
	public int hashCode() {
		return Objects.hash(latitude, longitude);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Position other = (Position) obj;
		return latitude == other.latitude && longitude == other.longitude;
	}

	public Position() {
		super();
	}

	public Position(double latitude, double longitude) {
		super();
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public Position(double latitude, double longitude, double altitude) {
		super();
		this.latitude = latitude;
		this.longitude = longitude;
		this.altitude = altitude;
	}

	public double getAltitude() {
		return altitude;
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getlongitude() {
		return longitude;
	}

	public void setlongitude(double longitude) {
		this.longitude = longitude;
	}

	@Override
	public String toString() {
		return "Position [latitude=" + latitude + ", longitude=" + longitude + "]";
	}

}
