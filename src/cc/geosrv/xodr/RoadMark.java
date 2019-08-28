/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.util.Arrays;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class RoadMark implements Comparable<RoadMark>
{
	public String m_sType;
	public double[] m_dLine;
	public String m_sColor;
	public double m_dS;
	
	public RoadMark(String sType, String sColor, double dS)
	{
		m_dLine = Arrays.newDoubleArray();
		m_dLine = Arrays.add(m_dLine, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		m_sType = sType;
		m_sColor = sColor;
		m_dS = dS;
	}
	
	
	public static RoadMark getRoadMark(double dDist, ArrayList<RoadMark> oRoadMarks)
	{
		int nIndex = oRoadMarks.size();
		while (nIndex-- > 0)
		{
			RoadMark oTemp = oRoadMarks.get(nIndex);
			if (dDist >= oTemp.m_dS)
				return oTemp;
		}
		
		return null;
	}


	@Override
	public int compareTo(RoadMark o)
	{
		return Double.compare(m_dS, o.m_dS);
	}
}
