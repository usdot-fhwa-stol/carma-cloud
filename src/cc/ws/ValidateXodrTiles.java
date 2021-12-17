/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.TrafCtrl;
import cc.ctrl.proc.ProcClosed;
import cc.ctrl.proc.ProcCtrl;
import cc.ctrl.proc.ProcDebug;
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
public class ValidateXodrTiles extends HttpServlet
{
	int m_nZoom;
	String m_sTdFileFormat;
	
	
	@Override
	public void init(ServletConfig oConfig)
	   throws ServletException
	{
		try
		{
			String sXodrDir = oConfig.getInitParameter("xodrdir");
			String sLineArcBaseDir = oConfig.getInitParameter("linearcdir");
			String sCtrlDir = oConfig.getInitParameter("ctrldir");
			String sTrackFile = oConfig.getInitParameter("trackfile");
			double dExplodeStep = Double.parseDouble(oConfig.getInitParameter("explodestep"));
			double dCombineTol = Double.parseDouble(oConfig.getInitParameter("combinetol"));
			m_sTdFileFormat = oConfig.getInitParameter("tileddataformat");
			m_nZoom = Integer.parseInt(oConfig.getInitParameter("zoom"));
//			ProcCtrl.setStaticVariables(dExplodeStep, sCtrlDir, m_sTdFileFormat, m_nZoom);
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
//			oProcesses.add(new ProcClosing("/direction"));
//			oProcesses.add(new ProcOpening("/direction"));
			
			Files.createDirectories(Paths.get(m_sTdFileFormat).getParent().getParent(), FileUtil.DIRPERS);
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
		Mercator.getInstance().metersToTile(dBounds[0] + dXAdjust, dBounds[3] - dYAdjust, m_nZoom, nTiles); // determine the correct tiles for the default zoom level
		int nDefaultX = nTiles[0];
		int nDefaultY = nTiles[1];
		Path oIndexFile = Paths.get(String.format(m_sTdFileFormat, nDefaultX, m_nZoom, nDefaultX, nDefaultY) + ".ndx");
		Path oTdFile = Paths.get(String.format(m_sTdFileFormat, nDefaultX, m_nZoom, nDefaultX, nDefaultY));
		if (!Files.exists(oTdFile) || !Files.exists(oIndexFile))
			return;
		
		ArrayList<byte[]> yIdsToRender = new ArrayList();
		byte[] yIdBuf = new byte[16];
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
		{
			while (oIn.available() > 0)
			{
				oIn.skipBytes(4);
				oIn.read(yIdBuf);
				long lStart = oIn.readLong();
				long lEnd = oIn.readLong();
				if (lStart >= lNow || lEnd > lNow) // everything valid now and in the future add to tile
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
}
