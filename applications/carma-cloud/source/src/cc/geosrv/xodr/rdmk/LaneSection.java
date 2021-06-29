/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.geosrv.xodr.XodrUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 *
 * @author aaron.cherney
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
	
	public LaneSection()
	{
	}
	
	public LaneSection(double dS, int nId)
	{
		m_dS = dS;
		m_nId = nId;
	}
	
	
	public void setInnerRoadMarks()
	{
		setInnerRoadMarks(m_oLeft);
		setInnerRoadMarks(m_oRight);
	}
	
	
	private void setInnerRoadMarks(ArrayList<Lane> oLanes)
	{
		int nSize = oLanes.size();
		if (nSize > 0)
		{
			oLanes.get(0).m_oInnerRoadMarks = m_oCenter.m_oOuterRoadMarks;
			for (int i = 1; i < nSize; i++)
			{
				oLanes.get(i).m_oInnerRoadMarks = oLanes.get(i - 1).m_oOuterRoadMarks;
			}
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
		boolean bLeftEmpty = m_oLeft.isEmpty();
		boolean bRightEmpty = m_oRight.isEmpty();
		boolean bCenterNone = m_oCenter.m_nType == XodrUtil.getLaneType("none");
		Collections.sort(m_oLeft, LEFTLANES);
		setInnerRoadMarks(m_oLeft);
		m_oLeft.add(null);
		
		if (bRightEmpty) // there are no right lanes, so add null to mean latperm is none
			m_oLeft.add(0, null);
		else if (bCenterNone) // if center is none, use the first right lane
			m_oLeft.add(0, m_oRight.get(0));
		else 
			m_oLeft.add(0, m_oCenter);

		Collections.sort(m_oRight, RIGHTLANES);
		setInnerRoadMarks(m_oRight);
		m_oRight.add(null);
		
		if (bLeftEmpty) // there are no left lanes, so add null to mean latperm is none
			m_oRight.add(0, null);
		else if (bCenterNone) // if center is none, use the first left lane, which is now position 1 since we insert at position 0 above
			m_oRight.add(0, m_oLeft.get(1));
		else
			m_oRight.add(0, m_oCenter);
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
		for (int nIndex = 1; nIndex < m_oLeft.size() - 1; nIndex++)
			oLaneList.add(m_oLeft.get(nIndex));
		for (int nIndex = 1; nIndex < m_oRight.size() - 1; nIndex++)
			oLaneList.add(m_oRight.get(nIndex));
	}
}