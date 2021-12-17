/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.pvmt;

import cc.geosrv.Proj;
import cc.util.Arrays;
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
	public double m_dHdg;
	public double m_dL;
	
	public Geometry(double dS, double dX, double dY, double dH, double dL)
	{
		m_dS = dS;
		m_dX = dX;
		m_dY = dY;
		m_dHdg = dH;
		m_dL = dL;
	}
	
	
	public abstract void addPoints(Road oRoad, double dMaxStep, boolean bIncludeEnd, Proj oProj, double[] dProjPt);
	
	public void addLanePoints(Road oRoad, double dHdg, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, LaneSection oLaneSection, Proj oProj, double[] dProjPt)
	{
		oProj.cs2cs(dXLaneZero, dYLaneZero, dProjPt);
		if (oRoad.m_dLastGeo != m_dS)
		{
			oRoad.m_dLastGeo = m_dS;
			oRoad.m_dGeoPoints.add(new double[]{dProjPt[0], dProjPt[1]});
		}
		oLaneSection.m_oCenter.m_dOuter = Arrays.addAndUpdate(oLaneSection.m_oCenter.m_dOuter, dProjPt[0], dProjPt[1]);
		addLanePoints(oLaneSection, oLaneSection.m_oLeft, dHdg + Math.PI / 2, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dProjPt, oRoad);
		addLanePoints(oLaneSection, oLaneSection.m_oRight, dHdg - Math.PI / 2, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dProjPt, oRoad);
	}
	
	
	private void addLanePoints(LaneSection oLaneSection, ArrayList<Lane> oLanes, double dTangent, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, Proj oProj, double[] dProjPt, Road oRoad)
	{
		double dXPrevLane = dXLaneZero;
		double dYPrevLane = dYLaneZero;
		oProj.cs2cs(dXPrevLane, dYPrevLane, dProjPt);
		double dXProjPrevLane = dProjPt[0];
		double dYProjPrevLane = dProjPt[1];
		for (Lane oLane : oLanes)
		{
			double dPx = dXProjPrevLane;
			double dPy = dYProjPrevLane;
			double dLengthAlongSection = dLengthAlongRoad - oLaneSection.m_dS;
			LaneWidth oLaneWidth = LaneWidth.getLaneWidth(dLengthAlongSection, oLane);
			double dT = MathUtil.cubic(dLengthAlongSection - oLaneWidth.m_dS, oLaneWidth.m_dA, oLaneWidth.m_dB, oLaneWidth.m_dC, oLaneWidth.m_dD);
			double dXNextLane = dXPrevLane + Math.cos(dTangent) * dT;
			double dYNextLane = dYPrevLane + Math.sin(dTangent) * dT;
			oLane.m_dOuter = addProjectedPoint(dXNextLane, dYNextLane, oLane.m_dOuter, oProj, dProjPt);
			oLane.m_dOuterW = Arrays.addAndUpdate(oLane.m_dOuterW, dProjPt[0], dProjPt[1]);
			oLane.m_dOuterW = Arrays.add(oLane.m_dOuterW, 0);
			double dProjW = Geo.distance(dXProjPrevLane, dYProjPrevLane, dProjPt[0], dProjPt[1]);
			dXProjPrevLane = dProjPt[0];
			dYProjPrevLane = dProjPt[1];
			double dXPath = (dXPrevLane + dXNextLane) / 2;
			double dYPath = (dYPrevLane + dYNextLane) / 2;
			oLane.m_dCenter = addProjectedPoint(dXPath, dYPath, oLane.m_dCenter, oProj, dProjPt);
			if (this instanceof Arc)
			{
				oRoad.m_dPerps.add(new double[]{dPx, dPy, dXProjPrevLane, dYProjPrevLane});
			}
			if (oLaneWidth.m_dS != oLane.m_dLastLaneWidth)
			{
				oLane.m_dLastLaneWidth = oLaneWidth.m_dS;
				oRoad.m_dLWPoints.add(new double[]{dProjPt[0], dProjPt[1]});
			}
			
			dXPrevLane = dXNextLane;
			dYPrevLane = dYNextLane;
			
			oLane.m_dCenter = Arrays.add(oLane.m_dCenter, dProjW);
		}
	}
	
	
	static double[] addProjectedPoint(double dX, double dY, double[] dPts, Proj oProj, double[] dPoint)
	{
		oProj.cs2cs(dX, dY, dPoint);
		dX = dPoint[0]; // transform coordinates
		dY = dPoint[1]; // and set local reference
		dPts = Arrays.addAndUpdate(dPts, dX, dY);
		return dPts;
	}
	
	@Override
	public int compareTo(Geometry o)
	{
		return Double.compare(m_dS, o.m_dS);
	}
}
