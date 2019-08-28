/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

import cc.geosrv.Mercator;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.text.DecimalFormat;
import java.util.BitSet;
import cc.vector_tile.VectorTile;
import java.awt.geom.Path2D;


/**
 *
 * @author Federal Highway Administration
 */
public abstract class TileUtil
{
	public final static int MOVETO = 1;
	public final static int LINETO = 2;
	public final static int CLOSEPATH = 7;
	public static DecimalFormat DF = new DecimalFormat("#");
	
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
	
	public static void addPolygon(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent,Area oPoly, int[] nPointBuffer)
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
						nPointBuffer = addPoint(nPointBuffer, nPosX, nPosY);
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
						nPointBuffer = addPoint(nPointBuffer, nPosX, nPosY);
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
	}
	
	
	public static void addLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, double[] dLine, int[] nPointBuffer, int... nTags)
	{
		addLinestring(oFeatureBuilder, nCur, dMercBounds, nExtent, 0, dLine, nPointBuffer, nTags);
	}
	
	
	public static void addLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, int nStartPos, double[] dLine, int[] nPointBuffer, int... nTags)
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
				nPointBuffer = addPoint(nPointBuffer, nPosX, nPosY);
				dTemp = dCoords;
				dCoords = dPrev;
				dPrev = dTemp;
			}
		}
		writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false);
		oFeatureBuilder.setType(VectorTile.Tile.GeomType.LINESTRING);
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
	
	
//	public static void addLinestring(VectorTile.Tile.Feature.Builder oFeatureBuilder, int[] nCur, double[] dMercBounds, int nExtent, double[] dLine, int[] nPointBuffer)
//	{
//		int[] nPos = new int[2];
//		int[] nPrev = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
//
//		int[] nTemp;
//		nPointBuffer[0] = 1;
//		for (int i = 1; i < dLine.length;)
//		{
//			nPos[0] = getPos(Mercator.lonToMeters(dLine[i++]), dMercBounds[0], dMercBounds[2], nExtent, false);
//			nPos[1] = getPos(Mercator.latToMeters(dLine[i++]), dMercBounds[1], dMercBounds[3], nExtent, true);
//			if (nPos[0] != nPrev[0] || nPos[1] != nPrev[1])
//			{
//				nPointBuffer = addPoint(nPointBuffer, nPos[0], nPos[1]);
//				nTemp = nPos;
//				nPos = nPrev;
//				nPrev = nTemp;
//			}
//		}
//		writePointBuffer(oFeatureBuilder, nPointBuffer, nCur, false);
//	}
//	
	
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
		int nStart = 1;
		int nBound = (int)(nPointBuffer[0]);
		int nInc = 2;

		oFeatureBuilder.addGeometry(command(MOVETO, 1));
		int i = nStart;
		int nPosX = nPointBuffer[i];
		int nPosY = nPointBuffer[i + 1];
		int nDeltaX = nPosX - nCur[0];
		int nDeltaY = nPosY - nCur[1];
		oFeatureBuilder.addGeometry(parameter(nDeltaX));
		oFeatureBuilder.addGeometry(parameter(nDeltaY));
		nCur[0] += nDeltaX;
		nCur[1] += nDeltaY;
		i += nInc;
		oFeatureBuilder.addGeometry(command(LINETO, nPointBuffer[0] / 2 - 1));
		for (; i != nBound; i += nInc)
		{
			nPosX = nPointBuffer[i];
			nPosY = nPointBuffer[i + 1];
			nDeltaX = nPosX - nCur[0];
			nDeltaY = nPosY - nCur[1];
			oFeatureBuilder.addGeometry(parameter(nDeltaX));
			oFeatureBuilder.addGeometry(parameter(nDeltaY));
			nCur[0] += nDeltaX;
			nCur[1] += nDeltaY;
		}
		if (bClose)
			oFeatureBuilder.addGeometry(command(CLOSEPATH, 1));
	}
	
	
	public static int[] addPoint(int[] nPoints, int nX, int nY)
	{
		nPoints = ensureCapacity(nPoints, 2);
		int nIndex = nPoints[0]; // extra space for hidden point
		nPoints[nIndex++] = nX;
		nPoints[nIndex++] = nY;
		nPoints[0] = nIndex; // track insertion point in array
		return nPoints;
	}
	
	
	public static double[] addPoint(double[] dPoints, double[] dPoint)
	{
		dPoints = ensureCapacity(dPoints, dPoint.length);
		int nIndex = (int)dPoints[0]; // extra space for hidden point
		dPoints[nIndex++] = dPoint[0];
		dPoints[nIndex++] = dPoint[1];
		dPoints[0] = nIndex; // track insertion point in array
		return dPoints;
	}
	
	
	public static int hash(int nHrz, int nVrt)
	{
		return (nHrz << 16) + nVrt;
	}
	
	
	public static int[] ensureCapacity(int[] nArray, int nMinCapacity)
    {
        nMinCapacity += nArray[0];
        if (nArray.length < nMinCapacity)
        {
            int[] dNew = new int[(nMinCapacity * 2)];
            System.arraycopy(nArray, 0, dNew, 0, nArray.length);
            return dNew;
        }
        return nArray; // no changes were needed
    }


    public static double[] ensureCapacity(double[] dArray, int nMinCapacity)
    {
		if ((int)dArray[0] + nMinCapacity < dArray.length)
	        return dArray; // no changes were needed

		double[] dNew = new double[dArray.length * 2 + nMinCapacity];
		System.arraycopy(dArray, 0, dNew, 0, (int)dArray[0]);
		return dNew;
    }
	
	
	public static void writeOutline(VectorTile.Tile.Feature.Builder oFeature, VectorTile.Tile.Layer.Builder oLayer, VectorTile.Tile.Builder oTile)
	{
		oFeature.clear();
		oLayer.clear();
		oLayer.setVersion(2);
		oLayer.setName("tile_outline");
		oLayer.setExtent(256);
		oFeature.setType(VectorTile.Tile.GeomType.LINESTRING);
		oFeature.addGeometry(command(MOVETO, 1));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(command(LINETO, 4));
		oFeature.addGeometry(parameter(255));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(255));
		oFeature.addGeometry(parameter(-255));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(-255));
		oLayer.addFeatures(oFeature.build());
		oFeature.clear();
		oTile.addLayers(oLayer.build());
		oLayer.clear();
	}
	
	public static void writeBox(VectorTile.Tile.Feature.Builder oFeature, VectorTile.Tile.Layer.Builder oLayer, VectorTile.Tile.Builder oTile)
	{
		oFeature.clear();
		oLayer.clear();
		oLayer.setVersion(2);
		oLayer.setName("MRMS_RTEPC_10.0");
		oLayer.setExtent(256);
		oFeature.setType(VectorTile.Tile.GeomType.POLYGON);
		oFeature.addGeometry(command(MOVETO, 1));
		oFeature.addGeometry(parameter(128));
		oFeature.addGeometry(parameter(128));
		oFeature.addGeometry(command(LINETO, 3));
		oFeature.addGeometry(parameter(50));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(parameter(50));
		oFeature.addGeometry(parameter(-50));
		oFeature.addGeometry(parameter(0));
		oFeature.addGeometry(command(CLOSEPATH, 1));
		oLayer.addFeatures(oFeature.build());
		oFeature.clear();
		oTile.addLayers(oLayer.build());
		oLayer.clear();
	}
	
	
	static Path2D.Double getPath(double[] dRing)
	{
		Path2D.Double oPath = new Path2D.Double();
		oPath.moveTo(dRing[1], dRing[2]); // start at 1 because the group value is in index 0
		for (int i = 3; i < dRing.length;)
			oPath.lineTo(dRing[i++], dRing[i++]);
		oPath.closePath();
		
		return oPath;
	}
}
