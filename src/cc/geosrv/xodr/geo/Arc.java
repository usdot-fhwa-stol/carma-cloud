/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.geo;

import cc.geosrv.Mercator;
import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 *
 * @author Federal Highway Administration
 */
public class Arc extends Geometry
{
	public double m_dC;
	
	public Arc(double dS, double dX, double dY, double dH, double dL, double dC)
	{
		super(dS, dX, dY, dH, dL);
		m_dC = dC;
	}

	
	@Override
	public void addPoints(Road oRoad, double dMaxStep, boolean bIncludeEnd, Proj oProj, double[] dProjPt)
	{
		try
		{
			int nLimit;
			double dEndDistOnRoad = m_dS + m_dL;
			ArrayDeque<LaneSection> oDeque = new ArrayDeque();
			oRoad.getLaneSections(oDeque, m_dS, dEndDistOnRoad);
			double dR = 1.0 / m_dC;
			double dH = m_dX + dR * Math.cos(m_dHdg + Mercator.PI_OVER_TWO);
			double dK = m_dY + dR * Math.sin(m_dHdg + Mercator.PI_OVER_TWO);
			double dInitTheta = m_dHdg + 3.0 * Mercator.PI_OVER_TWO;
			double dTotalAngle = 0;
			while (!oDeque.isEmpty())
			{
				LaneSection oSection = oDeque.removeFirst();
				double dStart = Math.max(m_dS, oSection.m_dS);
				double dEnd = oDeque.isEmpty() ? dEndDistOnRoad : oDeque.getFirst().m_dS;
				double dLength = dEnd - dStart;

				if (dMaxStep > dLength)
					nLimit = 1;
				else
					nLimit = (int)(dLength / dMaxStep) + 1;

				double dTheta = dLength * m_dC;

				oRoad.m_dLaneZero = Arrays.ensureCapacity(oRoad.m_dLaneZero, nLimit * 2);
				oRoad.m_dTrack = Arrays.ensureCapacity(oRoad.m_dTrack, nLimit * 2);
				double dAngleStep = dTheta / nLimit;
				double dLengthStep = dLength / nLimit;
				if (bIncludeEnd && oDeque.isEmpty()) // add last point for last section and last geometry
					++nLimit;
				for (int i = 0; i < nLimit; i++)
				{
					double dAngle = dInitTheta + dTotalAngle;
					dTotalAngle += dAngleStep;
					double dX = dH + dR * Math.cos(dAngle);
					double dY = dK + dR * Math.sin(dAngle);
					oRoad.m_dTrack = addProjectedPoint(dX, dY, oRoad.m_dTrack, oProj, dProjPt);
					oRoad.m_dTrack = Arrays.add(oRoad.m_dTrack, 0);

					double dLengthAlongRoad = dLengthStep * i + dStart;
					LaneOffset oLaneOffset = LaneOffset.getLaneOffset(dLengthAlongRoad, oRoad.m_oLaneOffsets);
					double dHdg = m_dHdg + dTotalAngle;
					double dT = MathUtil.cubic(dLengthAlongRoad - oLaneOffset.m_dS, oLaneOffset.m_dA, oLaneOffset.m_dB, oLaneOffset.m_dC, oLaneOffset.m_dD);
					double dXLaneZero = dX - Math.sin(dHdg) * dT;
					double dYLaneZero = dY + Math.cos(dHdg) * dT;
					oRoad.m_dLaneZero = addProjectedPoint(dXLaneZero, dYLaneZero, oRoad.m_dLaneZero, oProj, dProjPt);
					oRoad.m_dLaneZero = Arrays.add(oRoad.m_dLaneZero, 0);

					addLanePoints(oRoad, dHdg, dXLaneZero, dYLaneZero, dLengthAlongRoad, oSection, oProj, dProjPt);
				}
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
	
	public void addLanePoints(Road oRoad, double dHdg, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, LaneSection oLaneSection, Proj oProj, double[] dProjPt, double dH, double dK, double dR, double dAngle)
	{
		oProj.cs2cs(dXLaneZero, dYLaneZero, dProjPt);
		if (oRoad.m_dLastGeo != m_dS)
		{
			oRoad.m_dLastGeo = m_dS;
			oRoad.m_dGeoPoints.add(new double[]{dProjPt[0], dProjPt[1]});
		}
		oLaneSection.m_oCenter.m_dOuter = Arrays.addAndUpdate(oLaneSection.m_oCenter.m_dOuter, dProjPt[0], dProjPt[1]);
		addLanePoints(oLaneSection, oLaneSection.m_oLeft, dHdg + Math.PI / 2, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dProjPt, oRoad, dH, dK, dR, dAngle);
		addLanePoints(oLaneSection, oLaneSection.m_oRight, dHdg - Math.PI / 2, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dProjPt, oRoad, dH, dK, dR, dAngle);
	}
	
	
	private void addLanePoints(LaneSection oLaneSection, ArrayList<Lane> oLanes, double dTangent, double dXLaneZero, double dYLaneZero, double dLengthAlongRoad, Proj oProj, double[] dProjPt, Road oRoad, double dH, double dK, double dR, double dAngle)
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
			double dHdg = dR > 0 ? Geo.heading(dH, dK, dXPrevLane, dYPrevLane) : Geo.heading(dXPrevLane, dYPrevLane, dH, dK);
			double dOuterH = dH + Math.cos(dHdg) * dT;
			double dOuterK = dK + Math.sin(dHdg) * dT;
//			double dOuterR = dR > 0 ? dR + dT : dR - dT;
			double dXNextLane = dOuterH + Math.cos(dAngle) * dR;
			double dYNextLane = dOuterK + Math.sin(dAngle) * dR;
			
//			double dXNextLane = dOuterH + Math.cos(dAngle) * dR;
//			double dYNextLane = dOuterK + Math.sin(dAngle) * dR;
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
}
