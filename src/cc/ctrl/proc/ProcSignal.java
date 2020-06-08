/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlGeo;
import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import static cc.ctrl.proc.ProcCtrl.g_sTdFileFormat;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.Connection;
import cc.geosrv.xodr.Junction;
import cc.geosrv.xodr.Signal;
import cc.geosrv.xodr.XodrSignalParser;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.BufferedInStream;
import cc.util.FileUtil;
import cc.util.Geo;
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
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class ProcSignal extends ProcCtrl
{
	private static final double[][] SIGNAL = new double[][]{
										   new double[]{-0.45, 0.15, 0.45, 0.15, 0.45, -0.15, -0.45, -0.15},
										   new double[]{-0.33, 0.11, -0.27, 0.11, -0.23, 0.09, -0.21, 0.07, -0.19, 0.03, -0.19, -0.03, -0.21, -0.07, -0.23, -0.09, -0.27, -0.11, -0.33, -0.11, -0.37, -0.09, -0.39, -0.07, -0.41, -0.03, -0.41, 0.03, -0.39, 0.07, -0.37, 0.09},
										   new double[]{-0.03, 0.11, 0.03, 0.11, 0.07, 0.09, 0.09, 0.07, 0.11, 0.03, 0.11, -0.03, 0.09, -0.07, 0.07, -0.09, 0.03, -0.11, -0.03, -0.11, -0.07, -0.09, -0.09, -0.07, -0.11, -0.03, -0.11, 0.03, -0.09, 0.07, -0.07, 0.09},
										   new double[]{0.27, 0.11, 0.33, 0.11, 0.37, 0.09, 0.39, 0.07, 0.41, 0.03, 0.41, -0.03, 0.39, -0.07, 0.37, -0.09, 0.33, -0.11, 0.27, -0.11, 0.23, -0.09, 0.21, -0.07, 0.19, -0.03, 0.19, 0.03, 0.21, 0.07, 0.23, 0.09}
										   };
	private static final String[] COLORS = new String[]{"g", "y", "r"};
	private ArrayList<Junction> m_oJunctions = new ArrayList();
	private ArrayList<Signal> m_oSignals = new ArrayList();
	private int[] m_nInterPaths = Arrays.newIntArray();
	private int[] m_nSignalRoads = Arrays.newIntArray();
	private ArrayList<RoadPaths> m_oIncomingRoads = new ArrayList();
	private String m_sXodrDir;
	private static double MINLEN = 53.0;
	private static String[] SIGNAL_TYPES = new String[]{"1000001", "1000009", "1000010", "1000011"};
	
	public ProcSignal(String sLineArcDir, String sXodrDir)
	{
		super(sLineArcDir);
		m_sXodrDir = sXodrDir;
	}
	
	
	@Override
	public void parseMetadata(Path oSource)
	   throws Exception
	{
		m_oJunctions.clear();
		m_oSignals.clear();
		m_nInterPaths[0] = 1;
		m_nSignalRoads[0] = 1;
		m_oIncomingRoads.clear();
		new XodrSignalParser(SIGNAL_TYPES).parseSignalData(oSource, m_oJunctions, m_oSignals);
		for (Signal oSignal : m_oSignals)
		{
			Iterator<int[]> oIt = Arrays.iterator(oSignal.m_nRoads, new int[1], 1, 1);
			while (oIt.hasNext())
			{
				int nRoad = oIt.next()[0];
				int nIndex = Arrays.binarySearch(m_nSignalRoads, nRoad);
				if (nIndex < 0)
					m_nSignalRoads = Arrays.insert(m_nSignalRoads, nRoad, ~nIndex);
			}
		}
		
		RoadPaths oSearch = new RoadPaths();
		for (Junction oJunc : m_oJunctions)
		{
			for (Connection oConn : oJunc)
			{
				int nIndex = Arrays.binarySearch(m_nInterPaths, oConn.m_nConnRoad);
				if (nIndex < 0)
					m_nInterPaths = Arrays.insert(m_nInterPaths, oConn.m_nConnRoad, ~nIndex);
				
				oSearch.m_nRoadId = oConn.m_nInRoad;
				nIndex = Collections.binarySearch(m_oIncomingRoads, oSearch);
				if (nIndex < 0)
				{
					nIndex = ~nIndex;
					m_oIncomingRoads.add(nIndex, new RoadPaths(oConn.m_nInRoad));
				}
				
				if (Arrays.binarySearch(m_nSignalRoads, oConn.m_nConnRoad) >= 0)
				{
					RoadPaths oTemp = m_oIncomingRoads.get(nIndex);
					oTemp.addConnection(oConn);
				}
			}
		}	
	}


	@Override
	public void proc(String sLineArcsFile, double dTol)
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
			ArrayList<int[]> oTiles = new ArrayList();
			for (CtrlLineArcs oCLA : oLineArcs)
			{
				TrafCtrl oCtrl = new TrafCtrl("signal", "", 0, oCLA.m_dLineArcs); 
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
		ArrayList<CtrlLineArcs> oLanesByRoads = ProcCtrl.combineLaneByRoad(oLanes, dTol);
		Collections.sort(oLanesByRoads, CtrlLineArcs.CMPBYLANE);
		ArrayList<CtrlLineArcs> oCombined = new ArrayList();
		ArrayList<CtrlLineArcs> oInRoadsToSignals = new ArrayList();
		int nIndex = oLanesByRoads.size();
		RoadPaths oRPSearch = new RoadPaths();
		ArrayList<Signal> oSignals = new ArrayList();
		while (nIndex-- > 0)
		{
			int nRoadId = XodrUtil.getRoadId(oLanesByRoads.get(nIndex).m_nLaneId);
			oRPSearch.m_nRoadId = nRoadId;
			int nSearchIndex;
			if (Arrays.binarySearch(m_nInterPaths, nRoadId) >= 0)
			{
				oLanesByRoads.remove(nIndex);
			}
			else if ((nSearchIndex = Collections.binarySearch(m_oIncomingRoads, oRPSearch)) >= 0)
			{
				oSignals.clear();
				RoadPaths oRoadPaths = m_oIncomingRoads.get(nSearchIndex);
				int nLaneIndex = XodrUtil.getLaneIndex(oLanesByRoads.get(nIndex).m_lLaneSectionId);
				for (Signal oSignal : m_oSignals)
				{
					boolean bAdded = false;
					Iterator<int[]> oIt = Arrays.iterator(oSignal.m_nRoads, new int[1], 1, 1);
					while (oIt.hasNext())
					{
						int nConnPath = oIt.next()[0];
						for (Connection oConn : oRoadPaths.m_oConns)
						{
							if (oConn.m_nConnRoad == nConnPath)
							{
								if (nLaneIndex == oConn.m_nFromLane)
								{
									oSignals.add(oSignal);
									bAdded = true;
									break;
								}
							}
						}
						if (bAdded)
							break;
					}
				}
				
				boolean bHasSignal = false;
				boolean bPosLaneIndex = nLaneIndex > 0;
				for (Signal oSignal : oSignals)
				{
					if (oSignal.m_nOri == Signal.BOTHORI)
					{
						bHasSignal = true;
						break;
					}
					else if (oSignal.m_nOri == Signal.POSORI)
					{
						if (bPosLaneIndex)
						{
							bHasSignal = true;
							break;
						}
					}
					else if (oSignal.m_nOri == Signal.NEGORI)
					{
						if (!bPosLaneIndex)
						{
							bHasSignal = true;
							break;
						}
					}
				}

				if (bHasSignal)
					oInRoadsToSignals.add(oLanesByRoads.get(nIndex));
				
				oLanesByRoads.remove(nIndex);
			}
		}
		
		int nOuterLimit = oInRoadsToSignals.size();
		int nInnerLimit = oLanesByRoads.size();
		int[] nIds = Arrays.newIntArray(nOuterLimit);
		double dSqTol = dTol * dTol;
		
		for (int nOuter = 0; nOuter < nOuterLimit; nOuter++)
		{
			oSignals.clear();
			CtrlLineArcs oCur = oInRoadsToSignals.get(nOuter);
			double dLen = CtrlLineArcs.getLen(oCur.m_dLineArcs);
			boolean bAdded = false;
			if (dLen >= MINLEN)
			{
				oCombined.add(oCur);
				continue;
			}
			
			nIds[0] = 1;
			
			for (int nInner = 0; nInner < nInnerLimit; nInner++)
			{
				CtrlLineArcs oCmp = oLanesByRoads.get(nInner);

				int nInsert = java.util.Arrays.binarySearch(nIds, 1, Arrays.size(nIds), oCmp.m_nLaneId); 
				if (nInsert >= 0) // skip ids that have already been combined
					continue;
				
				int nConnect = oCur.connects(oCmp, dSqTol);
				if (nConnect == CtrlLineArcs.CON_TSTART_OEND) // the other linearc connects at the start of this linearc
				{
					double dOtherLen = CtrlLineArcs.getLen(oCmp.m_dLineArcs);
					if (dLen + dOtherLen < MINLEN)
					{
						oCur.combine(oCmp, nConnect); // combine the points of oCmp into oCur's point array
						nIds = Arrays.insert(nIds, oCmp.m_nLaneId, ~nInsert); // add oCmp's id to the list of combined ids
						nInner = -1; // start inner loop over
					}
					else
					{
						double[] dLineArc = oCmp.m_dLineArcs;
						double[] dPartial = Arrays.newDoubleArray();
						double[] dCenter = new double[2];
						nIndex = Arrays.size(dLineArc) - 9;
						dPartial = Arrays.add(dPartial, dLineArc[nIndex + 6], dLineArc[nIndex + 7]);
						dPartial = Arrays.add(dPartial, dLineArc[nIndex + 8]);
						
						for (;nIndex >= 5; nIndex -= 6)
						{
							if (dLen >= MINLEN)
							{
								nConnect = oCur.connects(dLineArc, dSqTol);
								oCur.combine(dLineArc, nConnect);
								oCombined.add(oCur);
								bAdded = true;
								break;
							}
							double dX3 = dLineArc[nIndex];
							double dY3 = dLineArc[nIndex + 1];
							double dX2 = dLineArc[nIndex + 3];
							double dY2 = dLineArc[nIndex + 4];
							double dX1 = dLineArc[nIndex + 6];
							double dY1 = dLineArc[nIndex + 7];
							dPartial = Arrays.add(dPartial, dX2, dY2);
							dPartial = Arrays.add(dPartial, dLineArc[nIndex + 5]);
							dPartial = Arrays.add(dPartial, dX3, dY3);
							dPartial = Arrays.add(dPartial, dLineArc[nIndex + 2]);
							double dR = Geo.circle(dX1, dY1, dX2, dY2, dX3, dY3, dCenter);
							if (!Double.isFinite(dR) || dR >= 10000) // expand line
							{
								dLen += Geo.distance(dX1, dY1, dX3, dY3);
							}
							else
							{
								int nRightHand = Geo.rightHand(dX2, dY2, dX1, dY1, dX3, dY3);
								double dC = -nRightHand / dR;
								double dCmAngleStep = dC / 100;
								double dCmAngleStepMag = Math.abs(dCmAngleStep);
								double dH = dCenter[0];
								double dK = dCenter[1];
								double dHdg = Geo.heading(dH, dK, dX1, dY1);
								int nSteps = 0;
								double dPrevX = dX1;
								double dPrevY = dY1;
								double dPrevAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dX3, dY3);
								while (dPrevAngle > dCmAngleStepMag)
								{
									dPrevX = dH + dR * Math.cos(dHdg + dCmAngleStep * ++nSteps);
									dPrevY = dK + dR * Math.sin(dHdg + dCmAngleStep * nSteps);
									dLen += 0.01;
									dPrevAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dX3, dY3);
								}
								double dLastAngle = Geo.angle(dPrevX, dPrevY, dH, dK, dX3, dY3);
								dLen += dLastAngle * dR;
							}
						}
					}
				}
			}
			
			if (!bAdded)
				oCombined.add(oCur);
		}
		return oCombined;
	}


	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		String[] sEmpty = new String[0];
		TdLayer oLights = new TdLayer("signal-lights", new String[]{"color"}, COLORS, TdLayer.POLYGON);
		TdLayer oBody = new TdLayer("signal-body", sEmpty, sEmpty, TdLayer.POLYGON);
		TdLayer oStopLine = new TdLayer("signal-sl", sEmpty, sEmpty, TdLayer.POLYGON);

		int[] nLightTags = new int[2];
		int[] nEmptyTags = new int[0];
		double[] dPt = new double[2];
		double[][] dPolys = new double[SIGNAL.length][];
		int[] nPts = new int[dPolys.length];
		for (int nIndex = 0; nIndex< dPolys.length; nIndex++)
		{
			double[] dTemp = new double[SIGNAL[nIndex].length + 1];
			dTemp[0] = dTemp.length;
			nPts[nIndex] =  SIGNAL[nIndex].length / 2;
			dPolys[nIndex] = dTemp;
		}
		
		ArrayList<double[]> dStopLines = new ArrayList();
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oLights.clear();
			oBody.clear();
			oStopLine.clear();
			dStopLines.clear();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);

			for (TrafCtrl oCtrl : oCtrls)
			{
				CtrlGeo oFullGeo = oCtrl.m_oFullGeo;
				if (Collections.binarySearch(oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0 || oFullGeo.m_dAverageWidth < WIDTHTH)
					continue;

				double[] dC = oFullGeo.m_dC;
				int nSize = Arrays.size(dC);
				int nIndex = getStopLine(oCtrl, dStopLines);
				double[] dStopLine = dStopLines.get(dStopLines.size() - 1);
				double[] dBoundingBox = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
				Iterator<double[]> oIt = Arrays.iterator(dStopLine, new double[2], 1, 2);
				while (oIt.hasNext())
				{
					double[] dPoint = oIt.next();
					if (dPoint[0] < dBoundingBox[0])
						dBoundingBox[0] = dPoint[0];
					if (dPoint[1] < dBoundingBox[1])
						dBoundingBox[1] = dPoint[1];
					if (dPoint[0] > dBoundingBox[2])
						dBoundingBox[2] = dPoint[0];
					if (dPoint[1] > dBoundingBox[3])
						dBoundingBox[3] = dPoint[1];
				}
				if (Geo.boundingBoxesIntersect(dBoundingBox, dClipBounds))
				{
					oStopLine.add(new TdFeature(dStopLine, nEmptyTags, oCtrl));
				}
				double dLen = 0.0;
				while (dLen < 0.5 && nIndex > 0)
				{
					double dX1 = dC[nIndex];
					double dY1 = dC[nIndex + 1];
					double dX2 = dC[nIndex + 2];
					double dY2 = dC[nIndex + 3];
					dLen += Geo.distance(dX1, dY1, dX2, dY2);
					nIndex -= 2;
				}
				double dX1 = dC[nIndex];
				double dY1 = dC[nIndex + 1];
				double dX2 = dC[nSize - 2];
				double dY2 = dC[nSize - 1];
				double dHdg = Geo.heading(dX1, dY1, dX2, dY2);
				if (Double.isNaN(dHdg))
					continue;



				// offset from center line
				dPt[0] = dX1 + Math.sin(dHdg) * 0.5; // cos(x - pi/2) = sin(x)
				dPt[1] = dY1 - Math.cos(dHdg) * 0.5; // sin(x - pi/2) = -cos(x)
				double[] dBodyBoundingBox = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
				AffineTransform oAt = new AffineTransform();
				oAt.translate(dPt[0], dPt[1]);
				oAt.rotate(dHdg, 0, 0);
				oAt.transform(SIGNAL[0], 0, dPolys[0], 1, nPts[0]);

				oIt = Arrays.iterator(dPolys[0], new double[2], 1, 2);
				while (oIt.hasNext())
				{
					double[] dPoint = oIt.next();
					if (dPoint[0] < dBodyBoundingBox[0])
						dBodyBoundingBox[0] = dPoint[0];
					if (dPoint[1] < dBodyBoundingBox[1])
						dBodyBoundingBox[1] = dPoint[1];
					if (dPoint[0] > dBodyBoundingBox[2])
						dBodyBoundingBox[2] = dPoint[0];
					if (dPoint[1] > dBodyBoundingBox[3])
						dBodyBoundingBox[3] = dPoint[1];
				}

				if (!Geo.boundingBoxesIntersect(dBodyBoundingBox[0], dBodyBoundingBox[1], dBodyBoundingBox[2], dBodyBoundingBox[3], dClipBounds[0], dClipBounds[1], dClipBounds[2], dClipBounds[3]))
				   continue;

				oBody.add(new TdFeature(dPolys[0], nEmptyTags, oCtrl));

				for (int i = 1; i < dPolys.length; i++)
				{
					oAt.transform(SIGNAL[i], 0, dPolys[i], 1, nPts[i]);
					nLightTags[1] = i - 1;
					oLights.add(new TdFeature(dPolys[i], nLightTags, oCtrl));
				}
			}

			if (!oLights.isEmpty() || !oBody.isEmpty() || !oStopLine.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oLights.write(oOut);
					oBody.write(oOut);
					oStopLine.write(oOut);
				}
			}
		}
	}
}
