package cc.util;

import cc.ctrl.CtrlLineArcs;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Paths;
import java.util.Iterator;


public abstract class Geo
{
	public final static double EARTH_MINOR_RADIUS = 6356752.0; // in meters
	public final static double EARTH_MAJOR_RADIUS = 6378137.0; // in meters
	public final static double EARTH_FLATTENING = EARTH_MINOR_RADIUS / EARTH_MAJOR_RADIUS;
	public final static double CIRCLE_TOL = 0.001;
	public final static double M_TOL = 1.0E-10;

	private Geo()
	{
	}


	public static double[] reverseOrder(double[] dPoly)
	{
		int nLimit = Arrays.size(dPoly);
		double[] dRet = Arrays.newDoubleArray(nLimit);
		for (int i = nLimit - 2; i >= 1; i -= 2)
			dRet = Arrays.add(dRet, dPoly[i], dPoly[i + 1]);
	
		return dRet;
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
		return Math.sqrt(sqDist(dXi, dYi, dXj, dYj));
//		double dXd = dXj - dXi; // correct distance by latitude
////		dXd = (dXd * Math.cos(Math.toRadians(dYi / 100000.0)) + 
////			dXd * Math.cos(Math.toRadians(dYj / 100000.0))) / 2.0;
////		double dYd = (dYj - dYi) * EARTH_FLATTENING;
//		double dYd = dYj - dYi;
//		return Math.sqrt(dXd * dXd + dYd * dYd);
	}
	
	
	public static double sqDist(double dXi, double dYi, double dXj, double dYj)
	{
		double dXd = dXj - dXi;
		double dYd = dYj - dYi;
		return dXd * dXd + dYd * dYd;
	}
	

	public static boolean boundingBoxesIntersect(double[] dBb1, double[] dBb2)
	{
		return boundingBoxesIntersect(dBb1[0], dBb1[1], dBb1[2], dBb1[3], dBb2[0], dBb2[1], dBb2[2], dBb2[3]);
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
	
	
	public static boolean isInBoundingBox(double dX, double dY, double dX1, double dY1, double dX2, double dY2)
	{
		if (dX1 > dX2)
		{
			double dTemp = dX1;
			dX1 = dX2;
			dX2 = dTemp;
		}
		
		if (dY1 > dY2)
		{
			double dTemp = dY1;
			dY1 = dY2;
			dY2 = dTemp;
		}
		
		return dX >= dX1 && dX <= dX2 && dY >= dY1 && dY <= dY2;
	}
	
	public static boolean polylineInside(int[] nPolygon, int[] nPolyline)
	{
		Iterator<int[]> oIt = Arrays.iterator(nPolyline, new int[2], 1, 2);
		while (oIt.hasNext())
		{
			int[] nPt = oIt.next();
			if (isInsidePolygon(nPolygon, nPt[0], nPt[1]))
				return true;
		}
		
		return false;
	}
	
		/**
	 * Determines if a given x,y coordinate is inside the polygon defined by the
	 * array.
	 *
	 * @param nPolyPoints array of coordinates that define a polygon. Order is
	 * [x1, y1, x2, y2, ..., x1, y1]
	 * @param nX x coordinate of the point
	 * @param nY y coordinate of the point
	 * @return true if the point is inside the polygon, otherwise false
	 */
	public static boolean isInsidePolygon(int[] nPolyPoints, double nX, double nY)
	{
		int nCount = 0;
		int nEnd = nPolyPoints.length - 2;
		for (int nPos = 0; nPos < nEnd; nPos += 2)
		{
			int nX1 = nPolyPoints[nPos];
			int nX2 = nPolyPoints[nPos + 2];
			int nY1 = nPolyPoints[nPos + 1];
			int nY2 = nPolyPoints[nPos + 3];

			if ((nY1 < nY && nY2 >= nY || nY2 < nY && nY1 >= nY)
			   && (nX1 <= nX || nX2 <= nX)
			   && (nX1 + (nY - nY1) / (nY2 - nY1) * (nX2 - nX1) < nX))
				++nCount;
		}
		return (nCount & 1) != 0;
	}
	
	
	public static boolean polylineInside(double[] dPolygon, double[] dPolyline)
	{
		Iterator<double[]> oIt = Arrays.iterator(dPolyline, new double[2], 1, 2);
		while (oIt.hasNext())
		{
			double[] nPt = oIt.next();
			if (isInsidePolygon(dPolygon, nPt[0], nPt[1]))
				return true;
		}
		
		return false;
	}
	
		/**
	 * Determines if a given x,y coordinate is inside the polygon defined by the
	 * array.
	 *
	 * @param dPolyPoints array of coordinates that define a polygon. Order is
	 * [x1, y1, x2, y2, ..., x1, y1]
	 * @param dX x coordinate of the point
	 * @param dY y coordinate of the point
	 * @return true if the point is inside the polygon, otherwise false
	 */
	public static boolean isInsidePolygon(double[] dPolyPoints, double dX, double dY)
	{
		int nCount = 0;
		int nEnd = dPolyPoints.length - 2;
		for (int nPos = 0; nPos < nEnd; nPos += 2)
		{
			double dX1 = dPolyPoints[nPos];
			double dX2 = dPolyPoints[nPos + 2];
			double dY1 = dPolyPoints[nPos + 1];
			double dY2 = dPolyPoints[nPos + 3];

			if ((dY1 < dY && dY2 >= dY || dY2 < dY && dY1 >= dY)
			   && (dX1 <= dX || dX2 <= dX)
			   && (dX1 + (dY - dY1) / (dY2 - dY1) * (dX2 - dX1) < dX))
				++nCount;
		}
		return (nCount & 1) != 0;
	}
	
	
	
	public static double polylineDist(double[] dPolyline, int nStart, int nStep)
	{
		Iterator<double[]> oIt = Arrays.iterator(dPolyline, new double[nStep * 2], nStart, nStep);
		double dTotalDist = 0.0;
		while (oIt.hasNext())
		{
			double[] dSeg = oIt.next();
			dTotalDist += distance(dSeg[0], dSeg[1], dSeg[nStep], dSeg[nStep + 1]);
		}
		
		return dTotalDist;
	}
	
	
	public static double distAlongLine(double[] dPts, double[] dSeg, double dX, double dY)
	{
		double dDist = 0.0;
		Iterator<double[]> oIt = Arrays.iterator(dPts, dSeg, 1, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			if (isInBoundingBox(dX, dY, dSeg[1], dSeg[0], dSeg[3], dSeg[2]) && collinear(dX, dY, dSeg[1], dSeg[0], dSeg[3], dSeg[2]))
			{
				dDist += distance(dX, dY, dSeg[1], dSeg[0]);
				return dDist;
			}
			dDist += distance(dSeg[1], dSeg[0], dSeg[3], dSeg[2]);
		}
		return Double.NaN;
	}
	

	public static boolean collinear(int nX1, int nY1, int nX2, int nY2, int nX3, int nY3, int nTol)
	{
		return Math.abs(nX1 * (nY2 - nY3) + nX2 * (nY3 - nY1) + nX3 * (nY1 - nY2)) < nTol;
	}
	
	
	public static boolean collinear(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3, double dTol)
	{
		return Math.abs(dX1 * (dY2 - dY3) + dX2 * (dY3 - dY1) + dX3 * (dY1 - dY2)) < dTol;
	}
	
	
	public static boolean collinear(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3)
	{
		return collinear(dX1, dY1, dX2, dY2, dX3, dY3, 0.0000001);
	}
	
	
	public static double angle(double dX1, double dY1, double dX2, double dY2)
	{
		return angle(dX1 + 1, dY1, dX1, dY1, dX2, dY2);
	}
	
	
	public static double angle(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3)
	{
		double dUi = dX1 - dX2;
		double dUj = dY1 - dY2;
		double dVi = dX3 - dX2;
		double dVj = dY3 - dY2;
		double dDot = dUi * dVi + dUj * dVj;
		double dLenU = Math.sqrt(dUi * dUi + dUj * dUj);
		double dLenV = Math.sqrt(dVi * dVi + dVj * dVj);
		if (dLenU == 0 || dLenV == 0) // prevent division by zero
			return Double.NaN;
		double dValue = dDot / (dLenU * dLenV);
		dValue = MathUtil.round(dValue, 12); // round to help prevent value outside of the domain of arccos
		if (dValue > 1 || dValue < -1) // prevent domain error for arcos
			return Double.NaN;
		
		return Math.acos(dValue); // return value in radians
	}
	
	
	public static double heading(double dX1, double dY1, double dX2, double dY2)
	{
		double dRads = angle(dX1, dY1, dX2, dY2);
		if (dY1 > dY2)
			dRads = 2 * Math.PI - dRads;
		
		return dRads;
	}
	
	
	public static boolean isClockwise(double[] dPoly, int nStart)
	{
		double dWinding = 0;
		double[] dLine = new double[4];
		Iterator<double[]> oIt = Arrays.iterator(dPoly, dLine, nStart, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			dWinding += (dLine[2] - dLine[0]) * (dLine[3] + dLine[1]);
		}
		dWinding += (dPoly[nStart] - dLine[2]) * (dPoly[nStart + 1] + dLine[3]); // have to calculate the last edge
		return dWinding > 0;
	}
	
	
	public static boolean isClockwise(Area oPoly)
	{
		double[] dCoords = new double[2];
		double[] dPrev = new double[2];
		double[] dFirst = new double[2];
		double dWinding = 0;

		PathIterator oIt = oPoly.getPathIterator(null);
		while (!oIt.isDone()) // first determine which parts of multi-path are polygons and holes
		{
			switch (oIt.currentSegment(dCoords))
			{
				case PathIterator.SEG_MOVETO:
				{
					dWinding = 0;
					System.arraycopy(dCoords, 0, dFirst, 0, 2);
					System.arraycopy(dCoords, 0, dPrev, 0, 2);
					break;
				}
				case PathIterator.SEG_LINETO:
				{
					dWinding += ((dCoords[0] - dPrev[0]) * (dCoords[1] + dPrev[1]));
					System.arraycopy(dCoords, 0, dPrev, 0, 2);
					break;
				}
				case PathIterator.SEG_CLOSE:
				{
					dWinding += ((dFirst[0] - dCoords[0]) * (dFirst[1] + dCoords[1]));
					break;
				}
			}
			oIt.next();
		}
		
		return dWinding > 0;
	}
	
	
		public static double perpDist(double dX, double dY,
	   double dX1, double dY1, double dX2, double dY2)
	{
		double dXd = dX2 - dX1;
		double dYd = dY2 - dY1;
		double dXp = dX - dX1;
		double dYp = dY - dY1;

		if (dXd == 0 && dYd == 0) // line segment is a point
			return Math.sqrt(dXp * dXp + dYp * dYp); // dist between the points

		double dU = dXp * dXd + dYp * dYd;
		double dV = dXd * dXd + dYd * dYd;

		if (dU < 0 || dU > dV) // nearest point is not on the line
		{
			return Double.NaN;
		}


		// find the perpendicular intersection of the point on the line
		dXp = dX1 + (dU * dXd / dV);
		dYp = dY1 + (dU * dYd / dV);

		dXd = dX - dXp; // calculate the distance
		dYd = dY - dYp; // between the point and the intersection
		return Math.sqrt(dXd * dXd + dYd * dYd);
	}
	
	public static double[] combineLines(double[] dPts, double dTol)
	{
		double[] dRet = Arrays.newDoubleArray(Arrays.size(dPts) / 2);
		dRet = Arrays.add(dRet, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		double[] dSeg = new double[9];
		double[] dCurPt = new double[3];
		double[] dCurLine = Arrays.newDoubleArray();
		double[] dEndPts = new double[6];
		double[] dCenter = new double[2];
		Iterator<double[]> oIt = Arrays.iterator(dPts, dSeg, 5, 6);
		while (oIt.hasNext())
		{
			oIt.next();
			double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[3], dSeg[4], dSeg[6], dSeg[7], dCenter); // determine if current segment is line or arc
			if (!Double.isFinite(dR) || dR >= 10000) // current segment is a line
			{
				if (Arrays.size(dCurLine) == 1) // initialize current line and endpoints
				{
					dCurLine = Arrays.add(dCurLine, dSeg[0], dSeg[1]);
					dCurLine = Arrays.add(dCurLine, dSeg[2]);
					dEndPts[0] = dSeg[0];
					dEndPts[1] = dSeg[1];
					dEndPts[2] = dSeg[2];
					dEndPts[3] = dSeg[6];
					dEndPts[4] = dSeg[7];
					dEndPts[5] = dSeg[8];
				}
				dCurLine = Arrays.add(dCurLine, dSeg[3], dSeg[4]); // add the midpoint
				dCurLine = Arrays.add(dCurLine, dSeg[5]);
				dCurLine = Arrays.add(dCurLine, dSeg[6], dSeg[7]); // and the last point of the current seg
				dCurLine = Arrays.add(dCurLine, dSeg[8]);
				int nSize = Arrays.size(dCurLine);
				if (nSize > 10) // if more than 1 line has been added to the current line
				{
					double dX1 = dCurLine[1]; // set start point
					double dY1 = dCurLine[2];
					double dX2 = dCurLine[nSize - 3]; //set end point
					double dY2 = dCurLine[nSize - 2];
					int nCount = 0;
					double dTotalDist = 0.0;
					Iterator<double[]> oCur = Arrays.iterator(dCurLine, dCurPt, 4, 3); // for each point in the current line, starting with the 2nd point
					while (oCur.hasNext())
					{
						oCur.next();
						dTotalDist += perpDist(dCurPt[0], dCurPt[1], dX1, dY1, dX2, dY2);
						++nCount;
					}
					--nCount; // subtract one off because we did the distance of the endpoint which should be zero and we dont want it to skew the average
					if (dTotalDist / nCount <= dTol) // points are still within tolerance, so save endpoints
					{
						dEndPts[0] = dX1;
						dEndPts[1] = dY1;
						dEndPts[2] = dCurLine[3];
						dEndPts[3] = dX2;
						dEndPts[4] = dY2;
						dEndPts[5] = dCurLine[nSize - 1];
					}
					else // points are no longer within tolerance, so use the last saved endpoint to calculate a midpoint and add to the final set of points
					{
						double dHdg = heading(dEndPts[0], dEndPts[1], dEndPts[3], dEndPts[4]);
						double dDist = distance(dEndPts[0], dEndPts[1], dEndPts[3], dEndPts[4]);
						double dMidX = dEndPts[0] + dDist * Math.cos(dHdg);
						double dMidY = dEndPts[1] + dDist * Math.sin(dHdg);
						double dMidW = (dEndPts[2] + dEndPts[5]) / 2;
						dRet = Arrays.addAndUpdate(dRet, dEndPts[0], dEndPts[1]); // only add the first point
						dRet = Arrays.add(dRet, dEndPts[2]);
						dRet = Arrays.addAndUpdate(dRet, dMidX, dMidY); // and the midpoint
						dRet = Arrays.add(dRet, dMidW); // the last point will be added by the next line or arc
						dCurLine[0] = 1; // reset the current line and the endpoints
						dCurLine = Arrays.add(dCurLine, dEndPts[3], dEndPts[4]);
						dCurLine = Arrays.add(dCurLine, dEndPts[5]);
						dEndPts[0] = dEndPts[3];
						dEndPts[1] = dEndPts[4];
						dEndPts[2] = dEndPts[5];
						dEndPts[3] = dSeg[6];
						dEndPts[4] = dSeg[7];
						dEndPts[5] = dSeg[8];
						dCurLine = Arrays.add(dCurLine, dSeg[3], dSeg[4]); // add the midpoint
						dCurLine = Arrays.add(dCurLine, dSeg[5]);
						dCurLine = Arrays.add(dCurLine, dSeg[6], dSeg[7]); // and the last point of the current seg
						dCurLine = Arrays.add(dCurLine, dSeg[8]);
					}
				}
			}
			else // current segment is arc
			{
				if (Arrays.size(dCurLine) >= 10) // save the current line
				{
					double dHdg = heading(dEndPts[0], dEndPts[1], dEndPts[3], dEndPts[4]);
					double dDist = distance(dEndPts[0], dEndPts[1], dEndPts[3], dEndPts[4]) / 2;
					double dMidX = dEndPts[0] + dDist * Math.cos(dHdg);
					double dMidY = dEndPts[1] + dDist * Math.sin(dHdg);
					double dMidW = (dEndPts[2] + dEndPts[5]) / 2;
					dRet = Arrays.addAndUpdate(dRet, dEndPts[0], dEndPts[1]); // only add the first point
					dRet = Arrays.add(dRet, dEndPts[2]);
					dRet = Arrays.addAndUpdate(dRet, dMidX, dMidY); // and the midpoint
					dRet = Arrays.add(dRet, dMidW); // the last point will be added by the next line or arc
				}
				dCurLine[0] = 1; // reset current line
				dRet = Arrays.addAndUpdate(dRet, dSeg[0], dSeg[1]); // add the first point
				dRet = Arrays.add(dRet, dSeg[2]);
				dRet = Arrays.addAndUpdate(dRet, dSeg[3], dSeg[4]); // and the midpoint of arc
				dRet = Arrays.add(dRet, dSeg[5]); // the last point will be added by the next line or arc
			}
		}
		dRet = Arrays.addAndUpdate(dRet, dSeg[6], dSeg[7]); // always add the last point. it will finish the last arc or line
		dRet = Arrays.add(dRet, dSeg[8]);
		return dRet;
	}
	
	
	
	public static double[] combineLineArcs(double[] dPts, double dTol)
	{
		int nLimit = Arrays.size(dPts);
		double[] dRet = Arrays.newDoubleArray(nLimit / 10);
		dRet = Arrays.add(dRet, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		
		int nStart = 5; // start at 5 to skip bounding box and insertion point
		int nEnd = nStart + 6; // each point is 3 ordinates (x,y,w), skip two poitns
		double dPrevDist = Double.NaN;
		while (nEnd < nLimit)
		{
			double dMaxDist = -Double.MAX_VALUE;
			double dX1 = dPts[nStart];
			double dY1 = dPts[nStart + 1];
			double dX2 = dPts[nEnd];
			double dY2 = dPts[nEnd + 1];
			for (int nTest = nStart + 3; nTest < nEnd; nTest += 3)
			{
				double dDist = perpDist(dPts[nTest], dPts[nTest + 1], dX1, dY1, dX2, dY2);
				if (dDist > dMaxDist)
					dMaxDist = dDist;
			}
			if (dMaxDist > dTol)
			{
				nEnd -= 3;
				double dMidX = (dX1 + dX2) / 2;
				double dMidY = (dY1 + dY2) / 2;
				boolean bArc = true;
				int nFirstSide = rightHand(dPts[nStart + 3], dPts[nStart + 4], dX1, dY1, dX2, dY2);
				for (int nTest = nStart + 6; nTest < nEnd; nTest += 3)
				{
					if (rightHand(dPts[nTest], dPts[nTest + 1], dX1, dY1, dX2, dY2) != nFirstSide)
					{
						bArc = false;
						break;
					}
				}
				
				dRet = Arrays.addAndUpdate(dRet, dX1, dY1);
				dRet = Arrays.add(dRet, dPts[nStart + 2]);
				if (bArc)
				{
					double dPerpAngle = heading(dX1, dY1, dX2, dY2) + (nFirstSide * Mercator.PI_OVER_TWO);
					dMidX += dPrevDist * Math.cos(dPerpAngle);
					dMidY += dPrevDist * Math.sin(dPerpAngle);
				}
				
				int nMidWidth = (nEnd - nStart) / 3;
				double dMidWidth;
				if (nMidWidth % 2 == 0)
				{
					dMidWidth = dPts[nStart + nMidWidth / 2 * 3 + 2];
				}
				else
				{
					int nFirstIndex = nStart + nMidWidth / 2 * 3 + 2;
					int nSecondIndex = nFirstIndex + 3;
					dMidWidth = (dPts[nFirstIndex] + dPts[nSecondIndex]) / 2;
				}
				dRet = Arrays.addAndUpdate(dRet, dMidX, dMidY);
				dRet = Arrays.add(dRet, dMidWidth);
				
				nStart = nEnd;
				nEnd = nStart + 6;
				dPrevDist = Double.NaN;
			}
			else
			{
				dPrevDist = dMaxDist;
				nEnd += 3;
			}
		}
		
		if (Double.isNaN(dPrevDist) || dPrevDist < dTol)
		{
			nEnd -= 3;
			dRet = Arrays.addAndUpdate(dRet, dPts[nStart], dPts[nStart + 1]);
			dRet = Arrays.add(dRet, dPts[nStart + 2]);
			
			double dMidX = (dPts[nStart] + dPts[nEnd]) / 2;
			double dMidY = (dPts[nStart + 1] + dPts[nEnd + 1]) / 2;
			int nMidWidth = (nEnd - nStart) / 3;
			double dMidWidth;
			if (nMidWidth % 2 == 0)
			{
				dMidWidth = dPts[nStart + nMidWidth / 2 * 3 + 2];
			}
			else
			{
				int nFirstIndex = nStart + nMidWidth / 2 * 3 + 2;
				int nSecondIndex = nFirstIndex + 3;
				dMidWidth = (dPts[nFirstIndex] + dPts[nSecondIndex]) / 2;
			}
			
			dRet = Arrays.addAndUpdate(dRet, dMidX, dMidY);
			dRet = Arrays.add(dRet, dMidWidth);
		}
		
		dRet = Arrays.addAndUpdate(dRet, dPts[nLimit - 3], dPts[nLimit - 2]);
		dRet = Arrays.add(dRet, dPts[nLimit - 1]);
		return dRet;
	}
	
	
	public static double[] combineArcs(double[] dPts, double dTol)
	{
		double[] dRet = Arrays.newDoubleArray(Arrays.size(dPts) / 2);
		dRet = Arrays.add(dRet, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		double[] dSeg = new double[9];
		double[] dCurPt = new double[3];
		double[] dCurArc = Arrays.newDoubleArray();
		double[] dSavePts = new double[9];
		double[] dCenter = new double[2];
		int nRightHand = 0;
		Iterator<double[]> oIt = Arrays.iterator(dPts, dSeg, 5, 6);
		while (oIt.hasNext())
		{
			oIt.next();
			double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[3], dSeg[4], dSeg[6], dSeg[7], dCenter); // determine if current segment is line or arc
			if (Double.isFinite(dR) && dR < 10000) // current segment is a line
			{
				if (Arrays.size(dCurArc) == 1) // initialize current arc and endpoints and right hand
				{
					dCurArc = Arrays.add(dCurArc, dSeg[0], dSeg[1]);
					dCurArc = Arrays.add(dCurArc, dSeg[2]);
					dSavePts[0] = dSeg[0];
					dSavePts[1] = dSeg[1];
					dSavePts[2] = dSeg[2];
					dSavePts[3] = dSeg[3];
					dSavePts[4] = dSeg[4];
					dSavePts[5] = dSeg[5];
					dSavePts[6] = dSeg[6];
					dSavePts[7] = dSeg[7];
					dSavePts[8] = dSeg[8];
					nRightHand = rightHand(dSeg[3], dSeg[4], dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
				}

				int nTempRH = rightHand(dSeg[3], dSeg[4], dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
				if (nTempRH != nRightHand) // curve is on the other side of the line compared to the current arc
				{ // so finish the current arc and re-initialize it to the current seg
					nRightHand = nTempRH;
					dRet = Arrays.addAndUpdate(dRet, dSavePts[0], dSavePts[1]); // only add the first point
					dRet = Arrays.add(dRet, dSavePts[2]);
					dRet = Arrays.addAndUpdate(dRet, dSavePts[3], dSavePts[4]); // and the midpoint
					dRet = Arrays.add(dRet, dSavePts[5]); // the last point will be added by the next line or arc

					dCurArc[0] = 1; // reset the current arc, save points
					dCurArc = Arrays.add(dCurArc, dSeg[0], dSeg[1]);
					dCurArc = Arrays.add(dCurArc, dSeg[2]);
					dSavePts[0] = dSeg[0];
					dSavePts[1] = dSeg[1];
					dSavePts[2] = dSeg[2];
					dSavePts[3] = dSeg[3];
					dSavePts[4] = dSeg[4];
					dSavePts[5] = dSeg[5];
					dSavePts[6] = dSeg[6];
					dSavePts[7] = dSeg[7];
					dSavePts[8] = dSeg[8];
				}
				
				dCurArc = Arrays.add(dCurArc, dSeg[3], dSeg[4]); // add the midpoint
				dCurArc = Arrays.add(dCurArc, dSeg[5]);
				dCurArc = Arrays.add(dCurArc, dSeg[6], dSeg[7]); // and the last point of the current seg
				dCurArc = Arrays.add(dCurArc, dSeg[8]);
				int nSize = Arrays.size(dCurArc);
				if (nSize > 10) // if more than 1 arc has been added to the current line
				{
					double dX1 = dCurArc[1]; // set start point
					double dY1 = dCurArc[2];
					double dX2 = dCurArc[nSize - 3]; //set end point
					double dY2 = dCurArc[nSize - 2];
					int nCount = 0;
					double dTotalDist = 0.0;
					Iterator<double[]> oCur = Arrays.iterator(dCurArc, dCurPt, 4, 6); // for control point (mid point of each arc) in the current arc, starting with the 2nd point
					while (oCur.hasNext())
					{
						oCur.next();
						dTotalDist += perpDist(dCurPt[0], dCurPt[1], dX1, dY1, dX2, dY2);
						++nCount;
					}
					double dAverageDist = dTotalDist / nCount;
					double dHdg = heading(dX1, dY1, dX2, dY2);
					double dMidLineX = (dX1 + dX2) / 2;
					double dMidLineY = (dY1 + dY2) / 2;
					double dMidArcX;
					double dMidArcY;
					if (nRightHand > 0)
					{
						dMidArcX = dMidLineX + Math.sin(dHdg) * dAverageDist;
						dMidArcY = dMidLineY - Math.cos(dHdg) * dAverageDist;
					}
					else
					{
						dMidArcX = dMidLineX - Math.sin(dHdg) * dAverageDist;
						dMidArcY = dMidLineY + Math.cos(dHdg) * dAverageDist;
					}
					dR = circle(dX1, dY1, dMidArcX, dMidArcY, dX2, dY2, dCenter);
					oCur = Arrays.iterator(dCurArc, dCurPt, 4, 6);
					boolean bWithinTol = true;
					while (oCur.hasNext())
					{
						oCur.next();
						dHdg = heading(dCenter[0], dCenter[1], dCurPt[0], dCurPt[1]);
						double dX = dCenter[0] + dR * Math.cos(dHdg);
						double dY = dCenter[1] + dR * Math.sin(dHdg);
						if (distance(dX, dY, dCurPt[0], dCurPt[1]) > dTol)
						{
							bWithinTol = false;
							break;
						}
					}
					if (bWithinTol) // points are still within tolerance, so save endpoints and midpoint
					{
						dSavePts[0] = dX1;
						dSavePts[1] = dY1;
						dSavePts[2] = dCurArc[3];
						dSavePts[3] = dMidArcX;
						dSavePts[4] = dMidArcY;
						dSavePts[5] = dCurArc[nSize / 3 / 2 * 3 + 3];
						dSavePts[6] = dX2;
						dSavePts[7] = dY2;
						dSavePts[8] = dCurArc[nSize - 1];
					}
					else // points are no longer within tolerance, so add the last saved endpoints and midpoint to the final set of points
					{
						dRet = Arrays.addAndUpdate(dRet, dSavePts[0], dSavePts[1]); // only add the first point
						dRet = Arrays.add(dRet, dSavePts[2]);
						dRet = Arrays.addAndUpdate(dRet, dSavePts[3], dSavePts[4]); // and the midpoint
						dRet = Arrays.add(dRet, dSavePts[5]); // the last point will be added by the next line or arc
						
						dCurArc[0] = 1; // reset the current arc, save points, and right hand value
						dCurArc = Arrays.add(dCurArc, dSavePts[6], dSavePts[7]);
						dCurArc = Arrays.add(dCurArc, dSavePts[8]);
						dSavePts[0] = dSavePts[6];
						dSavePts[1] = dSavePts[7];
						dSavePts[2] = dSavePts[8];
						dSavePts[3] = dSeg[3];
						dSavePts[4] = dSeg[4];
						dSavePts[5] = dSeg[5];
						dSavePts[6] = dSeg[6];
						dSavePts[7] = dSeg[7];
						dSavePts[8] = dSeg[8];
						dCurArc = Arrays.add(dCurArc, dSeg[3], dSeg[4]); // add the midpoint
						dCurArc = Arrays.add(dCurArc, dSeg[5]);
						dCurArc = Arrays.add(dCurArc, dSeg[6], dSeg[7]); // and the last point of the current seg
						dCurArc = Arrays.add(dCurArc, dSeg[8]);
						nRightHand = rightHand(dSeg[3], dSeg[4], dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
					}
				}
			}
			else // current segment is line
			{
				if (Arrays.size(dCurArc) >= 10) // finish the current arc
				{
					dRet = Arrays.addAndUpdate(dRet, dSavePts[0], dSavePts[1]); // only add the first point
					dRet = Arrays.add(dRet, dSavePts[2]);
					dRet = Arrays.addAndUpdate(dRet, dSavePts[3], dSavePts[4]); // and the midpoint
					dRet = Arrays.add(dRet, dSavePts[5]); // the last point will be added by the next line or arc
				}
				dCurArc[0] = 1; // reset current arc
				dRet = Arrays.addAndUpdate(dRet, dSeg[0], dSeg[1]); // add the first point
				dRet = Arrays.add(dRet, dSeg[2]);
				dRet = Arrays.addAndUpdate(dRet, dSeg[3], dSeg[4]); // and the midpoint of line
				dRet = Arrays.add(dRet, dSeg[5]); // the last point will be added by the next line or arc
			}
		}
		dRet = Arrays.addAndUpdate(dRet, dSeg[6], dSeg[7]); // always add the last point. it will finish the last arc or line
		dRet = Arrays.add(dRet, dSeg[8]);
		return dRet;		
	}
	
	
	public static double[] simplify(double[] dPts, double dMaxStep)
	{
		double[] dRet = Arrays.newDoubleArray(Arrays.size(dPts) / 2);
		System.arraycopy(dPts, 1, dRet, 1, 4); // copy bounding box
		dRet[0] = 5;
		if (Arrays.size(dPts) < 15) // [ins, bb1, bb2, bb3, bb4, p1x, p1y, p1w, p2x, p2y, p2w, p3x, p3y, p3w]
		{
			double[] dPt = new double[3];
			Iterator<double[]> oIt = Arrays.iterator(dPts, dPt, 5, 3);
			while(oIt.hasNext())
			{
				oIt.next();
				dRet = Arrays.add(dRet, dPt);
			}
			return dRet;
		}
		
		double[] dSeg = new double[9];
		Iterator<double[]> oIt = Arrays.iterator(dPts, dSeg, 5, 3);
		dRet = Arrays.add(dRet, dPts[5], dPts[6]); // add first x,y
		dRet = Arrays.add(dRet, dPts[7]);  // add first w
		double[] dCenter = new double[2];
		double dH = dCenter[0];
		double dK = dCenter[1];
		int nCurIndex = 8;
		int nLastAdded = 5;
		while(oIt.hasNext())
		{
			oIt.next();
			double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[3], dSeg[4], dSeg[6], dSeg[7], dCenter); // determine current segment is co-arc
			if (!Double.isFinite(dR) || dR >= 10000) // current segment is a line
			{
				if (Double.isFinite(dH)) // last segment was an arc, so finish it
				{
					int nMidIndex = (nCurIndex - nLastAdded) / 3 / 2 * 3 + nLastAdded;
					if (nMidIndex == nLastAdded)
					{
						dH = dCenter[0];
						dK = dCenter[1];
						nCurIndex += 3;
						continue;
					}
					dRet = Arrays.add(dRet, dPts[nMidIndex], dPts[nMidIndex + 1]);
					dRet = Arrays.add(dRet, dPts[nMidIndex + 2]);
					dRet = Arrays.add(dRet, dSeg[3], dSeg[4]); // add last point of the arc
					dRet = Arrays.add(dRet, dSeg[5]);
					nLastAdded = nCurIndex;
				}
				dH = dK = Double.NaN; // reset the previous center, do not need to add a point for the current segment
			}
			else // current segment is not a line
			{
				if (!Double.isFinite(dH)) // last segment was a line, so finish it
				{
					int nMidIndex = (nCurIndex - nLastAdded) / 3 / 2 * 3 + nLastAdded;
					dRet = Arrays.add(dRet, dPts[nMidIndex], dPts[nMidIndex + 1]);
					dRet = Arrays.add(dRet, dPts[nMidIndex + 2]);
					nLastAdded = nCurIndex;
				}
				else // last segment was an arc
				{
					if (Geo.distance(dH, dK, dCenter[0], dCenter[1]) < dMaxStep) // same center, so co-arc
					{
						nCurIndex += 3;
						continue; // do nothing
					}
					else // last segment was not co-arc, so finish it
					{						
						int nMidIndex = (nCurIndex - nLastAdded) / 3 / 2 * 3 + nLastAdded;
						if (nMidIndex == nLastAdded)
						{
							dH = dCenter[0];
							dK = dCenter[1];
							nCurIndex += 3;
							continue;
						}
						dRet = Arrays.add(dRet, dPts[nMidIndex], dPts[nMidIndex + 1]);
						dRet = Arrays.add(dRet, dPts[nMidIndex + 2]);
						nLastAdded = nCurIndex;
					}
				}
				dRet = Arrays.add(dRet, dSeg[3], dSeg[4]); // add the last point of the previous segment
				dRet = Arrays.add(dRet, dSeg[5]);
				dH = dCenter[0];
				dK = dCenter[1];
			}
			nCurIndex += 3;
		}
		if (Double.isFinite(dH)) // finish the last segment, if the previous center is finite, it is an arc
		{
			int nMidIndex = (nCurIndex - nLastAdded) / 3 / 2 * 3 + nLastAdded;
			dRet = Arrays.add(dRet, dPts[nMidIndex], dPts[nMidIndex + 1]);
			dRet = Arrays.add(dRet, dPts[nMidIndex + 2]);
		}
		else // otherwise a line
		{
			int nMidIndex = (nCurIndex - nLastAdded) / 3 / 2 * 3 + nLastAdded;
			dRet = Arrays.add(dRet, dPts[nMidIndex], dPts[nMidIndex + 1]);
			dRet = Arrays.add(dRet, dPts[nMidIndex + 2]);
		}
		dRet = Arrays.add(dRet, dSeg[6], dSeg[7]); // add last x,y
		dRet = Arrays.add(dRet, dSeg[8]);
		
		return dRet;
	}
	
	
	public static void updateBounds(double dX, double dY, double[] dBounds)
	{
		if (dX < dBounds[0])
			dBounds[0] = dX;
		if (dY < dBounds[1])
			dBounds[1] = dY;
		if (dX > dBounds[2])
			dBounds[2] = dX;
		if (dY > dBounds[3])
			dBounds[3] = dY;
	}
	
	public static void updateBoundingBox(double[] dUpdate, double[] dPts)
	{
		if (dPts[1] < dUpdate[1])
			dUpdate[1] = dPts[1];
		
		if (dPts[2] < dUpdate[2])
			dUpdate[2] = dPts[2];
		
		if (dPts[3] > dUpdate[3])
			dUpdate[3] = dPts[3];
		
		if (dPts[4] > dUpdate[4])
			dUpdate[4] = dPts[4];
	}
	
	
	public static double circle(double dX1, double dY1, double dX2, double dY2, double dX3, double dY3, double[] dCenter)
	{
		double dDist1 = distance(dX1, dY1, dX2, dY2);
		double dDeltaX1 = dX2 - dX1;
		if (MathUtil.compareTol(dDeltaX1, 0.0, dDist1 * CIRCLE_TOL) == 0) // first two points make a vertical line, switch points
		{
			double dTempX = dX2;
			double dTempY = dY2;
			dX2 = dX3;
			dY2 = dY3;
			dX3 = dTempX;
			dY3 = dTempY;
			dDeltaX1 = dX2 - dX1;
			dDist1 = distance(dX1, dY1, dX2, dY2);
			if (MathUtil.compareTol(dDeltaX1, 0.0, dDist1 * CIRCLE_TOL) == 0) // all 3 points make a vertical line, no valid circle
			{
				dCenter[0] = dCenter[1] = Double.NaN;
				return Double.NaN;
			}
		}
		
		double dDeltaX2 = dX3 - dX2;
		double dDeltaY2 = dY3 - dY2;
		double dDeltaY1 = dY2 - dY1;
		double dM1 = dDeltaY1 / dDeltaX1;
		double dM2 = dDeltaY2 / dDeltaX2;

		if (MathUtil.compareTol(dM1, dM2, 0.0001) == 0) // parallel lines, no valid circle
		{
			dCenter[0] = dCenter[1] = Double.NaN;
			return Double.NaN;
		}
		dCenter[0] = (dM1 * dM2 * (dY1 - dY3) + dM2 * (dX1 + dX2) - dM1 * (dX2 + dX3)) / (2 * (dM2 - dM1));
		dCenter[1] = -1 * (dCenter[0] - (dX1 + dX2) / 2) / dM1 + (dY1 + dY2) / 2;

		return distance(dX1, dY1, dCenter[0], dCenter[1]); // calculate radius
	}
	
	
	public static int rightHand(double dX, double dY, double dX1, double dY1, double dX2, double dY2)
	{
		double dXp = dX - dX1;
		double dXd = dX2 - dX1;

		double dYp = dY - dY1;
		double dYd = dY2 - dY1;

		double dVal = (dXd * dYp) - (dYd * dXp);
		if (dVal > 0)
			return 1;
		else if (dVal < 0)
			return -1;
		
		return 0;
	}
	
	
	public static void arcMdpt(double dH, double dK, double dR, double dX1, double dY1, double dX2, double dY2, double dXa, double dYa, double[] dMdpt)
	{
		double dMidCX = (dX1 + dX2) / 2;
		double dMidCY = (dY1 + dY2) / 2;
		int nRH1 = rightHand(dXa, dYa, dX1, dY1, dX2, dY2);
		double dAngle;
		if (dH == dMidCX && dK == dMidCY)
			dAngle = heading(dH, dK, dX1, dY1) - Math.PI / 2;
		else
			dAngle = heading(dH, dK, dMidCX, dMidCY);

		double dX = dH + dR * Math.cos(dAngle);
		double dY = dK + dR * Math.sin(dAngle);
		int nRH2 = rightHand(dX, dY, dX1, dY1, dX2, dY2);
		if (nRH1 != nRH2)
		{
			dAngle += Math.PI;
			dX = dH + dR * Math.cos(dAngle);
			dY = dK + dR * Math.sin(dAngle);
		}
		
		dMdpt[0] = dX;
		dMdpt[1] = dY;
	}


	public static double[] createPolygon(double[] dInOrder, double[] dReverseOrder)
	{
		int nInOrderLimit = Arrays.size(dInOrder);
		int nReverseOrderLimit = Arrays.size(dReverseOrder);
		double[] dPoly = Arrays.newDoubleArray(nInOrderLimit + nReverseOrderLimit);

		for (int i = 1; i < nInOrderLimit;)
			dPoly = Arrays.add(dPoly, dInOrder[i++], dInOrder[i++]);

		for (int i = nReverseOrderLimit - 2; i >= 1; i -= 2)
			dPoly = Arrays.add(dPoly, dReverseOrder[i], dReverseOrder[i + 1]);
		
		return dPoly;
	}
	
	
	public static void main(String[] sArgs)
	   throws Exception
	{
//		double[] dA = Arrays.newDoubleArray();
//		dA = Arrays.add(dA, 1, 2);
//		dA = Arrays.add(dA, 3);
//		dA = Arrays.add(dA, 1, 2);
//		dA = Arrays.add(dA, 3);
//		dA = Arrays.add(dA, 1, 2);
//		dA = Arrays.add(dA, 3);
//		System.out.println(Arrays.size(dA));
//		if (true)
//			return;
		try (DataInputStream oIn = new DataInputStream(new BufferedInStream(FileUtil.newInputStream(Paths.get("C:/Users/aaron.cherney/Documents/centi2and25.bin")))))
		{
			while (oIn.available() > 0)
			{
				CtrlLineArcs oCla = new CtrlLineArcs(oIn);
				int nRoadId = XodrUtil.getRoadId(oCla.m_nLaneId);
				int nLaneIndex = XodrUtil.getLaneIndex(oCla.m_lLaneSectionId);
				if (nRoadId != 24 || nLaneIndex != -1)
					continue;
//				int nOrds = oIn.readInt();
//				double[] dPts = Arrays.newDoubleArray(nOrds);
//				for (int nIndex = 0; nIndex < nOrds; nIndex += 2)
//				{
//					dPts = Arrays.add(dPts, oIn.readDouble(), oIn.readDouble());
//				}
				double[] dSim = simplify(oCla.m_dLineArcs, 0.05);
				double[] dCombine = combineLines(dSim, 0.05);
				double[] dCombineArc = combineArcs(dSim, 0.05);
				System.out.println(Arrays.size(oCla.m_dLineArcs));
				System.out.println(Arrays.size(dSim));
				System.out.println(Arrays.size(dCombine));
				System.out.println(Arrays.size(dCombineArc));
				Iterator<double[]> oIt = Arrays.iterator(dSim, new double[3], 5, 3);
//				while (oIt.hasNext())
//				{
//					double[] dPt = oIt.next();
//					System.out.println(String.format("%3.8f\t%3.8f\t%3.8f", dPt[0], dPt[1], dPt[2]));
//				}
			}
		}
	}
	
//	public static void main(String[] sArgs)
//	{
//		double[] dPts = Arrays.newDoubleArray();
//		dPts = Arrays.add(dPts, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
//		double dH = 0;
//		double dK = 0;
//		double dR = 1;
//		double dStep = Math.PI / 4;
//		for (int i = 0; i < 8; i++)
//		{
//			double dAngle = dStep * i;
//			dPts = Arrays.addAndUpdate(dPts, dH + dR * Math.cos(dAngle), dK + dR * Math.sin(dAngle));
//			dPts = Arrays.add(dPts, -999);
//		}
//	
//		for (int i = 2; i <= 100; i++)
//		{
//			dPts = Arrays.addAndUpdate(dPts, i, 0);
//			dPts = Arrays.add(dPts, -999);
//		}
//		
//		double[] dLines = Arrays.newDoubleArray();
//		dLines = Arrays.add(dLines, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
//		dLines = Arrays.addAndUpdate(dLines, 0, 0);
//		dLines = Arrays.add(dLines, -999);
//		dLines = Arrays.addAndUpdate(dLines, 1, 1);
//		dLines = Arrays.add(dLines, -999);
//		dLines = Arrays.addAndUpdate(dLines, 2, 2);
//		dLines = Arrays.add(dLines, -999);
//		dLines = Arrays.addAndUpdate(dLines, 3, 2);
//		dLines = Arrays.add(dLines, -999);
//		dLines = Arrays.addAndUpdate(dLines, 4, 2);
//		dLines = Arrays.add(dLines, -999);
//		dLines = Arrays.addAndUpdate(dLines, 5, 2);
//		dLines = Arrays.add(dLines, -999);
//		double[] dSim = simplify(dPts, 0.1);
//		System.out.println(Arrays.size(dSim));
//	}
	
	public static Area getArea(double[] dPath, int nStart)
	{
		if (Arrays.size(dPath) - nStart < 6) // must have at least 3 points
			return null;
		
		Path2D.Double oPath = new Path2D.Double();
		double[] dPt = new double[2];
		Iterator<double[]> oIt = Arrays.iterator(dPath, dPt, nStart, 2);
		oIt.next();
		oPath.moveTo(dPt[0], dPt[1]);
		while (oIt.hasNext())
		{
			oIt.next();
			oPath.lineTo(dPt[0], dPt[1]);
		}
		oPath.closePath();
		
		return new Area(oPath);
	}
	
	
	
}
