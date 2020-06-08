/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.ctrl.CtrlLineArcs;
import cc.geosrv.Proj;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.Geo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
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
	private static Comparator<int[]> RDMKCOMP = (int[] n1, int[] n2) -> 
	{
		int nRet = Integer.compare(n1[2], n2[2]);
		if (nRet == 0)
			nRet = Integer.compare(n1[1], n2[1]);
		return nRet;
	};
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
	
	
//	public void setInnerRoadMarks()
//	{
//		for (LaneSection oSection : this)
//			oSection.setInnerRoadMarks();
//	}

	
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
			get(nIndex).getLanes(oCurrLanes);
			for (Lane oCurr: oCurrLanes)
			{
				if (oCurr.m_nLaneIndex == 0)
					continue;
				double[] dCurr = oCurr.m_dCenters.get(0);
				oCurr.m_nLaneIdByRoad = Integer.MIN_VALUE;
				for (Lane oPrev: oPrevLanes)
				{
					if (oPrev.m_nLaneIndex == 0)
						continue;
					double[] dPrev = oPrev.m_dCenters.get(oPrev.m_dCenters.size() - 1);
					int nSize = Arrays.size(dPrev);	
					
					double dDist = Geo.distance(dCurr[5], dCurr[6], dPrev[nSize - 3], dPrev[nSize - 2]);
					if (dDist > dTol)
						System.out.println(dDist);
					if (dDist <= dTol && oCurr.m_nType == oCurr.m_nType)
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
	
	
	public void getRdMkCtrlLineArcs(ArrayList<CtrlLineArcs> oCtrlLineArcs, double dMaxStep, ArrayList<int[]> oRdMkInfo)
	{
		ArrayList<Lane> oLanes = new ArrayList();
		int[] nSearch = new int[3];
		for (LaneSection oSection : this)
		{
			oSection.getLanes(oLanes);
			for (Lane oLane : oLanes)
			{
				if (oLane.m_nLaneIndex == 0) // skip zero width center lane
				{
					continue;
				}
//				if (oLane.m_nLaneIndex != -2)
//					continue;
				
				XodrUtil.splitLaneSectionId(XodrUtil.getLaneSectionId(m_nId, oSection.m_nId, oLane.m_nLaneIndex), 1, nSearch);
				int nIndex = Collections.binarySearch(oRdMkInfo, nSearch, RDMKCOMP);
				if (nIndex < 0)
				{
					oLane.m_nRdMkTags = Arrays.insert(oLane.m_nRdMkTags, nSearch[1], 1);
					oLane.m_nRdMkTags = Arrays.insert(oLane.m_nRdMkTags, nSearch[2], 2);
					oRdMkInfo.add(~nIndex, oLane.m_nRdMkTags);	
				}
				else
				{
					int[] nRdMkInfo = oRdMkInfo.get(nIndex);
					Iterator<int[]> oIt = Arrays.iterator(oLane.m_nRdMkTags, new int[8], 1, 8);
					while (oIt.hasNext())
					{
						int[] nTags = oIt.next();
						nRdMkInfo = Arrays.add(nRdMkInfo, nTags);
					}
				}
				for (double[] dCenter : oLane.m_dCenters)
				{
					oCtrlLineArcs.add(new CtrlLineArcs(m_nId, oSection.m_nId, oLane.m_nLaneIndex, oLane.m_nLaneIdByRoad, oLane.m_nType, dCenter, dMaxStep));
				}
			}
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
}

