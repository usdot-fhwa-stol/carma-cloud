/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class RoadMark implements Comparable<RoadMark>
{
	private static String[] TYPES = new String[]
	{
		"none",
		"solid",
		"broken",
		"solid solid",
		"solid broken",
		"broken solid",
		"broken broken",
		"botts dots",
		"grass",
		"curb",
		"custom",
		"edge"
	};
	
	public static String[] COLORS = new String[]
	{
		"standard",
		"blue",
		"green",
		"red",
		"white",
		"yellow",
		"orange"
	};


	public int m_nType;
	public double[] m_dLine;
	public ArrayList<double[]> m_oTileLines;
	public int m_nColor;
	public double m_dS;
	private static double g_dSolidSolidWidth;
	private static double g_dBrokenSpace;
	private static double g_dBrokenLine;
	
	public RoadMark(String sType, String sColor, double dS)
	{
		m_dLine = Arrays.newDoubleArray();
		m_dLine = Arrays.add(m_dLine, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		m_nType = getType(sType);
		m_nColor = getColor(sColor);
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
	
	
	public static int getColor(String sColor)
	{
		if (sColor == null)
			return 0;

		int nIndex = COLORS.length;
		while (nIndex-- > 0)
		{
			if (COLORS[nIndex].compareTo(sColor) == 0)
				return nIndex;
		}
		
		return 0; // return 0 which is "standard" if there is no match
	}
	
	
	public static int getType(String sType)
	{
		int nIndex = TYPES.length;
		while (nIndex-- > 0)
		{
			if (TYPES[nIndex].compareTo(sType) == 0)
				return nIndex;
		}
		
		return 0; // return 0 which is "none" if there is no match
	}
	
	
	public static String getColor(int nColor)
	{
		return COLORS[nColor];
	}
	
	
	public static String getType(int nType)
	{
		return TYPES[nType];
	}
	
	
	public void createLinesForTile(Proj oProj, double[] dPoint, int nId)
	{
		double[] dSegment = new double[4];
		m_oTileLines = new ArrayList();
		switch (m_nType)
		{
			case 1:
			{
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
					dHeading = Geo.heading(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (Double.isNaN(dHeading))
						continue;
					dRight = Geometry.addProjectedPoint(dSegment[0] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading) * g_dSolidSolidWidth, dRight, oProj, dPoint);
					dLeft = Geometry.addProjectedPoint(dSegment[0] - Math.sin(dHeading - Math.PI) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading - Math.PI) * g_dSolidSolidWidth, dLeft, oProj, dPoint);
				}
				dRight = Geometry.addProjectedPoint(dSegment[2] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[3] + Math.cos(dHeading) * g_dSolidSolidWidth, dRight, oProj, dPoint);
				dLeft = Geometry.addProjectedPoint(dSegment[2] - Math.sin(dHeading - Math.PI) * g_dSolidSolidWidth, dSegment[3] + Math.cos(dHeading - Math.PI) * g_dSolidSolidWidth, dLeft, oProj, dPoint);
				m_oTileLines.add(dRight);
				m_oTileLines.add(dLeft);
				break;
			}
			case 2:
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
							dLine = Geometry.addProjectedPoint(dSegment[0], dSegment[1], dLine, oProj, dPoint);
					}
				}
				break;
			}
			case 4 :
			case 5 :
			{
				double dSolidAngle = 0;
				double dBrokenAngle = 0;
				if (m_nType == 5)
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
							double dHeading = Geo.heading(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
							if (Double.isNaN(dHeading))
								continue;
							dHeading += dBrokenAngle;
							dLine = Geometry.addProjectedPoint(dSegment[0] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading) * g_dSolidSolidWidth, dLine, oProj, dPoint);
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
					dHeading = Geo.heading(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					if (Double.isNaN(dHeading))
						continue;
					dHeading += dSolidAngle;
					dSolid = Geometry.addProjectedPoint(dSegment[0] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[1] + Math.cos(dHeading) * g_dSolidSolidWidth, dSolid, oProj, dPoint);
				}
				dSolid = Geometry.addProjectedPoint(dSegment[2] - Math.sin(dHeading) * g_dSolidSolidWidth, dSegment[3] + Math.cos(dHeading) * g_dSolidSolidWidth, dSolid, oProj, dPoint);
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
					dLine = Geometry.addProjectedPoint(dReuse[0], dReuse[1], dLine, oProj, dPoint);
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
