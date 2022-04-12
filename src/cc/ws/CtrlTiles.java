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
import cc.ctrl.proc.ProcCtrl;
import cc.ctrl.proc.ProcDebug;
import cc.ctrl.proc.ProcDebugOutlines;
import cc.ctrl.proc.ProcDirection;
import cc.ctrl.proc.ProcLatPerm;
import cc.ctrl.proc.ProcMaxSpeed;
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
import cc.util.MathUtil;
import cc.util.Text;
import cc.util.TileUtil;
import cc.util.Units;
import cc.vector_tile.VectorTile;
import java.awt.geom.Area;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
		17,  //{"debug"}
		18, // maxplatoonsize
		18 // minplatoonhdwy
	};
	
	
	@Override
	public void init(ServletConfig oConfig)
	   throws ServletException
	{
		try
		{
			Units.getInstance().init(oConfig.getInitParameter("units"));
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
		else if (sMethod.compareTo("split") == 0)
		{
			splitLineArcs(oReq, oRes);
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
			String[] sVals = TrafCtrlEnums.UNITS[nIndex];
			if (sVals.length > 0)
			{
				sBuf.append("\"").append(nIndex).append("\":[");
				for (String sVal : sVals)
					sBuf.append("\"").append(sVal).append("\",");
				sBuf.setLength(sBuf.length() - 1);
				sBuf.append("],");
			}
		}
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("},\"vtypegroups\":{");
		for (int nGroup = 0; nGroup < TrafCtrlEnums.VTYPEGROUPS.length; nGroup++)
		{
			String[] sVals = TrafCtrlEnums.VTYPEGROUPS[nGroup];
			sBuf.append("\"").append(sVals[0]).append("\":[");
			for (int nIndex = 1; nIndex < sVals.length; nIndex++)
				sBuf.append("\"").append(sVals[nIndex]).append("\",");
			sBuf.setLength(sBuf.length() - 1);
			sBuf.append("],");
		}
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("},\"vtypes\":[");
		for (int nIndex = 0; nIndex < TrafCtrlEnums.VTYPES.length; nIndex++)
			sBuf.append("\"").append(TrafCtrlEnums.VTYPES[nIndex]).append("\",");
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("]}");
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
			String[] sVtypes = oReq.getParameterValues("vtypes[]");
			ArrayList<Integer> nVtypes = new ArrayList(sVtypes.length);
			for (String sVtype : sVtypes)
				nVtypes.add(Integer.parseInt(sVtype));
			String[] sUnits = TrafCtrlEnums.UNITS[nType];
			String sType = TrafCtrlEnums.CTRLS[nType][0];
			int nControlValue;
			String sLabel = oReq.getParameter("label");
			if (sLabel == null)
				sLabel = "";
			sLabel = Text.truncate(sLabel, 63).trim();
			String sReg = oReq.getParameter("reg");
			boolean bReg;
			if (sReg == null || sReg.compareTo("on") != 0)
				bReg = false;
			else
				bReg = true;
			String sVal = oReq.getParameter("value");
			if (sVal != null)
			{
				nControlValue = Integer.parseInt(sVal);
			}
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
			if (sUnits.length > 0)
			{
				double dVal = Units.getInstance().convert(sUnits[1], sUnits[0], nControlValue);
				nControlValue = (int)Math.round(dVal);
			}
			String sId = oReq.getParameter("id");
			int nStartIndex = Integer.parseInt(oReq.getParameter("s")) * 4 + 1; // add one since we use the growable arrays with the insertion index at position 0
			int nEndIndex = Integer.parseInt(oReq.getParameter("e")) * 4 + 1;
			String sFile = g_sCtrlDir + sId + ".bin";
			TrafCtrl oOriginalCtrl;
			try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
			{
				oOriginalCtrl = new TrafCtrl(oIn, false);
			}
			
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
			TrafCtrl oCtrl = new TrafCtrl(TrafCtrlEnums.CTRLS[nType][0], nControlValue, nVtypes, lNow, lNow, oCla.m_dLineArcs, sLabel, bReg, ProcCtrl.CC);
			oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			oCtrls.add(oCtrl);
			ProcCtrl.renderCtrls(sType, oCtrls, oCtrl.m_oFullGeo.m_oTiles);
//			StringBuilder sBuf = new StringBuilder();
//			try (BufferedWriter oOut = Files.newBufferedWriter(Paths.get("/opt/tomcat/work/carmacloud/sample_tcm.xml")))
//			{
//				oCtrl.getXml(sBuf, "cb9353e606e5aafa", 1, 1, 1, "1.0", true, 0);
//				oOut.append(sBuf);
//			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
			oRes.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	
	private void deleteControl(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		try
		{
			String sId = oReq.getParameter("id");
			if (!ProcCtrl.deleteControl(sId))
				oRes.sendError(HttpServletResponse.SC_BAD_REQUEST);
			
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
			oRes.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	
	private void saveEdit(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		try
		{
			long lNow = System.currentTimeMillis();
			int nType = Integer.parseInt(oReq.getParameter("type"));
			String[] sVtypes = oReq.getParameterValues("vtypes[]");
			ArrayList<Integer> nVtypes = new ArrayList(sVtypes.length);
			for (String sVtype : sVtypes)
				nVtypes.add(Integer.parseInt(sVtype));
			String[] sUnits = TrafCtrlEnums.UNITS[nType];
			String sType = TrafCtrlEnums.CTRLS[nType][0];
			int nControlValue;
			String sVal = oReq.getParameter("value");
			String sLabel = oReq.getParameter("label");
			if (sLabel == null)
				sLabel = "";
			sLabel = Text.truncate(sLabel, 63).trim();
			String sReg = oReq.getParameter("reg");
			boolean bReg;
			bReg = !(sReg == null || sReg.compareTo("on") != 0);
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
			if (sUnits.length > 0)
			{
				double dVal = Units.getInstance().convert(sUnits[1], sUnits[0], nControlValue);
				nControlValue = (int)Math.round(dVal);
			}
			String sId = oReq.getParameter("id");
			String sFile = g_sCtrlDir + sId + ".bin";
			TrafCtrl oOriginalCtrl;
			try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
			{
				oOriginalCtrl = new TrafCtrl(oIn, false);
			}
			TrafCtrl oCtrlToWrite;
			Collections.sort(nVtypes);
			boolean bSameVtypes = oOriginalCtrl.m_nVTypes.size() == nVtypes.size();
			int nIndex = nVtypes.size();
			while (nIndex-- > 0 && bSameVtypes)
			{
				bSameVtypes = oOriginalCtrl.m_nVTypes.get(nIndex).intValue() == nVtypes.get(nIndex).intValue();
			}
			
			
			if (MathUtil.bytesToInt(oOriginalCtrl.m_yControlValue) == nControlValue && bReg == oOriginalCtrl.m_bRegulatory && bSameVtypes) // value, regulatory, and vtypes are the same so only the label has changed
			{
				oOriginalCtrl.m_sLabel = sLabel;
				oOriginalCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
				oCtrlToWrite = oOriginalCtrl;
			}
			else
			{	
				try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
				{
					oOriginalCtrl.m_oFullGeo = new CtrlGeo(oIn, true, g_nZoom);
				}
				TrafCtrl oNewCtrl = new TrafCtrl(sType, nControlValue, nVtypes, lNow, lNow, oOriginalCtrl, sLabel, bReg, ProcCtrl.CC);
				oCtrlToWrite = oNewCtrl;
				oNewCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
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
			}
			oRes.setContentType("application/json");
			StringBuilder sBuf = new StringBuilder();
			sBuf.append("{");
			sBuf.append("\"id\":\"").append(TrafCtrl.getId(oCtrlToWrite.m_yId)).append("\"");
			sBuf.append(",\"label\":\"").append(oCtrlToWrite.m_sLabel).append("\"");
			sBuf.append(",\"vtypes\":[");
			for (int nVTypeIndex = 0; nVTypeIndex < oCtrlToWrite.m_nVTypes.size(); nVTypeIndex++)
				sBuf.append(oCtrlToWrite.m_nVTypes.get(nVTypeIndex)).append(",");
			sBuf.setLength(sBuf.length() - 1);
			sBuf.append("],\"reg\":").append(oCtrlToWrite.m_bRegulatory);
			sBuf.append(",\"vals\":[");
			ArrayList<String> sVals = new ArrayList(4);
			TrafCtrlEnums.getCtrlValString(oCtrlToWrite.m_nControlType, oCtrlToWrite.m_yControlValue, sVals);
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
			oEx.printStackTrace();
			oRes.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	
	private void splitLineArcs(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		try
		{
			String sLineArcFile = oReq.getParameter("clafile");
			if (sLineArcFile == null)
				return;
			String sPvmtFile = sLineArcFile.replace("/direction", "/pavement") + ".pvmt";
			Path oPath = Paths.get(sLineArcFile);
			if (!Files.exists(oPath))
				return;
			ArrayList<CtrlLineArcs> oClas = new ArrayList();
			int nShoulder = XodrUtil.getLaneType("shoulder");
			try (DataInputStream oIn = new DataInputStream(Files.newInputStream(oPath)))
			{
				while (oIn.available() > 0)
				{
					CtrlLineArcs oCla = new CtrlLineArcs(oIn);
					if (oCla.m_nLaneType == nShoulder)
						continue;
					oClas.add(oCla);
				}
			}

			double dMaxStep = ProcCtrl.g_dExplodeStep;
			double[] dSeg = new double[9];
			ArrayList<CtrlLineArcs> oLanesByRoads = ProcCtrl.combineLaneByRoad(oClas, 0.1);
			ArrayList<CtrlLineArcs> oNewClas = new ArrayList();
			for (CtrlLineArcs oCla : oLanesByRoads)
			{
				double[] dNew1 = Arrays.newDoubleArray((int)Arrays.size(oCla.m_dLineArcs));
				double[] dNew2 = Arrays.newDoubleArray((int)Arrays.size(oCla.m_dLineArcs));
				dNew1 = Arrays.add(dNew1, new double[]{0,0,0,0});
				dNew2 = Arrays.add(dNew2, new double[]{0,0,0,0});
				Iterator<double[]> oIt = Arrays.iterator(oCla.m_dLineArcs, dSeg, 5, 6);
				double[] dCenter = new double[2];
				double dTotalLen = 0.0;
				double dLastTan = 0;
				while (oIt.hasNext())
				{
					oIt.next();
					double dR = Geo.circle(dSeg[0], dSeg[1], dSeg[3], dSeg[4], dSeg[6], dSeg[7], dCenter);
					if (!Double.isFinite(dR) || dR >= 10000) // expand line
					{
						double dHdg = Geo.heading(dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
						double dLength = Geo.distance(dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
						if (Double.isNaN(dHdg) || dLength == 0.0)
							continue;
						dLastTan = dHdg;
						double dAngle = dHdg - Mercator.PI_OVER_TWO;

						double dW = dSeg[2] / 4;
						double dDeltaX = Math.cos(dAngle) * dW;
						double dDeltaY = Math.sin(dAngle) * dW;

						double dX = dSeg[0] + dDeltaX;
						double dY = dSeg[1] + dDeltaY;
						dNew1 = Arrays.add(dNew1, dX, dY);
						dNew1 = Arrays.add(dNew1, dW * 2);

						dX = dSeg[0] - dDeltaX;
						dY = dSeg[1] - dDeltaY;

						dNew2 = Arrays.add(dNew2, dX, dY);
						dNew2 = Arrays.add(dNew2, dW * 2);

						dW = dSeg[5] / 4;
						dDeltaX = Math.cos(dAngle) * dW;
						dDeltaY = Math.sin(dAngle) * dW;

						dX = dSeg[3] + dDeltaX;
						dY = dSeg[4] + dDeltaY;

						dNew1 = Arrays.add(dNew1, dX, dY);
						dNew1 = Arrays.add(dNew1, dW * 2);

						dX = dSeg[3] - dDeltaX;
						dY = dSeg[4] - dDeltaY;

						dNew2 = Arrays.add(dNew2, dX, dY);
						dNew2 = Arrays.add(dNew2, dW * 2);
					}
					else
					{
						int nRightHand = Geo.rightHand(dSeg[3], dSeg[4], dSeg[0], dSeg[1], dSeg[6], dSeg[7]);
						double dRForCalcs = dR * -nRightHand;
						double dC = 1 / dRForCalcs;
						double dCmAngleStep = dC / 100;
						double dH = dCenter[0];
						double dK = dCenter[1];
						double dHdg = Geo.heading(dH, dK, dSeg[0], dSeg[1]);

						double dTheta = dCmAngleStep;
						double dDist;
						while (true)
						{
							double dCirX = dH + dR * Math.cos(dHdg + dTheta);
							double dCirY = dK + dR * Math.sin(dHdg + dTheta);
							dDist = Geo.distance(dSeg[6], dSeg[7], dCirX, dCirY);

							if (dDist > 0.02)
								dTheta += dCmAngleStep; 
							else
							{
								if (nRightHand == Geo.rightHand(dCirX, dCirY, dSeg[0], dSeg[1], dSeg[6], dSeg[7]))
									dTheta += -nRightHand * Geo.angle(dCirX, dCirY, dH, dK, dSeg[6], dSeg[7]);
								else
									dTheta -= -nRightHand * Geo.angle(dCirX, dCirY, dH, dK, dSeg[6], dSeg[7]);
								break;
							}
						}
						double dTanAdd = dTheta > 0 ? Mercator.PI_OVER_TWO : -Mercator.PI_OVER_TWO;
						double dInitHdg = dHdg + dTanAdd;
						double dAngle = dInitHdg - Mercator.PI_OVER_TWO;

						double dW = dSeg[2] / 4;
						double dDeltaX = Math.cos(dAngle) * dW;
						double dDeltaY = Math.sin(dAngle) * dW;

						double dX = dSeg[0] + dDeltaX;
						double dY = dSeg[1] + dDeltaY;
						dNew1 = Arrays.add(dNew1, dX, dY);
						dNew1 = Arrays.add(dNew1, dW * 2);

						dX = dSeg[0] - dDeltaX;
						dY = dSeg[1] - dDeltaY;

						dNew2 = Arrays.add(dNew2, dX, dY);
						dNew2 = Arrays.add(dNew2, dW * 2);

						dInitHdg += dTheta / 2;
						dAngle = dInitHdg - Mercator.PI_OVER_TWO;
						dW = dSeg[5] / 4;
						dDeltaX = Math.cos(dAngle) * dW;
						dDeltaY = Math.sin(dAngle) * dW;

						dX = dSeg[3] + dDeltaX;
						dY = dSeg[4] + dDeltaY;

						dNew1 = Arrays.add(dNew1, dX, dY);
						dNew1 = Arrays.add(dNew1, dW * 2);

						dX = dSeg[3] - dDeltaX;
						dY = dSeg[4] - dDeltaY;

						dNew2 = Arrays.add(dNew2, dX, dY);
						dNew2 = Arrays.add(dNew2, dW * 2);

						dLastTan = dHdg + dTheta + dTanAdd;
					}
				}

				double dW = dSeg[8] / 4;
				double dXPrime = dSeg[6] + Math.sin(dLastTan) * dW; // cos(x - pi/2) = sin(x)
				double dYPrime = dSeg[7] - Math.cos(dLastTan) * dW; // sin(x - pi/2) = -cos(x)
				dNew1 = Arrays.add(dNew1, dXPrime, dYPrime);
				dNew1 = Arrays.add(dNew1, dW * 2);

				dXPrime = dSeg[6] - Math.sin(dLastTan) * dW; // cos(x + pi/2 = -sin(x)
				dYPrime = dSeg[7] + Math.cos(dLastTan) * dW; // sin(x + pi/2) = cos(x)
				dNew2 = Arrays.add(dNew2, dXPrime, dYPrime);
				dNew2 = Arrays.add(dNew2, dW * 2);

				oNewClas.add(new CtrlLineArcs(-100, -1, -1, -1, XodrUtil.getLaneType("driving"), dNew1, 0.1));
				oNewClas.add(new CtrlLineArcs(-100, -1, -1, -1, XodrUtil.getLaneType("driving"), dNew2, 0.1));
			}

			ArrayList<int[]> nTiles = new ArrayList();
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			System.out.println("new line arces: " + oNewClas.size());
			for (CtrlLineArcs oCla : oNewClas)
			{
				TrafCtrl oCtrl = new TrafCtrl("debug", "", 0, oCla.m_dLineArcs, "", false, ProcCtrl.CC); 
				oCtrls.add(oCtrl);
				oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
				ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
			}
			ProcDebug.renderTiledData(oCtrls, nTiles);
			
			nTiles.clear();
			oCtrls.clear();
			for (CtrlLineArcs oCla : oNewClas)
			{
				TrafCtrl oCtrl = new TrafCtrl("direction", "forward", 0, oCla.m_dLineArcs, "", true, ProcCtrl.CC);
				System.out.println(Text.toHexString(oCtrl.m_yId));
				oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
				ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
				oCtrls.add(oCtrl);
			}
			ProcDirection.renderTiledData(oCtrls, nTiles);
			
			nTiles.clear();
			oCtrls.clear();
			for (CtrlLineArcs oCla : oNewClas)
			{
				TrafCtrl oCtrl = new TrafCtrl("maxspeed", 70, 0, oCla.m_dLineArcs, "", true, ProcCtrl.CC);
				System.out.println(Text.toHexString(oCtrl.m_yId));
				oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
				ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
				oCtrls.add(oCtrl);
			}
			ProcMaxSpeed.renderTiledData(oCtrls, nTiles);
			
			nTiles.clear();
			oCtrls.clear();
			int nCount = 0;
			int nVal1 = 2;
			nVal1 <<= 16;
			nVal1 |= (1 & 0xff);
			
			int nVal2 = 1;
			nVal2 <<= 16;
			nVal2 |= (2 & 0xff);
			int[] nColors = Arrays.newIntArray();
			for (CtrlLineArcs oCla : oNewClas)
			{
				int nVal;
				if (nCount++ % 2 == 0)
				{
					nVal = nVal1;
					nColors = Arrays.add(nColors, 4, 4);
				}
				else
				{
					nVal = nVal2;
					nColors = Arrays.add(nColors, 5, 4);
				}
				
				TrafCtrl oCtrl = new TrafCtrl("latperm", nVal, 0, oCla.m_dLineArcs, "", true, ProcCtrl.CC);
				System.out.println(Text.toHexString(oCtrl.m_yId));
				oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
				ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
				oCtrls.add(oCtrl);
			}
			ProcLatPerm.renderTiledData(oCtrls, nTiles, nColors);
			
			ArrayList<CtrlLineArcs> oPvmt = new ArrayList();
			try (DataInputStream oIn = new DataInputStream(Files.newInputStream(Paths.get(sPvmtFile))))
			{
				while (oIn.available() > 0)
				{
					CtrlLineArcs oCla = new CtrlLineArcs(oIn);
					oPvmt.add(oCla);
				}
			}
			
			nTiles.clear();
			oCtrls.clear();
			for (CtrlLineArcs oCla : oPvmt)
			{
				TrafCtrl oCtrl = new TrafCtrl("pavement", oCla.m_nLaneType, 0, oCla.m_dLineArcs, "", true, ProcCtrl.CC);
				System.out.println(Text.toHexString(oCtrl.m_yId));
				oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.CC);
				ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
				oCtrls.add(oCtrl);
			}
			ProcPavement.renderTiledData(oCtrls, nTiles);
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
}
