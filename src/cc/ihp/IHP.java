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

	/**
	 * Path specifying the data endpoint. Can be a local file or remote URL
	 */
	private String m_sEndpoint;

	/**
	 * Object used to execute tasks on a regular interval
	 */
	private Timer m_oTimer = null;

	/**
	 * List that contains the definitions of the Speed Harmonization Corridors
	 */
	private final ArrayList<Corridor> m_oCorridors = new ArrayList();

	/**
	 * Path to file contains parameters for the Speed Harmonization Algorithm
	 */
	private String m_sParameterFile;

	/**
	 * Array of parameter names
	 */
	private final String[] PARAMETER_KEYS = new String[]{"space_factor", "ratio_reduced_speed", "consecutive_time_interval_trigger", "time_interval", "maximal_gap", "baseline_flow"};

	/**
	 * Array containing status messages
	 */
	private final String[] STATUS_MSGS = new String[]{"Error", "Invalid subsegment definitions", "Ready to run", "Running"};

	/**
	 * Status code for Error
	 */
	private final int ERROR = 0;

	/**
	 * Status code for a corridor not properly defined
	 */
	private final int INVALIDCORRIDOR = 1;

	/**
	 * Status code for ready
	 */
	private final int READY = 2;

	/**
	 * Status code for running
	 */
	private final int RUNNING = 3;

	/**
	 * Map used to store parameters for the algorithm
	 */
	private final HashMap<String, Double> m_oParameters = new HashMap();

	/**
	 * Log4j logger object
	 */
	private static final Logger LOGGER = LogManager.getLogger(IHP.class);

	/**
	 * Keeps track of the number of controls created for the last run of the Speed Harmonization algorithm
	 */
	private int m_nLastControls = 0;

	/**
	 * Timestamp of the last run of the Speed Harmonization algorithm
	 */
	private long m_lLastRun = 0;

	/**
	 * Map used to keep track of users accessing the system
	 */
	private final HashMap<String, Long> m_oUsers = new HashMap();
	
	/**
	 * Keeps track of the current user
	 */
	private String m_sCurrentUser;

	/**
	 * Path of the file that contains corridor definitions
	 */
	private String m_sCorridorFile;

	/**
	 * Path of the file containing detector data
	 */
	private String m_sDetectorFile;

	/**
	 * Path of the working directory for IHP files
	 */
	private String m_sBaseDir;

	/**
	 * Lock object used to manage asynchronous requests
	 */
	private final Lock LOCK = new ReentrantLock();

	/**
	 * Keeps track of the number of consecutive runs of the Speed Harmonization Algorithm that have a reduced speed
	 */
	private int m_nTriggers = 0;

	/**
	 * Path of the current log file
	 */
	private String m_sCurrentFile = null;

	/**
	 * JSONArray containing the current values for detectors
	 */
	private JSONArray m_oCurrentDetectors = null;
	
	/**
	 * Initializes the servlet by reading config parameters from web.xml and loading
	 * the current algorithm parameters and speed harm corridor
	 * 
	 * @param oConfig object that contains configuration values defined in web.xml
	 * @throws ServletException
	 */
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
			m_sParameterFile = "/opt/tomcat/work/carmacloud/ihp/parameters.csv"; // default value
		
		readParameters();
		
		m_sCorridorFile = oConfig.getInitParameter("cfile");
		if (m_sCorridorFile == null)
			m_sCorridorFile = "/opt/tomcat/work/carmacloud/ihp/corridor.csv"; // default value
		
		m_sDetectorFile = oConfig.getInitParameter("dfile");
		if (m_sDetectorFile == null)
			m_sDetectorFile = "/opt/tomcat/work/carmacloud/ihp/detectors.json"; // default value
		
		m_sEndpoint = oConfig.getInitParameter("endpoint");
		
		m_sBaseDir = oConfig.getInitParameter("basedir");
		if (m_sBaseDir == null)
			m_sBaseDir = "/opt/tomcat/work/carmacloud/ihp/"; // default value
		
		readCorridors(m_sCorridorFile);
	}
	
	/**
	 * Reads the corridor csv file to load the geometric definition of the corridor
	 * and its subsegments. The file consists of sections separated by blank lines
	 * that represent a corridor to be used for the speed harmonization algorithm.
	 * The lines of a section represent each subsegment of the corridor, starting
	 * with subsegment 0 (the furthest downstream subsegment) and continuing in 
	 * reverse direction of travel order. Each subsegment is a comma separated list
	 * representing the center line of the pavement of the subsegment in lon,lat,width 
	 * coordinates. The first point of each subsegment should be the last point of the
	 * next subsegment.
	 * 
	 * @param sFile path of the corridor csv file
	 */
	public void readCorridors(String sFile)
	{
		LOCK.lock(); // lock for async requests
		try
		{
			Path oPath = Paths.get(sFile);
			if (!Files.exists(oPath))
				return;

			m_oCorridors.clear();
			try (CsvReader oIn = new CsvReader(Files.newInputStream(oPath)))
			{
				int nCol;
				Corridor oCorridor = null;
				while ((nCol = oIn.readLine()) > 0)
				{
					if (oIn.isNull(0)) // blank lines represent the end of a corridor
					{
						oCorridor = null;
						continue;
					}

					if (oCorridor == null)
					{
						oCorridor = new Corridor();
						m_oCorridors.add(oCorridor);
					}

					oCorridor.add(new Subsegment(oIn, nCol));

				}
			}
			catch (IOException oEx)
			{
				LOGGER.error(oEx, oEx);
			}
		}
		finally
		{
			LOCK.unlock();
		}
	}
	
	/**
	 * Reads the detector JSON file used to simulate traffic data if there is not
	 * a live data feed. The detector file is a JSON array of array of arrays.
	 * A sample is below, the first array contains each corridor. Each corridor contains
	 * an array of subsegments. Each subsegment contains an array of values which
	 * represent detector data in this format [speed limit (mph), 15th percentile speed (mph),
	 * 85th percentile speed (mph), occupancy (veh/mile/lane), volume (veh/h/lane]
	 * [ 
	 *	[ // start of corridor 0
	 *		[], // subsegment 0
	 *		[], // subsegment 1
	 *		[], // subsegment 2
	 *		[], // subsegment 3
	 *		[]  // subsegment 4
	 *	], // end of corridor 0
	 *	[ // start of corridor 1
	 *		[], // subsegment 0
	 *		[], // subsegment 1
	 *		[], // subsegment 2
	 *		[], // subsegment 3
	 *		[]  // subsegment 4
	 *	] // end of corridor 1
	 * ]
	 * 
	 * @throws IOException
	 */
	public void readDetectors()
		throws IOException
	{
		Path oPath = Paths.get(m_sDetectorFile);
		LOCK.lock(); // lock for async requests
		try
		{
			if (!Files.exists(oPath))
				return;
			
			JSONArray oAllDetectors;
			try (BufferedReader oIn = Files.newBufferedReader(oPath, StandardCharsets.UTF_8))
			{
				oAllDetectors = new JSONArray(new JSONTokener(oIn));
			}
			m_oCurrentDetectors = oAllDetectors; // keep updated copy in memory
			
			Units oUnits = Units.getInstance();
			for (int nCorridorsIndex = 0; nCorridorsIndex < m_oCorridors.size(); nCorridorsIndex++)
			{
				Corridor oList = m_oCorridors.get(nCorridorsIndex);
				JSONArray oDetectors = oAllDetectors.getJSONArray(nCorridorsIndex);
				for (int nSubsegmentIndex = 0; nSubsegmentIndex < oList.size(); nSubsegmentIndex++)
				{
					Subsegment oSubsegment = oList.get(nSubsegmentIndex);
					JSONArray oSubsegmentDetectors = oDetectors.getJSONArray(nSubsegmentIndex);
					oSubsegment.updateDetectors(oUnits.convert("mph", "m/s", oSubsegmentDetectors.getDouble(0)), // store in m/s for calculations
						oUnits.convert("mph", "m/s", oSubsegmentDetectors.getDouble(1)), // store in m/s for calculations
						oUnits.convert("mph", "m/s", oSubsegmentDetectors.getDouble(2)), // store in m/s for calculations 
						oUnits.convert("veh/mi/lane", "veh/m/lane", oSubsegmentDetectors.getDouble(3)), // store in veh/m/lane
						oUnits.convert("veh/hr/lane", "veh/s/lane", oSubsegmentDetectors.getDouble(4))); // store in veh/s/lane
				}
			}
		}
		finally
		{
			LOCK.unlock();
		}
	}
		
	/**
	 * Main execution of the Speed Harmonization Algorithm. If there is a configured
	 * endpoint, it tries to use data from that source. Otherwise the data in the
	 * detector json file is used.
	 */
	public void execute()
	{
		if (LOCK.tryLock()) // lock for async requests, if lock is already acquired don't execute
		{
			try
			{
				for (Corridor oCorridor : m_oCorridors) // for each subsegment reset data values to be filled in from data source or detectors.json
					for (Subsegment oSubsegment : oCorridor)
						oSubsegment.reset();
				
				if (m_sEndpoint != null) // if the endpoint is defined, attempt to connect to it and get data
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
				else // if there is not an endpoint, read the detector file that contains simulated data
				{
					readDetectors();
				}
				
				ArrayList<TrafCtrl> oCtrls = generateSpeedControls(m_oCorridors, readParameters(), m_sEndpoint == null);
				ArrayList<int[]> nTiles = new ArrayList();
				for (TrafCtrl oCtrl : oCtrls)
				{
					oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom, ProcCtrl.IHP2); // save the new traffic controls
					ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles); // determine which tiles they will be in
				}
				ProcCtrl.renderCtrls("maxspeed", oCtrls, nTiles); // add the rendered view of the control to the correct tiles
				m_lLastRun = System.currentTimeMillis(); // update status variables
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
	
	/**
	 * Reads the algorithm parameters file and updates the values in memory
	 * 
	 * @return A new HashMap object with the current parameter values
	 */
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
			
			HashMap<String, Double> oReturn = new HashMap(); // create a copy of the map to return
			for (Map.Entry<String, Double> oEntry : m_oParameters.entrySet())
			{
				oReturn.put(oEntry.getKey(), oEntry.getValue());
			}
			
			return oReturn;
		}
	}
	
	/**
	 * Parses and processes the HTTP requests from the IHP2 Web Application 
	 * 
	 * @param oReq object that contains all of the HTTP request information
	 * @param oRes object used to set the HTTP response
	 * @throws IOException
	 * @throws ServletException
	 */
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
		throws IOException, ServletException
	{
		Session oSession = SessMgr.getSession(oReq);
		if (oSession == null) // request must contain a valid session token
		{
			oRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		String[] sUriParts = oReq.getRequestURI().split("/");
		String sMethod = sUriParts[sUriParts.length - 1]; // determine what type of request is made
		oRes.setContentType("application/json"); // all responses are json
		JSONObject oResponse = new JSONObject();
		oResponse.put("error", "");
		int nStatus;
		try // call the correct method depending on the request
		{
			if (sMethod.compareTo("getParameters") == 0)
				nStatus = getParameters(oResponse);
			else if (sMethod.compareTo("status") == 0)
				nStatus = status(oSession, oResponse);
			else if (sMethod.compareTo("saveParameters") == 0)
				nStatus = saveParameters(oReq, oResponse);
			else if (sMethod.compareTo("start") == 0)
				nStatus = startSim(oResponse);
			else if (sMethod.compareTo("stop") == 0)
				nStatus = stopSim(oResponse);
			else if (sMethod.compareTo("saveGeo") == 0)
				nStatus = saveGeo(oReq);
			else if (sMethod.compareTo("getGeo") == 0)
				nStatus = getGeo(oResponse);
			else if (sMethod.compareTo("updateDetectors") == 0)
				nStatus = updateDetectors(oReq);
			else if (sMethod.compareTo("getDetectors") == 0)
				nStatus = getDetectors(oResponse);
			else if (sMethod.compareTo("getFiles") == 0)
				nStatus = getFiles(oResponse);
			else if (sMethod.compareTo("getFile") == 0)
				nStatus = getFile(oReq, oResponse);
			else
				nStatus = HttpServletResponse.SC_UNAUTHORIZED;
			
			oRes.setStatus(nStatus);
			try (PrintWriter oOut = oRes.getWriter()) // always write the JSON response
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
			try (PrintWriter oOut = oRes.getWriter()) // write error messages to the JSON response
			{
				oResponse.write(oOut);
			}
		}
	}
	
	/**
	 * Adds the algorithm parameters to the JSON response
	 * 
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int getParameters(JSONObject oResponse)
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
	
	/**
	 * Reads the parameters from the HTTP request and saves them to disk and updates
	 * the values in memory
	 * 
	 * @param oReq object that contains all of the HTTP request information
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int saveParameters(HttpServletRequest oReq, JSONObject oResponse)
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
					oOut.append(String.format("%s,%1.3f\n", PARAMETER_KEYS[nIndex], dNewParas[nIndex])); // update values on disk
					m_oParameters.put(PARAMETER_KEYS[nIndex], dNewParas[nIndex]); // and in memory
				}
			}
		}
		return HttpServletResponse.SC_OK;
	}
	
	/**
	 * Adds status values to the JSON response and updates what users are trying to
	 * use the web application. Status values include the number of users, time of
	 * the next run of the algorithm, time of the most recent run of the algorithm,
	 * status of the application, description of the status, and number of controls
	 * made in the last run of the algorithm.
	 * 
	 * @param oSession object that contain information about the session making the
	 * HTTP request
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int status(Session oSession, JSONObject oResponse)
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
			if (nUsers == 1)
				m_sCurrentUser = oSession.getUserName();
		}
		if (m_sCurrentUser.compareTo(oSession.getUserName()) == 0) // the web app doesn't work if the number of users is greater than one so if the user is the first user to start using the application force the nubmer to 1 regardless of others trying to use it
			oResponse.put("users", 1);
		else
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
		getFiles(oResponse);
		
		return HttpServletResponse.SC_OK;
	}
	
	/**
	 * Attempts to start the simulation of the IHP2 Speed Harmonization Algorithm
	 * 
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int startSim(JSONObject oResponse)
		throws Exception
	{
		synchronized (this)
		{
			if (m_oTimer != null) // if the Timer isn't null the simulation is arleady running
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
			m_sCurrentFile = oFileFormat.format(lNext); // set the log file name for this run of the simulation
			LOCK.lock();
			try // reset the necessary objects to start the simulation from scratch
			{
				m_nTriggers = 0;
				for (Corridor oCorridor : m_oCorridors)
				{
					for (Subsegment oSubsegment : oCorridor)
					{
						oSubsegment.m_dPreviousAdvisorySpeed = oSubsegment.m_dAdvisorySpeed = oSubsegment.m_nMaxSpeed;
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
	
	/**
	 * Stops the simulation of the IHP2 Speed Harmonization Algorithm if it is 
	 * currently running.
	 * 
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int stopSim(JSONObject oResponse)
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
	
	/**
	 * Parses and saves the geometry of a Speed Harmonization Corridor coming from
	 * the web application and then loads its into memory.
	 * 
	 * @param oReq object that contains all of the HTTP request information
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int saveGeo(HttpServletRequest oReq)
		throws Exception
	{
		JSONArray oFeatures = new JSONArray(oReq.getParameter("geo")); // geojson feature collection
		double dLaneWidth = Double.parseDouble(oReq.getParameter("width"));
		int nLanes = (int)Double.parseDouble(oReq.getParameter("lanes"));
		double dWidth = dLaneWidth * nLanes;
		
		StringBuilder sBuf = new StringBuilder();
		DecimalFormat oOrdDf = new DecimalFormat("#.#######");
		String sWidth = new DecimalFormat("#.##").format(dWidth);
		for (int nFeatureIndex = oFeatures.length() - 1; nFeatureIndex >= 0; nFeatureIndex--) // save in reverse order so that the furthest downstream subsegment is the first one written to file
		{
			JSONArray oCoords = oFeatures.getJSONObject(nFeatureIndex).getJSONObject("geometry").getJSONArray("coordinates");
			for (int nCoordIndex = 0; nCoordIndex < oCoords.length(); nCoordIndex++)
			{
				JSONArray oPt = oCoords.getJSONArray(nCoordIndex);
				sBuf.append(oOrdDf.format(oPt.get(0))).append(',').append(oOrdDf.format(oPt.get(1))).append(',').append(sWidth).append(',');
			}
			sBuf.setLength(sBuf.length() - 1); // remove trailing comma
			sBuf.append('\n');
		}
		
		LOCK.lock(); // lock for async requests
		try
		{
			try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sCorridorFile), FileUtil.WRITE, FileUtil.FILEPERS), StandardCharsets.UTF_8))) // save to disk
			{
				oOut.append(sBuf);
			}
			
			Files.deleteIfExists(Paths.get(m_sDetectorFile)); // remove stale detector file
			readCorridors(m_sCorridorFile); // load the new corridor into memory
		}
		finally
		{
			LOCK.unlock();
		}
		return HttpServletResponse.SC_OK;
	}
	
	/**
	 * Adds the geometry of the Speed Harmonization Corridor to the JSON response.
	 * 
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 */
	private int getGeo(JSONObject oResponse)
	{
		JSONArray oFeatures = new JSONArray(); // features array that will be part of a geojson FeatureCollection
		LOCK.lock(); // lock for async requests
		try
		{
			for (Corridor oCorridor : m_oCorridors)
			{
				for (Subsegment oSubsegment : oCorridor)
				{
					JSONObject oFeature = new JSONObject(); // geojson feature
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
	
	/**
	 * Saves the detector values of the subsegments of the Speed Harmonization 
	 * Corridor from the web application request to a files and updates them in memory.
	 * 
	 * @param oReq object that contains all of the HTTP request information
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int updateDetectors(HttpServletRequest oReq)
		throws Exception
	{
		JSONArray oAllDetectors = new JSONArray(oReq.getParameter("detectors"));
		LOCK.lock(); // lock for async requests
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
	
	/**
	 * Adds the detector values of the subsegments of the Speed Harmonization 
	 * Corridor to the JSON response.
	 * 
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int getDetectors(JSONObject oResponse)
		throws Exception
	{
		LOCK.lock(); // lock for async requests
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
	
	/**
	 * Add a list of log files available to download grouped by month and then day
	 * and sorted so that the most recent files are at the beginning of the list.
	 * 
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int getFiles(JSONObject oResponse)
		throws Exception
	{
		Path oBaseDir = Paths.get(m_sBaseDir);
		List<Path> oMonths = Files.walk(oBaseDir, 1).filter(Files::isDirectory).filter(oP -> 
		{
			String sPath = oP.toString();
			if (!sPath.endsWith("/"))
				sPath += "/";
			return m_sBaseDir.compareTo(sPath) != 0;
		}).sorted().collect(Collectors.toList()); // get list of month directories
		
		JSONObject oFiles = new JSONObject();
		for (Path oMonth : oMonths)
		{
			JSONObject oMonthJson = new JSONObject();
			List<Path> oDays = Files.walk(oMonth, 1).filter(Files::isDirectory).filter(oP -> oP.toString().compareTo(oMonth.toString()) != 0).sorted(Comparator.reverseOrder()).collect(Collectors.toList()); // get list of day directories
			for (Path oDay : oDays)
			{
				JSONArray oDayJson = new JSONArray();
				List<Path> oRuns = Files.walk(oDay, 1).filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).collect(Collectors.toList()); // get the files for that day
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
	
	/**
	 * Adds the requested log file to the JSON response.
	 * 
	 * @param oReq object that contains all of the HTTP request information
	 * @param oResponse JSON object that gets returned to the web application
	 * @return HTTP status code
	 * @throws Exception
	 */
	private int getFile(HttpServletRequest oReq, JSONObject oResponse)
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
	
	/**
	 * Executes one run of the IHP2 Speed Harmonization Algorithm.
	 * 
	 * @param oCorridors List that contains the Corridors to run the algorithm on
	 * @param oParameters HashMap containing all of the algorithm parameters
	 * @param bSimulation Flag telling if the algorithm is running in simulation mode 
	 * or using live data
	 * @return A list containing new traffic controls generated from the IHP2 Speed
	 * Algorithm
	 * @throws IOException
	 */
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
		Units oUnits = Units.getInstance();
		double dBaselineFlow = oUnits.convert("veh/hr/lane", "veh/s/lane", oParameters.get("baseline_flow").doubleValue());
		StringBuilder sLogBuf = new StringBuilder();
		sLogBuf.append(oSdf.format(lNow)).append('\n');
		sLogBuf.append("space factor,").append(oDf.format(dSpaceFactor)).append('\n');
		sLogBuf.append("ratio of reduced speed,").append(oDf.format(dReducedSpeedRatio)).append('\n');
		sLogBuf.append("number of consecutive reduced speed,").append(nConsecutiveTrigger).append('\n');
		sLogBuf.append("time interval,").append(nTimeInterval).append('\n');
		sLogBuf.append("maximal gap,").append(oDf.format(dMaximalGap)).append('\n');
		sLogBuf.append("baseline traffic flow,").append(dBaselineFlow).append('\n');
		sLogBuf.append("input json,\"").append(m_oCurrentDetectors.toString()).append("\"\n");
		if (!bSimulation) // if data was received from live source
		{
			for (Corridor oCorridor : oCorridors)
			{
				for (Subsegment oSubsegment : oCorridor)
				{
					for (Detector oDetector : oSubsegment) // calculate the traffic parameters using the detectors inside of the subsegment
					{
						oSubsegment.m_dDensity += oDetector.m_dDensity;
						oSubsegment.m_dVolume += oDetector.m_nVolume;
						Iterator<double[]> oIt = Arrays.iterator(oDetector.m_dSpeeds, new double[1], 1, 1);
						while (oIt.hasNext())
							oSubsegment.m_dSpeeds = Arrays.add(oSubsegment.m_dSpeeds, oIt.next()[0]);
					}
					oSubsegment.m_dDensity /= oSubsegment.size(); // average the density

					oSubsegment.generateStats();
					double dX = Mercator.lonToMeters(oSubsegment.get(0).m_dLon);
					double dY = Mercator.latToMeters(oSubsegment.get(0).m_dLat);
					oSubsegment.m_nMaxSpeed = Integer.MIN_VALUE;
					oM.metersToTile(dX, dY, ProcCtrl.g_nDefaultZoom, nTiles);
					Path oIndexFile = Paths.get(String.format(ProcCtrl.g_sTdFileFormat, nTiles[0], ProcCtrl.g_nDefaultZoom, nTiles[0], nTiles[1]) + ".ndx");

					try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile)))) // check the index file to file speed limit controls
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
									dPolygon = Arrays.add(dPolygon, oCtrl.m_oFullGeo.m_dPT[1], oCtrl.m_oFullGeo.m_dPT[2]); // close the polygon
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
		
		
		String[] sUnits = TrafCtrlEnums.UNITS[TrafCtrlEnums.getCtrl("maxspeed")];
		
		for (Corridor oCorridor : oCorridors) // for each corridor run the algorithm
		{
			int n = oCorridor.size(); // number of subsegments
			Subsegment oReducedSpeedZone = oCorridor.get(0);
			Subsegment oSubSegOne = oCorridor.get(1);

			if (oSubSegOne.m_d85th < dReducedSpeedRatio * oSubSegOne.m_nMaxSpeed) // determine if the speed is lower enough to run the algorithm
			{
				++m_nTriggers; // increase consecutive trigger count
				sLogBuf.append("Consecutive reduced speed triggers,").append(m_nTriggers).append('\n');
				double dControlFlowRate = dSpaceFactor * oReducedSpeedZone.m_dVolume + (1 - dSpaceFactor) * oSubSegOne.m_dVolume; // equation 1
				sLogBuf.append("Equation 1,").append(oDf.format(dControlFlowRate)).append('\n');
				double dTargetDensity = oSubSegOne.m_dPreviousDensity + (oReducedSpeedZone.m_dVolume - oSubSegOne.m_dVolume) / oSubSegOne.m_dLength * nTimeInterval; // equation 2
				sLogBuf.append("Equation 2,").append(oDf.format(dTargetDensity)).append('\n');
				double dTargetSpeed = Math.max(Math.min(dControlFlowRate / dTargetDensity, oSubSegOne.m_d85th), oSubSegOne.m_d15th); // equation 3
				sLogBuf.append("Equation 3,").append(oDf.format(dTargetSpeed)).append('\n');
				for (int i = 2; i < n; i++)
				{
					Subsegment oUpstream = oCorridor.get(i);
					oUpstream.m_dTarCtrlSpeed = dTargetSpeed + ((oUpstream.m_d85th - dTargetSpeed) / (n - 1)) * (i - 1); // equation 4
					sLogBuf.append("Equation 4,subsegment ").append(i).append(',').append(oDf.format(oUpstream.m_dTarCtrlSpeed)).append('\n');
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
					sLogBuf.append("Equation 8,subsegment ").append(i).append(',').append(oDf.format(oUpstream.m_dAdvisorySpeed)).append('\n');
					if (m_nTriggers >= nConsecutiveTrigger)
					{
						int nSpd = (int)Math.round(oUnits.convert("m/s", sUnits[0], oUpstream.m_dAdvisorySpeed));
						TrafCtrl oSpeed = new TrafCtrl("maxspeed", nSpd, null, lNow, lNow, oUpstream.m_dCenterLine, String.format("Sub %d - Speed Harm", i), false, ProcCtrl.IHP2);
						oSpeed.m_lEnd = lNow + nTimeInterval * 1000; // timeout new controls after one time interval
						oUpstream.m_oCtrl = oSpeed;
						sLogBuf.append("new maxspeed,subsegment ").append(i).append(',').append(oDf.format(oUnits.convert(sUnits[0], "mph", nSpd))).append(" mph").append('\n');
					}
				}
			}
			else // speed is not low enough to trigger the algorithm
			{
				m_nTriggers = 0; // reset consecutive trigger count
				sLogBuf.append("Speed greater than reduced ratio\n");
				for (int i = 2; i < n; i++)
				{
					Subsegment oUpstream = oCorridor.get(i);
					oUpstream.m_dPreviousAdvisorySpeed = oUpstream.m_nMaxSpeed;
				}
			}
		}
		
		for (Corridor oCorridor : oCorridors) // add any controls that have been generated for each subsegment of each corridor
		{
			for (Subsegment oSubsegment : oCorridor)
			{
				if (oSubsegment.m_oCtrl != null)
					oCtrls.add(oSubsegment.m_oCtrl);
			}
		}
		
		sLogBuf.append('\n').append('\n');
		Path oPath = Paths.get(m_sCurrentFile);
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.APPENDTO, FileUtil.FILEPERS), StandardCharsets.UTF_8))) // append to the log file
		{
			oOut.append(sLogBuf);
		}
		
		return oCtrls;
	}
}
