/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.ctrl.TrafCtrl;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.CsvReader;
import cc.util.Geo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * This class represents a Subsegment of a Corridor used in the IHP2 Speed
 * Harmonization Algorithm
 * 
 * @author aaron.cherney
 */
public class Subsegment extends ArrayList<Detector>
{

	/**
	 * Growable array that contains the center line of the subsegment
	 * described by 3 coordinate points (x, y, width) in Mercator meters 
	 * 
	 * @see cc.util.Arrays
	 */
	public double[] m_dCenterLine;

	/**
	 * Growable array that contains the points in Mercator meters
	 * that define the polygon(edges) of the subsegment. Points are in clockwise order and the
	 * polygon is closed (first point is the same as last point)
	 * 
	 * @see cc.util.Arrays
	 */
	public double[] m_dGeo;

	/**
	 * Array containing the bounding box of the subsegment in Mercator meters in order
	 * min_x, min_y, max_x, max_y
	 */
	public double[] m_dBb;

	/**
	 * Growable array that contains speed observations for the current time interval
	 * 
	 * @see cc.util.Arrays
	 */
	public double[] m_dSpeeds = Arrays.newDoubleArray();

	/**
	 * Traffic volume in veh/s/lane
	 */
	public double m_dVolume;

	/**
	 * Traffic density veh/m/lane
	 */
	public double m_dDensity;

	/**
	 * Calculated 85th percentile of speeds in the subsegment for the current time interval
	 */
	public double m_d85th;

	/**
	 * Calculated 15th percentile of speeds in the subsegment for the current time interval
	 */
	public double m_d15th;

	/**
	 * Length of the center line of the subsegment in Mercator meters
	 */
	public double m_dLength = 0;

	/**
	 * Speed limit of the subsegment
	 */
	public int m_nMaxSpeed;

	/**
	 * Target speed calculated by equation 4
	 */
	public double m_dTarCtrlSpeed;

	/**
	 * Advisory speed used when generating new traffic controls. Calculated by equations
	 * 6,7, and 8
	 */
	public double m_dAdvisorySpeed;

	/**
	 * The advisory speed from the last run of the algorithm
	 */
	public double m_dPreviousAdvisorySpeed;

	/**
	 * The density from the last run of the algorithm
	 */
	public double m_dPreviousDensity;

	/**
	 * Reference to the traffic control created by the algorithm for this subsegment
	 */
	public TrafCtrl m_oCtrl = null;
	
	/**
	 * Constructor for Subsegment that reads parses a line from the corridor file
	 * @param oIn  CsvReader that has already called readLine() and has the current
	 * line in its buffer to parse
	 * @param nCols number of columns read for the current line
	 * @throws IOException
	 */
	public Subsegment(CsvReader oIn, int nCols)
		throws IOException
	{
		double[] dC = Arrays.newDoubleArray();
		double[] dPT = Arrays.newDoubleArray();
		double[] dNT = Arrays.newDoubleArray();
		double[] dBb = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (int nIndex = 0; nIndex < nCols;)
		{
			double dX = Mercator.lonToMeters(oIn.parseDouble(nIndex++));
			double dY = Mercator.latToMeters(oIn.parseDouble(nIndex++));
			double dW = oIn.parseDouble(nIndex++);

			dC = Arrays.add(dC, new double[]{dX, dY, dW});
		}
		m_dCenterLine = Arrays.newDoubleArray(Arrays.size(dC) * 2);
		m_dCenterLine = Arrays.add(m_dCenterLine, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		double[] dSeg = new double[6];
		double dHeading = 0;
		Iterator<double[]> oIt = Arrays.iterator(dC, dSeg, 1, 3);

		while (oIt.hasNext())
		{
			oIt.next();
			dHeading = Geo.heading(dSeg[0], dSeg[1], dSeg[3], dSeg[4]);
			double dW = dSeg[2] / 2;
			double dXPrime = dSeg[0] + Math.sin(dHeading) * dW; // cos(x - pi/2) = sin(x)
			double dYPrime = dSeg[1] - Math.cos(dHeading) * dW; // sin(x - pi/2) = -cos(x)
			dNT = Arrays.add(dNT, dXPrime, dYPrime);
			updateBoundingBox(dXPrime, dYPrime, dBb);

			dXPrime = dSeg[0] - Math.sin(dHeading) * dW; // cos(x + pi/2 = -sin(x)
			dYPrime = dSeg[1] + Math.cos(dHeading) * dW; // sin(x + pi/2) = cos(x)
			dPT = Arrays.add(dPT, dXPrime, dYPrime); 
			updateBoundingBox(dXPrime, dYPrime, dBb);

			m_dLength += Geo.distance(dSeg[0], dSeg[1], dSeg[3], dSeg[4]);
			m_dCenterLine = Arrays.add(m_dCenterLine, new double[]{dSeg[0], dSeg[1], dSeg[2]});
			m_dCenterLine = Arrays.add(m_dCenterLine, new double[]{(dSeg[0] + dSeg[3]) / 2, (dSeg[1] + dSeg[4]) / 2, dSeg[2]});
		}

		m_dCenterLine = Arrays.add(m_dCenterLine, new double[]{dSeg[3], dSeg[4], dSeg[5]});

		double dW = dSeg[5] / 2;
		double dXPrime = dSeg[3] + Math.sin(dHeading) * dW; // cos(x - pi/2) = sin(x)
		double dYPrime = dSeg[4] - Math.cos(dHeading) * dW; // sin(x - pi/2) = -cos(x)
		dNT = Arrays.add(dNT, dXPrime, dYPrime);
		updateBoundingBox(dXPrime, dYPrime, dBb);

		dXPrime = dSeg[3] - Math.sin(dHeading) * dW; // cos(x + pi/2 = -sin(x)
		dYPrime = dSeg[4] + Math.cos(dHeading) * dW; // sin(x + pi/2) = cos(x)
		dPT = Arrays.add(dPT, dXPrime, dYPrime); 
		updateBoundingBox(dXPrime, dYPrime, dBb);

		m_dGeo = Geo.createPolygon(dPT, dNT);
		m_dGeo = Arrays.add(m_dGeo, m_dGeo[1], m_dGeo[2]);
		m_dBb = dBb;
	}
	
	/**
	 * Called at the start of each algorithm run to reset the necessary values
	 * for the algorithm
	 */
	public void reset()
	{
		m_dSpeeds[0] = 1;
		m_dVolume = 0;
		m_nMaxSpeed = Integer.MIN_VALUE;
		m_dPreviousDensity = m_dDensity;
		m_dPreviousAdvisorySpeed = m_dAdvisorySpeed;
		m_dDensity = 0;
		m_oCtrl = null;
		clear();
	}
	
	/**
	 * Tests if the given point in Mercator meters is inside the subsegment
	 * @param dX x coordinate
	 * @param dY y coordinate
	 * @return true if the point is inside the polygon that represents the subsegment
	 * otherwise false
	 */
	public boolean pointInside(double dX, double dY)
	{
		if (Geo.isInBoundingBox(dX, dY, m_dBb[0], m_dBb[1], m_dBb[2], m_dBb[3])) // quick bounding box test
		{
			return Geo.isInsidePolygon(m_dBb, dX, dY, 1);
		}
		
		return false;
	}
	
	/**
	 * Calculates the 15th and 85th percentiles speeds from the current speed
	 * observations
	 */
	public void generateStats()
	{
		int nCount = Arrays.size(m_dSpeeds) - 1;
		java.util.Arrays.sort(m_dSpeeds, 1, nCount + 1);
		int nIndex = (int)Math.round(0.85 * nCount);
		if (nIndex >= nCount)
			nIndex = nCount - 1;
		m_d85th = m_dSpeeds[nIndex + 1];
		
		nIndex = (int)Math.round(0.15 * nCount);
		if (nIndex >= nCount)
			nIndex = nCount - 1;
		m_d15th = m_dSpeeds[nIndex + 1];
	}

	
	/**
	 * Updates the bounding box of the subsegment using the given coordinates
	 * @param dX x coordinate in Mercator meters
	 * @param dY y coordinate in Mercator meters
	 * @param dBb Array containing the bounding box with the following format:
	 * [min_x, min_y, max_x, max_y]
	 */
	public final void updateBoundingBox(double dX, double dY, double[] dBb)
	{
		if (dX < dBb[0])
			dBb[0] = dX;
		if (dY < dBb[1])
			dBb[1] = dY;
		if (dX > dBb[2])
			dBb[2] = dX;
		if (dY > dBb[3])
			dBb[3] = dY;
	}
	
	/**
	 * Updates the values for the current time interval the speed harmonization 
	 * algorithm is being ran for
	 * @param dSpeedLimit speed limit in m/s
	 * @param d15th 15th percentile speed in m/s
	 * @param d85th 85th percentile speed in m/2
	 * @param dDensity density 0 <= x <= 1
	 * @param dVolume volume in veh/h/lane
	 */
	public void updateDetectors(double dSpeedLimit, double d15th, double d85th, double dDensity, double dVolume)
	{
		m_nMaxSpeed = (int)Math.round(dSpeedLimit);
		m_d15th = d15th;
		m_d85th = d85th;
		m_dDensity = dDensity;
		m_dVolume = dVolume;
	}
}
