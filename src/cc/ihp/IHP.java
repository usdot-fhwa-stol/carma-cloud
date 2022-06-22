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
import cc.util.CsvReader;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.MathUtil;
import cc.util.Units;
import cc.ws.SessMgr;
import cc.ws.Session;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author aaron.cherney
 */
public class IHP extends HttpServlet
{
	private String m_sEndpoint;
	private Timer m_oTimer = null;
	private final ArrayList<Corridor> m_oCorridors = new ArrayList();
	private String m_sParameterFile;
	private final String[] PARAMETER_KEYS = new String[]{"space_factor", "ratio_reduced_speed", "consecutive_time_interval_trigger", "time_interval", "maximal_gap", "baseline_flow"};
	private final String[] STATUS_MSGS = new String[]{"Error", "Invalid subsegment definitions", "Ready to run", "Running"};
	private final int ERROR = 0;
	private final int INVALIDBOUNDS = 1;
	private final int READY = 2;
	private final int RUNNING = 3;
	private final HashMap<String, Double> m_oParameters = new HashMap();
	private static final Logger LOGGER = LogManager.getLogger(IHP.class);
	private int m_nLastControls = 0;
	private long m_lLastRun = 0;
	private final HashMap<String, Long> m_oUsers = new HashMap();
	private String m_sCorridorFile;
	private String m_sDetectorFile;
	private long m_lLastUnder40 = 0L;
	private String m_sBaseDir;
	private final Lock LOCK = new ReentrantLock();
	private int m_nTriggers = 0;
	private String m_sCurrentFile = null;
	private JSONArray m_oCurrentDetectors = null;
	
		
	@Override
	public void init(ServletConfig oConfig)
		throws ServletException
	{
		// default parameters
		m_oParameters.put("space_factor", 0.05);
		m_oParameters.put("ratio_reduced_speed", 0.8);
		m_oParameters.put("consecutive_time_interval_trigger", 2.0);
		m_oParameters.put("time_interval", 300.0); // seconds
		m_oParameters.put("maximal_gap", 3.0); // m/s
		m_oParameters.put("baseline_flow", 2000.0); // veh/h/lane
		
		m_sParameterFile = oConfig.getInitParameter("pfile");
		if (m_sParameterFile == null)
			m_sParameterFile = "/opt/tomcat/work/carmacloud/ihp/parameters.csv";
		
		readParameters();
		
		m_sCorridorFile = oConfig.getInitParameter("cfile");
		if (m_sCorridorFile == null)
			m_sCorridorFile = "/opt/tomcat/work/carmacloud/ihp/corridor.csv";
		
		m_sDetectorFile = oConfig.getInitParameter("dfile");
		if (m_sDetectorFile == null)
			m_sDetectorFile = "/opt/tomcat/work/carmacloud/ihp/detectors.json";
		
		m_sEndpoint = oConfig.getInitParameter("endpoint");
		
		m_sBaseDir = oConfig.getInitParameter("basedir");
		if (m_sBaseDir == null)
			m_sBaseDir = "/opt/tomcat/work/carmacloud/ihp/";
		
		readCorridors(m_sCorridorFile);
	}
	
	
	public void readCorridors(String sFile)
	{
		LOCK.lock();
		try
		{
			Path oPath = Paths.get(sFile);
			if (!Files.exists(oPath))
				return;
			for (Corridor oList : m_oCorridors)
			{
				for (Subsegment oSubsegment : oList)
				{
					oSubsegment.cancelCtrl();
				}
			}
			m_oCorridors.clear();
			try (CsvReader oIn = new CsvReader(Files.newInputStream(oPath)))
			{
				int nCol;
				Corridor oCorridor = null;
				while ((nCol = oIn.readLine()) > 0)
				{
					if (oIn.isNull(0))
					{
						oCorridor = null;
						continue;
					}

					if (oCorridor == null)
					{
						oCorridor = new Corridor();
						m_oCorridors.add(oCorridor);
					}

					oCorridor.add(new Subsegment(oIn, nCol, false));

				}
			}
			catch (IOException oEx)
			{
				oEx.printStackTrace();
			}
		}
		finally
		{
			LOCK.unlock();
		}
	}
	
	
	public void readDetectors()
		throws IOException
	{
		Path oPath = Paths.get(m_sDetectorFile);
		LOCK.lock();
		try
		{
			if (!Files.exists(oPath))
				return;
			
			JSONArray oAllDetectors;
			try (BufferedReader oIn = Files.newBufferedReader(oPath, StandardCharsets.UTF_8))
			{
				oAllDetectors = new JSONArray(new JSONTokener(oIn));
			}
			m_oCurrentDetectors = oAllDetectors;
			
			Units oUnits = Units.getInstance();
			for (int nCorridorsIndex = 0; nCorridorsIndex < m_oCorridors.size(); nCorridorsIndex++)
			{
				Corridor oList = m_oCorridors.get(nCorridorsIndex);
				JSONArray oDetectors = oAllDetectors.getJSONArray(nCorridorsIndex);
				for (int nSubsegmentIndex = 0; nSubsegmentIndex < oList.size(); nSubsegmentIndex++)
				{
					Subsegment oSubsegment = oList.get(nSubsegmentIndex);
					JSONArray oSubsegmentDetectors = oDetectors.getJSONArray(nSubsegmentIndex);
					oSubsegment.updateDetectors(oUnits.convert("mph", "m/s", oSubsegmentDetectors.getDouble(0)), oUnits.convert("mph", "m/s", oSubsegmentDetectors.getDouble(1)), oUnits.convert("mph", "m/s", oSubsegmentDetectors.getDouble(2)), oSubsegmentDetectors.getDouble(3) / 100.0, oSubsegmentDetectors.getDouble(4));
				}
			}
		}
		finally
		{
			LOCK.unlock();
		}
	}
		
	
	public void execute()
	{
		if (LOCK.tryLock())
		{
			try
			{
				for (Corridor oCorridor : m_oCorridors)
					for (Subsegment oSubsegment : oCorridor)
						oSubsegment.reset();
				if (m_sEndpoint != null)
				{
					URL oUrl = new URL(m_sEndpoint);
					URLConnection oConn = oUrl.openConnection();
					oConn.setReadTimeout(30000);
					oConn.setConnectTimeout(10000);
					try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
					{
						IHPResponse oResponse = new IHPResponse(oIn, m_oCorridors);
					}
				}
				else
				{
					readDetectors();
				}
				
				ArrayList<TrafCtrl> oCtrls = generateSpeedControls(m_oCorridors, readParameters(), m_sEndpoint == null);
				ArrayList<int[]> nTiles = new ArrayList();
				for (TrafCtrl oCtrl : oCtrls)
				{
					oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.IHP2);
					ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
				}
				ProcCtrl.renderCtrls("maxspeed", oCtrls, nTiles);
				m_lLastRun = System.currentTimeMillis();
				m_nLastControls = oCtrls.size();
			}
			catch (Exception oEx)
			{
				LOGGER.error(oEx, oEx);
			}
			finally
			{
				LOCK.unlock();
			}
		}
	}
	
	
	private HashMap<String, Double> readParameters()
	{
		synchronized (m_oParameters)
		{
			Path oParameters = Paths.get(m_sParameterFile);
			if (Files.exists(oParameters))
			{
				try
				{
					try (CsvReader oIn = new CsvReader(Files.newInputStream(oParameters)))
					{
						int nCol;
						while ((nCol = oIn.readLine()) > 0)
						{
							try
							{
								String sKey = oIn.parseString(0);
								int nIndex = PARAMETER_KEYS.length;
								while (nIndex-- > 0)
									if (sKey.compareTo(PARAMETER_KEYS[nIndex]) == 0)
										break;
								
								if (nIndex < 0)
									continue;
								double dVal = oIn.parseDouble(1);
								m_oParameters.put(sKey, dVal);
							}
							catch (Exception oInnerEx)
							{
								LOGGER.error("Failed setting parameter " + oIn.parseString(0) + " as " + oIn.parseString(1));
							}
						}
					}
				}
				catch (Exception oEx)
				{
					LOGGER.error(oEx.getMessage());
				}
			}
			
			HashMap<String, Double> oReturn = new HashMap();
			for (Map.Entry<String, Double> oEntry : m_oParameters.entrySet())
			{
				oReturn.put(oEntry.getKey(), oEntry.getValue());
			}
			
			return oReturn;
		}
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
		oRes.setContentType("application/json");
		JSONObject oResponse = new JSONObject();
		oResponse.put("error", "");
		int nStatus;
		try
		{
			if (sMethod.compareTo("getParameters") == 0)
			{
				nStatus = getParameters(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("status") == 0)
			{
				nStatus = status(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("saveParameters") == 0)
			{
				nStatus = saveParameters(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("start") == 0)
			{
				nStatus = startSim(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("stop") == 0)
			{
				nStatus = stopSim(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("saveGeo") == 0)
			{
				nStatus = saveGeo(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("getGeo") == 0)
			{
				nStatus = getGeo(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("updateDetectors") == 0)
			{
				nStatus = updateDetectors(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("getDetectors") == 0)
			{
				nStatus = getDetectors(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("getFiles") == 0)
			{
				nStatus = getFiles(oReq, oSession, oResponse);
			}
			else if (sMethod.compareTo("getFile") == 0)
			{
				nStatus = getFile(oReq, oSession, oResponse);
			}
			else
				nStatus = HttpServletResponse.SC_UNAUTHORIZED;
			
			oRes.setStatus(nStatus);
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
			catch (IOException oInner)
			{
				oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				LOGGER.error("Failed to write response for " + sMethod);
			}
		}
		catch (Exception oEx)
		{
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			LOGGER.error(oEx.getMessage() + " during " + sMethod);
			String sErr = oResponse.getString("error");
			oResponse.put("error", sErr);
			try (PrintWriter oOut = oRes.getWriter())
			{
				oResponse.write(oOut);
			}
		}
	}
	
	
	private int getParameters(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		readParameters();
		synchronized (m_oParameters)
		{
			for (String sKey : PARAMETER_KEYS)
			{
				oResponse.put(sKey, m_oParameters.get(sKey));
			}
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	private int saveParameters(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		double[] dNewParas = new double[PARAMETER_KEYS.length];
		int nIndex = -1;
		try
		{
			for (nIndex = 0; nIndex < dNewParas.length; nIndex++)
				dNewParas[nIndex] = Double.parseDouble(oReq.getParameter(PARAMETER_KEYS[nIndex]));
		}
		catch (Exception oEx)
		{
			oResponse.put("error", "Missing/invalid parameter for key " + PARAMETER_KEYS[nIndex]);
			return HttpServletResponse.SC_BAD_REQUEST;
		}
		
		synchronized (m_oParameters)
		{
			Path oParameters = Paths.get(m_sParameterFile);
			Files.createDirectories(oParameters.getParent(), FileUtil.DIRPERS);
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oParameters, FileUtil.WRITE), StandardCharsets.UTF_8)))
			{
				for (nIndex = 0; nIndex < dNewParas.length; nIndex++)
				{
					oOut.append(String.format("%s,%1.3f\n", PARAMETER_KEYS[nIndex], dNewParas[nIndex]));
					m_oParameters.put(PARAMETER_KEYS[nIndex], dNewParas[nIndex]);
				}
			}
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	private int status(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		long lNow = System.currentTimeMillis();
		int nUsers = 1;
		int nResStatus = ERROR;
		synchronized (m_oUsers)
		{
			m_oUsers.put(oSession.getUserName(), lNow);
			long lTimeout = lNow - 30000; // timeout after 30 seconds
			ArrayList<String> oRemove = new ArrayList();
			for (String sUser : m_oUsers.keySet())
			{
				if (m_oUsers.get(sUser) <= lTimeout)
					oRemove.add(sUser);
			}
			
			for (String sRemove : oRemove)
				m_oUsers.remove(sRemove);
			
			nUsers = m_oUsers.size();
		}
		oResponse.put("users", nUsers);

		int nPeriodInMillis;
		synchronized (m_oParameters)
		{
			nPeriodInMillis = (int)m_oParameters.get("time_interval").doubleValue() * 1000;
		}
		long lNext = lNow / nPeriodInMillis * nPeriodInMillis + nPeriodInMillis;
		synchronized (this)
		{
			if (m_oTimer == null)
				nResStatus = READY;
			else
			{
				nResStatus = RUNNING;
				oResponse.put("next_run", lNext);
			}

			oResponse.put("ctrls_made", m_nLastControls);
			oResponse.put("last_run", m_lLastRun);
		}

		
		oResponse.put("status", nResStatus);
		oResponse.put("desc", STATUS_MSGS[nResStatus]);
		getFiles(oReq, oSession, oResponse);
		
		return HttpServletResponse.SC_OK;
	}
	
	
	private int startSim(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		synchronized (this)
		{
			if (m_oTimer != null)
			{
				oResponse.put("error", "Already running");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			m_oTimer = new Timer();
			GregorianCalendar oCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			oCal.set(Calendar.MILLISECOND, 0);
			oCal.set(Calendar.SECOND, 0);
			int nPeriodInMillis;
			synchronized (m_oParameters)
			{
				nPeriodInMillis = (int)m_oParameters.get("time_interval").doubleValue() * 1000;
			}
			long lNext = System.currentTimeMillis() / nPeriodInMillis * nPeriodInMillis + nPeriodInMillis;
			SimpleDateFormat oFileFormat = new SimpleDateFormat("'" + m_sBaseDir + "'yyyyMM'/'yyyyMMdd'/ihplog_'yyyyMMdd_HHmmss'.csv'");
			oFileFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			m_sCurrentFile = oFileFormat.format(lNext);
			LOCK.lock();
			try
			{
				m_nTriggers = 0;
				for (Corridor oCorridor : m_oCorridors)
				{
					for (Subsegment oSubsegment : oCorridor)
					{
						oSubsegment.m_dPreviousAdvisorySpeed = oSubsegment.m_dAdvisorySpeed = oSubsegment.m_nMaxSpeed;
						if (oSubsegment.m_oCtrl != null)
							oSubsegment.cancelCtrl();
					}
				}
			}
			finally
			{
				LOCK.unlock();
			}
			m_oTimer.scheduleAtFixedRate(new TimerTask(){@Override public void run(){execute();}}, new Date(lNext), nPeriodInMillis);
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	private int stopSim(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		synchronized (this)
		{
			if (m_oTimer == null)
			{
				oResponse.put("error", "Not running");
				return HttpServletResponse.SC_BAD_REQUEST;
			}
			
			m_oTimer.cancel();
			m_oTimer = null;
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	private int saveGeo(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		JSONArray oFeatures = new JSONArray(oReq.getParameter("geo"));
		double dLaneWidth = Double.parseDouble(oReq.getParameter("width"));
		int nLanes = (int)Double.parseDouble(oReq.getParameter("lanes"));
		double dWidth = dLaneWidth * nLanes;
		
		StringBuilder sBuf = new StringBuilder();
		DecimalFormat oOrdDf = new DecimalFormat("#.#######");
		String sWidth = new DecimalFormat("#.##").format(dWidth);
		for (int nFeatureIndex = oFeatures.length() - 1; nFeatureIndex >= 0; nFeatureIndex--)
		{
			JSONArray oCoords = oFeatures.getJSONObject(nFeatureIndex).getJSONObject("geometry").getJSONArray("coordinates");
			for (int nCoordIndex = 0; nCoordIndex < oCoords.length(); nCoordIndex++)
			{
				JSONArray oPt = oCoords.getJSONArray(nCoordIndex);
				sBuf.append(oOrdDf.format(oPt.get(0))).append(',').append(oOrdDf.format(oPt.get(1))).append(',').append(sWidth).append(',');
			}
			sBuf.setLength(sBuf.length() - 1);
			sBuf.append('\n');
		}
		
		LOCK.lock();
		try
		{
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sCorridorFile), FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
			{
				oOut.append(sBuf);
			}
			
			Files.deleteIfExists(Paths.get(m_sDetectorFile));
			readCorridors(m_sCorridorFile);
		}
		finally
		{
			LOCK.unlock();
		}
		return HttpServletResponse.SC_OK;
	}
	
	
	private int getGeo(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
	{
		JSONArray oFeatures = new JSONArray();
		LOCK.lock();
		try
		{
			for (Corridor oCorridor : m_oCorridors)
			{
				for (Subsegment oSubsegment : oCorridor)
				{
					JSONObject oFeature = new JSONObject();
					oFeature.put("type", "Feature");
					JSONObject oGeo = new JSONObject();
					oGeo.put("type", "LineString");
					JSONArray oCoords = new JSONArray();
					Iterator<double[]> oIt = Arrays.iterator(oSubsegment.m_dGeo, new double[2], 1, 2);
					while (oIt.hasNext())
					{
						double[] dPt = oIt.next();
						oCoords.put(new double[]{Mercator.xToLon(dPt[0]), Mercator.yToLat(dPt[1])});
					}
					oGeo.put("coordinates", oCoords);
					oFeature.put("geometry", oGeo);
					oFeatures.put(oFeature);
				}
			}
		}
		finally
		{
			LOCK.unlock();
		}
		
		oResponse.put("features", oFeatures);
		
		return HttpServletResponse.SC_OK;
	}
	
	
	private int updateDetectors(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		JSONArray oAllDetectors = new JSONArray(oReq.getParameter("detectors"));
		LOCK.lock();
		try
		{
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sDetectorFile), FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
			{
				oAllDetectors.write(oOut);
			}
			readDetectors();
		}
		finally
		{
			LOCK.unlock();
		}

		return HttpServletResponse.SC_OK;
	}
	
	private int getDetectors(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		
		LOCK.lock();
		try
		{
			Path oPath = Paths.get(m_sDetectorFile);
			if (!Files.exists(oPath))
			{
				oResponse.put("detectors", new JSONArray());
			}
			else
			{
				try (BufferedReader oIn = Files.newBufferedReader(oPath, StandardCharsets.UTF_8))
				{
					JSONArray oDetectors = new JSONArray(new JSONTokener(oIn));
					oResponse.put("detectors", oDetectors);
				}
			}
		}
		finally
		{
			LOCK.unlock();
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	
	private int getFiles(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		Path oBaseDir = Paths.get(m_sBaseDir);
		List<Path> oMonths = Files.walk(oBaseDir, 1).filter(Files::isDirectory).filter(oP -> 
		{
			String sPath = oP.toString();
			if (!sPath.endsWith("/"))
				sPath += "/";
			return m_sBaseDir.compareTo(sPath) != 0;
		}).sorted().collect(Collectors.toList());
		JSONObject oFiles = new JSONObject();
		for (Path oMonth : oMonths)
		{
			JSONObject oMonthJson = new JSONObject();
			List<Path> oDays = Files.walk(oMonth, 1).filter(Files::isDirectory).filter(oP -> oP.toString().compareTo(oMonth.toString()) != 0).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
			for (Path oDay : oDays)
			{
				JSONArray oDayJson = new JSONArray();
				List<Path> oRuns = Files.walk(oDay, 1).filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
				for (Path oRun : oRuns)
				{
					oDayJson.put(oRun.getFileName().toString());
				}
				oMonthJson.put(oDay.getFileName().toString(), oDayJson);
			}
			oFiles.put(oMonth.getFileName().toString(), oMonthJson);
		}
		
		oResponse.put("files", oFiles);
		return HttpServletResponse.SC_OK;
	}
	
	
	private int getFile(HttpServletRequest oReq, Session oSession, JSONObject oResponse)
		throws Exception
	{
		String sFile = oReq.getParameter("file");
		Path oPath = Paths.get(m_sBaseDir + sFile);
		if (!Files.exists(oPath))
			return HttpServletResponse.SC_NOT_FOUND;
		
		StringBuilder sBuf = new StringBuilder();
		try (BufferedReader oIn = Files.newBufferedReader(oPath, StandardCharsets.UTF_8))
		{
			int nByte;
			while ((nByte = oIn.read()) >= 0)
				sBuf.append((char)nByte);
		}
		oResponse.put("csv", sBuf.toString());
		return HttpServletResponse.SC_OK;
	}
	
	
	ArrayList<TrafCtrl> generateSpeedControls(ArrayList<Corridor> oCorridors, HashMap<String, Double> oParameters, boolean bSimulation)
		throws IOException
	{
		ArrayList<TrafCtrl> oCtrls = new ArrayList();
		Mercator oM = Mercator.getInstance();
		int[] nTiles = new int[2];
		int nMaxSpeedCtrl = TrafCtrlEnums.getCtrl("maxspeed");
		byte[] yIdBuf = new byte[16];
		StringBuilder sIdBuf = new StringBuilder();
		long lNow = System.currentTimeMillis();
		SimpleDateFormat oSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		DecimalFormat oDf = new DecimalFormat("#.###");
		oSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		double dSpaceFactor = oParameters.get("space_factor");
		double dReducedSpeedRatio = oParameters.get("ratio_reduced_speed");
		int nConsecutiveTrigger = (int)oParameters.get("consecutive_time_interval_trigger").doubleValue();
		int nTimeInterval = (int)oParameters.get("time_interval").doubleValue();
		double dMaximalGap = oParameters.get("maximal_gap");
		int nBaselineFlow = (int)oParameters.get("baseline_flow").doubleValue();
		StringBuilder sBuf = new StringBuilder();
		sBuf.append(oSdf.format(lNow)).append('\n');
		sBuf.append("space factor,").append(oDf.format(dSpaceFactor)).append('\n');
		sBuf.append("ratio of reduced speed,").append(oDf.format(dReducedSpeedRatio)).append('\n');
		sBuf.append("number of consecutive reduced speed,").append(nConsecutiveTrigger).append('\n');
		sBuf.append("time interval,").append(nTimeInterval).append('\n');
		sBuf.append("maximal gap,").append(oDf.format(dMaximalGap)).append('\n');
		sBuf.append("baseline traffic flow,").append(nBaselineFlow).append('\n');
		sBuf.append("input json,\"").append(m_oCurrentDetectors.toString()).append("\"\n");
		if (!bSimulation)
		{
			for (Corridor oCorridor : oCorridors)
			{
				for (Subsegment oSubsegment : oCorridor)
				{

					for (Detector oDetector : oSubsegment)
					{
						oSubsegment.m_dOcc += oDetector.m_dOcc;
						oSubsegment.m_nVolume += oDetector.m_nVolume;
						Iterator<double[]> oIt = Arrays.iterator(oDetector.m_dSpeeds, new double[1], 1, 1);
						while (oIt.hasNext())
							oSubsegment.m_dSpeeds = Arrays.add(oSubsegment.m_dSpeeds, oIt.next()[0]);
					}
					oSubsegment.m_dOcc /= oSubsegment.size();

					oSubsegment.generateStats();
					double dX = Mercator.lonToMeters(oSubsegment.get(0).m_dLon);
					double dY = Mercator.latToMeters(oSubsegment.get(0).m_dLat);
					oSubsegment.m_nMaxSpeed = Integer.MIN_VALUE;
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
										oSubsegment.m_nMaxSpeed = MathUtil.bytesToInt(oCtrl.m_yControlValue);
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
		}
		
		Units oUnits = Units.getInstance();
		String[] sUnits = TrafCtrlEnums.UNITS[TrafCtrlEnums.getCtrl("maxspeed")];
		
		for (Corridor oCorridor : oCorridors)
		{
			int n = oCorridor.size();
			Subsegment oReducedSpeedZone = oCorridor.get(0);
			Subsegment oSubSegOne = oCorridor.get(1);
			if (oReducedSpeedZone.m_d85th <= oReducedSpeedZone.m_nMaxSpeed * 0.4)
				m_lLastUnder40 = lNow;
			if (oSubSegOne.m_d85th < dReducedSpeedRatio * oSubSegOne.m_nMaxSpeed)
			{
				++m_nTriggers;
				sBuf.append("Consecutive reduced speed triggers,").append(m_nTriggers).append('\n');
				double dControlFlowRate = dSpaceFactor * oReducedSpeedZone.m_nVolume + (1 - dSpaceFactor) * oSubSegOne.m_nVolume; // equation 1
				sBuf.append("Equation 1,").append(oDf.format(dControlFlowRate)).append('\n');
				double dTargetDensity = oSubSegOne.m_dPreviousDensity + (oReducedSpeedZone.m_nVolume - oSubSegOne.m_nVolume) / oSubSegOne.m_dLength * nTimeInterval; // equation 2
				sBuf.append("Equation 2,").append(oDf.format(dTargetDensity)).append('\n');
				double dTargetSpeed = Math.max(Math.min(dControlFlowRate / dTargetDensity, oSubSegOne.m_d85th), oSubSegOne.m_d15th); // equation 3
				sBuf.append("Equation 3,").append(oDf.format(dTargetSpeed)).append('\n');
				for (int i = 2; i < n; i++)
				{
					Subsegment oUpstream = oCorridor.get(i);
					oUpstream.m_dTarCtrlSpeed = dTargetSpeed + ((oUpstream.m_d85th - dTargetSpeed) / (n - 1)) * (i - 1); // equation 4
					sBuf.append("Equation 4,subsegment ").append(i).append(',').append(oDf.format(oUpstream.m_dTarCtrlSpeed)).append('\n');
					oUpstream.m_dAdvisorySpeed = oUpstream.m_dTarCtrlSpeed;
//					oUpstream.m_dAdvisorySpeed = Math.max(Math.min(oUpstream.m_dTarCtrlSpeed, oUpstream.m_d85th), oUpstream.m_d15th); // equation 6
//					sBuf.append("Equation 6,subsegment ").append(i).append(',').append(oDf.format(oUpstream.m_dAdvisorySpeed)).append('\n');
					
				}
				
//				int r = n;
//				Subsegment oLast = oCorridor.get(r - 1);
//				for (int i = 2; i < n; i++)
//				{
//					Subsegment oUpstream = oCorridor.get(i);
//					
//					for (int j = i; j < r; j++)
//					{
//						Subsegment oJ = oCorridor.get(j);
//						oUpstream.m_dAdvisorySpeed = oJ.m_dAdvisorySpeed + (oLast.m_d85th - oJ.m_dAdvisorySpeed) / (r - j) * (i - j); // equation 7
//						sBuf.append("Equation 7,subsegment ").append(j).append(',').append(oDf.format(oUpstream.m_dAdvisorySpeed)).append('\n');
//					}
//				}
				
				for (int i = 2; i < n; i++)
				{
					Subsegment oUpstream = oCorridor.get(i);
					oUpstream.m_dAdvisorySpeed = Math.max(Math.min(oUpstream.m_dAdvisorySpeed, oUpstream.m_dPreviousAdvisorySpeed + dMaximalGap), oUpstream.m_dPreviousAdvisorySpeed - dMaximalGap); // equation 8
					sBuf.append("Equation 8,subsegment ").append(i).append(',').append(oDf.format(oUpstream.m_dAdvisorySpeed)).append('\n');
					if (m_nTriggers >= nConsecutiveTrigger)
					{
						int nSpd = (int)Math.round(oUnits.convert("m/s", sUnits[0], oUpstream.m_dAdvisorySpeed));
						if (oUpstream.m_oCtrl != null && MathUtil.bytesToInt(oUpstream.m_oCtrl.m_yControlValue) == nSpd)
						{
							continue;
						}
						TrafCtrl oSpeed = new TrafCtrl("maxspeed", nSpd, null, lNow, lNow, oUpstream.m_dCenterLine, String.format("Sub %d - Speed Harm", i), false, ProcCtrl.IHP2);
						if (oUpstream.m_oCtrl != null)
							oUpstream.cancelCtrl();
						oUpstream.m_oCtrl = oSpeed;
						oUpstream.m_bNewControl = true;
						sBuf.append("new maxspeed,subsegment ").append(i).append(',').append(oDf.format(oUnits.convert(sUnits[0], "mph", nSpd))).append(" mph").append('\n');
					}
				}
			}
			else
			{
				m_nTriggers = 0;
				sBuf.append("Speed greater than reduced ratio\n");
				for (int i = 2; i < n; i++)
				{
					Subsegment oUpstream = oCorridor.get(i);
					oUpstream.m_dPreviousAdvisorySpeed = oUpstream.m_nMaxSpeed;
				}
				
				if (lNow - m_lLastUnder40 > 300000)
				{
					for (int i = 2; i < n; i++)
					{
						Subsegment oUpstream = oCorridor.get(i);
						if (oUpstream.m_oCtrl != null)
						{
							sBuf.append("Canceling control for subsegment ").append(i).append('\n');
							oUpstream.cancelCtrl();
						}
					}
				}
			}
		}
		
		for (Corridor oCorridor : oCorridors)
		{
			for (Subsegment oSubsegment : oCorridor)
			{
				if (oSubsegment.m_bNewControl && oSubsegment.m_oCtrl != null)
					oCtrls.add(oSubsegment.m_oCtrl);
			}
		}
		
		sBuf.append('\n').append('\n');
		Path oPath = Paths.get(m_sCurrentFile);
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.APPENDTO, FileUtil.FILEPERS), StandardCharsets.UTF_8)))
		{
			oOut.append(sBuf);
		}
		
		return oCtrls;
	}
}
