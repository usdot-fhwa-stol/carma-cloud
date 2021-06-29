/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.geosrv.Mercator;
import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.MathUtil;
import java.util.ArrayDeque;

/**
 *
 * @author aaron.cherney
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
				dTotalAngle -= dAngleStep;
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
}
