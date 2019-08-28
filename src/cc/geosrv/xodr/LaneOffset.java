/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class LaneOffset implements Comparable<LaneOffset>
{
	public double m_dS;
	public double m_dA;
	public double m_dB;
	public double m_dC;
	public double m_dD;
	
	public LaneOffset(double dS, double dA, double dB, double dC, double dD)
	{
		m_dS = dS;
		m_dA = dA;
		m_dB = dB;
		m_dC = dC;
		m_dD = dD;
	}


	@Override
	public int compareTo(LaneOffset o)
	{
		return Double.compare(m_dS, o.m_dS);
	}
	
	
	public static LaneOffset getLaneOffset(double dDist, ArrayList<LaneOffset> oLaneOffsets)
	{
		int nIndex = oLaneOffsets.size();
		while (nIndex-- > 0)
		{
			LaneOffset oTemp = oLaneOffsets.get(nIndex);
			if (dDist >= oTemp.m_dS)
				return oTemp;
		}
		
		return oLaneOffsets.get(0);
	}
}
