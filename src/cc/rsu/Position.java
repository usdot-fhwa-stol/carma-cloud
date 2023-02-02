package cc.rsu;

import java.util.Objects;

public class Position {
	public long latitude;
	public long longitude;

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

	public Position(long latitude, long longitude) {
		super();
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public long getLatitude() {
		return latitude;
	}

	public void setLatitude(long latitude) {
		this.latitude = latitude;
	}

	public long getLongitude() {
		return longitude;
	}

	public void setLongitude(long longitude) {
		this.longitude = longitude;
	}

	@Override
	public String toString() {
		return "Position [latitude=" + latitude + ", longitude=" + longitude + "]";
	}

}
