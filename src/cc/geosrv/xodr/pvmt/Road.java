/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.pvmt;

import cc.ctrl.CtrlLineArcs;
import cc.geosrv.Proj;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.Geo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author Federal Highway Administration
 */
public class Road extends ArrayList<LaneSection>
{
	public int m_nId;
	public double m_dLength;
	public double[] m_dTrack;
	public double[] m_dLaneZero;
	public double[] m_dBounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
	ArrayList<LaneOffset> m_oLaneOffsets = new ArrayList();
	ArrayList<Geometry> m_oGeometries = new ArrayList();
	public double m_dLastGeo = Double.MAX_VALUE;
	public int m_nMaxSpeed;
	public ArrayList<double[]> m_dGeoPoints = new ArrayList();
	public ArrayList<double[]> m_dLWPoints = new ArrayList();
	public ArrayList<double[]> m_dPerps = new ArrayList();
	
	protected Road()
	{
		
	}
	
	
	public Road(int nId, double dLength)
	{
		m_nId = nId;
		m_dLength = dLength;
		m_dTrack = Arrays.newDoubleArray();
		m_dLaneZero = Arrays.newDoubleArray();
	}
	
	
	public void adjustSections(double dMaxStep)
	{
		int nIndex = size();
		if (nIndex == 1)
			return;
		
		double dPrevOffset = m_dLength;
		while (nIndex-- > 0)
		{
			double dSectionLength = dPrevOffset - get(nIndex).m_dS;
			if (dSectionLength < dMaxStep)
				remove(nIndex);
			else
				dPrevOffset = get(nIndex).m_dS;
		}
		
		if (get(0).m_dS != 0.0)
			get(0).m_dS = 0;
	}

	
	public void createPoints(double dMaxStep, Proj oProj)
	{
		Collections.sort(m_oGeometries);
		double[] dProjPt = new double[2];
		for (int nIndex = 0; nIndex < m_oGeometries.size() - 1; nIndex++)
			m_oGeometries.get(nIndex).addPoints(this, dMaxStep, false, oProj, dProjPt);
		m_oGeometries.get(m_oGeometries.size() - 1).addPoints(this, dMaxStep, true, oProj, dProjPt);
	}
	
	
	public void setLaneIds(double dMaxStep)
	{
		double dTol = dMaxStep * 2;
		ArrayList<Lane> oPrevLanes = new ArrayList();
		get(0).getLanes(oPrevLanes);
		int nNextId = Integer.MIN_VALUE;
		for (Lane oLane : oPrevLanes)
		{
			oLane.m_nLaneIdByRoad = oLane.m_nLaneIndex;
			int nComp = Math.abs(oLane.m_nLaneIndex);
			if (nComp > nNextId)
				nNextId = nComp;
		}
		
		for (int nIndex = 1; nIndex < size(); nIndex++)
		{
			ArrayList<Lane> oCurrLanes = new ArrayList();
			get(nIndex).m_oPrevSect = get(nIndex - 1);
			get(nIndex).getLanes(oCurrLanes);
			for (Lane oCurr: oCurrLanes)
			{
				if (oCurr.m_nLaneIndex == 0)
					continue;
				double[] dCurr = oCurr.m_dCenter;
				oCurr.m_nLaneIdByRoad = Integer.MIN_VALUE;
				for (Lane oPrev: oPrevLanes)
				{
					if (oPrev.m_nLaneIndex == 0)
						continue;
					double[] dPrev = oPrev.m_dCenter;
					int nSize = Arrays.size(dPrev);	
					
					double dDist = Geo.distance(dCurr[5], dCurr[6], dPrev[nSize - 3], dPrev[nSize - 2]);
//					if (dDist > dTol)
//						System.out.println(dDist);
					if (dDist <= dTol && oCurr.m_nLaneType == oPrev.m_nLaneType)
					{
						oCurr.m_nLaneIdByRoad = oPrev.m_nLaneIdByRoad;
						break;
					}
				}
				
				if (oCurr.m_nLaneIdByRoad == Integer.MIN_VALUE)
				{
					++nNextId;
					if (oCurr.m_nLaneIndex < 0)
						oCurr.m_nLaneIdByRoad = -nNextId;
					else
						oCurr.m_nLaneIdByRoad = nNextId;
				}
			}
			oPrevLanes.clear();
			oPrevLanes.addAll(oCurrLanes);
		}
	}
	
	
	public void getPavementCtrlLineArcs(ArrayList<CtrlLineArcs> oCtrlLineArcs, double dMaxStep)
	{
		int nType = XodrUtil.getLaneType("driving");
		for (LaneSection oSection : this)
		{
			oSection.createPavement();
			if (oSection.m_dPavement != null)
				oCtrlLineArcs.add(new CtrlLineArcs(m_nId, oSection.m_nId, 0, 0, nType, oSection.m_dPavement, dMaxStep));
		}
	}
	
	
	public void getLaneSections(ArrayDeque<LaneSection> oSections, double dStart, double dEnd)
	{
		oSections.clear();
		int nIndex = size();
		double dSectionEnd = m_dLength;
		while (nIndex-- > 0)
		{
			LaneSection oTemp = get(nIndex);
			if (dStart < dSectionEnd && dEnd > oTemp.m_dS)
				oSections.addFirst(oTemp);
			dSectionEnd = oTemp.m_dS;
		}
	}


	void getShoulderCtrlLineArcs(ArrayList<CtrlLineArcs> oCtrlLineArcs, double dMaxStep)
	{
		int nType = XodrUtil.getLaneType("shoulder");
		for (LaneSection oSection : this)
		{
			ArrayList<double[]> oShoulders = new ArrayList();
			oSection.getShoulders(oShoulders);
			for (double[] dShoulder : oShoulders)
				oCtrlLineArcs.add(new CtrlLineArcs(m_nId, oSection.m_nId, 0, 0, nType, dShoulder, dMaxStep));
		}
	}
}
