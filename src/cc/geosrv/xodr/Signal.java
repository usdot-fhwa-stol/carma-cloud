/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.util.Arrays;

/**
 *
 * @author aaron.cherney
 */
public class Signal implements Comparable<Signal>
{
	public static int POSORI = 0;
	public static int NEGORI = 1;
	public static int BOTHORI = 2;
	public String m_sId;
	public int m_nOri;
	public int[] m_nRoads = Arrays.newIntArray();
	public String m_sType;
	public boolean m_bDynamic;
	
	public Signal()
	{
	}
	
	public Signal(String sId, String sOri, int nRoadId, String sType, String sDynamic)
	{
		m_sId = sId;
		if (sOri.compareTo("+") == 0)
			m_nOri = POSORI;
		else if (sOri.compareTo("-") == 0)
			m_nOri = NEGORI;
		else
			m_nOri = BOTHORI;
		m_nRoads = Arrays.add(m_nRoads, nRoadId);
		m_sType = sType;
		m_bDynamic = sDynamic.compareTo("yes") == 0;
	}
	
	
	public void addRoadId(int nRoadId)
	{
		int nIndex = java.util.Arrays.binarySearch(m_nRoads, 1, m_nRoads[0], nRoadId);
		if (nIndex < 0)
			m_nRoads = Arrays.insert(m_nRoads, nRoadId, ~nIndex);
	}
	
	
	@Override
	public int compareTo(Signal o)
	{
		return m_sId.compareTo(o.m_sId);
	}
}
