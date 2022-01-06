/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcCtrl;
import static cc.ctrl.proc.ProcCtrl.updateTiles;
import cc.util.CsvReader;
import cc.util.FileUtil;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author aaron.cherney
 */
public class IHP /*extends HttpServlet*/
{
	private String m_sEndpoint;
	private int m_nPeriod;
	private String m_sMetadata;
	private Timer m_oTimer;
	private int m_nCollectsPerMinute = 3;
	final static double SPACE_FACTOR = 0.05;
	final static double RATIO_REDUCED_SPEED = 0.8;
	final static int CONSECUTIVE_TIME_INTERVAL_TRIGGER = 2;
	final static double MAX_DIFF = 6.711409; // ~3m/s in mph
	private ArrayList<BoundsList> m_oBounds = new ArrayList();

	
	public static void main(String[] sArgs)
	{
		Config oConfig = new Config();
		oConfig.PARAMS.put("period", "5");
		oConfig.PARAMS.put("endpoint", "file:///C:/Users/aaron.cherney/Documents/cc/ihp2/endpoint.json");
		oConfig.PARAMS.put("boundsfile", "C:/Users/aaron.cherney/Documents/cc/ihp2/bounds.csv");
		IHP oIHP = new IHP();
		oIHP.init(oConfig);
	}
	
	//	@Override
	public void init(ServletConfig oConfig)
	{
		m_sEndpoint = oConfig.getInitParameter("endpoint");
		int nPeriod = Integer.parseInt(oConfig.getInitParameter("period")); // unit is minutes
		m_nPeriod = nPeriod;
		try (CsvReader oIn = new CsvReader(Files.newInputStream(Paths.get(oConfig.getInitParameter("boundsfile")))))
		{
			int nCol;
			BoundsList oBoundsList = null;
			while ((nCol = oIn.readLine()) > 0)
			{
				if (oIn.isNull(0))
				{
					oBoundsList = null;
					continue;
				}
				
				if (oBoundsList == null)
				{
					oBoundsList = new BoundsList();
					m_oBounds.add(oBoundsList);
				}
				
				oBoundsList.add(new Bounds(oIn, nCol));
				
			}
		}
		catch (IOException ex)
		{
			
		}
		createData();
		execute();
//		m_oTimer = new Timer();
//		GregorianCalendar oCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
//		oCal.set(Calendar.MILLISECOND, 0);
//		oCal.set(Calendar.SECOND, 0);
//		oCal.set(Calendar.MINUTE, (oCal.get(Calendar.MINUTE) / nPeriod) * nPeriod + nPeriod); // determine the next execution period with no midnight offset
//		m_oTimer.scheduleAtFixedRate(new TimerTask(){@Override public void run(){execute();}}, oCal.getTime(), 60 * 1000 * nPeriod);
//		oCal.add(Calendar.MINUTE, -1);
//		m_oTimer.scheduleAtFixedRate(new TimerTask(){@Override public void run(){createData();}}, oCal.getTime(), 60 * 1000 * nPeriod);
	}
	
	
	public void execute()
	{
		try
		{
			for (BoundsList oBoundsList : m_oBounds)
				for (Bounds oBounds : oBoundsList)
					oBounds.reset();
			URL oUrl = new URL(m_sEndpoint);
			URLConnection oConn = oUrl.openConnection();
			oConn.setReadTimeout(30000);
			oConn.setConnectTimeout(10000);
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
			{
				IHPResponse oResponse = new IHPResponse(oIn, m_oBounds);
				
				ArrayList<TrafCtrl> oCtrls = oResponse.generateSpeedControls(m_oBounds, m_nPeriod * 60);
				ArrayList<int[]> nTiles = new ArrayList();
				for (TrafCtrl oCtrl : oCtrls)
				{

					oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.IHP2);
					updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
					
					ProcCtrl.renderCtrls("maxspeed", oCtrls, nTiles);
				}
			}
		}
		catch (Exception oEx)
		{
			
		}
	}
	
	
	private void createData()
	{
		if (!m_sEndpoint.startsWith("file:///"))
			return;
		
		SecureRandom oRng = new SecureRandom();
		long lNow = System.currentTimeMillis();
		long lPeriodMillis = 60 * 1000 * m_nPeriod;
		long lTimeStep = 60000 / m_nCollectsPerMinute;
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		JSONArray oOutRes = new JSONArray();
		JSONArray oFeatureArray = new JSONArray();

		try (BufferedInputStream oIn = new BufferedInputStream(Files.newInputStream(Paths.get(m_sEndpoint.substring(8)))))
		{
			JSONObject oJson = new JSONObject(new JSONTokener(oIn));
			JSONArray oResponse = oJson.getJSONArray("response");
			for (int nLayerIndex = 0; nLayerIndex < oResponse.length(); nLayerIndex++)
			{
				JSONObject oOuterLayer = oResponse.getJSONObject(nLayerIndex);
				String sType = oOuterLayer.getString("layer_type");
				JSONObject oInnerLayer = oOuterLayer.getJSONObject("layer");
				if (sType.compareTo("mvd-metadata") == 0)
				{
					oOutRes.put(oOuterLayer);
					JSONArray oFeatures = oInnerLayer.getJSONArray("features");
					for (int nFeatureIndex = 0; nFeatureIndex < oFeatures.length(); nFeatureIndex++)
					{
						JSONObject oFeature = oFeatures.getJSONObject(nFeatureIndex);
						JSONObject oFeatureProps = oFeature.getJSONObject("properties");
						String sId = oFeatureProps.getString("detector_id");
						int nInsert = sId.lastIndexOf("-");
						JSONArray oLanes = oFeatureProps.getJSONArray("lane_positions");
						
						int nSpeedLimit = oFeatureProps.optInt("spdlimit", 65);
						double d85th = nSpeedLimit * 0.85;
						double dSpeedCap = nSpeedLimit * 1.15;
						double dDiff = dSpeedCap - d85th;
						int nFlowLimit = oFeatureProps.optInt("flowlimit", 10);
						StringBuilder sBuf = new StringBuilder(sId.length() + 7);
						for (int nLaneIndex = 0; nLaneIndex < oLanes.length(); nLaneIndex++)
						{
							int nLane = oLanes.getInt(nLaneIndex);
							sBuf.setLength(0);
							sBuf.append(sId);
							sBuf.insert(nInsert, String.format("-Lane-%d", nLane));
							for (int nTimeIndex = 0; nTimeIndex < m_nPeriod * m_nCollectsPerMinute; nTimeIndex++)
							{
								long lCollectTime = lNow - (60 * 1000 * m_nPeriod) + (nTimeIndex * lTimeStep); 
								JSONObject oDataFeature = new JSONObject();
								oDataFeature.put("type", "Feature");
								JSONObject oDataProps = new JSONObject();
								oDataProps.put("detector_id", sBuf.toString());
								oDataProps.put("collection_time", oSdf.format(lCollectTime));
								oDataProps.put("lane_position", nLane);
								oDataProps.put("period_seconds", 60 / m_nCollectsPerMinute);
								oDataProps.put("volume", oRng.nextInt(nFlowLimit));
								oDataProps.put("occupancy", oRng.nextDouble());
								double dChance= oRng.nextDouble();
								double dSpeed = nSpeedLimit;
								if (dChance < 0.15)
								{
									dSpeed = oRng.nextDouble() * d85th;
								}
								else
								{
									dSpeed = (dDiff * dChance) + d85th; 
								}
								oDataProps.put("speed", dSpeed);
								oDataFeature.put("properties", oDataProps);
								oFeatureArray.put(oDataFeature);
							}
							
						}
					}
				}
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
		
		JSONObject oLayerObj = new JSONObject();
		oLayerObj.put("type", "FeatureCollection");
		oLayerObj.put("features", oFeatureArray);
		JSONObject oDataLayer = new JSONObject();
		oDataLayer.put("layer_type", "mvd-data");
		oDataLayer.put("layer", oLayerObj);
		oOutRes.put(oDataLayer);
		JSONObject oOutput = new JSONObject();
		oOutput.put("request_id", UUID.randomUUID().toString());
		oOutput.put("response", oOutRes);
		oOutput.put("timestamp", oSdf.format(System.currentTimeMillis()));
		
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sEndpoint.substring(8)), FileUtil.WRITE), "UTF-8")))
		{
			oOutput.write(oOut, 2, 0);
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
	
	static class Config implements ServletConfig
	{
		HashMap<String, String> PARAMS = new HashMap();
		@Override
		public String getServletName()
		{
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public ServletContext getServletContext()
		{
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getInitParameter(String name)
		{
			return PARAMS.get(name);
		}

		@Override
		public Enumeration<String> getInitParameterNames()
		{
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}
		
	}
}
