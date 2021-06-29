/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.pvmt;

import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author Federal Highway Administration
 */
public class Lane extends ArrayList<LaneWidth> implements Comparable<Lane>
{
	public int m_nLaneIndex;
	public double[] m_dOuter;
	public double[] m_dInner;
	public double[] m_dCenter;
	public int m_nRoadId;
	public int m_nSectionId;
	public int m_nLaneIdByRoad;
	public int m_nLaneType;
	public double m_dLastLaneWidth = Double.MAX_VALUE;
	public double[] m_dOuterW;
	public int m_nMaxSpeed;
	
	public Lane()
	{
		super();
		m_dOuter = Arrays.newDoubleArray();
		m_dOuter = Arrays.add(m_dOuter, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		m_dOuterW = Arrays.newDoubleArray();
		m_dOuterW = Arrays.add(m_dOuterW, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		m_dCenter = Arrays.newDoubleArray();
		m_dCenter = Arrays.add(m_dCenter, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
	}
	
	
	public Lane(int nId, String sType, int nRoadId, int nSectionId, int nMaxSpeed)
	{
		this();
		m_nLaneType = XodrUtil.getLaneType(sType);
		m_nLaneIndex = nId;
		m_nRoadId = nRoadId;
		m_nSectionId = nSectionId;
		m_nMaxSpeed = nMaxSpeed;
	}

	
	
	public void writePts(Writer oOut)
	   throws IOException
	{
		oOut.append(String.format("%d,%d,%d", m_nRoadId, m_nSectionId, m_nLaneIndex));
		oOut.append(String.format(",%3.7f,%3.7f,%3.7f,%3.7f", m_dCenter[1], m_dCenter[2], m_dCenter[3], m_dCenter[4]));
		Iterator<double[]> oIt = Arrays.iterator(m_dCenter, new double[3], 5, 3);
		while (oIt.hasNext())
		{
			double[] dPt = oIt.next();
			oOut.append(String.format(",%3.7f,%3.7f,%3.7f", dPt[0], dPt[1], dPt[2]));
		}
		oOut.append("\n");
	}


	@Override
	public int compareTo(Lane o)
	{
		int nRet = Integer.compare(m_nRoadId, o.m_nRoadId);
		if (nRet == 0)
			nRet = Integer.compare(m_nLaneIdByRoad, o.m_nLaneIdByRoad);
		
		return nRet;
	}
}
