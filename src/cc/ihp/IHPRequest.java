/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.util.Arrays;
import cc.util.Geo;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author aaron.cherney
 */
public class IHPRequest
{
	String m_sRequestId;
	String m_sDeviceId;
	String m_sDeviceType;
	ArrayList<int[]> m_oGeometry = new ArrayList();
	
	public IHPRequest()
	{
		
	}

	public IHPRequest(InputStream oIn)
	{
		JSONObject oRequest = new JSONObject(new JSONTokener(oIn));
		JSONObject oInfo = oRequest.getJSONObject("general_info");
		m_sRequestId = oInfo.optString("request_id", null);
		m_sDeviceId = oInfo.optString("device_id", null);
		m_sDeviceType = oInfo.optString("device_type", null);
		JSONArray oLayers = oRequest.getJSONArray("layer_request");
		for (int nIndex = 0; nIndex < oLayers.length(); nIndex++)
		{
			JSONObject oLayer = oLayers.getJSONObject(nIndex);
		}
	}
	
	public void sendRequest()
	{
		JSONObject oRequest = new JSONObject();
		JSONObject oInfo = new JSONObject();
		oInfo.put("request_id", m_sRequestId);
		oInfo.put("device_id", m_sDeviceId);
		oInfo.put("device_type", m_sDeviceType);
		oRequest.put("general_info", oInfo);
		JSONArray oLayerRequests = new JSONArray();
		JSONObject oDataLayer = new JSONObject();
		oDataLayer.put("layer", "mvd-data");
		JSONObject oRegion = new JSONObject();
		oRegion.put("type", "polygons");
		JSONArray oPolygons = new JSONArray();
		for (int nIndex = 0; nIndex < m_oGeometry.size(); nIndex++)
		{
			JSONArray oRing = new JSONArray();
			int[] nRing = m_oGeometry.get(nIndex);
			Iterator<int[]> oIt = Arrays.iterator(nRing, new int[2], 1, 2);
			while (oIt.hasNext())
			{
				int[] nPt = oIt.next();
				oRing.put(new JSONArray(new double[]{Geo.fromIntDeg(nPt[0]), Geo.fromIntDeg(nPt[1])}));
			}
			oPolygons.put(oRing);
		}
		oRegion.put("polygons", oPolygons);
		oDataLayer.put("region", oRegion);
		oLayerRequests.put(oDataLayer);
		oRequest.put("layer_requests", oLayerRequests);
		
		System.out.print(oRequest.toString(2));
	}
	
	
	public static void main(String[] sArgs)
		throws Exception
	{
		IHPRequest oReq = new IHPRequest();
		oReq.m_sDeviceId = "CARMA_CLOUD";
		oReq.m_sDeviceType = "carma-cloud";
		oReq.m_sRequestId = "9db9df03-09cb-4a0a-bcb1-ff751f32a6dd";
		int[] nGeo = Arrays.newIntArray();
		try (BufferedReader oIn = Files.newBufferedReader(Paths.get("C:/Users/aaron.cherney/Documents/IMRCP/stlouis.geojson"), StandardCharsets.UTF_8))
		{
			JSONArray oThing = new JSONArray(new JSONTokener(oIn)).getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0);
			for (int nIndex = 0; nIndex < oThing.length(); nIndex++)
			{
				double dLon = oThing.getJSONArray(nIndex).getDouble(0);
				double dLat = oThing.getJSONArray(nIndex).getDouble(1);
				nGeo = Arrays.add(nGeo, Geo.toIntDeg(dLon), Geo.toIntDeg(dLat));
			}
		}
		oReq.m_oGeometry = new ArrayList();
		oReq.m_oGeometry.add(nGeo);
		oReq.sendRequest();
		
	}
}
