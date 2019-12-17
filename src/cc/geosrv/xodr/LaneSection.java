/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

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
	private static Comparator<Lane> LEFTLANES = (Lane o1, Lane o2) -> Integer.compare(o1.m_nId, o2.m_nId);
	private static Comparator<Lane> RIGHTLANES = (Lane o1, Lane o2) -> Integer.compare(o2.m_nId, o1.m_nId);
	
	public LaneSection()
	{
	}
	
	public LaneSection(double dS)
	{
		this();
		m_dS = dS;
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
		if (oLane.m_nId > 0)
			m_oLeft.add(oLane);
		else if (oLane.m_nId < 0)
			m_oRight.add(oLane);
	}
	
	
	public void sortLanes()
	{
		Collections.sort(m_oLeft, LEFTLANES);
		Collections.sort(m_oRight, RIGHTLANES);
	}
	
	
	public void createPolygons()
	{
		for (Lane oLane : m_oLeft)
			oLane.createPolygon();
		for (Lane oLane : m_oRight)
			oLane.createPolygon();
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
}
