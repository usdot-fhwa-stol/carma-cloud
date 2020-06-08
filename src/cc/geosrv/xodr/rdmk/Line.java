/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.MathUtil;
import java.util.ArrayDeque;

/**
 *
 * @author aaron.cherney
 */
public class Line extends Geometry
{
	public Line(double dS, double dX, double dY, double dH, double dL)
	{
		super(dS, dX, dY, dH, dL);
	}

	
	@Override
	public void addPoints(Road oRoad, double dMaxStep, boolean bIncludeEnd, Proj oProj, double[] dProjPt)
	{
		try
		{
			int nLimit;
			double dStep;
			double dEndDistOnRoad = m_dS + m_dL;
			double dStartX = m_dX;
			double dStartY = m_dY;
			ArrayDeque<LaneSection> oDeque = new ArrayDeque();
			oRoad.getLaneSections(oDeque, m_dS, dEndDistOnRoad);
			while (!oDeque.isEmpty())
			{
				LaneSection oSection = oDeque.removeFirst();
				double dStart = Math.max(m_dS, oSection.m_dS);
				double dEnd = oDeque.isEmpty() ? dEndDistOnRoad : oDeque.getFirst().m_dS;
				double dLength = dEnd - dStart;
				if (dMaxStep > dLength)
				{
					nLimit = 1;
					dStep = dLength;
				}
				else
				{
					nLimit = (int)(dLength / dMaxStep) + 1;
					dStep = dLength / nLimit;
				}

				oRoad.m_dTrack = Arrays.ensureCapacity(oRoad.m_dTrack, nLimit * 2);
				oRoad.m_dLaneZero = Arrays.ensureCapacity(oRoad.m_dLaneZero, nLimit * 2);
				double dDeltaX = dStep * Math.cos(m_dHdg);
				double dDeltaY = dStep * Math.sin(m_dHdg);
				if (bIncludeEnd && oDeque.isEmpty())
					++nLimit;
				for (int i = 0; i < nLimit; i++)
				{
					double dX = dStartX + dDeltaX * i;
					double dY = dStartY + dDeltaY * i;
					oRoad.m_dTrack = addProjectedPoint(dX, dY, oRoad.m_dTrack, oProj, dProjPt);
					oRoad.m_dTrack = Arrays.add(oRoad.m_dTrack, 0);

					double dLengthAlongRoad = dStep * i + dStart;
					LaneOffset oLaneOffset = LaneOffset.getLaneOffset(dLengthAlongRoad, oRoad.m_oLaneOffsets);
					double dT = MathUtil.cubic(dLengthAlongRoad - oLaneOffset.m_dS, oLaneOffset.m_dA, oLaneOffset.m_dB, oLaneOffset.m_dC, oLaneOffset.m_dD);
					double dXLaneZero = dX - Math.sin(m_dHdg) * dT;
					double dYLaneZero = dY + Math.cos(m_dHdg) * dT;
					oRoad.m_dLaneZero = addProjectedPoint(dXLaneZero, dYLaneZero, oRoad.m_dLaneZero, oProj, dProjPt);
					oRoad.m_dLaneZero = Arrays.add(oRoad.m_dLaneZero, 0);

					addLanePoints(oRoad, m_dHdg, dXLaneZero, dYLaneZero, dLengthAlongRoad, oSection, oProj, dProjPt);
				}
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
}

