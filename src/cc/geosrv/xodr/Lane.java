/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author Federal Highway Administration
 */
public class Lane extends ArrayList<LaneWidth>
{
	public int m_nId;
	public String m_sType;
	public double[] m_dOuter;
	public double[] m_dInner;
	public double[] m_dPolygon;
	public ArrayList<RoadMark> m_oRoadMarks = new ArrayList();
	public Control m_oControl = new Control();
	
	public Lane()
	{
		super();
		m_dOuter = Arrays.newDoubleArray();
		m_dOuter = Arrays.add(m_dOuter, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		m_dPolygon = Arrays.newDoubleArray();
		m_dPolygon = Arrays.add(m_dPolygon, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
	}
	
	
	public Lane(int nId, String sType)
	{
		this();
		m_nId = nId;
		m_sType = sType;
	}
	
	
	public void createPolygon()
	{
		double[] dPoint = new double[2];
		if (m_nId > 0) // left lanes. add outer in order, then inner in reverse
			addPointsToPolygon(m_dOuter, m_dInner, dPoint);
		else // right lanes, add inner in order, then outer in reverse
			addPointsToPolygon(m_dInner, m_dOuter, dPoint);
	}
	
	public void addPointsToPolygon(double[] dInOrder, double[] dReverse, double[] dPoint)
	{
		int nLimit = Arrays.size(dInOrder);
		m_dPolygon = Arrays.ensureCapacity(m_dPolygon, nLimit - 5);
		for (int i = 5; i < nLimit;)
			m_dPolygon = Geo.addPoint(dInOrder[i++], dInOrder[i++], m_dPolygon, Proj.NULLPROJ, dPoint);

		nLimit = Arrays.size(dReverse);
		m_dPolygon = Arrays.ensureCapacity(m_dPolygon, nLimit - 5);
		for (int i = nLimit - 2; i >= 5; i -= 2)
			m_dPolygon = Geo.addPoint(dReverse[i], dReverse[i + 1], m_dPolygon, Proj.NULLPROJ, dPoint);
	}
	
	
	public boolean isClockwise()
	{
		double dWinding = 0;
		double[] dLine = new double[4];
		Iterator<double[]> oIt = Arrays.iterator(m_dPolygon, dLine, 5, 4);
		while (oIt.hasNext())
		{
			oIt.next();
			dWinding += (dLine[2] - dLine[0]) * (dLine[3] + dLine[1]);
		}
		dWinding += m_dPolygon[5] - dLine[2] * m_dPolygon[6] + dLine[3];
		return dWinding > 0;
	}
}
