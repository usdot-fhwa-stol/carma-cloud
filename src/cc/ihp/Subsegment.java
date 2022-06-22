/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.ctrl.TrafCtrl;
import cc.ctrl.proc.ProcCtrl;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.CsvReader;
import cc.util.Geo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class Subsegment extends ArrayList<Detector>
{
	public double[] m_dCenterLine;
	public double[] m_dGeo;
	public double[] m_dBb;
	public double[] m_dSpeeds = Arrays.newDoubleArray();
	public int m_nVolume;
	public double m_dOcc;
	public int m_nConsecutiveTimes = 0;
	public double m_d85th;
	public double m_d15th;
	public double m_dLength = 0;
	public int m_nMaxSpeed;
	public double m_dTarDensity;
	public double m_dTarCtrlSpeed;
	public double m_dAdvisorySpeed;
	public double m_dPreviousAdvisorySpeed;
	public double m_dPreviousDensity;
	public TrafCtrl m_oCtrl = null;
	public boolean m_bNewControl = false;
	
	public Subsegment(CsvReader oIn, int nCols, boolean bRect)
		throws IOException
	{
		if (bRect)
		{
			double[] dGeo = Arrays.newDoubleArray();
			double[] dBb = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
			for (int nIndex = 0; nIndex < nCols;)
			{
				double dX = Mercator.lonToMeters(oIn.parseDouble(nIndex++));
				double dY = Mercator.latToMeters(oIn.parseDouble(nIndex++));
				dGeo = Arrays.add(dGeo, dX, dY);

				if (dBb[0] < dX)
					dBb[0] = dX;
				if (dBb[1] < dY)
					dBb[1] = dY;
				if (dBb[2] > dX)
					dBb[2] = dX;
				if (dBb[3] > dY)
					dBb[3] = dY;
			}

			int nSize = Arrays.size(dGeo);
			if (dGeo[1] != dGeo[nSize - 2] || dGeo[2] != dGeo[nSize -1]) // ensure a closed polygon
				dGeo = Arrays.add(dGeo, dGeo[1], dGeo[2]);

			m_dGeo = dGeo;
			m_dBb = dBb;

			double[] dCenterLine = Arrays.newDoubleArray(10);
			double dStartX = (m_dGeo[1] + m_dGeo[7]) / 2;
			double dStartY = (m_dGeo[2] + m_dGeo[8]) / 2;
			double dStartW = Geo.distance(m_dGeo[1], m_dGeo[2], m_dGeo[7], m_dGeo[8]);
			double dEndX = (m_dGeo[3] + m_dGeo[5]) / 2;
			double dEndY = (m_dGeo[4] + m_dGeo[6]) / 2;
			double dEndW = Geo.distance(m_dGeo[3], m_dGeo[4], m_dGeo[5], m_dGeo[6]);
			double dMidX = (dStartX + dEndX) / 2;
			double dMidY = (dStartY + dEndY) / 2;
			double dMidW = (dStartW + dEndW) / 2;
			dCenterLine = Arrays.add(dCenterLine, new double[]{dStartX, dStartY, dStartW, dMidX, dMidY, dMidW, dEndX, dEndY, dEndW});
			m_dCenterLine = dCenterLine;
		}
		else
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
	}
	
	
	public void reset()
	{
		m_dSpeeds[0] = 1;
		m_nVolume = 0;
		m_nMaxSpeed = Integer.MIN_VALUE;
		m_dPreviousDensity = m_dOcc;
		m_dPreviousAdvisorySpeed = m_dAdvisorySpeed;
		m_dOcc = 0;
		m_bNewControl = false;
		clear();
	}
	
	
	public boolean pointInside(double dX, double dY)
	{
		if (Geo.isInBoundingBox(dX, dY, m_dBb[0], m_dBb[1], m_dBb[2], m_dBb[3]))
		{
			return Geo.isInsidePolygon(m_dBb, dX, dY, 1);
		}
		
		return false;
	}
	
	
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
	
	
	public void cancelCtrl()
	{
		ProcCtrl.deleteControl(m_oCtrl);
		m_oCtrl = null;
	}
	
	
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
	
	
	public void updateDetectors(double dSpeedLimit, double d15th, double d85th, double dOcc, double dVolume)
	{
		m_nMaxSpeed = (int)Math.round(dSpeedLimit);
		m_d15th = d15th;
		m_d85th = d85th;
		m_dOcc = dOcc;
		m_nVolume = (int)Math.round(dVolume);
	}
}
