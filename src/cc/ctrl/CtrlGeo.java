/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class CtrlGeo implements Comparable<CtrlGeo>
{
	public byte[] m_yId;
	/**
	 * negative tangent path
	 */
	public double[] m_dNT; // points are in mercator meters
	/**
	 * center path
	 */
	public double[] m_dC; // points are in mercator meters
	/**
	 * positive tangent path
	 */
	public double[] m_dPT; // points are in mercator meters
	
	public double[] m_dBB = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
	public ArrayList<int[]> m_oTiles = new ArrayList();
	int m_nZoom;
	public int m_nCtrlType;
	public byte[] m_yCtrlValue;
	public double m_dLength;
	public double m_dAverageWidth = 0.0;
	public double[] m_dDebugNT;
	public double[] m_dDebugPT;
	public boolean[] m_bDow = new boolean[7];
	
	
	public CtrlGeo(DataInputStream oIn, int nZoom)
		throws IOException
	{
		this(oIn, false, nZoom);
	}
	
	
	public CtrlGeo(DataInputStream oIn, boolean bUpdateTiles, int nZoom)
	   throws IOException
	{
		m_nZoom = nZoom;
		oIn.readUTF(); // read version, discard it
		m_yId = new byte[16];
		oIn.read(m_yId);
		oIn.skip(8); // skip updated, long

		int nCount = oIn.readInt();
		oIn.skip(nCount * 4); // skip vehicle types, they are ints
		oIn.skip(20); // skip start, end, DoW (long, long ,int)
		nCount = oIn.readInt();
		oIn.skip(nCount * 8); // skip between which is pairs of ints
		oIn.skip(13); // skip offset, period, span, regulatory (int, int, int, bool)
		
		m_nCtrlType = oIn.readInt();
		m_yCtrlValue = new byte[oIn.readInt()];
		oIn.read(m_yCtrlValue);
		
		oIn.readUTF(); // read proj, discard it
		oIn.readUTF(); // read datum, discard it
		
		oIn.skip(28); // skip time, lon, lat, alt, width, heading (long, int, int, int, int, int)
		oIn.readUTF(); // read label, discard it

		nCount = oIn.readInt();
		oIn.skip(nCount * 16); // skip all the points (each are 4 ints)
		
		for (int nIndex = 0; nIndex < m_dBB.length; nIndex++)
			m_dBB[nIndex] = oIn.readInt() / 100.0; // convert mercator cm to mercator meters

		m_dLength = oIn.readDouble();
		m_dAverageWidth = oIn.readDouble();
		if (bUpdateTiles)
		{
			m_dC = readPtsUpdateTiles(oIn, m_dC, m_oTiles, m_nZoom);
			m_dNT = readPtsUpdateTiles(oIn, m_dNT, m_oTiles, m_nZoom);
			m_dPT = readPtsUpdateTiles(oIn, m_dPT, m_oTiles, m_nZoom);
		}
		else
		{
			m_dC = readPts(oIn, m_dC);
			m_dNT = readPts(oIn, m_dNT);
			m_dPT = readPts(oIn, m_dPT);
		}
	}
	
	
	public CtrlGeo(TrafCtrl oCtrl, double dMaxStep, int nZoom)
	   throws Exception
	{
		m_nZoom = nZoom;
		m_yId = new byte[oCtrl.m_yId.length];
		m_nCtrlType = oCtrl.m_nControlType;
		m_yCtrlValue = new byte[oCtrl.m_yControlValue.length];
		System.arraycopy(oCtrl.m_yControlValue, 0, m_yCtrlValue, 0, m_yCtrlValue.length);
		System.arraycopy(oCtrl.m_yId, 0, m_yId, 0, m_yId.length);
		int nNumPts = oCtrl.size();
		double dTotalLength = 0.0;
		double[] dPts = Arrays.newDoubleArray(nNumPts * 3);
		double[] dSeg = new double[9]; // used to store the current line/arc
		int nPrevX = Mercator.lonToCm(Geo.fromIntDeg(oCtrl.m_nLon));
		int nPrevY = Mercator.latToCm(Geo.fromIntDeg(oCtrl.m_nLat));
		int nPrevW = oCtrl.m_nWidth;
		
		int[] nTile = new int[2];
		double[] dPixel = new double[2];
		double[] dCenter = new double[2];
		Mercator oM = Mercator.getInstance();
		double dTotalWidth = 0.0;
		for (int nIndex = 0; nIndex < nNumPts; nIndex++)
		{
			TrafCtrlPt oTemp = oCtrl.get(nIndex);
			int nX = oTemp.m_nX + nPrevX;
			int nY = oTemp.m_nY + nPrevY;
			int nW = oTemp.m_nW + nPrevW;
			dPts = Arrays.add(dPts, nX / 100.0, nY / 100.0); // convert mercator cm to mercator meters
			double dW = nW /100.0;
			dTotalWidth += dW;
			dPts = Arrays.add(dPts, dW);
			nPrevX = nX;
			nPrevY = nY;
			nPrevW = nW;
		}
		m_dAverageWidth = dTotalWidth / nNumPts;
		double dLastTangent = 0;
		double[] dNT = Arrays.newDoubleArray(nNumPts * 2);
		m_dC = Arrays.newDoubleArray(nNumPts * 2);
		double[] dPT = Arrays.newDoubleArray(nNumPts * 2);
		m_dDebugNT = Arrays.newDoubleArray(nNumPts * 2);
		m_dDebugPT = Arrays.newDoubleArray(nNumPts * 2);
		Iterator<double[]> oIt = Arrays.iterator(dPts, dSeg, 1, 6);
		int nCount = 0;
		while (oIt.hasNext())
		{
			oIt.next();
			double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[3], dSeg[4], dSeg[6], dSeg[7], dCenter);
			if (!Double.isFinite(dR) || dR >= 10000) // expand line
			{
				int nLimit;
				double dStep;
				double dLength = Geo.distance(dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
				dTotalLength += dLength;
				if (dMaxStep > dLength)
					nLimit = 1;
				else
					nLimit = (int)(dLength / dMaxStep) + 1;
				int nHalfLimit = nLimit / 2;
				if (nHalfLimit == 0)
					++nHalfLimit;
				double dW1Step = (dSeg[5] - dSeg[2]) / nHalfLimit;
				double dW2Step = (dSeg[8] - dSeg[5]) / nHalfLimit;
				dStep = dLength / nLimit;
				double dHeading = Geo.heading(dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
				if (Double.isNaN(dHeading) || dLength == 0.0)
					continue;
				dLastTangent = dHeading;
				double dDeltaX = dStep * Math.cos(dHeading);
				double dDeltaY = dStep * Math.sin(dHeading);
				for (int i = 0; i < nLimit; i++)
				{
					double dX = dSeg[0] + dDeltaX * i;
					double dY = dSeg[1] + dDeltaY * i;
					double dW = i < nHalfLimit ? dSeg[2] + dW1Step * i : dSeg[5] + dW2Step * (i - nHalfLimit);
					dW /= 2;
					m_dC = Arrays.add(m_dC, dX, dY);
//					updateTileIndices(dX, dY, oM, m_nTileIndices, nTile, m_nZoom);
//					updateBoundingBox((int)dX, (int)dY);
					
					// calculate negative tangent path by subtracting pi/2 to heading
					double dXPrime = dX + Math.sin(dHeading) * dW; // cos(x - pi/2) = sin(x)
					double dYPrime = dY - Math.cos(dHeading) * dW; // sin(x - pi/2) = -cos(x)
					dNT = Arrays.add(dNT, dXPrime, dYPrime);
					updateTiles(dXPrime, dYPrime, oM, m_oTiles, nTile, dPixel, m_nZoom);
					updateBoundingBox(dXPrime, dYPrime);
					if (i == 0 || i == nLimit - 1)
						m_dDebugNT = Arrays.add(m_dDebugNT, dXPrime, dYPrime);
					
					// calculate positive tangent path by adding pi/2 to heading
					dXPrime = dX - Math.sin(dHeading) * dW; // cos(x + pi/2 = -sin(x)
					dYPrime = dY + Math.cos(dHeading) * dW; // sin(x + pi/2) = cos(x)
					dPT = Arrays.add(dPT, dXPrime, dYPrime); 
					updateTiles(dXPrime, dYPrime, oM, m_oTiles, nTile, dPixel, m_nZoom);
					updateBoundingBox(dXPrime, dYPrime);
					if (i == 0 || i == nLimit - 1)
						m_dDebugPT = Arrays.add(m_dDebugPT, dXPrime, dYPrime);
				}
			}
			else // expand arc
			{
				int nRightHand = Geo.rightHand(dSeg[3], dSeg[4], dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
				double dRForCalcs = dR * -nRightHand;
				double dC = 1 / dRForCalcs;
				double dCmAngleStep = dC / 100;
//				double dCmAngleStepMag = Math.abs(dCmAngleStep);
				double dH = dCenter[0];
				double dK = dCenter[1];
				double dHdg = Geo.heading(dH, dK, dSeg[0], dSeg[1]);
//				double dLength = 0;
//				int nSteps = 0;
//				double dTheta = 0;
//				double dPrevX = dSeg[0];
//				double dPrevY = dSeg[1];
//				double dPrevAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dSeg[6], dSeg[7]);
//				while (dPrevAngle > dCmAngleStepMag)
//				{
//					dPrevX = dH + dR * Math.cos(dHdg + dCmAngleStep * ++nSteps);
//					dPrevY = dK + dR * Math.sin(dHdg + dCmAngleStep * nSteps);
//					dLength += 0.01;
//					dTheta += dCmAngleStep; 
//					dPrevAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dSeg[6], dSeg[7]);
//				}
//				double dLastAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dSeg[6], dSeg[7]);
//				dLength += dLastAngle * dR;
//				dTheta += dLastAngle * -nRightHand;
//				dTotalLength += dLength;
				
				double dTheta = dCmAngleStep;
				double dDist;
				while (true)
				{
					double dCirX = dH + dR * Math.cos(dHdg + dTheta);
					double dCirY = dK + dR * Math.sin(dHdg + dTheta);
					dDist = Geo.distance(dSeg[6], dSeg[7], dCirX, dCirY);
//					dLength += 0.01;
					
					if (dDist > 0.02)
						dTheta += dCmAngleStep; 
					else
					{
						if (nRightHand == Geo.rightHand(dCirX, dCirY, dSeg[0], dSeg[1], dSeg[6], dSeg[7]))
							dTheta += -nRightHand * Geo.angle(dCirX, dCirY, dH, dK, dSeg[6], dSeg[7]);
						else
							dTheta -= -nRightHand * Geo.angle(dCirX, dCirY, dH, dK, dSeg[6], dSeg[7]);
						break;
					}
					
				}
				
				double dLength = dTheta * dRForCalcs;
				dTotalLength += dLength;
 
				int nLimit;
				if (dMaxStep > dLength)
					nLimit = 1;
				else
					nLimit = (int)(dLength / dMaxStep) + 1;
				int nHalfLimit = nLimit / 2;
				if (nHalfLimit == 0)
					++nHalfLimit;
				double dW1Step = (dSeg[5] - dSeg[2]) / nHalfLimit;
				double dW2Step = (dSeg[8] - dSeg[5]) / nHalfLimit;
				double dAngleStep = dTheta / nLimit;
				double dTanAdd = dAngleStep > 0 ? Math.PI / 2 : -Math.PI / 2;

				dAngleStep = dTheta / nLimit;
				double dTotalAngle = 0;
				for (int i = 0; i < nLimit; i++) 
				{
					double dAngle = dHdg + dTotalAngle;
					double dX = dH + dR * Math.cos(dAngle);
					double dY = dK + dR * Math.sin(dAngle);
					double dW = i < nHalfLimit ? dSeg[2] + dW1Step * i : dSeg[5] + dW2Step * (i - nHalfLimit); // interpolate the width
					dW /= 2;
					double dTangent = dAngle + dTanAdd;
					m_dC = Arrays.add(m_dC, dX, dY);
//					updateTileIndices(dX, dY, oM, m_nTileIndices, nTile, m_nZoom);
//					updateBoundingBox((int)dX, (int)dY);
					
					// calculate negative tangent path by subtracting pi/2 to heading
					double dXPrime = dX + Math.sin(dTangent) * dW; // cos(x - pi/2) = sin(x)
					double dYPrime = dY - Math.cos(dTangent) * dW; // sin(x - pi/2) = -cos(x)
					dNT = Arrays.add(dNT, dXPrime, dYPrime);
					updateTiles(dXPrime, dYPrime, oM, m_oTiles, nTile, dPixel, m_nZoom);
					updateBoundingBox(dXPrime, dYPrime);
					if (i == 0 || i == nLimit - 1)
						m_dDebugNT = Arrays.add(m_dDebugNT, dXPrime, dYPrime);
					
					// calculate postive tangent path by adding pi/2 to heading
					dXPrime = dX - Math.sin(dTangent) * dW; // cos(x + pi/2 = -sin(x)
					dYPrime = dY + Math.cos(dTangent) * dW; // sin(x + pi/2) = cos(x)
					dPT = Arrays.add(dPT, dXPrime, dYPrime); 
					updateTiles(dXPrime, dYPrime, oM, m_oTiles, nTile, dPixel, m_nZoom);
					updateBoundingBox(dXPrime, dYPrime);
					if (i == 0 || i == nLimit - 1)
						m_dDebugPT = Arrays.add(m_dDebugPT, dXPrime, dYPrime);
					
					dTotalAngle += dAngleStep;
				}
				dLastTangent = dHdg + dTotalAngle + dTanAdd;
			}
		}
		
		// always add the last point since the last point of each linearc is usually the first point of the next linearc
		m_dC = Arrays.add(m_dC, dSeg[6], dSeg[7]);
//		updateTileIndices(oLast.m_nX, oLast.m_nY, oM, m_nTileIndices, nTile, m_nZoom);
//		updateBoundingBox(oLast.m_nX, oLast.m_nY);
		
		// calculate negative tangent path by subtracting pi/2 to heading
		double dW = dSeg[8] / 2;
		double dXPrime = dSeg[6] + Math.sin(dLastTangent) * dW; // cos(x - pi/2) = sin(x)
		double dYPrime = dSeg[7] - Math.cos(dLastTangent) * dW; // sin(x - pi/2) = -cos(x)
		dNT = Arrays.add(dNT, dXPrime, dYPrime);
		updateTiles(dXPrime, dYPrime, oM, m_oTiles, nTile, dPixel, m_nZoom);
		updateBoundingBox(dXPrime, dYPrime);
		
		// calculate postive tangent path by adding pi/2 to heading
		dXPrime = dSeg[6] - Math.sin(dLastTangent) * dW; // cos(x + pi/2 = -sin(x)
		dYPrime = dSeg[7] + Math.cos(dLastTangent) * dW; // sin(x + pi/2) = cos(x)
		dPT = Arrays.add(dPT, dXPrime, dYPrime); 
		updateTiles(dXPrime, dYPrime, oM, m_oTiles, nTile, dPixel, m_nZoom);
		updateBoundingBox(dXPrime, dYPrime);
		
		m_dLength = dTotalLength;
//		Iterator<double[]> oC = Arrays.iterator(dPT, new double[4], 1, 2);
//		double dPrevHdg = Geo.heading(dPT[1], dPT[2], dPT[3], dPT[4]);
//		double dPrevLen = Geo.distance(dPT[1], dPT[2], dPT[3], dPT[4]);
//		nCount = 0;
//		while (oC.hasNext())
//		{
//			dSeg = oC.next();
//			double dHdg = Geo.heading(dSeg[0], dSeg[1], dSeg[2], dSeg[3]);
//			double dLen = Geo.distance(dSeg[0], dSeg[1], dSeg[2], dSeg[3]);
//			System.out.println(String.format("%d\t%f\t%f\t%1.7f\t%1.7f", nCount, dSeg[0], dSeg[1], dHdg, dLen));
//			if (Math.abs(dHdg - dPrevHdg) > 1)
//				System.out.println(String.format("fail\t%d\t%1.7f%17f", nCount, dPrevHdg, dPrevLen));
//			++nCount;
//			dPrevHdg = dHdg;
//			dPrevLen = dLen;
//		}
		m_dPT = dPT;
		m_dNT = dNT;
	}
	
	
	public final static void updateTiles(double dX, double dY, Mercator oM, ArrayList<int[]> oTiles, int[] nTile, double[] dPixel, int nZoom)
	{
		oM.metersToTile(dX, dY, nZoom, nTile);
		int nIndex = Collections.binarySearch(oTiles, nTile, Mercator.TILECOMP);
		if (nIndex < 0)
		{
			int[] nNew = new int[]{nTile[0], nTile[1]};
			oTiles.add(~nIndex, nNew);
		}
	}
	
	
	public final void updateBoundingBox(double dX, double dY)
	{
		if (dX < m_dBB[0])
			m_dBB[0] = dX;
		if (dY < m_dBB[1])
			m_dBB[1] = dY;
		if (dX > m_dBB[2])
			m_dBB[2] = dX;
		if (dY > m_dBB[3])
			m_dBB[3] = dY;
	}
	
	
	public static void writePts(DataOutputStream oOut, double[] dPts)
	   throws IOException
	{
		oOut.writeInt(Arrays.size(dPts) - 1);
		int nPrevX = (int)(MathUtil.round(dPts[1] * 100.0, 0)); // convert mercator meters to mercator cms, rounding to nearest cm
		int nPrevY = (int)(MathUtil.round(dPts[2] * 100.0, 0));
		oOut.writeInt(nPrevX);
		oOut.writeInt(nPrevY);
		Iterator<double[]> oIt = Arrays.iterator(dPts, new double[2], 3, 2);
		int nCurX;
		int nCurY;
		while (oIt.hasNext())
		{
			double[] dPt = oIt.next();
			nCurX = (int)(MathUtil.round(dPt[0] * 100.0, 0)); // convert mercator meters to mercator cms, rounding to nearest cm
			nCurY = (int)(MathUtil.round(dPt[1] * 100.0, 0));
			oOut.writeByte(nCurX - nPrevX); // write the deltas in the file
			oOut.writeByte(nCurY - nPrevY);
			nPrevX = nCurX;
			nPrevY = nCurY;
		}
	}
	
	private static double[] readPtsUpdateTiles(DataInputStream oIn, double[] dPts, ArrayList<int[]> nTiles, int nZoom)
		throws IOException
	{
		int[] nTile = new int[2];
		double[] dPixel = new double[2];
		Mercator oM = Mercator.getInstance();
		double dXPrime;
		double dYPrime;
		int nLen = oIn.readInt(); // read array length
		dPts = Arrays.newDoubleArray(nLen); // create new growable array
		int nPrevX = oIn.readInt(); // read first point, these values are in mercator cm
		int nPrevY = oIn.readInt();
		dPts = Arrays.add(dPts, nPrevX / 100.0, nPrevY / 100.0); // add first point, converting them to mercator meters
		for (int nIndex = 2; nIndex < nLen; nIndex += 2)
		{
			int nX = nPrevX + oIn.readByte(); // calculate the rest of the points by using deltas
			int nY = nPrevY + oIn.readByte();
			dXPrime = nX / 100.0;
			dYPrime = nY / 100.0;
			dPts = Arrays.add(dPts, nX / 100.0, nY / 100.0); // convert them to mercator meters
			updateTiles(dXPrime, dYPrime, oM, nTiles, nTile, dPixel, nZoom);
			nPrevX = nX;
			nPrevY = nY;
		}
		
		return dPts;
	}
	
	
	private static double[] readPts(DataInputStream oIn, double[] dPts)
	   throws IOException
	{
		int nLen = oIn.readInt(); // read array length
		dPts = Arrays.newDoubleArray(nLen); // create new growable array
		int nPrevX = oIn.readInt(); // read first point, these values are in mercator cm
		int nPrevY = oIn.readInt();
		dPts = Arrays.add(dPts, nPrevX / 100.0, nPrevY / 100.0); // add first point, converting them to mercator meters
		for (int nIndex = 2; nIndex < nLen; nIndex += 2)
		{
			int nX = nPrevX + oIn.readByte(); // calculate the rest of the points by using deltas
			int nY = nPrevY + oIn.readByte();
			dPts = Arrays.add(dPts, nX / 100.0, nY / 100.0); // convert them to mercator meters
			nPrevX = nX;
			nPrevY = nY;
		}
		
		return dPts;
	}
	
	
	public static int[] readPtsLonLats(DataInputStream oIn, int[] nPts)
	   throws IOException
	{
		int nLen = oIn.readInt(); // read array length
		nPts = Arrays.ensureCapacity(nPts, nLen); // create new growable array
		int nPrevX = oIn.readInt(); // read first point
		int nPrevY = oIn.readInt();
		
		nPts = Arrays.add(nPts, Geo.toIntDeg(Mercator.xToLon(nPrevX / 100.0)), Geo.toIntDeg(Mercator.yToLat(nPrevY / 100.0))); // add first point
		for (int nIndex = 2; nIndex < nLen; nIndex += 2)
		{
			int nX = nPrevX + oIn.readByte(); // calculate the rest of the points by using deltas
			int nY = nPrevY + oIn.readByte();
			nPts = Arrays.add(nPts, Geo.toIntDeg(Mercator.xToLon(nPrevX / 100.0)), Geo.toIntDeg(Mercator.yToLat(nPrevY / 100.0)));
			nPrevX = nX;
			nPrevY = nY;
		}
		
		return nPts;
	}


	@Override
	public int compareTo(CtrlGeo o)
	{
		int nRet = 0;
		for (int nIndex = 0; nIndex < m_yId.length; nIndex++)
		{
			nRet = m_yId[nIndex] - o.m_yId[nIndex];
			if (nRet != 0)
				return nRet;
		}
		
		return nRet;
	}
	
	
	public Area createPolygon()
	{
		double[] dInOrder = m_dPT;
		double[] dReverseOrder = m_dNT;
		int nInOrderLimit = Arrays.size(dInOrder);
		int nReverseOrderLimit = Arrays.size(dReverseOrder);
		
		Path2D.Double dPoly = new Path2D.Double();
		dPoly.moveTo(dInOrder[5], dInOrder[6]);
		for (int i = 7; i < nInOrderLimit;)
			dPoly.lineTo(dInOrder[i++], dInOrder[i++]);

		for (int i = nReverseOrderLimit - 2; i >= 5; i -= 2)
			dPoly.lineTo(dReverseOrder[i], dReverseOrder[i + 1]);
		
		dPoly.closePath();
		return new Area(dPoly);
	}
}
