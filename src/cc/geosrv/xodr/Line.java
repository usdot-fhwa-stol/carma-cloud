/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;

/**
 *
 * @author Federal Highway Administration
 */
public class Line extends Geometry
{
	public Line(double dS, double dX, double dY, double dH, double dL)
	{
		super(dS, dX, dY, dH, dL);
	}


	@Override
	public void addPoints(Road oRoad, double dMaxStep, Proj oProj, double[] dPoint)
	{
		int nLimit;
		double dStep;
		if (dMaxStep > m_dL)
		{
			nLimit = 2;
			dStep = m_dL;
		}
		else
		{
			nLimit = (int)(m_dL / dMaxStep) + 1;
			dStep = m_dL / nLimit;
		}
		
		oRoad.m_dTrack = Arrays.ensureCapacity(oRoad.m_dTrack, nLimit * 2);
		oRoad.m_dLaneZero = Arrays.ensureCapacity(oRoad.m_dLaneZero, nLimit * 2);
		double dDeltaX = dStep * Math.cos(m_dH);
		double dDeltaY = dStep * Math.sin(m_dH);
		for (int i = 0; i <= nLimit; i++)
		{
			double dX = m_dX + dDeltaX * i;
			double dY = m_dY + dDeltaY * i;
			oRoad.m_dMeters = Arrays.add(oRoad.m_dMeters, dX, dY);
			oRoad.m_dTrack = Geo.addPoint(dX, dY, oRoad.m_dTrack, oProj, dPoint);
			
			double dLengthAlongRoad = dStep * i + m_dS;
			LaneOffset oLaneOffset = LaneOffset.getLaneOffset(dLengthAlongRoad, oRoad.m_oLaneOffsets);
			double dT = MathUtil.cubic(dLengthAlongRoad - oLaneOffset.m_dS, oLaneOffset.m_dA, oLaneOffset.m_dB, oLaneOffset.m_dC, oLaneOffset.m_dD);
			double dXLaneZero = dX - Math.sin(m_dH) * dT;
			double dYLaneZero = dY + Math.cos(m_dH) * dT;
			oRoad.m_dLaneZero = Geo.addPoint(dXLaneZero, dYLaneZero, oRoad.m_dLaneZero, oProj, dPoint);
			
			addLanePoints(oRoad, m_dH, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dPoint);
		}
	}
}
