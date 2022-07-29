/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlGeo;
import cc.ctrl.TrafCtrl;
import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrlEnums;
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

/**
 *
 * @author aaron.cherney
 */
public class ProcDirection extends ProcCtrl
{
	private HashMap<String, String> m_oJunctions;
	private String m_sXodrDir;
	private static final double[][] ARROW = new double[][]{new double[]{-0.5,0.0,0.5,0.0}, new double[]{0.15,0.35,0.5,0.0,0.15,-0.35}};
	
	public ProcDirection(String sLineArcDir, String sXodrDir)
	{
		super(sLineArcDir);
		m_sXodrDir = sXodrDir;
	}
	
	
	@Override
	public void parseMetadata(Path oSource)
	   throws Exception
	{
		m_oJunctions = new XodrJunctionParser().parseXodrIntersections(oSource);
	}


	@Override
	public void proc(String sLineArcsFile, double dTol)
	   throws Exception
	{
		try
		{
			parseMetadata(Paths.get(m_sXodrDir + sLineArcsFile.substring(sLineArcsFile.lastIndexOf("/")).replace(".bin", ".xodr")));
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
			ArrayList<int[]> nTiles = new ArrayList();
			int nShoulder = XodrUtil.getLaneType("shoulder");
			for (CtrlLineArcs oCLA : oLineArcs)
			{
				TrafCtrl oCtrl = new TrafCtrl("direction", "forward", 0, oCLA.m_dLineArcs, "", true, CC);
				if (oCLA.m_nLaneType != nShoulder)
					oCtrls.add(oCtrl);
				oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom, CC);
				updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
			}
			renderTiledData(oCtrls, nTiles);
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
		
	}


	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
		ArrayList<CtrlLineArcs> oLanesByRoads = ProcCtrl.combineLaneByRoad(oLanes, dTol);
		Collections.sort(oLanesByRoads, CtrlLineArcs.CMPBYLANE);
		ArrayList<CtrlLineArcs> oCombined = new ArrayList();
		int nIndex = oLanesByRoads.size();
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
		TdLayer oLayer = new TdLayer("direction", sEmpty, sEmpty, TdLayer.LINESTRING);
		int[] nTags = new int[0];
		double[] dPt = new double[2];
		double[] dPoint = new double[2];
		double[] dLine = new double[ARROW[0].length + 1];
		double[] dHead = new double[ARROW[1].length + 1];
		dLine[0] = dLine.length;
		dHead[0] = dHead.length;
		int nLinePts = ARROW[0].length / 2;
		int nHeadPts = ARROW[1].length / 2;
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

				int nArrows = 2 + ((int)(oFullGeo.m_dLength / LENLOWTH) - 1) * 2;
				double dPercentStep = 1.0 / (nArrows + 1);

				for (int nIndex = 1; nIndex <= nArrows; nIndex++)
				{
					boolean bForward = TrafCtrlEnums.getCtrlVal("direction", "forward") == MathUtil.bytesToInt(oCtrl.m_yControlValue);
					double dHdg = getCoordAndHeadingAtLength(oFullGeo.m_dC, oFullGeo.m_dLength * dPercentStep * nIndex, !bForward, dPt);
					if (Double.isNaN(dHdg))
						continue;

					AffineTransform oAt = new AffineTransform();
					oAt.translate(dPt[0], dPt[1]);
					oAt.rotate(dHdg, 0, 0);
					oAt.transform(ARROW[0], 0, dLine, 1, nLinePts);
					oAt.transform(ARROW[1], 0, dHead, 1, nHeadPts);

					double[] dArrowBounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};		
					Iterator<double[]> oIt = Arrays.iterator(dLine, dPoint, 1, 2);
					while (oIt.hasNext())
					{
						oIt.next();
						Geo.updateBounds(dPoint[0], dPoint[1], dArrowBounds);
					}
					oIt = Arrays.iterator(dHead, dPoint, 1, 2);
					while (oIt.hasNext())
					{
						oIt.next();
						Geo.updateBounds(dPoint[0], dPoint[1], dArrowBounds);
					}
					
					if (Geo.boundingBoxesIntersect(dClipBounds[0], dClipBounds[1], dClipBounds[2], dClipBounds[3], dArrowBounds[0], dArrowBounds[1], dArrowBounds[2], dArrowBounds[3]))
					{
						oLayer.add(new TdFeature(dLine, nTags, oCtrl));
						oLayer.add(new TdFeature(dHead, nTags, oCtrl));
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
}
