/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

import cc.ctrl.CtrlGeo;
import cc.geosrv.Mercator;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.BitSet;
import cc.vector_tile.VectorTile;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Iterator;


/**
 *
 * @author Federal Highway Administration
 */
public abstract class TileUtil
{
	public final static int MOVETO = 1;
	public final static int LINETO = 2;
	public final static int CLOSEPATH = 7;
	
	
	public static int command(int nId, int nCount)
	{
		return (nId & 0x7) | (nCount << 3);
	}
	
	
	public static int parameter(double dValue)
	{
		int nVal = (int)Math.round(dValue);
		return (nVal << 1) ^ (nVal >> 31);
	}
	
	
	public static int getPos(double dVal, double dMin, double dMax, double dExtent, boolean bInvert)
	{
		if (bInvert)
			return (int)Math.round((dMax - dVal) * dExtent / (dMax - dMin));
		
		return (int)Math.round((dVal - dMin) * dExtent / (dMax - dMin));
	}
	
	
	public static int[] addPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, Area oPoly, int[] nPointBuffer)
	{		
		double[] dCoords = new double[2];
		double[] dPrev = new double[2];
		double[] dFirst = new double[2];
		double[] dTemp;
		int nPosX;
		int nPosY;
		PathIterator oIt = oPoly.getPathIterator(null);
		BitSet oHoles = new BitSet();
		nPointBuffer[0] = 1;
		
		double dWinding = 0;
		int nBitIndex = 0;
		while (!oIt.isDone()) // first determine which parts of multi-path are polygons and holes
		{
			switch (oIt.currentSegment(dCoords))
			{
				case PathIterator.SEG_MOVETO:
				{
					dWinding = 0;
					System.arraycopy(dCoords, 0, dFirst, 0, 2);
					System.arraycopy(dCoords, 0, dPrev, 0, 2);
					break;
				}
				case PathIterator.SEG_LINETO:
				{
					dWinding += ((dCoords[0] - dPrev[0]) * (dCoords[1] + dPrev[1]));
					System.arraycopy(dCoords, 0, dPrev, 0, 2);
					break;
				}
				case PathIterator.SEG_CLOSE:
				{
					dWinding += ((dFirst[0] - dCoords[0]) * (dFirst[1] + dCoords[1]));
					oHoles.set(nBitIndex++, dWinding < 0); // negative winding number is hole
					break;
				}
			}
			oIt.next();
		}
		
		
		nBitIndex = 0;
		dPrev[0] = -1;
		dPrev[1] = -1;
		oIt = oPoly.getPathIterator(null);
		while (!oIt.isDone()) // write polygons
		{
			if (oHoles.get(nBitIndex++)) // skip holes
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
					oIt.next();
			}
			else
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
				{
					nPosX = getPos(dCoords[0], dMercBounds[0], dMercBounds[2], nExtent, false);
					nPosY = getPos(dCoords[1], dMercBounds[1], dMercBounds[3], nExtent, true);
					if (dCoords[0] != dPrev[0] || dCoords[1] != dPrev[1])
					{
						nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
						dTemp = dCoords;
						dCoords = dPrev;
						dPrev = dTemp;
					}
					oIt.next();
				}
				writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true);
				nPointBuffer[0] = 1;
				dPrev[0] = -1;
				dPrev[1] = -1;
				
			}
			oIt.next();
		}
		
		nBitIndex = 0;
		dPrev[0] = -1;
		dPrev[1] = -1;
		oIt = oPoly.getPathIterator(null);

		while (!oIt.isDone()) // write holes
		{
			if (!oHoles.get(nBitIndex++)) // skip polygons
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
					oIt.next();
			}
			else
			{
				while (oIt.currentSegment(dCoords) != PathIterator.SEG_CLOSE)
				{
					nPosX = getPos(dCoords[0], dMercBounds[0], dMercBounds[2], nExtent, false);
					nPosY = getPos(dCoords[1], dMercBounds[1], dMercBounds[3], nExtent, true);
					if (dCoords[0] != dPrev[0] || dCoords[1] != dPrev[1])
					{
						nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
						dTemp = dCoords;
						dCoords = dPrev;
						dPrev = dTemp;
					}
					oIt.next();
				}
				writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true);
				nPointBuffer[0] = 1;
				dPrev[0] = -1;
				dPrev[1] = -1;
			}
			oIt.next();
		}
		
		return nPointBuffer;
	}

	
	public static int[] addMercPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, double[] dLine, int[] nPointBuffer)
	{
		return addMercPolygon(oFeatureBuilder, nCur, dBounds, nExtent, 1, dLine, null, nPointBuffer);
	}
	
	
	public static int[] addMercPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, double[] dLine, double[][] dHoles, int[] nPointBuffer)
	{
		return addMercPolygon(oFeatureBuilder, nCur, dBounds, nExtent, 1, dLine, dHoles, nPointBuffer);
	}
	
	
	public static int[] addMercPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, int nStartPos, double[] dLine, double[][] dHoles, int[] nPointBuffer)
	{
		int nPosX;
		int nPosY;
		double[] dCoords = new double[2];
		nPointBuffer[0] = 1;
		Iterator<double[]> oIt = Arrays.iterator(dLine, dCoords, nStartPos, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			nPosX = getPos(dCoords[0], dBounds[0], dBounds[2], nExtent, false);
			nPosY = getPos(dCoords[1], dBounds[1], dBounds[3], nExtent, true);

			nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
		}
		writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true);
		nPointBuffer[0] = 1;
		if (dHoles != null)
		{
			for (double[] dHole : dHoles)
			{
				oIt = Arrays.iterator(dHole, dCoords, nStartPos, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					nPosX = getPos(dCoords[0], dBounds[0], dBounds[2], nExtent, false);
					nPosY = getPos(dCoords[1], dBounds[1], dBounds[3], nExtent, true);

					nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
				}
				writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, true);
				nPointBuffer[0] = 1;
			}
		}
		
		
		return nPointBuffer;
	}
	
	
	public static int[] addMercLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, double[] dLine, int[] nPointBuffer)
	{
		return addMercLinestring(oFeatureBuilder, nCur, dBounds, nExtent, 1, dLine, nPointBuffer);
	}
	
	
	public static int[] addMercLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, int nStartPos, double[] dLine, int[] nPointBuffer)
	{
		int nPosX;
		int nPosY;
		double[] dCoords = new double[2];
		nPointBuffer[0] = 1;
		Iterator<double[]> oIt = Arrays.iterator(dLine, dCoords, nStartPos, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			nPosX = getPos(dCoords[0], dBounds[0], dBounds[2], nExtent, false);
			nPosY = getPos(dCoords[1], dBounds[1], dBounds[3], nExtent, true);

			nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
		}
		writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false);
		
		return nPointBuffer;
	}
	
	
	public static void addMercPointToFeature(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dBounds, int nExtent, double dX, double dY)
	{
		int nPosX = getPos(dX, dBounds[0], dBounds[2], nExtent, false);
		int nPosY = getPos(dY, dBounds[1], dBounds[3], nExtent, true);
		
		oFeatureBuilder.addGeometry(command(MOVETO, 1));
		int nDeltaX = nPosX - nCur[0];
		int nDeltaY = nPosY - nCur[1];
		oFeatureBuilder.addGeometry(parameter(nDeltaX));
		oFeatureBuilder.addGeometry(parameter(nDeltaY));
		nCur[0] += nDeltaX;
		nCur[1] += nDeltaY;
	}
	
	
	public static int[] addLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, double[] dLine, int[] nPointBuffer)
	{
		return addLinestring(oFeatureBuilder, nCur, dMercBounds, nExtent, 0, dLine, nPointBuffer);
	}
	
	
	public static int[] addLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, int nStartPos, double[] dLine, int[] nPointBuffer)
	{
		int nPosX;
		int nPosY;
		double[] dCoords = new double[2];
		double[] dPrev = new double[2];
		double[] dTemp;
		nPointBuffer[0] = 1;
		for (int i = nStartPos; i < dLine.length;)
		{
			dCoords[0] = dLine[i++];
			dCoords[1] = dLine[i++];
			nPosX = getPos(Mercator.lonToMeters(dCoords[0]), dMercBounds[0], dMercBounds[2], nExtent, false);
			nPosY = getPos(Mercator.latToMeters(dCoords[1]), dMercBounds[1], dMercBounds[3], nExtent, true);
			if (dCoords[0] != dPrev[0] || dCoords[1] != dPrev[1])
			{
				nPointBuffer = Arrays.add(nPointBuffer, nPosX, nPosY);
				dTemp = dCoords;
				dCoords = dPrev;
				dPrev = dTemp;
			}
		}
		writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false);
		
		return nPointBuffer;
	}
	
	
	public static void addPointToFeature(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, double dLon, double dLat)
	{
		int nPosX = getPos(Mercator.lonToMeters(dLon), dMercBounds[0], dMercBounds[2], nExtent, false);
		int nPosY = getPos(Mercator.latToMeters(dLat), dMercBounds[1], dMercBounds[3], nExtent, true);
		
		oFeatureBuilder.addGeometry(command(MOVETO, 1));
		int nDeltaX = nPosX - nCur[0];
		int nDeltaY = nPosY - nCur[1];
		oFeatureBuilder.addGeometry(parameter(nDeltaX));
		oFeatureBuilder.addGeometry(parameter(nDeltaY));
		nCur[0] += nDeltaX;
		nCur[1] += nDeltaY;
	}

	
	public static void writeArea(Area oArea)
	{
		PathIterator oIt = oArea.getPathIterator(null);
		double[] dCoords = new double[2];
		while (!oIt.isDone()) 
		{
			int nType;
			while ((nType = oIt.currentSegment(dCoords)) != PathIterator.SEG_CLOSE)
			{
				System.out.println(String.format("%7.1f %7.1f", dCoords[0], dCoords[1]));
				oIt.next();
			}
			System.out.println();
			oIt.next();
		}	
	}
	
	
	public static void writePointBuffer(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nPointBuffer, int[] nCur, boolean bClose)
	{
		Iterator<int[]> oIt = Arrays.iterator(nPointBuffer, new int[2], 1, 2);
		int nInitCurX = nCur[0];
		int nInitCurY = nCur[1];
		if (oIt.hasNext())
		{
			int[] nPos = oIt.next();
			
			int nMoveX = nPos[0] - nCur[0];
			int nMoveY = nPos[1] - nCur[1];	
			int nDeltaX = nMoveX;
			int nDeltaY = nMoveY;
			nCur[0] += nDeltaX;
			nCur[1] += nDeltaY;
			int[] nDeltas = Arrays.newIntArray(Arrays.size(nPointBuffer));
			while (oIt.hasNext())
			{
				nPos = oIt.next();
				nDeltaX = nPos[0] - nCur[0];
				nDeltaY = nPos[1] - nCur[1];
				if (nDeltaX != 0 || nDeltaY != 0)
					nDeltas = Arrays.add(nDeltas, nDeltaX, nDeltaY);
				nCur[0] += nDeltaX;
				nCur[1] += nDeltaY;
			}
			if (nDeltas[0] / 2 == 0)
			{
				nCur[0] = nInitCurX;
				nCur[1] = nInitCurY;
				return;
			}
			StringBuilder sBuf = new StringBuilder();
			sBuf.append(command(MOVETO, 1)).append(',');
			oFeatureBuilder.addGeometry(command(MOVETO, 1));
			oFeatureBuilder.addGeometry(parameter(nMoveX));
			oFeatureBuilder.addGeometry(parameter(nMoveY));
			sBuf.append(parameter(nMoveX)).append(',').append(parameter(nMoveY)).append(',');
			oFeatureBuilder.addGeometry(command(LINETO, nDeltas[0] / 2));
			sBuf.append(command(LINETO, nDeltas[0] / 2));
			Iterator<int[]> oDeltaIt = Arrays.iterator(nDeltas, new int[2], 1, 2);
			while (oDeltaIt.hasNext())
			{
				int[] nDelta = oDeltaIt.next();
				oFeatureBuilder.addGeometry(parameter(nDelta[0]));
				oFeatureBuilder.addGeometry(parameter(nDelta[1]));
				sBuf.append(',').append(parameter(nDelta[0])).append(',').append(parameter(nDelta[1]));
			}
			if (bClose)
				oFeatureBuilder.addGeometry(command(CLOSEPATH, 1));
//			System.out.println(sBuf);
		}
	}
	
	
	public static boolean includeInTile(double dX1, double dY1, double dX2, double dY2, double[] dBounds)
	{
		double dXmax = Math.max(dX1, dX2);
		double dXmin = Math.min(dX1, dX2);
		double dYmax = Math.max(dY1, dY2);
		double dYmin = Math.min(dY1, dY2);
		
		return Geo.boundingBoxesIntersect(dXmin, dYmin, dXmax, dYmax, dBounds[0], dBounds[1], dBounds[2], dBounds[3]);
	}
	
	
	public static boolean includeInTile(CtrlGeo oGeo, double[] dBounds)
	{
		Iterator<double[]> oNT = Arrays.iterator(oGeo.m_dNT, new double[4], 1, 2);
		Iterator<double[]> oPT = Arrays.iterator(oGeo.m_dPT, new double[4], 1, 2);
		if (includeInTile(oGeo.m_dNT[1], oGeo.m_dNT[2], oGeo.m_dPT[1], oGeo.m_dPT[2], dBounds))
			return true;
		while (oNT.hasNext())
		{
			double[] dSegNT = oNT.next();
			double[] dSegPT = oPT.next();
		
			if (includeInTile(dSegNT[2], dSegNT[3], dSegPT[2], dSegPT[3], dBounds))
				return true;
		}
			
		return false;
	}
	
	
	public static void clipCtrlGeoForTile(CtrlGeo oGeo, double[][] dClips, double[] dBounds, ArrayList<ArrayList<double[]>> oClippedLines)
	{
		for (double[] dClipped : dClips)
			dClipped[0] = 1; // reset reusable arrays

		double[] dC = dClips[0];
		double[] dNT = dClips[1];
		double[] dPT = dClips[2];
		
		boolean bPrevInside = includeInTile(oGeo.m_dNT[1], oGeo.m_dNT[2], oGeo.m_dPT[1], oGeo.m_dPT[2], dBounds);
//		boolean bPrevInside = Geo.isInside(oGeo.m_dNT[1], oGeo.m_dNT[2], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0) || 
//							  Geo.isInside(oGeo.m_dPT[1], oGeo.m_dPT[2], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0); 
		if (bPrevInside)
		{
			dC = Arrays.add(dC, oGeo.m_dC[1], oGeo.m_dC[2]);
			dNT = Arrays.add(dNT, oGeo.m_dNT[1], oGeo.m_dNT[2]);
			dPT = Arrays.add(dPT, oGeo.m_dPT[1], oGeo.m_dPT[2]);
		}
		Iterator<double[]> oC = Arrays.iterator(oGeo.m_dC, new double[4], 1, 2);
		Iterator<double[]> oNT = Arrays.iterator(oGeo.m_dNT, new double[4], 1, 2);
		Iterator<double[]> oPT = Arrays.iterator(oGeo.m_dPT, new double[4], 1, 2);
		while (oC.hasNext())
		{
			double[] dSegC = oC.next();
			double[] dSegNT = oNT.next();
			double[] dSegPT = oPT.next();
			
			if (bPrevInside) // previous point was inside
			{
				if (includeInTile(dSegNT[2], dSegNT[3], dSegPT[2], dSegPT[3], dBounds))
//				   Geo.isInside(dSegNT[2], dSegNT[3], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0) ||
//				    Geo.isInside(dSegPT[2], dSegPT[3], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0)) // current point is inside
				{
					dC = Arrays.add(dC, dSegC[2], dSegC[3]);
					dNT = Arrays.add(dNT, dSegNT[2], dSegNT[3]);
					dPT = Arrays.add(dPT, dSegPT[2], dSegPT[3]);
				}
				else // current point is outside
				{ 
					dC = Arrays.add(dC, dSegC[2], dSegC[3]);
					dNT = Arrays.add(dNT, dSegNT[2], dSegNT[3]);
					dPT = Arrays.add(dPT, dSegPT[2], dSegPT[3]);
					bPrevInside = false;
					dClips[0] = dC;
					dClips[1] = dNT;
					dClips[2] = dPT;
					for (int nIndex = 0; nIndex < dClips.length; nIndex++)
					{
						double[] dClipped = dClips[nIndex];
						double[] dFinished = new double[Arrays.size(dClipped)]; // now that the line is outside the tile finish the current line
						System.arraycopy(dClipped, 0, dFinished, 0, dFinished.length);
						oClippedLines.get(nIndex).add(dFinished);
						dClipped[0] = 1; // reset reusable array
					}
				}
			}
			else // previous point was outside
			{
				if (includeInTile(dSegNT[2], dSegNT[3], dSegPT[2], dSegPT[3], dBounds))
//				   Geo.isInside(dSegNT[2], dSegNT[3], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0) ||
//				    Geo.isInside(dSegPT[2], dSegPT[3], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0)) // current point is inside
				{
					bPrevInside = true;
					dC = Arrays.add(dC, dSegC[0], dSegC[1]); // so add previous point, it should be in the current tile's buffered area
					dNT = Arrays.add(dNT, dSegNT[0], dSegNT[1]);
					dPT = Arrays.add(dPT, dSegPT[0], dSegPT[1]);
					dC = Arrays.add(dC, dSegC[2], dSegC[3]); // and add current point
					dNT = Arrays.add(dNT, dSegNT[2], dSegNT[3]);
					dPT = Arrays.add(dPT, dSegPT[2], dSegPT[3]);
				} 
				// previous point and current point are outside, so check if the line segment intersects the tile
				else if (includeInTile(dSegNT[2], dSegNT[3], dSegPT[2], dSegPT[3], dBounds))
//				   Geo.boundingBoxesIntersect(dBounds[0], dBounds[1], dBounds[2], dBounds[3], dSegNT[0], dSegNT[1], dSegNT[2], dSegNT[3]) ||
//						Geo.boundingBoxesIntersect(dBounds[0], dBounds[1], dBounds[2], dBounds[3], dSegPT[0], dSegPT[1], dSegPT[2], dSegPT[3]))  
				{ 
					dC = Arrays.add(dC, dSegC[0], dSegC[1]); // if it does, add both points of the segment
					dNT = Arrays.add(dNT, dSegNT[0], dSegNT[1]);
					dPT = Arrays.add(dPT, dSegPT[0], dSegPT[1]);
					dC = Arrays.add(dC, dSegC[2], dSegC[3]);
					dNT = Arrays.add(dNT, dSegNT[2], dSegNT[3]);
					dPT = Arrays.add(dPT, dSegPT[2], dSegPT[3]);
					dClips[0] = dC;
					dClips[1] = dNT;
					dClips[2] = dPT;
					for (int nIndex = 0; nIndex < dClips.length; nIndex++)
					{
						double[] dClipped = dClips[nIndex];
						double[] dFinished = new double[Arrays.size(dClipped)]; // now that the line is outside the tile finish the current line
						System.arraycopy(dClipped, 0, dFinished, 0, dFinished.length);
						oClippedLines.get(nIndex).add(dFinished);
						dClipped[0] = 1; // reset reusable array
					}
				}
			}
		}
		
		if (Arrays.size(dC) > 3) // if the remaining line has more than 2 points
		{
			dClips[0] = dC;
			dClips[1] = dNT;
			dClips[2] = dPT;
			for (int nIndex = 0; nIndex < dClips.length; nIndex++)
			{
				double[] dClipped = dClips[nIndex];
				double[] dFinished = new double[Arrays.size(dClipped)]; // now that the line is outside the tile finish the current line
				System.arraycopy(dClipped, 0, dFinished, 0, dFinished.length);
				oClippedLines.get(nIndex).add(dFinished);
			}
		}
	}
	
	
	public static void clipLineString(double[] dLine, double[] dBounds, ArrayList<double[]> oClipped)
	{
		clipLineString(dLine, 1, dBounds, oClipped);
	}
	
	
	public static void clipLineString(double[] dLine, int nStart, double[] dBounds, ArrayList<double[]> oClipped)
	{
		double[] dClipped = Arrays.newDoubleArray();
		
		boolean bPrevInside = Geo.isInside(dLine[nStart], dLine[nStart + 1], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0);
		   
		if (bPrevInside)
			dClipped = Arrays.add(dClipped, dLine[nStart], dLine[nStart + 1]);

		Iterator<double[]> oIt = Arrays.iterator(dLine, new double[4], nStart, 2);
		while (oIt.hasNext())
		{
			double[] dSeg = oIt.next();
			
			if (bPrevInside) // previous point was inside
			{
				if (Geo.isInside(dSeg[2], dSeg[3], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0))
				{
					dClipped = Arrays.add(dClipped, dSeg[2], dSeg[3]);
				}
				else // current point is outside
				{ 
					dClipped = Arrays.add(dClipped, dSeg[2], dSeg[3]);
					bPrevInside = false;

					double[] dFinished = new double[Arrays.size(dClipped)]; // now that the line is outside the tile finish the current line
					System.arraycopy(dClipped, 0, dFinished, 0, dFinished.length);
					oClipped.add(dFinished);
					dClipped[0] = 1; // reset reusable array
				}
			}
			else // previous point was outside
			{
				if (Geo.isInside(dSeg[2], dSeg[3], dBounds[3], dBounds[2], dBounds[1], dBounds[0], 0)) // current point is inside
				{
					bPrevInside = true;
					dClipped = Arrays.add(dClipped, dSeg[0], dSeg[1]);
					dClipped = Arrays.add(dClipped, dSeg[2], dSeg[3]);
				} 
				// previous point and current point are outside, so check if the line segment intersects the tile
				else if (Geo.boundingBoxesIntersect(dBounds[0], dBounds[1], dBounds[2], dBounds[3], dSeg[0], dSeg[1], dSeg[2], dSeg[3])) 
				{ 
					dClipped = Arrays.add(dClipped, dSeg[0], dSeg[1]);
					dClipped = Arrays.add(dClipped, dSeg[2], dSeg[3]);

					double[] dFinished = new double[Arrays.size(dClipped)]; // now that the line is outside the tile finish the current line
					System.arraycopy(dClipped, 0, dFinished, 0, dFinished.length);
					oClipped.add(dFinished);
					dClipped[0] = 1; // reset reusable array
				}
			}
		}
		
		if (Arrays.size(dClipped) > 3) // if the remaining line has more than 2 points
		{
			double[] dFinished = new double[Arrays.size(dClipped)]; // now that the line is outside the tile finish the current line
			System.arraycopy(dClipped, 0, dFinished, 0, dFinished.length);
			oClipped.add(dFinished);
		}
	}
	
	
	public static double[] getTileBounds(int nZ, int nX, int nY)
	{
		double[] dBounds = new double[4];
		Mercator oM = Mercator.getInstance();
		oM.tileBounds(nX, nY, nZ, dBounds);
		
		return dBounds;
	}
	
	
	public static double[] getClippingBounds(int nZ, int nX, int nY, double[] dBounds)
	{
		Mercator oM = Mercator.getInstance();
		double dPadding = oM.resolution(nZ) * 8;
		double[] dClip = new double[4];
		dClip[0] = dBounds[0] - dPadding;
		dClip[1] = dBounds[1] - dPadding;
		dClip[2] = dBounds[2] + dPadding;
		dClip[3] = dBounds[3] + dPadding;
		
		return dClip;
	}
	
	
	public static double[] getClippingBounds(int nZ, int nX, int nY)
	{
		return getClippingBounds(nZ, nX, nY, getTileBounds(nZ, nX, nY));
	}
	
	
	public static Area getClippingArea(int nZ, int nX, int nY, double[] dClipBounds)
	{
		Path2D.Double oTilePath = new Path2D.Double(); // create clipping boundary
		oTilePath.moveTo(dClipBounds[0], dClipBounds[1]);
		oTilePath.lineTo(dClipBounds[0], dClipBounds[3]);
		oTilePath.lineTo(dClipBounds[2], dClipBounds[3]);
		oTilePath.lineTo(dClipBounds[2], dClipBounds[1]);
		oTilePath.closePath();
		return new Area(oTilePath);
	}
	
	
	public static Area getClippingArea(int nZ, int nX, int nY)
	{
		return getClippingArea(nZ, nX, nY, getClippingBounds(nZ, nX, nY));
	}
}
