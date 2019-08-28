/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Mercator;
import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;

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
	public void addPoints(Road oRoad, double dMaxStep, Proj oProj, double[] dPoint)
	{
		int nLimit;
		if (dMaxStep > m_dL)
			nLimit = 2;
		else
			nLimit = (int)(m_dL / dMaxStep) + 1;
		double dTheta = m_dL * m_dC;
		double dR = 1.0 / m_dC;
		double dH = m_dX + dR * Math.cos(m_dH + Mercator.PI_OVER_TWO);
		double dK = m_dY + dR * Math.sin(m_dH + Mercator.PI_OVER_TWO);

		oRoad.m_dLaneZero = Arrays.ensureCapacity(oRoad.m_dLaneZero, nLimit * 2);
		oRoad.m_dTrack = Arrays.ensureCapacity(oRoad.m_dTrack, nLimit * 2);
		double dAngleStep = dTheta / nLimit;
		double dInitTheta = m_dH + 3.0 * Mercator.PI_OVER_TWO;
		double dLengthStep = dR * dAngleStep;
		for (int i = 0; i <= nLimit; i++) // start point added by geometry tag or previous geometry
		{
			double dAngle = dInitTheta + dAngleStep * i;
			double dX = dH + dR * Math.cos(dAngle);
			double dY = dK + dR * Math.sin(dAngle);
			oRoad.m_dMeters = Arrays.add(oRoad.m_dMeters, dX, dY);
			oRoad.m_dTrack = Geo.addPoint(dX, dY, oRoad.m_dTrack, oProj, dPoint);
			
			double dLengthAlongRoad = dLengthStep * i + m_dS;
			LaneOffset oLaneOffset = LaneOffset.getLaneOffset(dLengthAlongRoad, oRoad.m_oLaneOffsets);
			double dTangent = m_dH + dAngleStep * i;
			double dT = MathUtil.cubic(dLengthAlongRoad - oLaneOffset.m_dS, oLaneOffset.m_dA, oLaneOffset.m_dB, oLaneOffset.m_dC, oLaneOffset.m_dD);
			double dXLaneZero = dX - Math.sin(dTangent) * dT;
			double dYLaneZero = dY + Math.cos(dTangent) * dT;
			oRoad.m_dLaneZero = Geo.addPoint(dXLaneZero, dYLaneZero, oRoad.m_dLaneZero, oProj, dPoint);
			
			addLanePoints(oRoad, dTangent, dXLaneZero, dYLaneZero, dLengthAlongRoad, oProj, dPoint);
		}
	}
}
