package cc.rsu;

import java.util.Arrays;

public class RSUBoundingbox {
	public static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;
	public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
	/**
	 * RSU location received from V2xhub. The location is described with latitude
	 * and longitude
	 **/
	private RSULocation centerLoc;
	/**
	 * Determine the size of the bounding box
	 */
	private double boundingBoxRadius;
	/**
	 * Bounding Box coordinates (latitude and longitude in degree) around the center
	 * location of the RSU
	 ***/
	private double[] boundingBoxLatLngCoordinates;
	/***
	 * BoundingBox around the center location of RSU
	 */
	private BoundingBox boundingBox;

	public RSUBoundingbox(RSULocation centerLoc, double radius) {
		super();
		GeodesicCartesianConverter converter = new GeodesicCartesianConverter();
		this.boundingBoxRadius = radius;
		this.centerLoc = centerLoc;
		this.boundingBoxLatLngCoordinates = BoundingBoxUtils
				.calculateBoundingBoxCoordinates(new Position(centerLoc.latitude, centerLoc.longitude), radius);
		Position locP = new Position(this.boundingBoxLatLngCoordinates[0], this.boundingBoxLatLngCoordinates[1]);
		Point p = converter.geodesic2Cartesian(locP);
		Position locQ = new Position(this.boundingBoxLatLngCoordinates[2], this.boundingBoxLatLngCoordinates[3]);
		Point q = converter.geodesic2Cartesian(locQ);
		this.boundingBox = new BoundingBox(p, q);
	}

	public RSULocation getCenterLoc() {
		return centerLoc;
	}

	public void setCenterLoc(RSULocation centerLoc) {
		this.centerLoc = centerLoc;
	}

	public double getBoundingBoxRadius() {
		return boundingBoxRadius;
	}

	public void setBoundingBoxRadius(double boundingBoxRadius) {
		this.boundingBoxRadius = boundingBoxRadius;
	}

	public double[] getBoundingBoxLatLngCoordinates() {
		return boundingBoxLatLngCoordinates;
	}

	public BoundingBox getBoundingBox() {
		return boundingBox;
	}

	@Override
	public String toString() {
		return "RSUBoundingbox [centerLoc=" + centerLoc + ", boundingBoxRadius=" + boundingBoxRadius
				+ ", boundingBoxLatLngCoordinates=" + Arrays.toString(boundingBoxLatLngCoordinates) + ", boundingBox="
				+ boundingBox + "]";
	}

}
