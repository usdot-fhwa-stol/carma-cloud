/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.geosrv.Mercator;
import cc.util.Arrays;
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
public class ProcDebugOutlines extends ProcCtrl
{
	
	public ProcDebugOutlines(String sLineArcDir)
	{
		super(sLineArcDir);
	}
	
	
	@Override
	public void parseMetadata(Path oSource)
	   throws Exception
	{
		
	}


	@Override
	public void proc(String sLineArcsFile, double dTol)
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
			ArrayList<int[]> oTiles = new ArrayList();
			for (CtrlLineArcs oCLA : oLineArcs)
			{
				TrafCtrl oCtrl = new TrafCtrl("debug", "", 0, oCLA.m_dLineArcs); 
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


	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
		return oLanes;
	}	


	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		String[] sEmpty = new String[0];
		TdLayer oOutlines = new TdLayer("debug-o", sEmpty, sEmpty, TdLayer.LINESTRING);

		ArrayList<double[]> oClipped = new ArrayList();
		int[] nCenterTags = new int[0];

		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oOutlines.clear();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);
			for (TrafCtrl oCtrl : oCtrls)
			{
				if (Collections.binarySearch(oCtrl.m_oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0)
					continue;
				double[] dOutline = Arrays.newDoubleArray();
				dOutline = Arrays.add(dOutline, oCtrl.m_oFullGeo.m_dPT[1], oCtrl.m_oFullGeo.m_dPT[2]);
				dOutline = Arrays.add(dOutline, oCtrl.m_oFullGeo.m_dNT[1], oCtrl.m_oFullGeo.m_dNT[2]);
				oClipped.clear();
				TileUtil.clipLineString(dOutline, dClipBounds, oClipped);
				for (double[] dLine : oClipped)
				{
					oOutlines.add(new TdFeature(dLine, nCenterTags, oCtrl));
				}
				
				dOutline[0] = 1;
				int nIndex = Arrays.size(oCtrl.m_oFullGeo.m_dPT) - 2;
				dOutline = Arrays.add(dOutline, oCtrl.m_oFullGeo.m_dPT[nIndex], oCtrl.m_oFullGeo.m_dPT[nIndex + 1]);
				dOutline = Arrays.add(dOutline, oCtrl.m_oFullGeo.m_dNT[nIndex], oCtrl.m_oFullGeo.m_dNT[nIndex + 1]);
				oClipped.clear();
				TileUtil.clipLineString(dOutline, dClipBounds, oClipped);
				for (double[] dLine : oClipped)
				{
					oOutlines.add(new TdFeature(dLine, nCenterTags, oCtrl));
				}
			}
			if (!oOutlines.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oOutlines.write(oOut);
				}
			}
		}
	}
	
}
