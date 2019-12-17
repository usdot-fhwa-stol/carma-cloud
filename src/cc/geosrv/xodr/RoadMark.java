/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author Federal Highway Administration
 */
public class RoadMark implements Comparable<RoadMark>
{
	public String m_sType;
	public double[] m_dLine;
	public ArrayList<double[]> m_oTileLines;
	public String m_sColor;
	public double m_dS;
	private static double g_dSolidSolidWidth;
	private static double g_dBrokenSpace;
	private static double g_dBrokenLine;
	
	public RoadMark(String sType, String sColor, double dS)
	{
		m_dLine = Arrays.newDoubleArray();
		m_dLine = Arrays.add(m_dLine, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		m_sType = sType;
		m_sColor = sColor;
		m_dS = dS;
	}
	
	
	public static RoadMark getRoadMark(double dDist, ArrayList<RoadMark> oRoadMarks)
	{
		int nIndex = oRoadMarks.size();
		while (nIndex-- > 0)
		{
			RoadMark oTemp = oRoadMarks.get(nIndex);
			if (dDist >= oTemp.m_dS)
				return oTemp;
		}
		
		return null;
	}
	
	
	public void createLinesForTile(Proj oProj, double[] dPoint, int nId)
	{
		double[] dSegment = new double[4];
		m_oTileLines = new ArrayList();
		switch (m_sType)
		{
			case "solid solid":
			{
				if (nId == 13)
					System.out.println();
				double[] dLeft = Arrays.newDoubleArray(Arrays.size(m_dLine));
				double[] dRight = Arrays.newDoubleArray(Arrays.size(m_dLine));
				dLeft = Arrays.add(dLeft, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				dRight = Arrays.add(dRight, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				double dHeading = Double.NaN;
				Iterator<double[]> oIt = Arrays.iterator(m_dLine, dSegment, 5, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					if (Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]) < 0.01) // if the distance between points is less than 1 cm skip
						continue;
					dHeading = Geo.angle(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (Double.isNaN(dHeading))
						continue;
					dRight = Geo.addPoint(dSegment[0] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading) * g_dSolidSolidWidth, dRight, oProj, dPoint);
					dLeft = Geo.addPoint(dSegment[0] - Math.sin(dHeading - Math.PI) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading - Math.PI) * g_dSolidSolidWidth, dLeft, oProj, dPoint);
				}
				dRight = Geo.addPoint(dSegment[2] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[3] + Math.cos(dHeading) * g_dSolidSolidWidth, dRight, oProj, dPoint);
				dLeft = Geo.addPoint(dSegment[2] - Math.sin(dHeading - Math.PI) * g_dSolidSolidWidth, dSegment[3] + Math.cos(dHeading - Math.PI) * g_dSolidSolidWidth, dLeft, oProj, dPoint);
				m_oTileLines.add(dRight);
				m_oTileLines.add(dLeft);
				break;
			}
			case "broken":
			{
				double dLength = 0.0;
				Iterator<double[]> oIt = Arrays.iterator(m_dLine, dSegment, 5, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					double dDist = Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (dDist < 0.01) // if the distance between points is less than 1 cm skip
						continue;
					dLength += dDist;
				}
				int nMarks = (int)Math.floor(dLength / (g_dBrokenSpace + g_dBrokenLine));
				double dMarkLength = (g_dBrokenSpace + g_dBrokenSpace) * nMarks;
				double dOffset = (dLength - dMarkLength) / 2;
				oIt = Arrays.iterator(m_dLine, dSegment, 5, 2);
				dLength = 0.0;
				double dCurrent = dOffset;
				boolean bSpace = true;
				double[] dLine = Arrays.newDoubleArray();
				while (oIt.hasNext())
				{
					oIt.next();
					double dDist = Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (dDist < 0.01)
						continue;
					dCurrent += dDist;
					if (bSpace)
					{
						if (dCurrent >= g_dBrokenSpace)
						{
							bSpace = false;
							dCurrent = 0.0;
							dLine = Arrays.newDoubleArray();
							dLine = Arrays.add(dLine, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
						}
					}
					else
					{
						if (dCurrent >= g_dBrokenLine)
						{
							bSpace = true;
							dCurrent = 0.0;
							m_oTileLines.add(dLine);
						}
						else
							dLine = Geo.addPoint(dSegment[0], dSegment[1], dLine, oProj, dPoint);
					}
				}
				break;
			}
			case "broken solid" :
			case "solid broken" :
			{
				double dSolidAngle = 0;
				double dBrokenAngle = 0;
				if (m_sType.startsWith("broken"))
					dBrokenAngle = -Math.PI;
				else
					dSolidAngle = -Math.PI;
				
				double dLength = 0.0;
				Iterator<double[]> oIt = Arrays.iterator(m_dLine, dSegment, 5, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					double dDist = Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (dDist < 0.01) // if the distance between points is less than 1 cm skip
						continue;
					dLength += dDist;
				}
				int nMarks = (int)Math.floor(dLength / (g_dBrokenSpace + g_dBrokenLine));
				double dMarkLength = (g_dBrokenSpace + g_dBrokenSpace) * nMarks;
				double dOffset = (dLength - dMarkLength) / 2;
				oIt = Arrays.iterator(m_dLine, dSegment, 5, 2);
				dLength = 0.0;
				double dCurrent = dOffset;
				boolean bSpace = true;
				double[] dLine = Arrays.newDoubleArray();
				while (oIt.hasNext())
				{
					oIt.next();
					double dDist = Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (dDist < 0.01)
						continue;
					dCurrent += dDist;
					if (bSpace)
					{
						if (dCurrent >= g_dBrokenSpace)
						{
							bSpace = false;
							dCurrent = 0.0;
							dLine = Arrays.newDoubleArray();
							dLine = Arrays.add(dLine, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
						}
					}
					else
					{
						if (dCurrent >= g_dBrokenLine)
						{
							bSpace = true;
							dCurrent = 0.0;
							m_oTileLines.add(dLine);
						}
						else
						{
							double dHeading = Geo.angle(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
							if (Double.isNaN(dHeading))
								continue;
							dHeading += dBrokenAngle;
							dLine = Geo.addPoint(dSegment[0] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading) * g_dSolidSolidWidth, dLine, oProj, dPoint);
						}
					}
				}
				
				double[] dSolid = Arrays.newDoubleArray(Arrays.size(m_dLine));
				dSolid = Arrays.add(dSolid, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				double dHeading = Double.NaN;
				oIt = Arrays.iterator(m_dLine, dSegment, 5, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					if (Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]) < 0.01) // if the distance between points is less than 1 cm skip
						continue;
					dHeading = Geo.angle(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (Double.isNaN(dHeading))
						continue;
					dHeading += dSolidAngle;
					dSolid = Geo.addPoint(dSegment[0] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading) * g_dSolidSolidWidth, dSolid, oProj, dPoint);
				}
				dSolid = Geo.addPoint(dSegment[2] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[3] + Math.cos(dHeading) * g_dSolidSolidWidth, dSolid, oProj, dPoint);
				m_oTileLines.add(dSolid);
				break;
			}
			default:
			{
				double[] dLine = Arrays.newDoubleArray(Arrays.size(m_dLine));
				double[] dReuse = new double[2];
				Iterator<double[]> oIt = Arrays.iterator(m_dLine, dReuse, 5, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					dLine = Geo.addPoint(dReuse[0], dReuse[1], dLine, oProj, dPoint);
				}
				m_oTileLines.add(dLine);
				break;
			}
		}
	}


	public static void setSolidSolid(double dValue)
	{
		g_dSolidSolidWidth = dValue;
	}
	
	
	public static void setBrokenSpace(double dValue)
	{
		g_dBrokenSpace = dValue;
	}
	
	public static void setBrokenLine(double dValue)
	{
		g_dBrokenLine = dValue;
	}
	
	
	@Override
	public int compareTo(RoadMark o)
	{
		return Double.compare(m_dS, o.m_dS);
	}
}
