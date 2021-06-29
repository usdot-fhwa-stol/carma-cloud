/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.TrafCtrl;
import cc.ctrl.CtrlLineArcs;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.TileUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public abstract class ProcCtrl
{
	public static double g_dExplodeStep;
	public static String g_sTrafCtrlDir;
	public static String g_sTdFileFormat;
	public static String g_sGeolanesDir;
	public static int g_nDefaultZoom = Integer.MIN_VALUE;
	protected static final double WIDTHTH = 1.5;
	protected static final double LENLOWTH = 75.0;
	protected static final double STOPLINEENDOFFSET = 0.6;
	protected static final double STOPLINELATOFFSET = 0.1;
	protected static double[][] NUMBERS = new double[][]
	{
		new double[]{-0.1, 0.3, 0.1, 0.3, 0.2, 0.2, 0.2, -0.2, 0.1, -0.3, -0.1, -0.3, -0.2, -0.2, -0.2, 0.2, -0.1, 0.3},
		new double[]{-0.1, 0.2, 0.0, 0.3, 0.0, -0.3, -0.1, -0.3, 0.1, -0.3},
		new double[]{-0.2, 0.2, -0.1, 0.3, 0.1, 0.3, 0.2, 0.2, 0.2, 0.1, 0.1, 0.0, -0.1, 0.0, -0.2, -0.1, -0.2, -0.3, 0.2, -0.3},
		new double[]{-0.2, 0.2, -0.1, 0.3, 0.1, 0.3, 0.2, 0.2, 0.2, 0.1, 0.1, 0.0, 0.0, 0.0, 0.1, 0.0, 0.2, -0.1, 0.2, -0.2, 0.1, -0.3, -0.1, -0.3, -0.2, -0.2},
		new double[]{0.2, -0.1, -0.2, -0.1, -0.2, 0.0, 0.1, 0.3, 0.1, -0.3},
		new double[]{0.2, 0.3, -0.2, 0.3, -0.2, 0.0, 0.1, 0.0, 0.2, -0.1, 0.2, -0.2, 0.1, -0.3, -0.2, -0.3},
		new double[]{0.1, 0.3, 0.0, 0.3, -0.2, 0.1, -0.2, -0.2, -0.1, -0.3, 0.1, -0.3, 0.2, -0.2, 0.2, -0.1, 0.1, 0.0, -0.2, 0.0},
		new double[]{-0.2, 0.3, 0.2, 0.3, 0.2, 0.2, 0.0, 0.0, -0.1, -0.2, -0.1, -0.3},
		new double[]{-0.1, 0.0, -0.2, 0.1, -0.2, 0.2, -0.1, 0.3, 0.1, 0.3, 0.2, 0.2, 0.2, 0.1, 0.1, 0.0, 0.2, -0.1, 0.2, -0.2, 0.1, -0.3, -0.1, -0.3, -0.2, -0.2, -0.2, -0.1, -0.1, 0.0, 0.1, 0.0},
		new double[]{0.2, 0.0, -0.1, 0.0, -0.2, 0.1, -0.2, 0.2, -0.1, 0.3, 0.1, 0.3, 0.2, 0.2, 0.2, -0.1, 0.0, -0.3, -0.1, -0.3}
	};
	protected static Comparator<int[]> LANETYPECOMP = (int[] n1, int[] n2) -> 
	{
		return Integer.compare(n1[0], n2[0]);
	};
	protected final static HashMap<String, double[]> CHARS = new HashMap();
	static
	{
		CHARS.put("A", new double[]{0.05, 0.0, -0.05, 0.0, -0.1, -0.2, 0.0, 0.2, 0.1, -0.2});
		CHARS.put("H", new double[]{-0.1, -0.2, -0.1, 0.2, -0.1, 0.0, 0.1, 0.0, 0.1, 0.2, 0.1, -0.2});
		CHARS.put("M", new double[]{-0.1, -0.2, -0.1, 0.2, 0.0, 0.0, 0.1, 0.2, 0.1, -0.2});
		CHARS.put("P", new double[]{-0.1, -0.2, -0.1, 0.2, 0.1, 0.2, 0.1, 0.0, -0.1, 0.0});
		CHARS.put("X", new double[]{-0.1, -0.2, 0.1, 0.2, 0.0, 0.0, -0.1, 0.2, 0.1, -0.2});
		CHARS.put("s", new double[]{0.1, 0.0, -0.1, 0.0, -0.1, -0.1, 0.1, -0.1, 0.1, -0.2, -0.1, -0.2});
		CHARS.put(".", new double[]{0.1, -0.075, 0.1, -0.1, 0.075, -0.1, 0.075, -0.075, 0.1, -0.075});
	}
	
	public String m_sLineArcDir;
	
	public ProcCtrl(String sLineArcDir)
	{
		m_sLineArcDir = sLineArcDir;
	}
	
	
	public void parseMetadata(String sSource)
	   throws Exception
	{
		parseMetadata(Paths.get(sSource));
	}
	public abstract void parseMetadata(Path oSource) throws Exception;
	
	public void process(String sLineArcsFile, double dTol)
	   throws Exception
	{
		if (g_nDefaultZoom == Integer.MIN_VALUE)
			throw new Exception("Static variables not initialized.");
		proc(sLineArcsFile, dTol);
	}
	
	protected abstract void proc(String sLineArcsFile, double dTol) throws Exception;
	public abstract ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol);
//	public abstract void renderTiledData(ArrayList<TrafCtrl> oCtrls, int[] nTileIndices) throws IOException;

	public static void setStaticVariables(double dStep, String sDir, String sTdFF, int nZoom)
	{
		g_dExplodeStep = dStep;
		g_sTrafCtrlDir = sDir;
		g_sTdFileFormat = sTdFF;
		g_nDefaultZoom = nZoom;
		g_sGeolanesDir = sTdFF.substring(0, sTdFF.indexOf("/td/") + 1) + "geolanes";
	}
	
	
	protected static boolean checkTileRange(int[] nTileIndices)
	{
		return (Math.abs(nTileIndices[2] - nTileIndices[0]) > 1000 || Math.abs(nTileIndices[3] - nTileIndices[1]) > 1000);
	}
	
	
	public static void updateTiles(ArrayList<int[]> oTiles, ArrayList<int[]> oCtrlTiles)
	{
		for (int[] nCtrlTile : oCtrlTiles)
		{
			int nIndex = Collections.binarySearch(oTiles, nCtrlTile, Mercator.TILECOMP);
			if (nIndex < 0)
				oTiles.add(~nIndex, nCtrlTile);
		}
	}
	
	
	public static void updateTileRange(int[] nTileIndices, int[] nTmpTiles)
	{
		if (nTmpTiles[0] < nTileIndices[0])
			nTileIndices[0] = nTmpTiles[0];
		if (nTmpTiles[1] < nTileIndices[1])
			nTileIndices[1] = nTmpTiles[1];
		if (nTmpTiles[2] > nTileIndices[2])
			nTileIndices[2] = nTmpTiles[2];
		if (nTmpTiles[3] > nTileIndices[3])
			nTileIndices[3] = nTmpTiles[3];
	}
	
	
	public static ArrayList<CtrlLineArcs> combineLaneByRoad(ArrayList<CtrlLineArcs> oLineArcs, double dTol)
	{
		Collections.sort(oLineArcs, CtrlLineArcs.CMPBYLANE);
		int nLastId = oLineArcs.get(0).m_nLaneId;
		ArrayList<CtrlLineArcs> oCombined = new ArrayList();
		ArrayList<CtrlLineArcs> oCurrRoad = new ArrayList();
		oCurrRoad.add(oLineArcs.get(0));
		double dSqTol = dTol * dTol;
		int nLimit = oLineArcs.size();
		for (int nIndex = 1; nIndex <= nLimit; nIndex++)
		{
			CtrlLineArcs oTemp = nIndex == nLimit ? null : oLineArcs.get(nIndex);
			if (oTemp == null || nLastId != oTemp.m_nLaneId)
			{
				CtrlLineArcs oRoad = oCurrRoad.get(0);
				boolean bDone = false;
				while (!bDone) // the order of the parts of the road are not guaranteed keep trying to combine parts until none are left
				{
					bDone = true;
					int nRoadIndex = oCurrRoad.size();
					while (nRoadIndex-- > 1)
					{
						int nConnect = oRoad.connects(oCurrRoad.get(nRoadIndex), dSqTol);
						if (nConnect > 0)
						{
							oRoad.combine(oCurrRoad.get(nRoadIndex), nConnect);
							oCurrRoad.remove(nRoadIndex);
							bDone = false;
							break;
						}
					}
				}
				oCombined.add(oRoad);
				for (int nRoadIndex = 1; nRoadIndex < oCurrRoad.size(); nRoadIndex++) // if for some reason a part of the road didnt get combined, add the part so we don't lose geometry
				{
					oCurrRoad.get(nRoadIndex).m_nLaneId = Integer.MIN_VALUE;
					oCombined.add(oCurrRoad.get(nRoadIndex));
				}
				oCurrRoad.clear(); // start of new road
				if (oTemp != null)
					nLastId = oTemp.m_nLaneId;
			}
			oCurrRoad.add(oTemp);
		}
		
		return oCombined;
	}
	
	static double getCoordAndHeadingAtLength(double[] dPoints, double dLength, boolean bReverse, double[] dPoint)
	{
		double dTotalLen = 0.0;
		dPoint[0] = dPoint[1] = Double.NaN;
		double dHdg = Double.NaN;
		int nSegSize = 22;
		int nEndX = nSegSize - 2;
		int nEndY = nSegSize - 1;
		Iterator<double[]> oIt = Arrays.iterator(dPoints, new double[nSegSize], 5, nSegSize - 2);
		while (oIt.hasNext())
		{
			double[] dSeg = oIt.next();
			double dLen = Geo.distance(dSeg[0], dSeg[1], dSeg[nEndX], dSeg[nEndY]);
			dTotalLen += dLen;
			if (dTotalLen > dLength)
			{
				dPoint[0] = (dSeg[0] + dSeg[nEndX]) / 2;
				dPoint[1] = (dSeg[1] + dSeg[nEndY]) / 2;
				dHdg = Geo.heading(dSeg[0], dSeg[1], dSeg[nEndX], dSeg[nEndY]);
				break;
			}
		}
		
		if (!Double.isNaN(dHdg) && bReverse)
			dHdg -= Math.PI;
		
		return dHdg;
	}
	
	static int getStopLine(TrafCtrl oCtrl, ArrayList<double[]> oStops)
	{
		double[] dStop = Arrays.newDoubleArray(8);
		double[] dPT = oCtrl.m_oFullGeo.m_dPT;
		double[] dNT = oCtrl.m_oFullGeo.m_dNT;
		double[] dC = oCtrl.m_oFullGeo.m_dC;
		int nIndex = Arrays.size(dC) - 2;
		double dAngle = Geo.heading(dPT[nIndex], dPT[nIndex + 1], dC[nIndex], dC[nIndex + 1]);
		dStop = Arrays.add(dStop, dPT[nIndex] + Math.cos(dAngle) * STOPLINELATOFFSET, dPT[nIndex + 1] + Math.sin(dAngle) * STOPLINELATOFFSET);
		dAngle += Math.PI;
		dStop = Arrays.add(dStop, dNT[nIndex] + Math.cos(dAngle) * STOPLINELATOFFSET, dNT[nIndex + 1] + Math.sin(dAngle) * STOPLINELATOFFSET);
		nIndex -= 2;
		double dLen = 0.0;
		while (dLen < STOPLINEENDOFFSET && nIndex > 3)
		{
			double dX1 = dC[nIndex];
			double dY1 = dC[nIndex + 1];
			double dX2 = dC[nIndex + 2];
			double dY2 = dC[nIndex + 3];
			dLen += Geo.distance(dX1, dY1, dX2, dY2);
			nIndex -= 2;
		}
		
		dAngle = Geo.heading(dNT[nIndex], dNT[nIndex + 1], dC[nIndex], dC[nIndex + 1]);
		dStop = Arrays.add(dStop, dNT[nIndex] + Math.cos(dAngle) * STOPLINELATOFFSET, dNT[nIndex + 1] + Math.sin(dAngle) * STOPLINELATOFFSET);
		dAngle -= Math.PI;
		dStop = Arrays.add(dStop, dPT[nIndex] + Math.cos(dAngle) * STOPLINELATOFFSET, dPT[nIndex + 1] + Math.sin(dAngle) * STOPLINELATOFFSET);
		oStops.add(dStop);
		return nIndex;
	}
	
	
	public static void writeIndexFile(ArrayList<TrafCtrl> oCtrls, int nX, int nY)
	   throws IOException
	{
		ArrayList<TrafCtrl> oToWrite = new ArrayList();
		double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);
		for (TrafCtrl oCtrl : oCtrls)
		{
			if (!Geo.boundingBoxesIntersect(oCtrl.m_oFullGeo.m_dBB, dClipBounds))
				continue;
			if (TileUtil.includeInTile(oCtrl.m_oFullGeo, dClipBounds))
				oToWrite.add(oCtrl);
		}
		
		if (oToWrite.isEmpty())
			return;
		
		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY) + ".ndx"), FileUtil.APPENDTO, FileUtil.FILEPERS))))
		{
			for (TrafCtrl oCtrl : oToWrite)
			{
				oCtrl.writeIndex(oOut);
			}
		}
	}
	
	
	public static void updateIndex(String sIndexFile, byte[] yId, long lTimestamp)
		throws IOException
	{
		Path oIndexFile = Paths.get(sIndexFile);
		int nPos = 0;
		byte[] yIdBuf = new byte[16];
		long lSize = Files.size(oIndexFile);
		int nParts = (int)(lSize / 36);
		int nCount = 0;
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
		{
			while (oIn.available() > 0)
			{
				oIn.skipBytes(4); // skip type (int)
				oIn.read(yIdBuf);
				if (TrafCtrl.ID_COMP.compare(yIdBuf, yId) == 0)
					break;
				oIn.skipBytes(16); // skip both timestamps (2 longs)
				nPos += 36;
			}
		}
		if (nPos >= lSize)
			throw new IOException("Id not found");
		System.out.println(sIndexFile + " " + nPos);
		try (RandomAccessFile oRaf = new RandomAccessFile(sIndexFile, "rw"))
		{
			oRaf.seek(nPos);
			oRaf.skipBytes(4);
			oRaf.read(yIdBuf);
			System.out.println(TrafCtrl.getId(yIdBuf));
			oRaf.seek(nPos + 28); // want to edit the end timestamp, so seek to the record position and skip type(4) id(16) startts(8)
			oRaf.writeLong(lTimestamp);
		}
	}
	
	
	public static void renderCtrls(String sCtrlType, ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles)
		throws IOException
	{
		switch (sCtrlType)
		{
			case "signal":
			{
				ProcSignal.renderTiledData(oCtrls, nTiles);
				break;
			}
			case "stop":
			{
				ProcStop.renderTiledData(oCtrls, nTiles);
				break;
			}
			case "yield":
			{
				ProcYield.renderTiledData(oCtrls, nTiles);
				break;
			}
//			case "notowing":
//				break;
//			case "restricted":
//				break;
			case "closed":
			{
				ProcClosed.renderTiledData(oCtrls, nTiles);
				break;
			}
//			case "chains":
//				break; 
			case "direction":
			{
				ProcDirection.renderTiledData(oCtrls, nTiles);
				break;
			}
//			case "lataffinity":
//				break;
			case "latperm":
			{
				ProcLatPerm.renderTiledData(oCtrls, nTiles, new int[]{2, 4, 4});
				break;
			}
			case "opening":
			{
				ProcOpening.renderTiledData(oCtrls, nTiles);
				break;
			}
			case "closing":
			{
				ProcClosing.renderTiledData(oCtrls, nTiles);
				break;
			}
//			case "parking":
//				break;
//			case "minspeed":
//				break;
			case "maxspeed":
			{
				ProcMaxSpeed.renderTiledData(oCtrls, nTiles);
				break;
			}
			case "minhdwy":
			{
				ProcHeadway.renderTiledData(oCtrls, nTiles);
				break;
			}
//			case "maxvehmass":
//				break;
//			case "maxvehheight":
//				break;
//			case "maxvehwidth":
//				break;
//			case "maxvehlength":
//				break; 
//			case "maxaxles":
//				break; 
//			case "minvehocc":
//				break;
		}
	}
}



