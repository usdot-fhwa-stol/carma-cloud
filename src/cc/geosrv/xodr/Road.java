/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 *
 * @author Federal Highway Administration
 */
public class Road extends ArrayList<LaneSection>
{
	public int m_nId;
	public double m_dLength;
	public double[] m_dMeters;
	public double[] m_dTrack;
	public double[] m_dLaneZero;
	public double[] m_dBounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
	ArrayList<LaneOffset> m_oLaneOffsets = new ArrayList();
	ArrayList<Geometry> m_oGeometries = new ArrayList();
	
	protected Road()
	{
		
	}
	
	
	public Road(int nId, double dLength)
	{
		m_nId = nId;
		m_dLength = dLength;
		m_dTrack = Arrays.newDoubleArray();
		m_dLaneZero = Arrays.newDoubleArray();
		m_dMeters = Arrays.newDoubleArray();
	}
	
	
	public void createPolygons()
	{
		ArrayList<Lane> oLanes = new ArrayList();
		double[] dPoint = new double[2];
		for (LaneSection oSection : this)
		{
			oSection.setInnerPaths();
			oSection.createPolygons();
			oSection.getLanes(oLanes);
			for (Lane oLane : oLanes)
			{
				double[] dPoly = oLane.m_dPolygon;
				if (dPoly[1] < m_dBounds[0])
					m_dBounds[0] = dPoly[1];
				if (dPoly[2] < m_dBounds[1])
					m_dBounds[1] = dPoly[2];
				if (dPoly[3] > m_dBounds[2])
					m_dBounds[2] = dPoly[3];
				if (dPoly[4] > m_dBounds[3])
					m_dBounds[3] = dPoly[4];
				
				int nSize = oLane.m_oRoadMarks.size();
				for (int i = 0; i < nSize - 1; i++)
				{
					RoadMark oCurrent = oLane.m_oRoadMarks.get(i);
					RoadMark oNext = oLane.m_oRoadMarks.get(i + 1);
					if (Arrays.size(oNext.m_dLine) > 5)
						oCurrent.m_dLine = Geo.addPoint(oNext.m_dLine[5], oNext.m_dLine[6], oCurrent.m_dLine, Proj.NULLPROJ, dPoint);
				}
			}
		}
	}
	
	
	public void createPoints(double dMaxStep, Proj oProj, double[] dPoint)
	{
		Collections.sort(m_oGeometries);
		for (Geometry oGeo : m_oGeometries)
			oGeo.addPoints(this, dMaxStep, oProj, dPoint);
	}
	
	
	public void writeLanes(BufferedWriter oLeftLanes, BufferedWriter oRightLanes, BufferedWriter oCenter, double[] dPoint, boolean bLines)
	   throws Exception
	{
		for (LaneSection oSection : this)
		{
			if (bLines)
			{
				writePolyline(oCenter, oSection.m_oCenter.m_dOuter, dPoint, 5, 2, false);
				for (Lane oLeft : oSection.m_oLeft)
					writePolyline(oLeftLanes, oLeft.m_dOuter, dPoint, 5, 2, false);

				for (Lane oRight : oSection.m_oRight)
					writePolyline(oRightLanes, oRight.m_dOuter, dPoint, 5, 2, false);
			}
			else
			{
				for (Lane oLeft : oSection.m_oLeft)
					writePolyline(oLeftLanes, oLeft.m_dPolygon, dPoint, 5, 2, true);

				for (Lane oRight : oSection.m_oRight)
					writePolyline(oRightLanes, oRight.m_dPolygon, dPoint, 5, 2, true);
			}
		}
	}
	
	
	public void writePolyline(BufferedWriter oOut, double[] dPoints, double[] dPoint, int nStart, int nGroup, boolean bRepeatFirst)
	   throws Exception
	{
		Iterator<double[]> oIt = Arrays.iterator(dPoints, dPoint, nStart, nGroup);
		if (oIt.hasNext())
		{
			oIt.next();
			oOut.write(String.format("%2.7f,%2.7f", dPoint[0], dPoint[1]));
		}
		else
			return;
		while (oIt.hasNext())
		{
			oIt.next();
			oOut.write(String.format(",%2.7f,%2.7f", dPoint[0], dPoint[1]));
		}
		if (bRepeatFirst)
			oOut.write(String.format(",%2.7f,%2.7f", dPoints[nStart], dPoints[nStart + 1]));
		oOut.write("\n");
	}
}
