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
	
	
	public IHPResponse(InputStream oIn, ArrayList<BoundsList> oAllBounds)
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
						for (BoundsList oBoundsList : oAllBounds)
						{
							if (Geo.isInBoundingBox(dX, dY, oBoundsList.m_dBb[0], oBoundsList.m_dBb[1], oBoundsList.m_dBb[2], oBoundsList.m_dBb[3]))
							{
								for (Bounds oBounds : oBoundsList)
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


	ArrayList<TrafCtrl> generateSpeedControls(ArrayList<BoundsList> oAllBounds, int nTimeIntervalSecs)
	{
		ArrayList<TrafCtrl> oCtrls = new ArrayList();
		Mercator oM = Mercator.getInstance();
		int[] nTiles = new int[2];
		int nMaxSpeedCtrl = TrafCtrlEnums.getCtrl("maxspeed");
		byte[] yIdBuf = new byte[16];
		StringBuilder sIdBuf = new StringBuilder();
		long lNow = System.currentTimeMillis();
		for (BoundsList oBoundsList : oAllBounds)
		{
			for (Bounds oBounds : oBoundsList)
			{
				for (Detector oDetector : oBounds)
				{
					oBounds.m_dOcc += oDetector.m_dOcc;
					oBounds.m_nVolume += oDetector.m_nVolume;
					Iterator<double[]> oIt = Arrays.iterator(oDetector.m_dSpeeds, new double[1], 1, 1);
					while (oIt.hasNext())
						oBounds.m_dSpeeds = Arrays.add(oBounds.m_dSpeeds, oIt.next()[0]);
				}
				oBounds.m_dOcc /= oBounds.size();
				
				oBounds.generateStats();
				double dX = Mercator.lonToMeters(oBounds.get(0).m_dLon);
				double dY = Mercator.latToMeters(oBounds.get(0).m_dLat);
				oBounds.m_nMaxSpeed = Integer.MIN_VALUE;
				oM.metersToTile(dX, dY, ProcCtrl.g_nDefaultZoom, nTiles);
				Path oIndexFile = Paths.get(String.format(ProcCtrl.g_sTdFileFormat, nTiles[0], ProcCtrl.g_nDefaultZoom, nTiles[0], nTiles[1]) + ".ndx");
				
				try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
				{
					while (oIn.available() > 0)
					{
						int nType = oIn.readInt();
						oIn.read(yIdBuf);
						long lStart = oIn.readLong();
						long lEnd = oIn.readLong();
						if (nType == nMaxSpeedCtrl && (lStart >= lNow || lEnd > lNow)) // everything valid now and in the future add to tile
						{
							TrafCtrl.getId(yIdBuf, sIdBuf);
							TrafCtrl oCtrl;
							Path oPath = Paths.get(ProcCtrl.g_sTrafCtrlDir + sIdBuf.toString() + ".bin");
							try (DataInputStream oCtrlIn = new DataInputStream(FileUtil.newInputStream(oPath)))
							{
								oCtrl = new TrafCtrl(oCtrlIn, false);
							}
							if (oCtrl.m_yId[0] != ProcCtrl.CC)
								continue;
							try (DataInputStream oCtrlIn = new DataInputStream(FileUtil.newInputStream(oPath)))
							{
								oCtrl.m_oFullGeo = new CtrlGeo(oCtrlIn, false, ProcCtrl.g_nDefaultZoom);
							}
							if (Geo.isInBoundingBox(dX, dY, oCtrl.m_oFullGeo.m_dBB[0], oCtrl.m_oFullGeo.m_dBB[1], oCtrl.m_oFullGeo.m_dBB[2], oCtrl.m_oFullGeo.m_dBB[3]))
							{
								double[] dPolygon = Geo.createPolygon(oCtrl.m_oFullGeo.m_dPT, oCtrl.m_oFullGeo.m_dNT);
								if (Geo.isInsidePolygon(dPolygon, dX, dY, 1))
								{
									oBounds.m_nMaxSpeed = MathUtil.bytesToInt(oCtrl.m_yControlValue);
									break;
								}
							}
						}
					}
				}
				catch (Exception oEx)
				{
					oEx.printStackTrace();
				}
			}
		}

		for (BoundsList oBoundsList : oAllBounds)
		{
			int nSize = oBoundsList.size();
			for (int nBoundsIndex = 0; nBoundsIndex < nSize - 1; nBoundsIndex++)
			{
				Bounds oBounds = oBoundsList.get(nBoundsIndex);
				if (oBounds.m_nMaxSpeed == Integer.MIN_VALUE) // if there isn't a maxspeed control found cannot run the algorithm
				{
					oBounds.m_nConsecutiveTimes = 0;
					continue;
				}
				if (oBounds.m_nConsecutiveTimes == 0) // if this is the first pass or the last speed did not fall under the threshold, reset the previous advisory speed
					oBounds.m_dPreviousAdvisorySpeed = oBounds.m_nMaxSpeed;
				
				if (oBounds.m_dPreviousAdvisorySpeed != oBounds.m_nMaxSpeed) // if an advisory speed has been set
				{
					if (oBounds.m_d85th > 1.4 * oBounds.m_dPreviousAdvisorySpeed) // check if the current speed is 40% greater than the advisory speed
					{
						oBounds.cancelCtrls(); // if so cancel ctrls
						for (int nIndex = nBoundsIndex + 1; nIndex < nSize; nIndex++) // and reset the upstream subsegments
						{
							Bounds oUpstream = oBoundsList.get(nIndex);
							oUpstream.m_nConsecutiveTimes = 0;
							oUpstream.m_dAdvisorySpeed = oUpstream.m_dPreviousAdvisorySpeed = oUpstream.m_nMaxSpeed;
						}
					}
				}
				
				
				if (oBounds.m_d85th < IHP.RATIO_REDUCED_SPEED * oBounds.m_nMaxSpeed)
				{
					++oBounds.m_nConsecutiveTimes;

					Bounds oSubSeg1 = oBoundsList.get(nBoundsIndex + 1);
					double dControlFlowRate = IHP.SPACE_FACTOR * oBounds.m_nVolume + (1 - IHP.SPACE_FACTOR) * oSubSeg1.m_nVolume; // equation 1
					oSubSeg1.m_dTarDensity = oSubSeg1.m_dPreviousDensity + (oBounds.m_nVolume - oSubSeg1.m_nVolume) / oSubSeg1.m_dLength * nTimeIntervalSecs; // equation 2
					oSubSeg1.m_dTarCtrlSpeed = Math.max(Math.min(dControlFlowRate / oSubSeg1.m_dTarDensity, oSubSeg1.m_d85th), oSubSeg1.m_d15th); // equation 3
					for (int nSubIndex = nBoundsIndex + 2; nSubIndex < oBoundsList.size(); nSubIndex++)
					{
						Bounds oSubSegN = oBoundsList.get(nSubIndex);
						oSubSegN.m_dTarCtrlSpeed = oSubSeg1.m_dTarCtrlSpeed + ((oSubSegN.m_d85th - oSubSeg1.m_dTarCtrlSpeed) / (nSize - 1)) * (nSubIndex - 1); // equation 4
						oSubSegN.m_dTarCtrlSpeed = Math.max(Math.min(oSubSegN.m_dTarCtrlSpeed, oSubSegN.m_d85th), oSubSegN.m_d15th); // equation 6
					}

					int r = nSize - 1;
					int j = nBoundsIndex + 1;
					Bounds oEnd = oBoundsList.get(r);

					for (int i = j; i < r; i++)
					{
						Bounds oAdvisorySeg = oBoundsList.get(i);
						oAdvisorySeg.m_dAdvisorySpeed = oSubSeg1.m_dTarCtrlSpeed + ((oEnd.m_d85th - oSubSeg1.m_dTarCtrlSpeed) / (r - j)) * (i - j); // equation 7
						oAdvisorySeg.m_dAdvisorySpeed = Math.max(Math.min(oAdvisorySeg.m_dAdvisorySpeed, oAdvisorySeg.m_dPreviousAdvisorySpeed + IHP.MAX_DIFF), oAdvisorySeg.m_dPreviousAdvisorySpeed - IHP.MAX_DIFF); // equation 8
						if (oBounds.m_nConsecutiveTimes > IHP.CONSECUTIVE_TIME_INTERVAL_TRIGGER)
						{
							TrafCtrl oSpeed = new TrafCtrl("maxspeed", (int)oAdvisorySeg.m_dAdvisorySpeed, lNow, lNow, oAdvisorySeg.m_dCenterLine, "IHP2 Speed Harmonization", false, ProcCtrl.IHP2);
							oCtrls.add(oSpeed);
							oBounds.m_oCtrls.add(oSpeed);
						}
					}
				}
				else // speed is not below the threshold so reset values
				{
					oBounds.m_nConsecutiveTimes = 0;
					oBounds.m_dAdvisorySpeed = oBounds.m_dPreviousAdvisorySpeed = oBounds.m_nMaxSpeed;
				}
				
				if (!oBounds.m_oCtrls.isEmpty()) // if controls have been set, do not check the upstream segments because advisory speeds will have been generated for them
					break;
			}
		}
		
		return oCtrls;
	}
}
