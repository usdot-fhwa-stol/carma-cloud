/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

import cc.ctrl.TrafCtrlEnums;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;


/**
 *
 * @author Federal Highway Administration
 */
public abstract class MathUtil
{
	public static double TWOPI = Math.PI * 2;
	
	
	public static void getIntersection(double dPx, double dPy, double dEnd1x, double dEnd1y, double dQx, double dQy, double dEnd2x, double dEnd2y, double[] dInter)
	{
		dInter[0] = Double.NaN;
		dInter[1] = Double.NaN;
		double dDeltaQPx = dQx - dPx;
		double dDeltaQPy = dQy - dPy;
		double dRx = dEnd1x - dPx;
		double dRy = dEnd1y - dPy;
		double dSx = dEnd2x - dQx;
		double dSy = dEnd2y - dQy;
		double dRCrossS = cross(dRx, dRy, dSx, dSy);
		if (dRCrossS == 0)
			return;
		double dT = cross(dDeltaQPx, dDeltaQPy, dSx, dSy) / dRCrossS;
		if (dT < 0 || dT > 1)
			return;
		double dU = cross(dDeltaQPx, dDeltaQPy, dRx, dRy) / dRCrossS;
		if (dU < 0 || dU > 1)
			return;
		dInter[0] = dPx + dT * dRx;
		dInter[1] = dPy + dT * dRy;
	}
	
	
	public static double[] addIntersection(double[] dPoints, double dX1, double dY1, double dX2, double dY2, double[] dLonLats)
	{
		double[] dPoint = new double[2];
		getIntersection(dX1, dY1, dX2, dY2, dLonLats[0], dLonLats[1], dLonLats[0], dLonLats[3], dPoint); // check left edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.addAndUpdate(dPoints, dPoint[0], dPoint[1]);
		
		getIntersection(dX1, dY1, dX2, dY2, dLonLats[0], dLonLats[3], dLonLats[2], dLonLats[3], dPoint); // check top edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.addAndUpdate(dPoints, dPoint[0], dPoint[1]);
		
		getIntersection(dX1, dY1, dX2, dY2, dLonLats[2], dLonLats[3], dLonLats[2], dLonLats[1], dPoint); // check right edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.addAndUpdate(dPoints, dPoint[0], dPoint[1]);
		
		getIntersection(dX1, dY1, dX2, dY2, dLonLats[2], dLonLats[1], dLonLats[0], dLonLats[1], dPoint); // check bot edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.addAndUpdate(dPoints, dPoint[0], dPoint[1]);
		
		return dPoints; // no intersections
	}


	public static double cross(double dVx, double dVy, double dWx, double dWy)
	{
		return dVx * dWy - dVy * dWx;
	}


	public static int compareTol(double d1, double d2, double dTol)
	{
		if (d2 > d1)
		{
			if (d2 - d1 > dTol)
				return -1;
		}
		else if (d1 - d2 > dTol)
			return 1;
		return 0;
	}
	
	
	public static int compareTol(int n1, int n2, int nTol)
	{
		if (n2 > n1)
		{
			if (n2 - n1 > nTol)
				return -1;
		}
		else if (n1 - n2 > nTol)
			return 1;
		return 0;
	}


	public static double cubic(double dX, double dA, double dB, double dC, double dD)
	{
		return dA + (dB * dX) + (dC * dX * dX) + (dD * dX * dX * dX);
	}
	
	
	public static double normalizeRadians(double dRad)
	{
		if (dRad < 0)
			return dRad + Math.ceil(-dRad / TWOPI) * TWOPI;
		else if (dRad >= TWOPI)
			return dRad - Math.floor(dRad / TWOPI) * TWOPI;
		
		return dRad;
	}
	
	
	public static byte[] doubleToBytes(double dDouble, byte[] yBytes)
	{
		long lLong = Double.doubleToLongBits(dDouble);
		return longToBytes(lLong, yBytes);
	}
	
	
	public static byte[] longToBytes(long lLong, byte[] yBytes)
	{
		yBytes[0] = (byte)(lLong >> 56);
		yBytes[1] = (byte)(lLong >> 48);
		yBytes[2] = (byte)(lLong >> 40);
		yBytes[3] = (byte)(lLong >> 32);
		yBytes[4] = (byte)(lLong >> 24);
		yBytes[5] = (byte)(lLong >> 16);
		yBytes[6] = (byte)(lLong >> 8);
		yBytes[7] = (byte)lLong;
		
		return yBytes;
	}
	
	
	public static byte[] intToBytes(int nInt, byte[] yBytes)
	{
		yBytes[0] = (byte)(nInt >> 24);
		yBytes[1] = (byte)(nInt >> 16);
		yBytes[2] = (byte)(nInt >> 8);
		yBytes[3] = (byte)nInt;
		
		return yBytes;
	}
	
	
	public static int bytesToInt(byte[] yBytes)
	{
		return ((yBytes[0] & 0xff) << 24) | 
			   ((yBytes[1] & 0xff) << 16) | 
			   ((yBytes[2] & 0xff) << 8) | 
			   ((yBytes[3] & 0xff));
	}
	
	
	public static double round(double dVal, int nPlaces)
	{
		BigDecimal dBd = new BigDecimal(Double.toString(dVal));
		dBd = dBd.setScale(nPlaces, RoundingMode.HALF_UP);
		return dBd.doubleValue();
	}
	
	public static void main(String[] s)
	{
//		byte[] yBytes = new byte[]{0, 1, 0, 2};
//		ArrayList<String> sVals = new ArrayList();
//		TrafCtrlEnums.getCtrlValString("latperm", yBytes, sVals);
//		for (String sVal : sVals)
//			System.out.println(sVal);
		
		int nCount = 0;
		int nVal1 = 2;
		nVal1 <<= 16;
		nVal1 |= (1 & 0xff);

		int nVal2 = 1;
		nVal2 <<= 16;
		nVal2 |= (2 & 0xff);
		
		ArrayList<String> sVals = new ArrayList();
		byte[] yBytes = new byte[4];
		MathUtil.intToBytes(nVal2, yBytes);
		TrafCtrlEnums.getCtrlValString("latperm", yBytes, sVals);
		sVals.forEach(sVal -> System.out.println(sVal));
	}
}
