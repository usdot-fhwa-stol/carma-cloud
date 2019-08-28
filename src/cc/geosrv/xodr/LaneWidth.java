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
public class LaneWidth implements Comparable<LaneWidth>
{
	public double m_dS;
	public double m_dA;
	public double m_dB;
	public double m_dC;
	public double m_dD;
	
	
	public LaneWidth(double dS, double dA, double dB, double dC, double dD)
	{
		m_dS = dS;
		m_dA = dA;
		m_dB = dB;
		m_dC = dC;
		m_dD = dD;
	}
	
	
	public static LaneWidth getLaneWidth(double dDist, ArrayList<LaneWidth> oLaneWidths)
	{
		int nIndex = oLaneWidths.size();
		while (nIndex-- > 0)
		{
			LaneWidth oTemp = oLaneWidths.get(nIndex);
			if (dDist >= oTemp.m_dS)
				return oTemp;
		}
		
		return oLaneWidths.get(0);
	}


	@Override
	public int compareTo(LaneWidth o)
	{
		return Double.compare(m_dS, o.m_dS);
	}
}
