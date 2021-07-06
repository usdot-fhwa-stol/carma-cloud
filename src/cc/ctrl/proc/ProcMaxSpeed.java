/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlGeo;
import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrJunctionParser;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.MathUtil;
import cc.util.TileUtil;
import java.awt.geom.AffineTransform;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 *
 * @author aaron.cherney
 */
public class ProcMaxSpeed extends ProcCtrl
{
	private String m_sXodrDir;
	private ArrayList<SpdMapping> m_oSpds;
	private HashMap<String, String> m_oJunctions;
	private static double[] SIGN = new double[]{-0.5, 0.33, 0.5, 0.33, 0.5, -0.33, -0.5, -0.33};
	private static int DEFAULTSPD = 25;
	
	
	public ProcMaxSpeed(String sLineArcDir, String sXodrDir)
	{
		super(sLineArcDir);
		m_sXodrDir = sXodrDir;
	}

	@Override
	public void parseMetadata(Path oSource) throws Exception
	{
		m_oSpds = new ArrayList();
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oSource))))
		{
			while (oIn.available() > 0)
			{
				SpdMapping oTemp = new SpdMapping(oIn.readInt(), oIn.readInt());
				int nIndex = Collections.binarySearch(m_oSpds, oTemp);
				if (nIndex < 0)
					m_oSpds.add(~nIndex, oTemp);
			}
		}
	}


	@Override
	protected void proc(String sLineArcsFile, double dTol) throws Exception
	{
		parseMetadata(Paths.get(sLineArcsFile + ".spd"));
		m_oJunctions = new XodrJunctionParser().parseXodrIntersections(Paths.get(m_sXodrDir + sLineArcsFile.substring(sLineArcsFile.lastIndexOf("/")).replace(".bin", ".xodr")));
		ArrayList<TrafCtrl> oCtrls = new ArrayList();
		ArrayList<CtrlLineArcs> oLineArcs = new ArrayList();
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(Paths.get(sLineArcsFile)))))
		{
			while (oIn.available() > 0)
			{
				oLineArcs.add(new CtrlLineArcs(oIn));
			}
		}
		oLineArcs = combine(oLineArcs, dTol);
		ArrayList<int[]> oTiles = new ArrayList();
		int nShoulder = XodrUtil.getLaneType("shoulder");
		SpdMapping oSearch = new SpdMapping();
		for (CtrlLineArcs oCLA : oLineArcs)
		{
			oSearch.m_nId = oCLA.m_nLaneId;
			int nIndex = Collections.binarySearch(m_oSpds, oSearch);
			int nSpeed = nIndex >= 0 ? m_oSpds.get(nIndex).m_nSpd : DEFAULTSPD;
			TrafCtrl oCtrl = new TrafCtrl("maxspeed", nSpeed, 0, oCLA.m_dLineArcs, "", true, CC);
			String sRoadId = Integer.toString(XodrUtil.getRoadId(oCLA.m_nLaneId));
			if (oCLA.m_nLaneType != nShoulder && !m_oJunctions.containsKey(sRoadId))
				oCtrls.add(oCtrl);
			oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom, CC);
			updateTiles(oTiles, oCtrl.m_oFullGeo.m_oTiles);
		}
		renderTiledData(oCtrls, oTiles);
	}


	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
		ArrayList<CtrlLineArcs> oLanesByRoads = ProcCtrl.combineLaneByRoad(oLanes, dTol);
		Collections.sort(oLanesByRoads, CtrlLineArcs.CMPBYLANE);
		ArrayList<CtrlLineArcs> oCombined = new ArrayList();
		int nIndex = oLanesByRoads.size();
		SpdMapping oSearch = new SpdMapping();
		while (nIndex-- > 0)
		{
			CtrlLineArcs oCla = oLanesByRoads.get(nIndex);
			String sRoadId = Integer.toString(XodrUtil.getRoadId(oCla.m_nLaneId));
			if (m_oJunctions.containsKey(sRoadId))
			{
				oLanesByRoads.remove(nIndex);
				oCombined.add(oCla);
			}
		}
		int nLimit = oLanesByRoads.size();
		double dSqTol = dTol * dTol;
		for (int nOuter = 0; nOuter < nLimit; nOuter++)
		{
			CtrlLineArcs oCur = oLanesByRoads.get(nOuter);
			
			for (int nInner = 0; nInner < nLimit; nInner++)
			{
				if (nOuter == nInner)
					continue;
				CtrlLineArcs oCmp = oLanesByRoads.get(nInner);
				if (oCmp.m_nLaneType != oCur.m_nLaneType)
					continue;

				int nConnect = oCur.connects(oCmp, dSqTol);
				if (nConnect == CtrlLineArcs.CON_TEND_OSTART || nConnect == CtrlLineArcs.CON_TSTART_OEND) // the pts of both lines are in the same direction
				{
					oSearch.m_nId = oCur.m_nLaneId;
					int nSearchIndex = Collections.binarySearch(m_oSpds, oSearch);
					int nCurSpeed = nSearchIndex >= 0 ? m_oSpds.get(nSearchIndex).m_nSpd : DEFAULTSPD;
					
					oSearch.m_nId = oCmp.m_nLaneId;
					nSearchIndex = Collections.binarySearch(m_oSpds, oSearch);
					int nCmpSpeed = nSearchIndex >= 0 ? m_oSpds.get(nSearchIndex).m_nSpd : DEFAULTSPD;
					if (nCurSpeed != nCmpSpeed)
						continue;
					
					oCur.combine(oCmp, nConnect); // combine the points of oCmp into oCur's point array
					oLanesByRoads.remove(nInner);
					--nLimit;
					nInner = -1; // start inner loop over
				}
			}
			oCombined.add(oCur);
		}
		return oCombined;
	}
	
	
	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		String[] sEmpty = new String[0];
		TdLayer oLayer = new TdLayer("maxspeed", new String[]{"color"}, new String[]{"white", "yellow"}, TdLayer.POLYGON);
		TdLayer oNumbers = new TdLayer("maxspeed-numbers", sEmpty, sEmpty, TdLayer.LINESTRING);
		int[] nEmpty = new int[0];
		int[] nTags = new int[2];
		double[] dPt = new double[2];
		double[] dPoint = new double[2];
		double[] dSign = new double[SIGN.length + 1];
		double[][] dNumbers = new double[NUMBERS.length][];
		int[] nNumbersPts = new int[NUMBERS.length];
		for (int nIndex = 0; nIndex < NUMBERS.length; nIndex++)
		{
			double[] dTemp = new double[NUMBERS[nIndex].length + 1];
			dTemp[0] = dTemp.length;
			dNumbers[nIndex] = dTemp;
			nNumbersPts[nIndex] = NUMBERS[nIndex].length / 2;
		}
		dSign[0] = dSign.length;
		int nSignPts = SIGN.length / 2;
		HashMap<String, double[]> oChars = new HashMap();
		HashMap<String, Integer> oCharPts = new HashMap();
		for (Entry<String, double[]> oEntry : CHARS.entrySet())
		{
			double[] dTemp = new double[oEntry.getValue().length + 1];
			dTemp[0] = dTemp.length;
			oChars.put(oEntry.getKey(), dTemp);
			oCharPts.put(oEntry.getKey(), oEntry.getValue().length / 2);
		}
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oLayer.clear();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);

			for (TrafCtrl oCtrl : oCtrls)
			{
				CtrlGeo oFullGeo = oCtrl.m_oFullGeo;
				if (Collections.binarySearch(oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0 || oFullGeo.m_dLength < LENLOWTH || oFullGeo.m_dAverageWidth < WIDTHTH)
					continue;

				double dHdg = getCoordAndHeadingAtLength(oFullGeo.m_dC, 5.0, false, dPt);
				if (Double.isNaN(dHdg))
					continue;

				double dOffsetAngle;
				if (oCtrl.m_bRegulatory)
				{
					nTags[1] = 0;
					dOffsetAngle = Math.PI / 2;
				}
				else
				{
					nTags[1] = 1;
					dOffsetAngle = -Math.PI / 2;
				}
				
				dPt[0] += Math.cos(dHdg + dOffsetAngle);
				dPt[1] += Math.sin(dHdg + dOffsetAngle);
				double dXc = dPt[0];
				double dYc = dPt[1];
				AffineTransform oAt = new AffineTransform();
				oAt.translate(dPt[0], dPt[1]);
				oAt.rotate(dHdg, 0, 0);
				oAt.transform(SIGN, 0, dSign, 1, nSignPts);

				double[] dSignBounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};		
				Iterator<double[]> oIt = Arrays.iterator(dSign, dPoint, 1, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					Geo.updateBounds(dPoint[0], dPoint[1], dSignBounds);
				}

				nTags[1] = oCtrl.m_bRegulatory ? 0 : 1;
				if (Geo.boundingBoxesIntersect(dClipBounds, dSignBounds))
				{
					oLayer.add(new TdFeature(dSign, nTags, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					
					dPt[0] += Math.cos(dHdg + Mercator.PI_OVER_TWO) * 0.15;
					dPt[1] += Math.sin(dHdg + Mercator.PI_OVER_TWO) * 0.15;

					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					String sVal = Integer.toString(MathUtil.bytesToInt(oCtrl.m_yControlValue));
					if (sVal.length() == 1) // speed limit zero exception
						sVal = "0" + sVal;
					int nNumber = Integer.parseInt(Character.toString(sVal.charAt(0)));
					oAt.transform(NUMBERS[nNumber], 0, dNumbers[nNumber], 1, nNumbersPts[nNumber]);
					
					oNumbers.add(new TdFeature(dNumbers[nNumber], nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					
					dPt[0] += Math.cos(dHdg - Mercator.PI_OVER_TWO) * 0.15;
					dPt[1] += Math.sin(dHdg - Mercator.PI_OVER_TWO) * 0.15;

					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					
					nNumber = Integer.parseInt(Character.toString(sVal.charAt(1)));
					oAt.transform(NUMBERS[nNumber], 0, dNumbers[nNumber], 1, nNumbersPts[nNumber]);
					oNumbers.add(new TdFeature(dNumbers[nNumber], nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					dPt[0] += Math.cos(dHdg) * 0.33;
					dPt[1] += Math.sin(dHdg) * 0.33;
					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					oAt.transform(CHARS.get("A"), 0, oChars.get("A"), 1, oCharPts.get("A"));
					
					oNumbers.add(new TdFeature(oChars.get("A"), nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					dPt[0] += Math.cos(dHdg) * 0.33;
					dPt[1] += Math.sin(dHdg) * 0.33;
					dPt[0] += Math.cos(dHdg + Mercator.PI_OVER_TWO) * 0.2;
					dPt[1] += Math.sin(dHdg + Mercator.PI_OVER_TWO) * 0.2;
					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					oAt.transform(CHARS.get("M"), 0, oChars.get("M"), 1, oCharPts.get("M"));
					
					oNumbers.add(new TdFeature(oChars.get("M"), nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					dPt[0] += Math.cos(dHdg) * 0.33;
					dPt[1] += Math.sin(dHdg) * 0.33;
					dPt[0] += Math.cos(dHdg - Mercator.PI_OVER_TWO) * 0.2;
					dPt[1] += Math.sin(dHdg - Mercator.PI_OVER_TWO) * 0.2;
					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					oAt.transform(CHARS.get("X"), 0, oChars.get("X"), 1, oCharPts.get("X"));
					
					oNumbers.add(new TdFeature(oChars.get("X"), nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					dPt[0] += Math.cos(dHdg) * -0.33;
					dPt[1] += Math.sin(dHdg) * -0.33;
					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					oAt.transform(CHARS.get("P"), 0, oChars.get("P"), 1, oCharPts.get("P"));
					
					oNumbers.add(new TdFeature(oChars.get("P"), nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					dPt[0] += Math.cos(dHdg) * -0.33;
					dPt[1] += Math.sin(dHdg) * -0.33;
					dPt[0] += Math.cos(dHdg + Mercator.PI_OVER_TWO) * 0.2;
					dPt[1] += Math.sin(dHdg + Mercator.PI_OVER_TWO) * 0.2;
					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					oAt.transform(CHARS.get("M"), 0, oChars.get("M"), 1, oCharPts.get("M"));
					
					oNumbers.add(new TdFeature(oChars.get("M"), nEmpty, oCtrl));
					
					dPt[0] = dXc;
					dPt[1] = dYc;
					dPt[0] += Math.cos(dHdg) * -0.33;
					dPt[1] += Math.sin(dHdg) * -0.33;
					dPt[0] += Math.cos(dHdg - Mercator.PI_OVER_TWO) * 0.2;
					dPt[1] += Math.sin(dHdg - Mercator.PI_OVER_TWO) * 0.2;
					oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
					oAt.scale(0.45, 0.45);
					oAt.transform(CHARS.get("H"), 0, oChars.get("H"), 1, oCharPts.get("H"));
					
					oNumbers.add(new TdFeature(oChars.get("H"), nEmpty, oCtrl));
				}
				
				
			}
			if (!oLayer.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oLayer.write(oOut);
					oNumbers.write(oOut);
				}
			}
		}
	}
	
	
	private class SpdMapping implements Comparable<SpdMapping>
	{
		int m_nId;
		int m_nSpd;
		
		SpdMapping()
		{
		}
		
		SpdMapping(int nId, int nSpd)
		{
			m_nId = nId;
			m_nSpd = nSpd;
		}


		@Override
		public int compareTo(SpdMapping o)
		{
			return Integer.compare(m_nId, o.m_nId);
		}
	}
}
