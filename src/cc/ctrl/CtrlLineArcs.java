/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class CtrlLineArcs implements Comparable<CtrlLineArcs>
{
	public int m_nLaneId;
	public long m_lLaneSectionId;
	public int m_nLaneType;
	public double[] m_dLineArcs;
	public static Comparator<CtrlLineArcs> CMPBYLANE = (CtrlLineArcs o1, CtrlLineArcs o2) -> Integer.compare(o1.m_nLaneId, o2.m_nLaneId);
	public static Comparator<CtrlLineArcs> CMPBYLANESECTION = (CtrlLineArcs o1, CtrlLineArcs o2) -> Long.compare(o1.m_lLaneSectionId, o2.m_lLaneSectionId);
	public static final int CON_TEND_OSTART = 1;
	public static final int CON_TEND_OEND = 2;
	public static final int CON_TSTART_OSTART = 3;
	public static final int CON_TSTART_OEND = 4;
	
	public CtrlLineArcs(int nRoadId, int nSectionId, int nLaneIndex, int nLaneIdByRoad, int nLaneType, double[] dCenterLine, double dTol)
	{
		m_nLaneId = XodrUtil.getLaneId(nRoadId, nLaneIdByRoad);
		m_lLaneSectionId = XodrUtil.getLaneSectionId(nRoadId, nSectionId, nLaneIndex);
		m_nLaneType = nLaneType;
		
		double[] dCenter;
		if (nRoadId == -100)
			dCenter = dCenterLine;
		else
			dCenter = Geo.combineLineArcs(dCenterLine, dTol);
		m_dLineArcs = Arrays.newDoubleArray(Arrays.size(dCenter));
		for (int i = 1; i < 5; i += 2) // add bounding box
		{
			m_dLineArcs = Arrays.add(m_dLineArcs, dCenter[i], dCenter[i + 1]);
		}

		if (nLaneIndex > 0)
		{
			for (int nIndex = Arrays.size(dCenter) - 3; nIndex >= 5; nIndex -= 3)
			{
				m_dLineArcs = Arrays.add(m_dLineArcs, dCenter[nIndex], dCenter[nIndex + 1]);
				m_dLineArcs = Arrays.add(m_dLineArcs, dCenter[nIndex + 2]);
			}
		}
		else
		{
			Iterator<double[]> oIt = Arrays.iterator(dCenter, new double[3], 5, 3);
			while (oIt.hasNext())
			{
				double[] dPt = oIt.next();
				m_dLineArcs = Arrays.add(m_dLineArcs, dPt[0], dPt[1]); // add x,y as mercator meters
				m_dLineArcs = Arrays.add(m_dLineArcs, dPt[2]); // add w
			}
		}
	}
	
	
	public CtrlLineArcs(cc.geosrv.xodr.geo.Lane oLane, double dTol)
	{
		this(oLane.m_nRoadId, oLane.m_nSectionId, oLane.m_nLaneIndex, oLane.m_nLaneIdByRoad, oLane.m_nLaneType, oLane.m_dCenter, dTol);
	}
	
	public CtrlLineArcs(cc.geosrv.xodr.pvmt.Lane oLane, double dTol)
	{
		this(oLane.m_nRoadId, oLane.m_nSectionId, oLane.m_nLaneIndex, oLane.m_nLaneIdByRoad, oLane.m_nLaneType, oLane.m_dCenter, dTol);
	}
	
	
	public CtrlLineArcs(DataInputStream oIn)
	   throws IOException
	{
		m_nLaneId = oIn.readInt();
		m_lLaneSectionId = oIn.readLong();
		m_nLaneType = oIn.readInt();
		
		int nOrdinates = oIn.readInt();
		m_dLineArcs = Arrays.newDoubleArray(nOrdinates);
		m_dLineArcs = Arrays.add(m_dLineArcs, oIn.readDouble(), oIn.readDouble()); // read min x y
		m_dLineArcs = Arrays.add(m_dLineArcs, oIn.readDouble(), oIn.readDouble()); // read max x y
		for (int nIndex = 5; nIndex < nOrdinates; nIndex += 3) // read points
		{
			m_dLineArcs = Arrays.add(m_dLineArcs, oIn.readDouble(), oIn.readDouble()); // read x y
			m_dLineArcs = Arrays.add(m_dLineArcs, oIn.readDouble()); // read w
		}
	}
	
	
	public void write(DataOutputStream oOut)
	   throws IOException
	{
		oOut.writeInt(m_nLaneId);
		oOut.writeLong(m_lLaneSectionId);
		oOut.writeInt(m_nLaneType);
		oOut.writeInt(Arrays.size(m_dLineArcs) - 1);
		for (int nIndex = 1; nIndex < 5; nIndex++) // write bounding box
		{
			oOut.writeDouble(m_dLineArcs[nIndex]);
		}
		double[] dPt = new double[3];
		Iterator<double[]> oIt = Arrays.iterator(m_dLineArcs, dPt, 5, 3);
		while (oIt.hasNext())
		{
			oIt.next();
			oOut.writeDouble(dPt[0]); // write x
			oOut.writeDouble(dPt[1]); // write y
			oOut.writeDouble(dPt[2]); // write w
		}
	}

	
	public void combine(CtrlLineArcs oOther, int nConnect)
	{
		combine(oOther.m_dLineArcs, nConnect);
	}
	
	public void combine(double[] dOtherLAs, int nConnect)
	{
		switch (nConnect)
		{
			case CON_TEND_OSTART: // this end and other start touch
			{
				int nCpLen = Arrays.size(dOtherLAs) - 8; // subtract insertion point, bounding box, and first point which is shared
				m_dLineArcs = Arrays.ensureCapacity(m_dLineArcs, nCpLen);
				System.arraycopy(dOtherLAs, 8, m_dLineArcs, Arrays.size(m_dLineArcs), nCpLen); // start copying from position 8 to skip insertion point, bb, first point
				m_dLineArcs[0] += nCpLen;
				break;
			}
			case CON_TEND_OEND: // this end and other end touch
			{
				int nIndex = Arrays.size(dOtherLAs) - 6; // minus 6 to skip the last point which is shared
				while (nIndex > 5) // add other points in reverse order
				{
					m_dLineArcs = Arrays.add(m_dLineArcs, dOtherLAs[nIndex], dOtherLAs[nIndex + 1]); // add x,y
					m_dLineArcs = Arrays.add(m_dLineArcs, dOtherLAs[nIndex + 2]); // add w
					nIndex -= 3;
				}
				break;
			}
			case CON_TSTART_OSTART: // this start and other start touch
			{
				double[] dPts = Arrays.newDoubleArray(Arrays.size(m_dLineArcs) + Arrays.size(dOtherLAs) - 8); // subtract 8 for extra insertion point, bounding box, and shared first point
				dPts = Arrays.add(dPts, m_dLineArcs[1], m_dLineArcs[2]); // add this bounding box
				dPts = Arrays.add(dPts, m_dLineArcs[3], m_dLineArcs[4]);
				int nIndex = Arrays.size(dOtherLAs) - 3;
				while (nIndex > 5) // add other points in reverse order
				{
					dPts = Arrays.add(dPts, dOtherLAs[nIndex], dOtherLAs[nIndex + 1]);
					dPts = Arrays.add(dPts, dOtherLAs[nIndex + 2]);
					nIndex -= 3;
				}
				int nCpLen = Arrays.size(m_dLineArcs) - 8;
				System.arraycopy(m_dLineArcs, 8, dPts, Arrays.size(dPts), nCpLen);
				m_dLineArcs = dPts;
				m_dLineArcs[0] += nCpLen;
				break;
			}
			case CON_TSTART_OEND: // this start and other end touch
			{
				double[] dPts = Arrays.newDoubleArray(Arrays.size(m_dLineArcs) + Arrays.size(dOtherLAs) - 8); // subtract 8 for extra insertion point, bounding box, and shared first point
				System.arraycopy(m_dLineArcs, 1, dPts, 1, 4); // copy this bb into pts
				System.arraycopy(dOtherLAs, 5, dPts, 5, Arrays.size(dOtherLAs) - 5);
				dPts[0] = dOtherLAs[0];
				int nCpLen = Arrays.size(m_dLineArcs) - 8;
				System.arraycopy(m_dLineArcs, 8, dPts, Arrays.size(dOtherLAs), nCpLen);
				m_dLineArcs = dPts;
				m_dLineArcs[0] += nCpLen;
				break;
			}
			default:
				return;
		}
		if (dOtherLAs[1] < m_dLineArcs[1])
			m_dLineArcs[1] = dOtherLAs[1];
		if (dOtherLAs[2] < m_dLineArcs[2])
			m_dLineArcs[2] = dOtherLAs[2];
		if (dOtherLAs[3] > m_dLineArcs[3])
			m_dLineArcs[3] = dOtherLAs[3];
		if (dOtherLAs[4] > m_dLineArcs[4])
			m_dLineArcs[4] = dOtherLAs[4];
	}
	
	
	public int connects(CtrlLineArcs oOther, double dSqTol)
	{
		return connects(oOther.m_dLineArcs, dSqTol);
	}
	
	
	public int connects(double[] dOtherGeo, double dSqTol)
	{
		double[] dThisGeo = m_dLineArcs;
		int nThisEnd = Arrays.size(dThisGeo) - 3;
		int nOtherEnd = Arrays.size(dOtherGeo) - 3;
		if (MathUtil.compareTol(Geo.sqDist(dThisGeo[nThisEnd], dThisGeo[nThisEnd + 1], dOtherGeo[5], dOtherGeo[6]), 0, dSqTol) == 0) // this end and other start touch
			return CON_TEND_OSTART;
		if (MathUtil.compareTol(Geo.sqDist(dThisGeo[nThisEnd], dThisGeo[nThisEnd + 1], dOtherGeo[nOtherEnd], dOtherGeo[nOtherEnd + 1]), 0, dSqTol) == 0) // this end and other end touch
			return CON_TEND_OEND;
		if (MathUtil.compareTol(Geo.sqDist(dThisGeo[5], dThisGeo[6], dOtherGeo[5], dOtherGeo[6]), 0, dSqTol) == 0) // this start and other start touch
			return CON_TSTART_OSTART;
		if (MathUtil.compareTol(Geo.sqDist(dThisGeo[5], dThisGeo[6], dOtherGeo[nOtherEnd], dOtherGeo[nOtherEnd + 1]), 0, dSqTol) == 0) // this start and other end touch
			return CON_TSTART_OEND;
		
		return -1;
			   
	}
	
	
	@Override
	public int compareTo(CtrlLineArcs o)
	{
		return Long.compare(m_lLaneSectionId, o.m_lLaneSectionId);
	}
	
	
	public static double getLen(double[] dLineArcs)
	{
		Iterator<double[]> oIt = Arrays.iterator(dLineArcs, new double[9], 5, 6);
		double[] dCenter = new double[2];
		double dTotalLen = 0.0;
		while (oIt.hasNext())
		{
			double[] dSeg = oIt.next();
			double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[3], dSeg[4], dSeg[6], dSeg[7], dCenter);
			if (!Double.isFinite(dR) || dR >= 10000) // expand line
			{
				dTotalLen += Geo.distance(dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
			}
			else
			{
				int nRightHand = Geo.rightHand(dSeg[3], dSeg[4], dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
				double dC = -nRightHand / dR;
				double dCmAngleStep = dC / 100;
				double dCmAngleStepMag = Math.abs(dCmAngleStep);
				double dH = dCenter[0];
				double dK = dCenter[1];
				double dHdg = Geo.heading(dH, dK, dSeg[0], dSeg[1]);
				int nSteps = 0;
				double dPrevX = dSeg[0];
				double dPrevY = dSeg[1];
				double dPrevAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dSeg[6], dSeg[7]);
				while (dPrevAngle > dCmAngleStepMag)
				{
					dPrevX = dH + dR * Math.cos(dHdg + dCmAngleStep * ++nSteps);
					dPrevY = dK + dR * Math.sin(dHdg + dCmAngleStep * nSteps);
					dTotalLen += 0.01;
					dPrevAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dSeg[6], dSeg[7]);
				}
				double dLastAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dSeg[6], dSeg[7]);
				dTotalLen += dLastAngle * dR;
			}
		}
		
		return dTotalLen;
	}
}