/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Proj;
import cc.util.Geo;
import cc.util.MathUtil;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public abstract class Geometry implements Comparable<Geometry>
{
	public double m_dS;
	public double m_dX;
	public double m_dY;
	public double m_dH;
	public double m_dL;
	
	public Geometry(double dS, double dX, double dY, double dH, double dL)
	{
		m_dS = dS;
		m_dX = dX;
		m_dY = dY;
		m_dH = dH;
		m_dL = dL;
	}
	
	
	public abstract void addPoints(Road oRoad, double dMaxStep, Proj oProj, double[] dPoint);

	
	public void addLanePoints(Road oRoad, double dTangent, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, Proj oProj, double[] dPoint)
	{
		LaneSection oLaneSection = LaneSection.getLaneSection(dLengthAlongRoad, oRoad);
		oLaneSection.m_oCenter.m_dOuter = Geo.addPoint(dXLaneZero, dYLaneZero, oLaneSection.m_oCenter.m_dOuter, oProj, dPoint);
		RoadMark oRoadMark = RoadMark.getRoadMark(dLengthAlongRoad - oLaneSection.m_dS, oLaneSection.m_oCenter.m_oRoadMarks);
		if (oRoadMark != null)
			oRoadMark.m_dLine = Geo.addPoint(dXLaneZero, dYLaneZero, oRoadMark.m_dLine, oProj, dPoint);
		addLanePoints(oLaneSection, oLaneSection.m_oLeft, dTangent, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dPoint);
		addLanePoints(oLaneSection, oLaneSection.m_oRight, dTangent - Math.PI, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dPoint);
	}
	
	
	private void addLanePoints(LaneSection oLaneSection, ArrayList<Lane> oLanes, double dTangent, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, Proj oProj, double[] dPoint)
	{
		double dXLanePrime = dXLaneZero;
		double dYLanePrime = dYLaneZero;
		for (Lane oLane : oLanes)
		{
			double dLengthAlongSection = dLengthAlongRoad - oLaneSection.m_dS;
			LaneWidth oLaneWidth = LaneWidth.getLaneWidth(dLengthAlongSection, oLane);
			double dT = MathUtil.cubic(dLengthAlongSection - oLaneWidth.m_dS, oLaneWidth.m_dA, oLaneWidth.m_dB, oLaneWidth.m_dC, oLaneWidth.m_dD);
			dXLanePrime = dXLanePrime - Math.sin(dTangent) * dT;
			dYLanePrime = dYLanePrime + Math.cos(dTangent) * dT;
			oLane.m_dOuter = Geo.addPoint(dXLanePrime, dYLanePrime, oLane.m_dOuter, oProj, dPoint);
			RoadMark oRoadMark = RoadMark.getRoadMark(dLengthAlongSection, oLane.m_oRoadMarks);
			if (oRoadMark != null)
				oRoadMark.m_dLine = Geo.addPoint(dXLanePrime, dYLanePrime, oRoadMark.m_dLine, oProj, dPoint);
		}
	}
	
	@Override
	public int compareTo(Geometry o)
	{
		return Double.compare(m_dS, o.m_dS);
	}
}
