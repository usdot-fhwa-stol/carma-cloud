/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import static cc.ctrl.proc.ProcCtrl.g_sTrafCtrlDir;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
import cc.geosrv.xodr.rdmk.RoadMark;
import cc.util.Arrays;
import cc.util.BufferedInStream;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.TileUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
public class ProcLatPerm extends ProcCtrl
{
//	private static double RDMKOFFSET = 0.0762;
	private static double RDMKOFFSET = 0.16;
	private static double BROKENSPACE = 9.144;
	private static double BROKENLINE = 3.048;
	private ArrayList<int[]> m_oRdMkInfo = new ArrayList();
	private int[] m_nColors = Arrays.newIntArray();
	private static Comparator<int[]> RDMKCOMP = (int[] n1, int[] n2) -> 
	{
		int nRet = Integer.compare(n1[1], n2[1]);
		if (nRet == 0)
			nRet = Integer.compare(n1[0], n2[0]);
		return nRet;
	};


	public ProcLatPerm(String sLineArcDir)
	{
		super(sLineArcDir);
	}

	
	@Override
	public void parseMetadata(Path oSource) throws Exception
	{
		m_oRdMkInfo.clear();
		m_nColors[0] = 1;
		try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(oSource)))
		{
			while (oIn.available() > 0)
			{
				int[] nTemp = new int[oIn.readInt()]; // read number elements
				nTemp[0] = oIn.readInt(); // read first 4 bytes of id
				nTemp[1] = oIn.readInt(); // read second 4 bytes of id
				int nIndex = 3; // start at 3, index 2 will hold which set of tags is ready to be used
				while (nIndex < nTemp.length)
					nTemp[nIndex++] = oIn.readInt();
				
				m_oRdMkInfo.add(nTemp);
			}
		}
		Collections.sort(m_oRdMkInfo, RDMKCOMP);
	}


	@Override
	protected void proc(String sLineArcsFile, double dTol) throws Exception
	{
		try
		{
			parseMetadata(Paths.get(sLineArcsFile + ".meta"));
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			ArrayList<CtrlLineArcs> oLineArcs = new ArrayList();
			try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(Paths.get(sLineArcsFile)))))
			{
				while (oIn.available() > 0)
				{
					oLineArcs.add(new CtrlLineArcs(oIn));
				}
			}
			ArrayList<int[]> oTiles = new ArrayList();
			int[] nSearch = new int[2];
			int nShoulder = XodrUtil.getLaneType("shoulder");
			for (CtrlLineArcs oCLA : oLineArcs)
			{
				XodrUtil.splitLaneSectionId(oCLA.m_lLaneSectionId, 0, nSearch);
				int nSIndex = Collections.binarySearch(m_oRdMkInfo, nSearch, RDMKCOMP);
				if (nSIndex < 0)
					System.currentTimeMillis();
				int[] nTags = m_oRdMkInfo.get(nSIndex);
				int nIndex = nTags[2]++;
				nIndex = 3 + nIndex * 8;
				if (nIndex == nTags.length)
				{
					System.out.println(oCLA.m_lLaneSectionId);
					System.currentTimeMillis();
				}
				int nInnerMarkType = nTags[nIndex++];
				int nInnerColor = nTags[nIndex++];
				int nInnerRoadType = nTags[nIndex++];
				boolean bSameInnerDir = nTags[nIndex++] == 0;
				int nOuterMarkType = nTags[nIndex++];
				int nOuterColor = nTags[nIndex++];
				int nOuterRoadType = nTags[nIndex++];
				boolean bSameOuterDir = nTags[nIndex] == 0;
				
				String sRoadMark = RoadMark.getType(nInnerMarkType);
				String sLaneType = XodrUtil.getLaneType(nInnerRoadType);
				int nCtrlVal = getInnerLatPerm(sRoadMark, sLaneType, bSameInnerDir);
				nCtrlVal <<= 16;
				sRoadMark = RoadMark.getType(nOuterMarkType);
				sLaneType = XodrUtil.getLaneType(nOuterRoadType);
				nCtrlVal |= (getOuterLatPerm(sRoadMark, sLaneType, bSameOuterDir) & 0xff);
				TrafCtrl oCtrl = new TrafCtrl("latperm", nCtrlVal, 0, oCLA.m_dLineArcs);
				if (oCLA.m_nLaneType != nShoulder)
				{
					oCtrls.add(oCtrl);
					m_nColors = Arrays.add(m_nColors, nInnerColor, nOuterColor);
				}
				oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom);
				updateTiles(oTiles, oCtrl.m_oFullGeo.m_oTiles);
			}
			renderTiledData(oCtrls, oTiles, m_nColors);
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}


	public int getInnerLatPerm(String sRdMkType, String sLaneType, boolean bSameDir)
	{
		String sVal = "none";
		switch (sLaneType)
		{
			case "offRamp":
			case "driving":
			{
				if (bSameDir)
				{
					sVal = "permitted";
				}
				else
				{
//					if (sLaneType.endsWith("solid")) // broken solid, solid, and solid solid
//						return TrafCtrlEnums.getCtrlVal("latperm", "none");
					if (sLaneType.endsWith("broken"))
						sVal = "passing-only";
				}
				break;
			}
			case "onRamp":
			case "shoulder":
			{
				if (bSameDir)
					sVal = "emergency-only";
			}
//			case "restricted":
//			case "none":
//			default:
		}
		
		return TrafCtrlEnums.getCtrlVal("latperm", sVal); //"none", "permitted", "passing-only", "emergency-only"
	}
	
	
		public int getOuterLatPerm(String sRdMkType, String sLaneType, boolean bSameDir)
	{
		String sVal = "none";
		switch (sLaneType)
		{
			case "offRamp":
			case "driving":
			{
				if (bSameDir)
				{
					sVal = "permitted";
				}
				else
				{
//					if (sLaneType.startsWith("solid")) // broken solid, solid, and solid solid
//						return TrafCtrlEnums.getCtrlVal("latperm", "none");
					if (sLaneType.startsWith("broken"))
						sVal = "passing-only";
				}
				break;
			}
			case "onRamp":
			case "shoulder":
			{
				if (bSameDir)
					sVal = "emergency-only";
			}
//			case "restricted":
//			case "none":
//			default:
		}
		
		return TrafCtrlEnums.getCtrlVal("latperm", sVal); //"none", "permitted", "passing-only", "emergency-only"
	}
	
	
	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
		return oLanes;
	}


	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles, int[] nLineColors) throws IOException
	{
		HashMap<String, ArrayList<double[]>> oCtrlInners = new HashMap();
		HashMap<String, ArrayList<double[]>> oCtrlOuters = new HashMap();
		int nNone = TrafCtrlEnums.getCtrlVal("latperm", "none");
		int nEmergency = TrafCtrlEnums.getCtrlVal("latperm", "emergency-only");
		double[] dSeg = new double[4];
		for (TrafCtrl oCtrl : oCtrls)
		{
			if (oCtrl.m_oFullGeo.m_dAverageWidth < WIDTHTH)
				continue;
			int nOuterVal = oCtrl.m_nControlValue & 0xff;
			int nInnerVal = oCtrl.m_nControlValue >> 16;
			
			double[] dInner = oCtrl.m_oFullGeo.m_dPT;
			double[] dOuter = oCtrl.m_oFullGeo.m_dNT;
			String sId = TrafCtrl.getId(oCtrl.m_yId);
			oCtrlInners.put(sId, new ArrayList());
			oCtrlOuters.put(sId, new ArrayList());
			if (nOuterVal == nNone || nOuterVal == nEmergency)
			{
				Iterator<double[]> oIt = Arrays.iterator(dOuter, dSeg, 1, 2);
				double dHdg = 0.0;
				double dTangent = Math.PI / 2;
				double[] dOffsetLine = Arrays.newDoubleArray(Arrays.size(dOuter));
				dOffsetLine = Arrays.add(dOffsetLine, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				while (oIt.hasNext())
				{
					oIt.next();
					dHdg = Geo.heading(dSeg[0], dSeg[1], dSeg[2], dSeg[3]);
					double dAngle = dHdg + dTangent;
					double dX = Math.cos(dAngle) * RDMKOFFSET;
					double dY = Math.sin(dAngle) * RDMKOFFSET;
					dOffsetLine = Arrays.addAndUpdate(dOffsetLine, dSeg[0] + dX, dSeg[1] + dY);
				}

				oCtrlOuters.get(sId).add(dOffsetLine);
			}
			else
			{
				createDashedLine(dOuter, oCtrlOuters.get(sId), true);
			}
			
			if (nInnerVal == nNone || nInnerVal == nEmergency)
			{
				Iterator<double[]> oIt = Arrays.iterator(dInner, dSeg, 1, 2);
				double dHdg = 0.0;
				double dTangent = -Math.PI / 2;
				double[] dOffsetLine = Arrays.newDoubleArray(Arrays.size(dInner));
				dOffsetLine = Arrays.add(dOffsetLine, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				while (oIt.hasNext())
				{
					oIt.next();
					dHdg = Geo.heading(dSeg[0], dSeg[1], dSeg[2], dSeg[3]);
					double dAngle = dHdg + dTangent;
					double dX = Math.cos(dAngle) * RDMKOFFSET;
					double dY = Math.sin(dAngle) * RDMKOFFSET;
					dOffsetLine = Arrays.addAndUpdate(dOffsetLine, dSeg[0] + dX, dSeg[1] + dY);
				}

				oCtrlInners.get(sId).add(dOffsetLine);
			}
			else
			{
				createDashedLine(dInner, oCtrlInners.get(sId), false);
			}	
		}
		TdLayer oLayer = new TdLayer("latperm", new String[]{"color"}, RoadMark.COLORS, TdLayer.LINESTRING);
		int[] nOuterTags = new int[2];
		int[] nInnerTags = new int[2];
		int[] nColors = new int[2];
		ArrayList<double[]> oClipped = new ArrayList();
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oLayer.clear();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);
			Iterator<int[]> oColorIt = Arrays.iterator(nLineColors, nColors, 1, 2);
			for (TrafCtrl oCtrl : oCtrls)
			{
				oColorIt.next();
				if (Collections.binarySearch(oCtrl.m_oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0 || oCtrl.m_oFullGeo.m_dAverageWidth < WIDTHTH)
					continue;
				
				nInnerTags[1] = nColors[0];
				nOuterTags[1] = nColors[1];

				String sId = TrafCtrl.getId(oCtrl.m_yId);
				ArrayList<double[]> dInners = oCtrlInners.get(sId);
				ArrayList<double[]> dOuters = oCtrlOuters.get(sId);
				for (double[] dInner : dInners)
				{
					if (!Geo.boundingBoxesIntersect(dClipBounds[0], dClipBounds[1], dClipBounds[2], dClipBounds[3], dInner[1], dInner[2], dInner[3], dInner[4]))
						continue;

					oClipped.clear();
					TileUtil.clipLineString(dInner, 5, dClipBounds, oClipped);
					for (double[] dClipped : oClipped)
					{
						oLayer.add(new TdFeature(dClipped, nInnerTags, oCtrl));
					}
				}

				for (double[] dOuter : dOuters)
				{
					if (!Geo.boundingBoxesIntersect(dClipBounds[0], dClipBounds[1], dClipBounds[2], dClipBounds[3], dOuter[1], dOuter[2], dOuter[3], dOuter[4]))
						continue;

					oClipped.clear();
					TileUtil.clipLineString(dOuter, 5, dClipBounds, oClipped);
					for (double[] dClipped : oClipped)
					{
						oLayer.add(new TdFeature(dClipped, nOuterTags, oCtrl));
					}
				}				
			}
			if (!oLayer.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oLayer.write(oOut);
				}
			}
		}
	}
	
	
	private static void createDashedLine(double[] dLine, ArrayList<double[]> oLines, boolean bOuter)
	{
		double dTangent = bOuter ? Math.PI / 2 : -Math.PI / 2;
		double dLength = 0.0;
		double[] dSegment = new double[4];
		Iterator<double[]> oIt = Arrays.iterator(dLine, dSegment, 1, 2);
		while (oIt.hasNext())
		{
			oIt.next();
			dLength += Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
		}
		int nMarks = (int)Math.floor(dLength / (BROKENSPACE + BROKENLINE));
		double dMarkLength = (BROKENSPACE + BROKENLINE) * nMarks;
		double dOffset = (dLength - dMarkLength) / 2;
		oIt = Arrays.iterator(dLine, dSegment, 1, 2);
		dLength = 0.0;
		double dCurrent = dOffset;
		boolean bSpace = true;
		double[] dDash = Arrays.newDoubleArray();
		while (oIt.hasNext())
		{
			oIt.next();
			double dDist = Geo.distance(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
			dCurrent += dDist;
			if (bSpace)
			{
				if (dCurrent >= BROKENSPACE)
				{
					bSpace = false;
					dCurrent = 0.0;
					dDash = Arrays.newDoubleArray();
					dDash = Arrays.add(dDash, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				}
			}
			else
			{
				if (dCurrent >= BROKENLINE)
				{
					bSpace = true;
					dCurrent = 0.0;
					oLines.add(dDash);
				}
				else
				{
					double dHdg = Geo.heading(dSegment[0], dSegment[1], dSegment[2], dSegment[3]);
					double dAngle = dHdg + dTangent;
					dDash = Arrays.addAndUpdate(dDash, dSegment[0] + Math.cos(dAngle) * RDMKOFFSET, dSegment[1] + Math.sin(dAngle) * RDMKOFFSET);
				}
			}
		}
	}
}
