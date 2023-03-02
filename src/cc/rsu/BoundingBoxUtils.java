package cc.rsu;

public class BoundingBoxUtils {

	/***
	 * Calculate the bounding box geodetic coordinate around the center of the RSU
	 * location
	 * 
	 * @param centerLoc the geodetic coordinate
	 * @param radius    The bounding box size
	 * @return geodetic coordinates around the center location
	 */
	public static double[] calculateBoundingBoxCoordinates(Position centerLoc, double radius) {
		double DEGREES_TO_RADIANS = Math.PI / 180.0;
		double RADIANS_TO_DEGREES = 180.0 / Math.PI;
		double[] result = new double[4];
		double latRad = Math.toRadians(centerLoc.getLatitude());
		double lonRad = Math.toRadians(centerLoc.getlongitude());
		;
		double earth_radius = calculate_earth_radius(latRad);
		double p_radius = earth_radius * Math.cos(latRad);
		double latRad_min = latRad - radius / earth_radius;
		double latRad_max = latRad + radius / earth_radius;
		double lonRad_min = lonRad - radius / p_radius;
		double lonRad_max = lonRad + radius / p_radius;
		result[0] = RADIANS_TO_DEGREES * latRad_min;
		result[1] = RADIANS_TO_DEGREES * lonRad_min;
		result[2] = RADIANS_TO_DEGREES * latRad_max;
		result[3] = RADIANS_TO_DEGREES * lonRad_max;
		return result;
	}

	/***
	 * Calculate earth radius
	 * 
	 * @param latRad latitude in radian
	 * @return earth radius
	 */
	public static double calculate_earth_radius(double latRad) {
		double wgsa = 6378137.0; // meter
		double wgsb = 6356752.314; // meter
		double f1 = Math.pow((Math.pow(wgsa, 2) * Math.cos(latRad)), 2);
		double f2 = Math.pow((Math.pow(wgsb, 2) * Math.sin(latRad)), 2);
		double f3 = Math.pow((wgsa * Math.cos(latRad)), 2);
		double f4 = Math.pow((wgsb * Math.sin(latRad)), 2);
		double earth_radius = Math.sqrt((f1 + f2) / (f3 + f4));
		return earth_radius;
	}
}
