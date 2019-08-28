package cc.util;

import cc.geosrv.Proj;


public abstract class Geo
{
	public final static double EARTH_MINOR_RADIUS = 6356752.0; // in meters
	public final static double EARTH_MAJOR_RADIUS = 6378137.0; // in meters
	public final static double EARTH_FLATTENING = EARTH_MINOR_RADIUS / EARTH_MAJOR_RADIUS;


	public static double[] addPoint(double dX, double dY, double[] dPts, Proj oProj, double[] dPoint)
	{
		oProj.cs2cs(dX, dY, dPoint);
		dX = dPoint[0]; // transform coordinates
		dY = dPoint[1]; // and set local reference
		dPts = Arrays.add(dPts, dX, dY); // append new point and update bounds
		if (dX < dPts[1])
			dPts[1] = dX;
		if (dY < dPts[2])
			dPts[2] = dY;
		if (dX > dPts[3])
			dPts[3] = dX;
		if (dY > dPts[4])
			dPts[4] = dY;
		return dPts;
	}


	private Geo()
	{
	}


	public static int scale(int nVal)
	{
		return (int)Math.floor(((double)nVal) / 100000.0); // OSM geo-coordinates have 7 decimal places
	}


	public static int getHash(int nLat, int nLon)
	{
		return (scale(nLon) << 16) + scale(nLat);
	}


	public static double fromIntDeg(int nOrd)
	{
		return ((double)nOrd) / 10000000.0;
	}


	public static int toIntDeg(double dOrd)
	{
		return (int)(dOrd * 10000000.0);
	}


	public static double toMeters(int nOrd)
	{
		return ((double)nOrd) / 100.0;
	}


	public static double distance(int nXi, int nYi, int nXj, int nYj)
	{
		return distance(toMeters(nXi), toMeters(nYi), toMeters(nXj), toMeters(nYj));
	}


	public static double distance(double dXi, double dYi, double dXj, double dYj)
	{
		double dXd = dXj - dXi; // correct distance by latitude
		dXd = (dXd * Math.cos(Math.toRadians(dYi / 100000.0)) + 
			dXd * Math.cos(Math.toRadians(dYj / 100000.0))) / 2.0;
		double dYd = (dYj - dYi) * EARTH_FLATTENING;
		return Math.sqrt(dXd * dXd + dYd * dYd);
	}
	
	
	
	
	public static boolean boundingBoxesIntersect(double dXmin1, double dYmin1, double dXmax1, double dYmax1, double dXmin2, double dYmin2, double dXmax2, double dYmax2)
	{
		return dYmax1 >= dYmin2 && dYmin1 <= dYmax2 && dXmax1 >= dXmin2 && dXmin1 <= dXmax2;
	}
		
	
	/**
	 * Determines if the specified point is within the specified boundary. A
	 * specified tolerance adjusts the compared region as needed.
	 *
	 * @param nX x coordinate of point
	 * @param nY y coordinate of point
	 * @param nT y value of the top of the region
	 * @param nR x value of the right side of the region
	 * @param nB y value of the bottom of the region
	 * @param nL x value of the left side of the region
	 * @param nTol the allowed margin for a point to be considered inside
	 * @return true if the point is inside or on the rectangular region
	 */
	public static boolean isInside(int nX, int nY,
	   int nT, int nR, int nB, int nL, int nTol)
	{
		if (nR < nL) // swap the left and right bounds as needed
		{
			nR ^= nL;
			nL ^= nR;
			nR ^= nL;
		}

		if (nT < nB) // swap the top and bottom bounds as needed
		{
			nT ^= nB;
			nB ^= nT;
			nT ^= nB;
		}

		// expand the bounds by the tolerance
		return (nX >= nL - nTol && nX <= nR + nTol
		   && nY >= nB - nTol && nY <= nT + nTol);
	}


	/**
	 * Determines if the specified point is within the specified boundary. A
	 * specified tolerance adjusts the compared region as needed
	 *
	 * @param dX x coordinate of point
	 * @param dY y coordinate of point
	 * @param dT y value of the top of the region
	 * @param dR x value of the right side of the region
	 * @param dB y value of the bottom of the region
	 * @param dL x value of the left side of the region
	 * @param dTol the allowed margin for a point to be considered inside
	 * @return true if the point is inside or on the rectangular region
	 */
	public static boolean isInside(double dX, double dY, double dT, double dR,
	   double dB, double dL, double dTol)
	{
		return (dX >= dL - dTol && dX <= dR + dTol
		   && dY >= dB - dTol && dY <= dT + dTol);
	}
}
