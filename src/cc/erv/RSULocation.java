package cc.erv;

import java.time.Instant;

/**
 * RSU location class define the GEO location of the RSU and its connected
 * V2xhub communication port
 */
public class RSULocation {
	// Unique identifier
	public String id;
	// RSU Geo location
	public long latitude;
	public long longitude;
	// Identify the v2xhub that this RSU is connected to
	public String v2xhub_port;
	//Timestamp to record RSU location update
	public Instant last_update_at;

	RSULocation() {

	}

	@Override
	public String toString() {
		return "RSULocation [id=" + id + ", latitude=" + latitude + ", longitude=" + longitude + ", v2xhub_port="
				+ v2xhub_port + "]";
	}
}
