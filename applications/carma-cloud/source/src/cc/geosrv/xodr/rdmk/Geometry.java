/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.geosrv.Proj;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;
import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
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
		addLanePoints(oLaneSection, oLaneSection.m_oLeft, dHdg + Math.PI / 2, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dProjPt);
		addLanePoints(oLaneSection, oLaneSection.m_oRight, dHdg - Math.PI / 2, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dProjPt);
	}
	
	
	private void addLanePoints(LaneSection oLaneSection, ArrayList<Lane> oLanes, double dTangent, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, Proj oProj, double[] dProjPt)
	{
		double dXPrevLane = dXLaneZero;
		double dYPrevLane = dYLaneZero;
		oProj.cs2cs(dXPrevLane, dYPrevLane, dProjPt);
		double dXProjPrevLane = dProjPt[0];
		double dYProjPrevLane = dProjPt[1];
		for (int nIndex = 1; nIndex < oLanes.size() - 1; nIndex++)  // center lane is index 0, null is last index
		{
			Lane oLane = oLanes.get(nIndex);
			double dLengthAlongSection = dLengthAlongRoad - oLaneSection.m_dS;
			RoadMark oInnerRdMk = RoadMark.getRoadMark(dLengthAlongSection, oLane.m_oInnerRoadMarks);
			RoadMark oOuterRdMk = RoadMark.getRoadMark(dLengthAlongSection, oLane.m_oOuterRoadMarks);
			if (oInnerRdMk.m_dS != oLane.m_dLastInnerRdMkS || oOuterRdMk.m_dS != oLane.m_dLastOuterRdMkS)
			{
				Lane oInner = oLanes.get(nIndex - 1);
				Lane oOuter = oLanes.get(nIndex + 1);
				double[] dCenter = Arrays.newDoubleArray();
				dCenter = Arrays.add(dCenter, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				oLane.m_dCenters.add(dCenter);
				oLane.m_dLastInnerRdMkS = oInnerRdMk.m_dS;
				oLane.m_dLastOuterRdMkS = oOuterRdMk.m_dS;
				int nNone = XodrUtil.getLaneType("none");
				int nInnerDir = 1;
				int nOuterDir = 1;
				int nInnerLaneType;
				int nOuterLaneType;
				if (oInner == null)
				{
					nInnerLaneType = nNone;
				}
				else
				{
					nInnerLaneType = oInner.m_nType;
					if (oLane.m_nLaneIndex > 0 && oInner.m_nLaneIndex > 0 ||
					    oLane.m_nLaneIndex < 0 && oInner.m_nLaneIndex < 0)
						nInnerDir = 0;
				}
				if (oOuter == null)
				{
					nOuterLaneType = nNone;
				}
				else
				{
					nOuterLaneType = oOuter.m_nType;
					if (oLane.m_nLaneIndex > 0 && oOuter.m_nLaneIndex > 0 ||
					    oLane.m_nLaneIndex < 0 && oOuter.m_nLaneIndex < 0)
						nOuterDir = 0;
				}
				oLane.m_nRdMkTags = Arrays.add(oLane.m_nRdMkTags, new int[]{oInnerRdMk.m_nType, oInnerRdMk.m_nColor, nInnerLaneType, nInnerDir});
				oLane.m_nRdMkTags = Arrays.add(oLane.m_nRdMkTags, new int[]{oOuterRdMk.m_nType, oOuterRdMk.m_nColor, nOuterLaneType, nOuterDir});
			}
			double[] dCenter = oLane.m_dCenters.get(oLane.m_dCenters.size() - 1);
			LaneWidth oLaneWidth = LaneWidth.getLaneWidth(dLengthAlongSection, oLane);
			double dT = MathUtil.cubic(dLengthAlongSection - oLaneWidth.m_dS, oLaneWidth.m_dA, oLaneWidth.m_dB, oLaneWidth.m_dC, oLaneWidth.m_dD);
			double dXNextLane = dXPrevLane + Math.cos(dTangent) * dT;
			double dYNextLane = dYPrevLane + Math.sin(dTangent) * dT;
			oProj.cs2cs(dXNextLane, dYNextLane, dProjPt);
			double dProjW = Geo.distance(dXProjPrevLane, dYProjPrevLane, dProjPt[0], dProjPt[1]);
			dXProjPrevLane = dProjPt[0];
			dYProjPrevLane = dProjPt[1];
			double dXPath = (dXPrevLane + dXNextLane) / 2;
			double dYPath = (dYPrevLane + dYNextLane) / 2;
			dCenter = addProjectedPoint(dXPath, dYPath, dCenter, oProj, dProjPt);
			dCenter = Arrays.add(dCenter, dProjW);
			oLane.m_dCenters.set(oLane.m_dCenters.size() - 1, dCenter);
			dXPrevLane = dXNextLane;
			dYPrevLane = dYNextLane;			
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
