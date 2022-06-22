/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.ctrl.CtrlGeo;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcCtrl;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.MathUtil;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author aaron.cherney
 */
public class IHPResponse
{
	public long m_lTimestamp;
	public String m_sRequestId;
	public ArrayList<Detector> m_oDetectors = new ArrayList();
	
	
	public IHPResponse(InputStream oIn, ArrayList<Corridor> oAllBounds)
		throws Exception
	{
		JSONObject oJson = new JSONObject(new JSONTokener(oIn));
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		m_lTimestamp = oSdf.parse(oJson.getString("timestamp")).getTime();
		m_sRequestId = oJson.getString("request_id");
		JSONArray oResponses = oJson.getJSONArray("response");
		for (int nLayerIndex = 0; nLayerIndex < oResponses.length(); nLayerIndex++)
		{
			JSONObject oRes = oResponses.getJSONObject(nLayerIndex);
			String sType = oRes.getString("layer_type");
			JSONObject oLayer = oRes.getJSONObject("layer");
			String sGeoJsonType = oLayer.getString("type");
			JSONArray oFeatures = oLayer.getJSONArray("features");
			if (sType.compareTo("mvd-metadata") == 0)
			{
				for (int nFeatureIndex = 0; nFeatureIndex < oFeatures.length(); nFeatureIndex++)
				{
					JSONObject oFeature = oFeatures.getJSONObject(nFeatureIndex);
					JSONObject oProps = oFeature.getJSONObject("properties");
					String sId = oProps.getString("detector_id");
					JSONObject oGeometry = oFeature.getJSONObject("geometry");
					
					JSONArray oCoords = oGeometry.getJSONArray("coordinates");
					Detector oDetector = new Detector(sId, oCoords.getDouble(0), oCoords.getDouble(1));
					int nSearch = Collections.binarySearch(m_oDetectors, oDetector);
					if (nSearch < 0)
					{
						m_oDetectors.add(~nSearch, oDetector);
						double dX = Mercator.lonToMeters(oDetector.m_dLon);
						double dY = Mercator.latToMeters(oDetector.m_dLat);
						for (Corridor oBoundsList : oAllBounds)
						{
							if (Geo.isInBoundingBox(dX, dY, oBoundsList.m_dBb[0], oBoundsList.m_dBb[1], oBoundsList.m_dBb[2], oBoundsList.m_dBb[3]))
							{
								for (Subsegment oBounds : oBoundsList)
								{
									if (oBounds.pointInside(dX, dY))
										oBounds.add(oDetector);
								}
							}
						}
					}
					
//					String sGeoType = oGeometry.getString("type");
//					if (sGeoType.compareTo("Point") == 0)
//					{
//						ArrayList<int[]> oPart = new ArrayList();
//						int[] nPart = Arrays.newIntArray(2);
//						nPart = Arrays.add(nPart, Geo.toIntDeg(oCoords.getDouble(0)), Geo.toIntDeg(oCoords.getDouble(1)));
//						oPart.add(nPart);
//						m_oGeometries.add(oPart);
//					}
//					else if (sGeoType.compareTo("LineString") == 0)
//					{
//						ArrayList<int[]> oPart = new ArrayList();
//						int[] nPart = Arrays.newIntArray(oCoords.length() * 2);
//						for (int nCoordIndex = 0; nCoordIndex < oCoords.length(); nCoordIndex++)
//						{
//							JSONArray oPos = oCoords.getJSONArray(nCoordIndex);
//							nPart = Arrays.add(nPart, Geo.toIntDeg(oPos.getDouble(0)), Geo.toIntDeg(oPos.getDouble(1)));
//						}
//						oPart.add(nPart);
//						m_oGeometries.add(oPart);
//					}
//					else if (sGeoType.compareTo("Polygon") == 0)
//					{
//						ArrayList<int[]> oPart = new ArrayList();
//						for (int nRingIndex = 0; nRingIndex < oCoords.length(); nRingIndex++)
//						{
//							JSONArray oRing = oCoords.getJSONArray(nRingIndex);
//							int[] nPart = Arrays.newIntArray(oRing.length() * 2);
//							for (int nCoordIndex = 0; nCoordIndex < oRing.length(); nCoordIndex++)
//							{
//								JSONArray oPos = oRing.getJSONArray(nCoordIndex);
//								nPart = Arrays.add(nPart, Geo.toIntDeg(oPos.getDouble(0)), Geo.toIntDeg(oPos.getDouble(1)));
//							}
//							oPart.add(nPart);
//						}
//						m_oGeometries.add(oPart);
//					}
//					else if (sGeoType.compareTo("MultiPoint") == 0)
//					{
//						ArrayList<int[]> oPart = new ArrayList();
//						for (int nCoordIndex = 0; nCoordIndex < oCoords.length(); nCoordIndex++)
//						{
//							JSONArray oPos = oCoords.getJSONArray(nCoordIndex);
//							int[] nPart = Arrays.newIntArray(2);
//							nPart = Arrays.add(nPart, Geo.toIntDeg(oPos.getDouble(0)), Geo.toIntDeg(oPos.getDouble(1)));
//							oPart.add(nPart);
//						}
//						m_oGeometries.add(oPart);
//					}
//					else if (sGeoType.compareTo("MultiLineString") == 0)
//					{
//						ArrayList<int[]> oPart = new ArrayList();
//						for (int nLineIndex = 0; nLineIndex < oCoords.length(); nLineIndex++)
//						{
//							JSONArray oLine = oCoords.getJSONArray(nLineIndex);
//							int[] nPart = Arrays.newIntArray(oLine.length() * 2);
//							for (int nCoordIndex = 0; nCoordIndex < oLine.length(); nCoordIndex++)
//							{
//								JSONArray oPos = oLine.getJSONArray(nCoordIndex);
//								nPart = Arrays.add(nPart, Geo.toIntDeg(oPos.getDouble(0)), Geo.toIntDeg(oPos.getDouble(1)));
//							}
//							oPart.add(nPart);
//						}
//						m_oGeometries.add(oPart);
//					}
//					else if (sGeoType.compareTo("MultiPolygon") == 0)
//					{
//						for (int nPolyIndex = 0; nPolyIndex < oCoords.length(); nPolyIndex++)
//						{
//							ArrayList<int[]> oPart = new ArrayList();
//							JSONArray oPoly = oCoords.getJSONArray(nPolyIndex);
//							for (int nRingIndex = 0; nRingIndex < oPoly.length(); nRingIndex++)
//							{
//								JSONArray oRing = oPoly.getJSONArray(nRingIndex);
//								int[] nPart = Arrays.newIntArray(oRing.length() * 2);
//								for (int nCoordIndex = 0; nCoordIndex < oRing.length(); nCoordIndex++)
//								{
//									JSONArray oPos = oRing.getJSONArray(nCoordIndex);
//									nPart = Arrays.add(nPart, Geo.toIntDeg(oPos.getDouble(0)), Geo.toIntDeg(oPos.getDouble(1)));
//								}
//								oPart.add(nPart);
//							}
//							m_oGeometries.add(oPart);
//						}
//					}
				}
			}

		}
		for (int nLayerIndex = 0; nLayerIndex < oResponses.length(); nLayerIndex++)
		{
			JSONObject oRes = oResponses.getJSONObject(nLayerIndex);
			String sType = oRes.getString("layer_type");
			JSONObject oLayer = oRes.getJSONObject("layer");
			String sGeoJsonType = oLayer.getString("type");
			JSONArray oFeatures = oLayer.getJSONArray("features");
			Detector oSearch = new Detector();
			if (sType.compareTo("mvd-data") == 0)
			{
				StringBuilder sBuf = new StringBuilder();
				for (int nFeatureIndex = 0; nFeatureIndex < oFeatures.length(); nFeatureIndex++)
				{
					JSONObject oFeature = oFeatures.getJSONObject(nFeatureIndex);
					String sFeatureType = oFeature.getString("type");
					JSONObject oProps = oFeature.getJSONObject("properties");
					String sId = oProps.getString("detector_id");
					int nPeriod = oProps.getInt("period_seconds");
					int nVolume = oProps.getInt("volume");
					double dSpeed = oProps.getDouble("speed");
					double dOcc = oProps.getDouble("occupancy");
					
					int nIndex = sId.indexOf("-Lane-");
					if (nIndex >= 0)
					{
						sBuf.setLength(0);
						sBuf.append(sId.substring(0, nIndex));
						nIndex += "-Lane-".length();
						nIndex = sId.indexOf("-", nIndex);
						sBuf.append(sId.substring(nIndex));
						sId = sBuf.toString();
					}
					
					oSearch.m_sId = sId;
					nIndex = Collections.binarySearch(m_oDetectors, oSearch);
					if (nIndex >= 0)
						m_oDetectors.get(nIndex).add(dSpeed, nVolume, dOcc);
				}
			}
		}
	}
}
