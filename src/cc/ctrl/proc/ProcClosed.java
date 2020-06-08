/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import static cc.ctrl.proc.ProcCtrl.g_sTrafCtrlDir;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
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

/**
 *
 * @author aaron.cherney
 */
public class ProcClosed extends ProcCtrl
{	
	public ProcClosed(String sLineArcDir)
	{
		super(sLineArcDir);
	}

	@Override
	public void parseMetadata(Path oSource) throws Exception
	{

	}


	@Override
	protected void proc(String sLineArcsFile, double dTol) throws Exception
	{
		try
		{
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			ArrayList<CtrlLineArcs> oLineArcs = new ArrayList();
			try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(Paths.get(sLineArcsFile)))))
			{
				while (oIn.available() > 0)
				{
					oLineArcs.add(new CtrlLineArcs(oIn));
				}
			}
			int[] nTileIndices = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			ArrayList<int[]> nTiles = new ArrayList();
			for (CtrlLineArcs oCLA : oLineArcs)
			{
				if (XodrUtil.getLaneType(oCLA.m_nLaneType).compareTo("roadWorks") != 0)
					continue;
				
				TrafCtrl oCtrl = new TrafCtrl("closed", "", 0, oCLA.m_dLineArcs); 
				oCtrls.add(oCtrl);
				oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom);
				updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
//				updateTileRange(nTileIndices, oCtrl.m_oFullGeo.m_nTileIndices);
			}
//			if (checkTileRange(nTileIndices))
//				throw new Exception("Ctrl spans too many tiles");
			renderTiledData(oCtrls, nTiles);
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}


	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		String[] sEmpty = new String[0];
		TdLayer oLayer = new TdLayer("closed-poly", sEmpty, sEmpty, TdLayer.POLYGON);
		TdLayer oLayerOutline  = new TdLayer("closed-outline", sEmpty, sEmpty, TdLayer.LINESTRING);
		int[] nTags = new int[0];
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oLayer.clear();
			oLayerOutline.clear();
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

					double[] dClippedPoly = Geo.createPolygon(dPT, dNT);
					if (!Geo.isClockwise(dClippedPoly, 1))
					{
						dClippedPoly = Geo.reverseOrder(dClippedPoly);
					}


					oLayer.add(new TdFeature(dClippedPoly, nTags, oCtrl));
					dClippedPoly = Arrays.add(dClippedPoly, dClippedPoly[1], dClippedPoly[2]);
					oLayerOutline.add(new TdFeature(dClippedPoly, nTags, oCtrl));
				}
			}

			if (!oLayer.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oLayer.write(oOut);
					oLayerOutline.write(oOut);
				}
			}
		}
	}


	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
		return oLanes;
	}
}