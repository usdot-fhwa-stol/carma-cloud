/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

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
}
