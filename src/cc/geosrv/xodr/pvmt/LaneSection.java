/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.pvmt;

import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.Geo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 *
 * @author Federal Highway Administration
 */
public class LaneSection
{
	public double m_dS;
	public ArrayList<Lane> m_oLeft = new ArrayList();
	public ArrayList<Lane> m_oRight = new ArrayList();
	public Lane m_oCenter;
	private static Comparator<Lane> LEFTLANES = (Lane o1, Lane o2) -> Integer.compare(o1.m_nLaneIndex, o2.m_nLaneIndex);
	private static Comparator<Lane> RIGHTLANES = (Lane o1, Lane o2) -> Integer.compare(o2.m_nLaneIndex, o1.m_nLaneIndex);
	public int m_nId;
	public LaneSection m_oPrevSect;
	public double[] m_dEdgeEnds;
	public double[] m_dPavement;
	public ArrayList<double[]> m_oShoulders = new ArrayList();
	
	public LaneSection()
	{
	}
	
	public LaneSection(double dS, int nId)
	{
		m_dS = dS;
		m_nId = nId;
	}
	
	
	public void setInnerPaths()
	{
		setInnerPaths(m_oLeft);
		setInnerPaths(m_oRight);
	}
	
	
	private void setInnerPaths(ArrayList<Lane> oLanes)
	{
		int nSize = oLanes.size();
		if (nSize > 0)
		{
			oLanes.get(0).m_dInner = m_oCenter.m_dOuter;
			for (int i = 1; i < nSize; i++)
				oLanes.get(i).m_dInner = oLanes.get(i - 1).m_dOuter;
		}
	}
	
	
	public void add(Lane oLane)
	{
		if (oLane.m_nLaneIndex > 0)
			m_oLeft.add(oLane);
		else if (oLane.m_nLaneIndex < 0)
			m_oRight.add(oLane);
	}
	
	
	public void sortLanes()
	{
		Collections.sort(m_oLeft, LEFTLANES);
		Collections.sort(m_oRight, RIGHTLANES);
	}
	
	
	public static LaneSection getLaneSection(double dDist, ArrayList<LaneSection> oLaneSections)
	{
		int nIndex = oLaneSections.size();
		while (nIndex-- > 0)
		{
			LaneSection oTemp = oLaneSections.get(nIndex);
			if (dDist >= oTemp.m_dS)
				return oTemp;
		}
		
		return oLaneSections.get(0);
	}
	
	
	public void getLanes(ArrayList<Lane> oLaneList)
	{
		oLaneList.clear();
		oLaneList.add(m_oCenter);
		oLaneList.addAll(m_oLeft);
		oLaneList.addAll(m_oRight);
	}

	
	public void createPavement()
	{
		ArrayList<Lane> oLanes = new ArrayList();
		getLanes(oLanes);
		Collections.sort(oLanes, LEFTLANES);
		int nDriving = XodrUtil.getLaneType("driving");
		double[] dEdgeR = null;
		for (int nIndex = 0; nIndex < oLanes.size(); nIndex++)
		{
			Lane oLane = oLanes.get(nIndex);
			if (oLane.m_nLaneType == nDriving)
			{
				dEdgeR = oLane.m_nLaneIndex < 0 ? oLane.m_dOuter : oLane.m_dInner;
				break;
			}
		}
		
		double[] dEdgeL = null;
		for (int nIndex = oLanes.size() - 1; nIndex >= 0; nIndex--)
		{
			Lane oLane = oLanes.get(nIndex);
			if (oLane.m_nLaneType == nDriving)
			{
				dEdgeL = oLane.m_nLaneIndex < 0 ? oLane.m_dInner : oLane.m_dOuter;
				break;
			}
		}
		
		if (dEdgeL == null || dEdgeR == null)
			return;
		double[] dPavementCenter = Arrays.newDoubleArray(Arrays.size(dEdgeL));
		dPavementCenter = Arrays.add(dPavementCenter, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		
		int nLimit = Arrays.size(dEdgeR);
		m_dEdgeEnds = new double[]{dEdgeR[nLimit - 2], dEdgeR[nLimit - 1], dEdgeL[nLimit - 2], dEdgeL[nLimit - 1]};
		if (m_oPrevSect != null && m_oPrevSect.m_dEdgeEnds != null)
		{
			double dXr = m_oPrevSect.m_dEdgeEnds[0];
			double dYr = m_oPrevSect.m_dEdgeEnds[1];
			double dXl = m_oPrevSect.m_dEdgeEnds[2];
			double dYl = m_oPrevSect.m_dEdgeEnds[3];
			
			double dPathX = (dXr + dXl) / 2;
			double dPathY = (dYr + dYl) / 2;
			double dW = Geo.distance(dXr, dYr, dXl, dYl);
			dPavementCenter = Arrays.addAndUpdate(dPavementCenter, dPathX, dPathY);
			dPavementCenter = Arrays.add(dPavementCenter, dW);
		}
		for (int nIndex = 5; nIndex < nLimit; nIndex += 2)
		{
			double dXr = dEdgeR[nIndex];
			double dYr = dEdgeR[nIndex + 1];
			double dXl = dEdgeL[nIndex];
			double dYl = dEdgeL[nIndex + 1];
			
			double dPathX = (dXr + dXl) / 2;
			double dPathY = (dYr + dYl) / 2;
			double dW = Geo.distance(dXr, dYr, dXl, dYl);
			dPavementCenter = Arrays.addAndUpdate(dPavementCenter, dPathX, dPathY);
			dPavementCenter = Arrays.add(dPavementCenter, dW);
		}
	
		m_dPavement = dPavementCenter; // save local reference
	}
	
	public void getShoulders(ArrayList<double[]> oShoulders)
	{
		ArrayList<Lane> oLanes = new ArrayList();
		getLanes(oLanes);
		int nShoulder = XodrUtil.getLaneType(("shoulder"));
		for (Lane oLane : oLanes)
		{
			if (oLane.m_nLaneType == nShoulder)
			{
				double[] dEdgeR = oLane.m_dOuter;
				double[] dEdgeL = oLane.m_dInner;
				double[] dPavementCenter = Arrays.newDoubleArray(Arrays.size(dEdgeL));
				dPavementCenter = Arrays.add(dPavementCenter, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});

				int nLimit = Arrays.size(dEdgeR);
				for (int nIndex = 5; nIndex < nLimit; nIndex += 2)
				{
					double dXr = dEdgeR[nIndex];
					double dYr = dEdgeR[nIndex + 1];
					double dXl = dEdgeL[nIndex];
					double dYl = dEdgeL[nIndex + 1];

					double dPathX = (dXr + dXl) / 2;
					double dPathY = (dYr + dYl) / 2;
					double dW = Geo.distance(dXr, dYr, dXl, dYl);
					dPavementCenter = Arrays.addAndUpdate(dPavementCenter, dPathX, dPathY);
					dPavementCenter = Arrays.add(dPavementCenter, dW);
				}
				
				oShoulders.add(dPavementCenter);
			}
		}
	}
}
