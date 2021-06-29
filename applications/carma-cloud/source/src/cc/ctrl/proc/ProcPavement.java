/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
import cc.geosrv.xodr.pvmt.XodrPvmtParser;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.MathUtil;
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

/**
 *
 * @author aaron.cherney
 */
public class ProcPavement extends ProcCtrl
{
	String m_sXodrDir;
	String m_sLineArcBase;
	public ProcPavement(String sLineArcDir, String sLineArcBase, String sXodrDir)
	{
		super(sLineArcDir);
		m_sXodrDir = sXodrDir;
		m_sLineArcBase = sLineArcBase;
	}
	
	
	@Override
	public void parseMetadata(Path oSource)
	   throws Exception
	{
		new XodrPvmtParser().parseXodrToCLA(oSource, m_sLineArcBase);
	}


	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
//		ArrayList<CtrlLineArcs> oLanesByRoads = ProcCtrl.combineLaneByRoad(oLanes, dTol);
//		return oLanesByRoads;
		return oLanes;
	}	


	@Override
	protected void proc(String sLineArcsFile, double dTol) throws Exception
	{
		try
		{
			String sPvmtFile = m_sXodrDir + sLineArcsFile.substring(sLineArcsFile.lastIndexOf("/") + 1).replace(".bin", ".xodr.pvmt");
			parseMetadata(Paths.get(sPvmtFile));
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			ArrayList<CtrlLineArcs> oLineArcs = new ArrayList();
			try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(Paths.get(sLineArcsFile + ".pvmt")))))
			{
				while (oIn.available() > 0)
				{
					oLineArcs.add(new CtrlLineArcs(oIn));
				}
			}
			oLineArcs = combine(oLineArcs, dTol);
			ArrayList<int[]> oTiles = new ArrayList();
			for (CtrlLineArcs oCLA : oLineArcs)
			{
				TrafCtrl oCtrl = new TrafCtrl("pavement", oCLA.m_nLaneType, 0, oCLA.m_dLineArcs, "", true);
				oCtrls.add(oCtrl);
				oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom);
				updateTiles(oTiles, oCtrl.m_oFullGeo.m_oTiles);
			}
			renderTiledData(oCtrls, oTiles);
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}


	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		TdLayer oLayer = new TdLayer("pavement", new String[]{"type"}, new String[]{"d", "s"}, TdLayer.POLYGON);
		int nDriving = XodrUtil.getLaneType("driving");
		int[] nTags = new int[2];
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oLayer.clear();

			ArrayList<ArrayList<double[]>> oClippedLanes = new ArrayList();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);
			double[][] dClips = new double[][]{Arrays.newDoubleArray(512), Arrays.newDoubleArray(512), Arrays.newDoubleArray(512)};
			oClippedLanes.add(new ArrayList());
			oClippedLanes.add(new ArrayList());
			oClippedLanes.add(new ArrayList());
			for (TrafCtrl oCtrl : oCtrls)
			{
				if (Collections.binarySearch(oCtrl.m_oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0)
					continue;
				
				for (ArrayList oClipped : oClippedLanes)
					oClipped.clear();
				TileUtil.clipCtrlGeoForTile(oCtrl.m_oFullGeo, dClips, dClipBounds, oClippedLanes);
				int nParts = oClippedLanes.get(0).size();
				for (int nPart = 0; nPart < nParts; nPart++)
				{
					double[] dNT = oClippedLanes.get(1).get(nPart);
					double[] dPT = oClippedLanes.get(2).get(nPart);

					double[] dClippedPavement = Geo.createPolygon(dPT, dNT);
					if (!Geo.isClockwise(dClippedPavement, 1))
					{
						dClippedPavement = Geo.reverseOrder(dClippedPavement);
					}

					nTags[1] = MathUtil.bytesToInt(oCtrl.m_yControlValue) == nDriving ? 0 : 1;
					oLayer.add(new TdFeature(dClippedPavement, nTags, oCtrl));
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
}
