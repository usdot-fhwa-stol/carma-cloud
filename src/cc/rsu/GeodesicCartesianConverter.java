package cc.rsu;

public class GeodesicCartesianConverter {
	protected final double Rea = 6378137.0; // Semi-major axis radius meters
	protected final double Rea_sqr = Rea * Rea;
	protected final double f = 1.0 / 298.257223563; // The flattening factor
	protected final double Reb = Rea * (1.0 - f); // //The semi-minor axis = 6356752.0
	protected final double Reb_sqr = Reb * Reb;
	protected final double e = 0.08181919084262149; // The first eccentricity (hard coded as optimization) calculated as
													// Math.sqrt(Rea*Rea - Reb*Reb) / Rea;
	protected final double e_sqr = e * e;
	protected final double e_p = 0.08209443794969568; // e prime (hard coded as optimization) calculated as
														// Math.sqrt((Rea_sqr - Reb_sqr) / Reb_sqr);
	public static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;
	public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;

	/**
	 * Converts a given 3d cartesian point into a WSG-84 geodesic location The
	 * provided point should be defined relative to a frame which has a transform
	 * with the ECEF
	 * 
	 * @param point               The cartesian point to be converted
	 * @param ecef2frameTransform The transform which defines the position of the
	 *                            desired frame relative to the ECEF frame.
	 * @return The calculated WSG-84 geodesic location
	 */
	public Position cartesian2Geodesic(Point point) {
		double x = point.getX();
		double y = point.getY();
		double z = point.getZ();
//	    // Calculate lat,lon,alt
		double p = Math.sqrt((x * x) + (y * y));
		// Handle special case of poles
		if (p < 1.0e-10) {
			double poleLat = z < 0 ? -90 : 90;
			double poleLon = 0;
			double poleAlt = z < 0 ? -z - Reb : z - Reb;
			return new Position(poleLat, poleLon, poleAlt);
		}
		double theta = Math.atan((z * Rea) / (p * Reb));

		double lon = 2.0 * Math.atan(y / (x + p));
		double lat = Math.atan((z + (e_p * e_p) * Reb * Math.pow(Math.sin(theta), 3))
				/ (p - e_sqr * Rea * Math.pow(Math.cos(theta), 3)));

		double cosLat = Math.cos(lat);
		double sinLat = Math.sin(lat);

		double N = Rea_sqr / Math.sqrt(Rea_sqr * cosLat * cosLat + Reb_sqr * sinLat * sinLat);
		double alt = (p / cosLat) - N;

		return new Position(Math.toDegrees(lat), Math.toDegrees(lon), alt);
	}

	/**
	 * Converts a given WSG-84 geodesic location into a 3d cartesian point The
	 * returned 3d point is defined relative to a frame which has a transform with
	 * the ECEF frame.
	 * 
	 * @param location            The geodesic location to convert
	 * @param frame2ecefTransform A transform which defines the location of the ECEF
	 *                            frame relative to the 3d point's frame of origin
	 * @return The calculated 3d point
	 */
	public Point geodesic2Cartesian(Position location) {
		// frame2ecefTransform needs to define the position of the ecefFrame relative to
		// the desired frame
		// Put geodesic in proper units
		double lonRad = Math.toRadians(location.getlongitude());
		double latRad = Math.toRadians(location.getLatitude());
		double alt = location.getAltitude();

		double sinLat = Math.sin(latRad);
		double sinLon = Math.sin(lonRad);
		double cosLat = Math.cos(latRad);
		double cosLon = Math.cos(lonRad);

		double Ne = Rea / Math.sqrt(1.0 - e_sqr * sinLat * sinLat);// The prime vertical radius of curvature
		double x = (Ne + alt) * cosLat * cosLon;
		double y = (Ne + alt) * cosLat * sinLon;
		double z = (Ne * (1 - e_sqr) + alt) * sinLat;
		return new Point(x, y, z);
	}
}
