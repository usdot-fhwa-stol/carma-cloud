/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.CreateCtrls;
import cc.ctrl.CtrlGeo;
import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcClosed;
import cc.ctrl.proc.ProcClosing;
import cc.ctrl.proc.ProcCtrl;
import cc.ctrl.proc.ProcDebug;
import cc.ctrl.proc.ProcDebugOutlines;
import cc.ctrl.proc.ProcDirection;
import cc.ctrl.proc.ProcLatPerm;
import cc.ctrl.proc.ProcMaxSpeed;
import cc.ctrl.proc.ProcOpening;
import cc.ctrl.proc.ProcPavement;
import cc.ctrl.proc.ProcSignal;
import cc.ctrl.proc.ProcStop;
import cc.ctrl.proc.ProcYield;
import cc.ctrl.proc.TdFeature;
import cc.ctrl.proc.TdLayer;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
import cc.geosrv.xodr.geo.XodrGeoParser;
import cc.geosrv.xodr.rdmk.XodrRoadMarkParser;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.TileUtil;
import cc.vector_tile.VectorTile;
import java.awt.geom.Area;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author aaron.cherney
 */
public class CtrlTiles extends HttpServlet
{
	static int g_nZoom;
	static String g_sTdFileFormat;
	static String g_sCtrlDir;
	int[] m_nZoomFilter = new int[]
	{
		18, //{"signal"}, 
		18, //{"stop"}, 
		18, //{"yield"}, 
		18, //{"notowing"}, 
		17, //{"restricted"}, 
		17, //{"closed"}, 
		18, //{"chains", "no", "permitted", "required"}, 
		18, //{"direction", "forward", "reverse"}, 
		19, //{"lataffinity", "left", "right"}, 
		17, //{"latperm", "none", "permitted", "passing-only", "emergency-only"}, 
		18, //{"parking", "no", "parallel", "angled"}, 
		18, //{"minspeed"}, 
		18, //{"maxspeed"}, 
		18, //{"minhdwy"}, 
		18, //{"maxvehmass"}, 
		18, //{"maxvehheight"}, 
		18, //{"maxvehwidth"}, 
		18, //{"maxvehlength"}, 
		18, //{"maxaxles"}, 
		18, //{"minvehocc"},
		17, //{"pavement"},
		17  //{"debug"}
	};
	
	
	@Override
	public void init(ServletConfig oConfig)
	   throws ServletException
	{
		try
		{
			String sXodrDir = oConfig.getInitParameter("xodrdir");
			String sLineArcBaseDir = oConfig.getInitParameter("linearcdir");
			String sTrackFile = oConfig.getInitParameter("trackfile");
			g_sCtrlDir = oConfig.getInitParameter("ctrldir");
			double dExplodeStep = Double.parseDouble(oConfig.getInitParameter("explodestep"));
			double dCombineTol = Double.parseDouble(oConfig.getInitParameter("combinetol"));
			g_sTdFileFormat = oConfig.getInitParameter("tileddataformat");
			g_nZoom = Integer.parseInt(oConfig.getInitParameter("zoom"));
			ProcCtrl.setStaticVariables(dExplodeStep, g_sCtrlDir, g_sTdFileFormat, g_nZoom);
			ArrayList<ProcCtrl> oProcesses = new ArrayList();
			oProcesses.add(new ProcDirection("/direction", sXodrDir));
			oProcesses.add(new ProcPavement("/pavement", sLineArcBaseDir, sXodrDir));
			oProcesses.add(new ProcSignal("/direction", sXodrDir));
			oProcesses.add(new ProcDebug("/direction", sXodrDir));
			oProcesses.add(new ProcStop("/direction", sXodrDir));
			oProcesses.add(new ProcYield("/direction", sXodrDir));
			oProcesses.add(new ProcLatPerm("/rdmks"));
			oProcesses.add(new ProcClosed("/direction"));
			oProcesses.add(new ProcMaxSpeed("/direction", sXodrDir));
			oProcesses.add(new ProcDebugOutlines("/direction"));
			
			Files.createDirectories(Paths.get(g_sTdFileFormat).getParent().getParent(), FileUtil.DIRPERS);
			Path oDir = Paths.get(sXodrDir);
			List<Path> oPaths = Files.walk(oDir).filter((oPath) -> {return Files.isRegularFile(oPath) && oPath.toString().endsWith(".xodr");}).collect(Collectors.toList());
			for (Path oXodrFile : oPaths)
			{
				if (new XodrGeoParser(sTrackFile).parseXodrToCLA(oXodrFile, sLineArcBaseDir))
				{
					new XodrRoadMarkParser().parseXodrToCLA(oXodrFile, sLineArcBaseDir);
					String sFilename = "/" + oXodrFile.getFileName().toString().replace(".xodr", ".bin");
					System.out.println(sFilename);
					for (ProcCtrl oProcess : oProcesses)
					{
						Path oLineArcFile = Paths.get(sLineArcBaseDir + oProcess.m_sLineArcDir + sFilename);
						System.out.println(oProcess.getClass().getName());
						oProcess.process(oLineArcFile.toString(), dCombineTol);
					}
				}
			}
			Path oManual = Paths.get("/dev/shm/cc/ctrl.csv");
			if (Files.exists(oManual))
			{
				CreateCtrls.createCtrl(oManual);
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
	
	
	@Override
	protected void doGet(HttpServletRequest oRequest, HttpServletResponse oResponse)
	   throws ServletException, IOException
	{
		long lNow = System.currentTimeMillis();
		String[] sUriParts = oRequest.getRequestURI().split("/");
		int nZ = Integer.parseInt(sUriParts[sUriParts.length - 3]);
		int nX = Integer.parseInt(sUriParts[sUriParts.length - 2]);
		int nY = Integer.parseInt(sUriParts[sUriParts.length - 1]);
		
		double[] dBounds = TileUtil.getTileBounds(nZ, nX, nY);
		double dXAdjust = 0.001;
		double dYAdjust = 0.001;
		int[] nTiles = new int[2];
		Mercator.getInstance().metersToTile(dBounds[0] + dXAdjust, dBounds[3] - dYAdjust, g_nZoom, nTiles); // determine the correct tiles for the default zoom level
		int nDefaultX = nTiles[0];
		int nDefaultY = nTiles[1];
		Path oIndexFile = Paths.get(String.format(g_sTdFileFormat, nDefaultX, g_nZoom, nDefaultX, nDefaultY) + ".ndx");
		Path oTdFile = Paths.get(String.format(g_sTdFileFormat, nDefaultX, g_nZoom, nDefaultX, nDefaultY));
		if (!Files.exists(oTdFile) || !Files.exists(oIndexFile))
			return;
		
		ArrayList<byte[]> yIdsToRender = new ArrayList();
		byte[] yIdBuf = new byte[16];
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
		{
			while (oIn.available() > 0)
			{
				int nType = oIn.readInt();
				oIn.read(yIdBuf);
				long lStart = oIn.readLong();
				long lEnd = oIn.readLong();
				if ((lStart >= lNow || lEnd > lNow) && m_nZoomFilter[nType] <= nZ) // everything valid now and in the future add to tile
				{
					byte[] yId = new byte[16];
					System.arraycopy(yIdBuf, 0, yId, 0, 16);
					int nIndex = Collections.binarySearch(yIdsToRender, yId, TrafCtrl.ID_COMP);
					if (nIndex < 0)
						yIdsToRender.add(~nIndex, yId);
				}
			}
		}
		double[] dClipBounds = TileUtil.getClippingBounds(nZ, nX, nY, dBounds);

		int nExtent = 4096;
//		int nExtent = Mercator.getExtent(nZ);
		VectorTile.Tile.Layer.Builder oLayerBuilder = VectorTile.Tile.Layer.newBuilder();
		oLayerBuilder.setVersion(2);
		oLayerBuilder.setExtent(nExtent);
		VectorTile.Tile.Feature.Builder oFeatureBuilder = VectorTile.Tile.Feature.newBuilder();
		VectorTile.Tile.Value.Builder oValueBuilder = VectorTile.Tile.Value.newBuilder();
		int[] nCur = new int[2];
		int[] nPointBuffer = Arrays.newIntArray(1024);
		Area oPolyClip = TileUtil.getClippingArea(nZ, nX, nY, dClipBounds);

		ArrayList<TdLayer> oLayers = new ArrayList();
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oTdFile))))
		{
			while (oIn.available() > 0)
			{
				TdLayer oTemp = new TdLayer(oIn, false);
				int nFeatures = oIn.readInt();
				for (int nIndex = 0; nIndex < nFeatures; nIndex++)
				{
					oIn.read(yIdBuf);
					int nBytesToSkip = oIn.readInt();
					int nSearch = Collections.binarySearch(yIdsToRender, yIdBuf, TrafCtrl.ID_COMP);
					if (nSearch < 0)
						oIn.skipBytes(nBytesToSkip);
					else
						oTemp.add(new TdFeature(oIn, oTemp.m_oKeys.length * 2, yIdBuf));
				}
				int nIndex = Collections.binarySearch(oLayers, oTemp);
				if (nIndex < 0)
				{
					oLayers.add(~nIndex, oTemp);
				}
				else
				{
					oLayers.get(nIndex).addAll(oTemp);
				}
			}
		}
		
		VectorTile.Tile.Builder oTile = VectorTile.Tile.newBuilder();
		ArrayList<byte[]> oIds = new ArrayList();
		for (TdLayer oLayer : oLayers)
		{
			oIds.clear();
			oLayerBuilder.clearFeatures();
			oLayerBuilder.clearKeys();
			oLayerBuilder.clearValues();
			oLayerBuilder.setName(oLayer.m_sName);
			int nIdKeyIndex = oLayer.m_oKeys.length;
			for (String sKey : oLayer.m_oKeys)
			{
				oLayerBuilder.addKeys(sKey);
			}
			oLayerBuilder.addKeys("sponge_id");
			
			int nIdValueOffset = oLayer.m_oValues.length;
			for (String sValue : oLayer.m_oValues)
			{
				oValueBuilder.setStringValue(sValue);
				oLayerBuilder.addValues(oValueBuilder.build());
			}
			for (TdFeature oFeature : oLayer)
			{
				int nIndex = Collections.binarySearch(oIds, oFeature.m_yCtrlId, TrafCtrl.ID_COMP);
				if (nIndex < 0)
					oIds.add(~nIndex, oFeature.m_yCtrlId);
			}
			
			for (byte[] yId : oIds)
			{
				oValueBuilder.setStringValue(TrafCtrl.getId(yId));
				oLayerBuilder.addValues(oValueBuilder.build());
			}
			
			switch (oLayer.m_yGeoType)
			{
				case TdLayer.POINT:
				{
					oFeatureBuilder.setType(VectorTile.Tile.GeomType.POINT);
					for (TdFeature oFeature : oLayer)
					{
						double[] dGeo = oFeature.m_dGeo;
						if (!Geo.isInside(dGeo[1], dGeo[2], dClipBounds[3], dClipBounds[2], dClipBounds[1], dClipBounds[0], 0))
							continue;
						
						for (int nTag : oFeature.m_nTags)
							oFeatureBuilder.addTags(nTag);
						oFeatureBuilder.addTags(nIdKeyIndex);
						oFeatureBuilder.addTags(nIdValueOffset + Collections.binarySearch(oIds, oFeature.m_yCtrlId, TrafCtrl.ID_COMP));
						TileUtil.addMercPointToFeature(oFeatureBuilder, nCur, dBounds, nExtent, dGeo[1], dGeo[2]);
						if (oFeatureBuilder.getGeometryCount() > 0)
							oLayerBuilder.addFeatures(oFeatureBuilder.build());
						nCur[0] = nCur[1] = 0;
						oFeatureBuilder.clearGeometry();
						oFeatureBuilder.clearTags();
					}
					break;
				}
				case TdLayer.LINESTRING:
				{
					oFeatureBuilder.setType(VectorTile.Tile.GeomType.LINESTRING);
					ArrayList<double[]> oClipped = new ArrayList();
					for (TdFeature oFeature : oLayer)
					{
						double[] dGeo = oFeature.m_dGeo;
						oClipped.clear();
						TileUtil.clipLineString(dGeo, dClipBounds, oClipped);
						if (oClipped.isEmpty())
							continue;
						
						for (int nTag : oFeature.m_nTags)
							oFeatureBuilder.addTags(nTag);
						oFeatureBuilder.addTags(nIdKeyIndex);
						oFeatureBuilder.addTags(nIdValueOffset + Collections.binarySearch(oIds, oFeature.m_yCtrlId, TrafCtrl.ID_COMP));
						for (double[] dLine : oClipped)
						{
							nPointBuffer = TileUtil.addMercLinestring(oFeatureBuilder, nCur, dBounds, nExtent, dLine, nPointBuffer);
							if (oFeatureBuilder.getGeometryCount() > 0)
								oLayerBuilder.addFeatures(oFeatureBuilder.build());
							nCur[0] = nCur[1] = 0;
							oFeatureBuilder.clearGeometry();
							oFeatureBuilder.clearTags();
						}
					}
					break;
				}
				case TdLayer.POLYGON:
				{
					oFeatureBuilder.setType(VectorTile.Tile.GeomType.POLYGON);
					for (TdFeature oFeature : oLayer)
					{
						double[] dGeo = oFeature.m_dGeo;
						Area oArea = Geo.getArea(dGeo, 1);
						if (oArea == null)
							continue;
						oArea.intersect(oPolyClip);
						if (oArea.isEmpty())
							continue;
						
						for (int nTag : oFeature.m_nTags)
							oFeatureBuilder.addTags(nTag);
						oFeatureBuilder.addTags(nIdKeyIndex);
						oFeatureBuilder.addTags(nIdValueOffset + Collections.binarySearch(oIds, oFeature.m_yCtrlId, TrafCtrl.ID_COMP));
						nPointBuffer = TileUtil.addPolygon(oFeatureBuilder, nCur, dBounds, nExtent, oArea, nPointBuffer);
						if (oFeatureBuilder.getGeometryCount() > 0)
							oLayerBuilder.addFeatures(oFeatureBuilder.build());
						nCur[0] = nCur[1] = 0;
						oFeatureBuilder.clearGeometry();
						oFeatureBuilder.clearTags();
					}
					break;
				}
				default:
				{
					oFeatureBuilder.setType(VectorTile.Tile.GeomType.UNKNOWN);
					break;
				}
			}
			if (oLayerBuilder.getFeaturesCount() > 0)
				oTile.addLayers(oLayerBuilder.build());
		}
		
		oResponse.setContentType("application/x-protobuf");
		if (oTile.getLayersCount() > 0)
			oTile.build().writeTo(oResponse.getOutputStream());
	}
	
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		Session oSession = SessMgr.getSession(oReq);
		if (oSession == null)
		{
			oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		String[] sUriParts = oReq.getRequestURI().split("/");
		String sMethod = sUriParts[sUriParts.length - 1];
		if (sMethod.compareTo("saveEdit") == 0)
		{
			saveEdit(oReq, oRes);
			return;
		}
		else if (sMethod.compareTo("delete") == 0)
		{
			deleteControl(oReq, oRes);
			return;
		}
		else if (sMethod.compareTo("add") == 0)
		{
			addControl(oReq, oRes);
			return;
		}
		synchronized (oSession)
		{
			oSession.oLoadedIds.clear();
		}
		StringBuilder sBuf = new StringBuilder();
		sBuf.append("{\"zoom\":").append(g_nZoom);
		sBuf.append(",\"enums\":[");
		for (String[] sArr : TrafCtrlEnums.CTRLS)
		{
			sBuf.append("[\"");
			sBuf.append(sArr[0]).append("\"");
			for (int nIndex = 1; nIndex < sArr.length; nIndex++)
				sBuf.append(",\"").append(sArr[nIndex]).append("\"");
			sBuf.append("],");
		}
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("],\"units\":{");
		for (int nIndex = 0; nIndex < TrafCtrlEnums.UNITS.length; nIndex++)
		{
			String sVal = TrafCtrlEnums.UNITS[nIndex];
			if (sVal != null)
			{
				sBuf.append("\"").append(nIndex).append("\":\"").append(sVal).append("\",");
			}
		}
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("}}");
		oRes.setContentType("application/json");
		try (PrintWriter oOut = oRes.getWriter())
		{
			oOut.append(sBuf);
		}
	}
	
	
	private void addControl(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		try
		{
			long lNow = System.currentTimeMillis() - 10;
			int nType = Integer.parseInt(oReq.getParameter("type"));
			String sType = TrafCtrlEnums.CTRLS[nType][0];
			int nControlValue;
			String sVal = oReq.getParameter("value");
			if (sVal != null)
				nControlValue = Integer.parseInt(sVal);
			else
			{
				sVal = oReq.getParameter("value1");
				if (sVal == null)
					nControlValue = Integer.MIN_VALUE;
				else
				{
					if (nType == TrafCtrlEnums.getCtrl("latperm"))
					{
						String sVal2 = oReq.getParameter("value2");
						nControlValue = Integer.parseInt(sVal2);
						nControlValue <<= 16;
						nControlValue |= (Integer.parseInt(sVal) & 0xff);
					}
					else
						nControlValue = Integer.parseInt(sVal);
				}
			}
			String sId = oReq.getParameter("id");
			int nStartIndex = Integer.parseInt(oReq.getParameter("s")) * 4 + 1; // add one since we use the growable arrays with the insertion index at position 0
			int nEndIndex = Integer.parseInt(oReq.getParameter("e")) * 4 + 1;
			String sFile = g_sCtrlDir + sId + ".bin";
			TrafCtrl oOriginalCtrl = new TrafCtrl(sFile);
			try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
			{
				oOriginalCtrl.m_oFullGeo = new CtrlGeo(oIn, true, g_nZoom);
			}
			double[] dCenter = Arrays.newDoubleArray();
			dCenter = Arrays.add(dCenter, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});			
			double[] dC = oOriginalCtrl.m_oFullGeo.m_dC;
			double[] dNT = oOriginalCtrl.m_oFullGeo.m_dNT;
			for (int nIndex = nStartIndex; nIndex <= nEndIndex;)
			{
				double dXc = dC[nIndex];
				double dYc = dC[nIndex + 1];
				dCenter = Arrays.addAndUpdate(dCenter, dXc, dYc);
				double dW = Geo.distance(dXc, dYc, dNT[nIndex++], dNT[nIndex++]) * 2;
				dCenter = Arrays.add(dCenter, dW);
			}
			CtrlLineArcs oCla = new CtrlLineArcs(-1, -1, -1, -1, XodrUtil.getLaneType("driving"), dCenter, 0.1);
			TrafCtrl oCtrl = new TrafCtrl(TrafCtrlEnums.CTRLS[nType][0], nControlValue, lNow, lNow, oCla.m_dLineArcs);
			oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom);
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			oCtrls.add(oCtrl);
			ProcCtrl.renderCtrls(sType, oCtrls, oCtrl.m_oFullGeo.m_oTiles);
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
	
	
	private void deleteControl(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		try
		{
			long lNow = System.currentTimeMillis() - 10;
			String sId = oReq.getParameter("id");
			String sFile = g_sCtrlDir + sId + ".bin";
			TrafCtrl oOriginalCtrl = new TrafCtrl(sFile);
			try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
			{
				oOriginalCtrl.m_oFullGeo = new CtrlGeo(oIn, true, g_nZoom);
			}
			synchronized (this)
			{
				for (int[] nTile : oOriginalCtrl.m_oFullGeo.m_oTiles)
				{
					String sIndex = String.format(g_sTdFileFormat, nTile[0], g_nZoom, nTile[0], nTile[1]) + ".ndx";
					ProcCtrl.updateIndex(sIndex, oOriginalCtrl.m_yId, lNow);
				}
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
	
	
	private void saveEdit(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		try
		{
			long lNow = System.currentTimeMillis();
			int nType = Integer.parseInt(oReq.getParameter("type"));
			String sType = TrafCtrlEnums.CTRLS[nType][0];
			int nControlValue;
			String sVal = oReq.getParameter("value");
			if (sVal != null)
				nControlValue = Integer.parseInt(sVal);
			else
			{
				sVal = oReq.getParameter("value1");
				if (nType == TrafCtrlEnums.getCtrl("latperm"))
				{
					String sVal2 = oReq.getParameter("value2");
					nControlValue = Integer.parseInt(sVal2);
					nControlValue <<= 16;
					nControlValue |= (Integer.parseInt(sVal) & 0xff);
				}
				else
					nControlValue = Integer.parseInt(sVal);
			}
			
			String sId = oReq.getParameter("id");
			String sFile = g_sCtrlDir + sId + ".bin";
			TrafCtrl oOriginalCtrl = new TrafCtrl(sFile);
			try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
			{
				oOriginalCtrl.m_oFullGeo = new CtrlGeo(oIn, true, g_nZoom);
			}
			TrafCtrl oNewCtrl = new TrafCtrl(sType, nControlValue, lNow, lNow, oOriginalCtrl);
			oNewCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom);
			ArrayList<TrafCtrl> oCtrls = new ArrayList(1);
			oCtrls.add(oNewCtrl);
			ProcCtrl.renderCtrls(sType, oCtrls, oOriginalCtrl.m_oFullGeo.m_oTiles);
			
			synchronized (this)
			{
				for (int[] nTile : oOriginalCtrl.m_oFullGeo.m_oTiles)
				{
//					ProcCtrl.writeIndexFile(oCtrls, nTile[0], nTile[1]);
					String sIndex = String.format(g_sTdFileFormat, nTile[0], g_nZoom, nTile[0], nTile[1]) + ".ndx";
					ProcCtrl.updateIndex(sIndex, oOriginalCtrl.m_yId, oNewCtrl.m_lStart);
				}
			}
			oRes.setContentType("application/json");
			StringBuilder sBuf = new StringBuilder();
			sBuf.append("{");
			sBuf.append("\"id\":\"").append(TrafCtrl.getId(oNewCtrl.m_yId)).append("\"");
			sBuf.append(",\"vals\":[");
			ArrayList<String> sVals = new ArrayList(4);
			TrafCtrlEnums.getCtrlValString(oNewCtrl.m_nControlType, oNewCtrl.m_yControlValue, sVals);
			for (String sValue : sVals)
				sBuf.append("\"").append(sValue).append("\",");
			sBuf.setLength(sBuf.length() - 1);
			sBuf.append("]}");
			try (PrintWriter oOut = oRes.getWriter())
			{
				oOut.append(sBuf);
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
}
