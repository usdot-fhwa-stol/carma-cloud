/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlPt;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrJunctionParser;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.TileUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
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
public class ProcDebug extends ProcCtrl
{
//	private static Base64.Encoder BASE64ENC = Base64.getEncoder().withoutPadding();
	private HashMap<String, String> m_oJunctions;
	private String m_sXodrDir;
	
	public ProcDebug(String sLineArcDir, String sXodrDir)
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
				TrafCtrl oCtrl = new TrafCtrl("debug", "", 0, oCLA.m_dLineArcs, "", false, CC); 
				oCtrls.add(oCtrl);
				oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom, CC);
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
					nInner = -1; // start inner loop over
					--nLimit;
				}
			}

			oCombined.add(oCur);
		}
		return oCombined;
	}	


	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		String[] sEmpty = new String[0];
		TdLayer oPoints = new TdLayer("debug-p", new String[]{"color"}, new String[]{"black", "blue", "red"}, TdLayer.POINT);
		TdLayer oCenters = new TdLayer("debug-c", sEmpty, sEmpty, TdLayer.LINESTRING);
		for (TrafCtrl oCtrl : oCtrls)
		{
			writeGeoLanes(oCtrl);
		}
//		ArrayList<EncodedGeo> oGeos = new ArrayList();
//		StringBuilder sBuf = new StringBuilder(16);
//		EncodedGeo oSearch = new EncodedGeo(sBuf);
//		for (TrafCtrl oCtrl : oCtrls)
//		{
//			TrafCtrl.getId(oCtrl.m_yId, sBuf);
//			EncodedGeo oGeo = new EncodedGeo(sBuf.toString());
//			oGeo.add(getEncodedLonLats(oCtrl.m_oFullGeo.m_dNT));
//			oGeo.add(getEncodedLonLats(oCtrl.m_oFullGeo.m_dC));
//			oGeo.add(getEncodedLonLats(oCtrl.m_oFullGeo.m_dPT));
//			int nIndex = Collections.binarySearch(oGeos, oGeo);
//			if (nIndex < 0)
//			{
//				oGeos.add(~nIndex, oGeo);
//			}
//		}
		ArrayList<double[]> oClipped = new ArrayList();
		int[] nTags = new int[2];
		int[] nCenterTags = new int[0];
//		int[] nCenterTags = new int[]{0, 0, 1, 1, 2, 2};

		double[] dCenter = new double[2];
		double[] dSeg = new double[6];
		double[] dPts = Arrays.newDoubleArray();
		double[] dPt = Arrays.newDoubleArray();
		
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oPoints.clear();
			oCenters.clear();
	//				ArrayList<String> oEncodedLonLats = new ArrayList();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);
	//				for (TrafCtrl oCtrl : oCtrls)
	//				{
	//					if (!Geo.boundingBoxesIntersect(oCtrl.m_oFullGeo.m_dBB, dClipBounds))
	//					   continue;
	//					TrafCtrl.getId(oCtrl.m_yId, sBuf);
	//					for (String sLonLats : oGeos.get(Collections.binarySearch(oGeos, oSearch)))
	//					{
	//						int nSearch = Collections.binarySearch(oEncodedLonLats, sLonLats);
	//						if (nSearch < 0)
	//							oEncodedLonLats.add(~nSearch, sLonLats);
	//					}
	//				}

	//				String[] sLayerLonLats = new String[oEncodedLonLats.size()];
	//				for (int nIndex = 0; nIndex < sLayerLonLats.length; nIndex++)
	//					sLayerLonLats[nIndex] = oEncodedLonLats.get(nIndex);
	//				TdLayer oCenters = new TdLayer("debug-c", new String[]{"nt", "c", "pt"}, sLayerLonLats, TdLayer.LINESTRING);

			for (TrafCtrl oCtrl : oCtrls)
			{
				if (Collections.binarySearch(oCtrl.m_oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0)
					continue;
				oClipped.clear();
	//					TrafCtrl.getId(oCtrl.m_yId, sBuf);
	//					EncodedGeo oGeo = oGeos.get(Collections.binarySearch(oGeos, oSearch));
	//					nCenterTags[1] = Collections.binarySearch(oEncodedLonLats, oGeo.get(0));
	//					nCenterTags[3] = Collections.binarySearch(oEncodedLonLats, oGeo.get(1));
	//					nCenterTags[5] = Collections.binarySearch(oEncodedLonLats, oGeo.get(2));
				TileUtil.clipLineString(oCtrl.m_oFullGeo.m_dC, dClipBounds, oClipped);
				for (double[] dLine : oClipped)
				{
					oCenters.add(new TdFeature(dLine, nCenterTags, oCtrl));
				}

				int nNumPts = oCtrl.size();
				dPts[0] = 1;
				dPts = Arrays.ensureCapacity(dPts, nNumPts * 2);
				int nPrevX = Mercator.lonToCm(Geo.fromIntDeg(oCtrl.m_nLon));
				int nPrevY = Mercator.latToCm(Geo.fromIntDeg(oCtrl.m_nLat));
				for (int nIndex = 0; nIndex < oCtrl.size(); nIndex++)
				{
					TrafCtrlPt oTemp = oCtrl.get(nIndex);
					int nXCo = oTemp.m_nX + nPrevX;
					int nYCo = oTemp.m_nY + nPrevY;
					dPts = Arrays.add(dPts, nXCo / 100.0, nYCo / 100.0); // convert mercator cm to mercator meters
					nPrevX = nXCo;
					nPrevY = nYCo;
				}

				Iterator<double[]> oIt = Arrays.iterator(dPts, dSeg, 1, 4);
				while (oIt.hasNext())
				{
					oIt.next();


					if (Geo.isInside(dSeg[0], dSeg[1], dClipBounds[3], dClipBounds[2], dClipBounds[1], dClipBounds[0], 0))
					{
						nTags[1] = 0;
						dPt = Arrays.add(dPt, dSeg[0], dSeg[1]);
						oPoints.add(new TdFeature(dPt, nTags, oCtrl));
						dPt[0] = 1;
					}


					if (Geo.isInside(dSeg[2], dSeg[3], dClipBounds[3], dClipBounds[2], dClipBounds[1], dClipBounds[0], 0))
					{
						double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[2], dSeg[3], dSeg[4], dSeg[5], dCenter);
						if (Double.isFinite(dR) && dR < 10000)
							nTags[1] = 1;
						else
							nTags[1] = 2;
						dPt = Arrays.add(dPt, dSeg[2], dSeg[3]);
						oPoints.add(new TdFeature(dPt, nTags, oCtrl));
						dPt[0] = 1;
					}
				}

				if (Geo.isInside(dSeg[4], dSeg[5], dClipBounds[3], dClipBounds[2], dClipBounds[1], dClipBounds[0], 0))
				{
					nTags[1] = 0;
					dPt = Arrays.add(dPt, dSeg[4], dSeg[5]);
					oPoints.add(new TdFeature(dPt, nTags, oCtrl));
					dPt[0] = 1;
				}
			}
			if (!oPoints.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oPoints.write(oOut);
					oCenters.write(oOut);
				}
			}
		}
	}
	
	
	private static void writeGeoLanes(TrafCtrl oCtrl)
	   throws IOException
	{
		StringBuilder sBuf = new StringBuilder();
		sBuf.append("{");
		sBuf.append("\"a\":[");
		double[] dPts = oCtrl.m_oFullGeo.m_dPT;
		double[] dPt = new double[2];
		Iterator<double[]> oIt = Arrays.iterator(dPts, dPt, 1, 2);
		oIt.next();
		int nPrevX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
		int nPrevY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
		sBuf.append(nPrevX).append(",").append(nPrevY);
		while (oIt.hasNext())
		{
			oIt.next();
			int nX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
			int nY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
			sBuf.append(",").append(nX - nPrevX).append(",").append(nY- nPrevY);
			nPrevX = nX;
			nPrevY = nY;
		}
		sBuf.append("],");
		sBuf.append("\"b\":[");
		dPts = oCtrl.m_oFullGeo.m_dNT;
		oIt = Arrays.iterator(dPts, dPt, 1, 2);
		oIt.next();
		nPrevX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
		nPrevY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
		sBuf.append(nPrevX).append(",").append(nPrevY);
		while (oIt.hasNext())
		{
			oIt.next();
			int nX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
			int nY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
			sBuf.append(",").append(nX - nPrevX).append(",").append(nY- nPrevY);
			nPrevX = nX;
			nPrevY = nY;
		}
		sBuf.append("],");
		sBuf.append("\"c\":[");
		dPts = oCtrl.m_oFullGeo.m_dC;
		oIt = Arrays.iterator(dPts, dPt, 1, 2);
		oIt.next();
		nPrevX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
		nPrevY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
		sBuf.append(nPrevX).append(",").append(nPrevY);
		while (oIt.hasNext())
		{
			oIt.next();
			int nX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
			int nY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
			sBuf.append(",").append(nX - nPrevX).append(",").append(nY- nPrevY);
			nPrevX = nX;
			nPrevY = nY;
		}
		sBuf.append("]");
		sBuf.append("}");
		
		Path oPath = Paths.get(g_sGeolanesDir + TrafCtrl.getId(oCtrl.m_yId));
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
		{
			oOut.append(sBuf);
		}
	}
	
//	private static String getEncodedLonLats(double[] dMercs)
//	   throws IOException
//	{
//		Iterator<double[]> oIt = Arrays.iterator(dMercs, new double[2], 1, 2);
//		ByteArrayOutputStream yBytes = new ByteArrayOutputStream();
//		byte[] yBuf = new byte[4];
//		
//		while (oIt.hasNext())
//		{
//			double[] dPt = oIt.next();
//			int nLon = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
//			MathUtil.intToBytes(nLon, yBuf);
//			yBytes.write(yBuf);
//			int nLat = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
//			MathUtil.intToBytes(nLat, yBuf);
//			yBytes.write(yBuf);
//		}
//		yBytes.flush();
//		String sRet = BASE64ENC.encodeToString(yBytes.toByteArray()); 
//		yBytes.close();	
//		
//		return sRet;
//	}
//	
//	
//	private class EncodedGeo extends ArrayList<String> implements Comparable<EncodedGeo>
//	{
//		CharSequence m_sId;
//
//		EncodedGeo()
//		{
//			super(3);
//		}
//		
//		
//		EncodedGeo(CharSequence sId)
//		{
//			this();
//			m_sId = sId;
//		}
//		
//		
//		@Override
//		public int compareTo(EncodedGeo o)
//		{
//			return Text.compare(m_sId, o.m_sId);
//		}
//	}
}
