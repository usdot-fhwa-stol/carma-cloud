/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.rsm;

import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcClosed;
import cc.ctrl.proc.ProcCtrl;
import static cc.ctrl.proc.ProcCtrl.g_dExplodeStep;
import static cc.ctrl.proc.ProcCtrl.g_nDefaultZoom;
import static cc.ctrl.proc.ProcCtrl.g_sTrafCtrlDir;
import static cc.ctrl.proc.ProcCtrl.updateTiles;
import cc.ctrl.proc.ProcMaxSpeed;
import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/**
 *
 * @author aaron.cherney
 */
public class RsmParser extends DefaultHandler2
{
	private StringBuilder m_sBuf = new StringBuilder();
	private ArrayList<TrafCtrl> m_oCtrls = new ArrayList();
	private GregorianCalendar m_oStartDate;
	private GregorianCalendar m_oEndDate;
	private boolean m_bCommonContainer = false;
	private boolean m_bRszContainer = false;
	private boolean m_bRszRegion = false;
	private boolean m_bLaneClosureContainer = false;
	private boolean m_bClosureRegion = false;
	private boolean m_bEventRecurrence = false;
	private boolean m_bStartTime = false;
	private boolean m_bEndTime = false;
	private boolean m_bStartDate = false;
	private boolean m_bEndDate = false;
	private int m_nEventRecurrence = -1;
	private boolean[][] m_bDow = new boolean[5][]; // there can be 5 EventRecurrences
	private int[] m_nStartTimes = new int[5];
	private int[] m_nEndTimes = new int[5];
	private int[][] m_nRszPaths = new int[10][]; // there can be 10 paths in a PathList
	private int[] m_nRszPathWidths = new int[10];
	private int m_nRszPathIndex = -1;
	private int[][] m_nLaneClosurePaths = new int[10][]; // there can be 10 paths in a PathList
	private int[] m_nLaneClosurePathWidths = new int[10];
	private int m_nLaneClosurePathIndex = -1;
	private int m_nLaneStatusIndex = -2;
	private int[] m_nLaneStatus = new int[20]; // there can be 10 lane statuses, store in pairs the lane position and whether or not it is closed (0 = open, 1 = closed)
	private GregorianCalendar[] m_oStartDates = new GregorianCalendar[5];
	private GregorianCalendar[] m_oEndDates = new GregorianCalendar[5];
	
	private int m_nDowIndex = 0;
	private int m_nSpeedLimit = Integer.MIN_VALUE;
	private String m_sSpeedLimitType;
	
	private int[] m_nLonLatAdjustment;
	public RsmParser(int[] nAdjusts)
	{
		super();
		m_oStartDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		m_oEndDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		m_nLonLatAdjustment = nAdjusts;
	}
	
	@Override
	public void characters(char[] cBuf, int nPos, int nLen)
	{
		m_sBuf.setLength(0);
		m_sBuf.append(cBuf, nPos, nLen);
	}
	
	@Override
 	public void startElement(String sUri, String sLocalName, 
		String sQname, Attributes iAtt)
	   throws SAXException
	{
		if (sQname.compareTo("commonContainer") == 0)
		{
			m_bCommonContainer = true;
			return;
		}
		else if (sQname.compareTo("rszContainer") == 0)
		{
			m_bRszContainer = true;
			return;
		}
		else if (sQname.compareTo("laneClosureContainer") == 0)
		{
			m_bLaneClosureContainer = true;
			return;
		}
		else if (sQname.compareTo("EventRecurrence") == 0)
		{
			++m_nEventRecurrence;
			m_bEventRecurrence = true;
			m_bDow[m_nEventRecurrence] = new boolean[7];
			m_oStartDates[m_nEventRecurrence] = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			m_oEndDates[m_nEventRecurrence] = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			return;
		}
		else if (sQname.compareTo("rszRegion") == 0)
		{
			m_bRszRegion = true;
			return;
		}
		else if (sQname.compareTo("closureRegion") == 0)
		{
			m_bClosureRegion = true;
			return;
		}
		
		if (m_bCommonContainer)
		{
			if (m_bEventRecurrence)
			{
				if (sQname.compareTo("monday") == 0)
					m_nDowIndex = 0;
				else if (sQname.compareTo("tuesday") == 0)
					m_nDowIndex = 1;
				else if (sQname.compareTo("wednesday") == 0)
					m_nDowIndex = 2;
				else if (sQname.compareTo("thursday") == 0)
					m_nDowIndex = 3;
				else if (sQname.compareTo("friday") == 0)
					m_nDowIndex = 4;
				else if (sQname.compareTo("saturday") == 0)
					m_nDowIndex = 5;
				else if (sQname.compareTo("sunday") == 0)
					m_nDowIndex = 6;
				else if (sQname.compareTo("true") == 0)
					m_bDow[m_nEventRecurrence][m_nDowIndex] = true;
				else if (sQname.compareTo("false") == 0)
					m_bDow[m_nEventRecurrence][m_nDowIndex] = false;
				else if (sQname.compareTo("startTime") == 0)
					m_bStartTime = true;
				else if (sQname.compareTo("endTime") == 0)
					m_bEndTime = true;
				else if (sQname.compareTo("startDate") == 0)
					m_bStartDate = true;
				else if (sQname.compareTo("endDate") == 0)
					m_bEndDate = true;
			}
			else
			{
				if (sQname.compareTo("startDateTime") == 0)
					m_bStartTime = true;
				else if (sQname.compareTo("endDateTime") == 0)
					m_bEndTime = true;
			}
			
			
		}
		
		if (m_bRszContainer)
		{
			if (m_bRszRegion)
			{
				if (sQname.compareTo("path") == 0)
				{
					m_nRszPaths[++m_nRszPathIndex] = Arrays.newIntArray();
				}
			}
		}
		
		if (m_bLaneClosureContainer)
		{
			if (m_bClosureRegion)
			{
				if (sQname.compareTo("path") == 0)
				{
					m_nLaneClosurePaths[++m_nLaneClosurePathIndex] = Arrays.newIntArray();
				}
			}
			else
			{
				if (sQname.compareTo("laneStatus") == 0)
				{
					++m_nLaneStatusIndex;
				}
			}
		}
	}
	
	@Override
 	public void endElement(String sUri, String sLocalName, String sQname)
	   throws SAXException
	{
		if (sQname.compareTo("commonContainer") == 0)
		{
			m_bCommonContainer = false;
			return;
		}
		else if (sQname.compareTo("rszContainer") == 0)
		{
			m_bRszContainer = false;
			return;
		}
		else if (sQname.compareTo("laneClosureContainer") == 0)
		{
			m_bLaneClosureContainer = false;
			return;
		}
		else if (sQname.compareTo("EventRecurrence") == 0)
		{
			m_bEventRecurrence = false;
			return;
		}
		else if (sQname.compareTo("rszRegion") == 0)
		{
			m_bRszRegion = false;
			return;
		}
		else if (sQname.compareTo("closureRegion") == 0)
		{
			m_bClosureRegion = false;
			return;
		}
		
		if (m_bCommonContainer)
		{
			if (sQname.compareTo("startDateTime") == 0)
				m_bStartTime = false;
			else if (sQname.compareTo("endDateTime") == 0)
				m_bEndTime = false;
			
			if (m_bEventRecurrence)
			{
				if (sQname.compareTo("startTime") == 0)
					m_bStartTime = false;
				else if (sQname.compareTo("endTime") == 0)
					m_bEndTime = false;
				else if (sQname.compareTo("startDate") == 0)
					m_bStartDate = false;
				else if (sQname.compareTo("endDate") == 0)
					m_bEndDate = false;
				else if (sQname.compareTo("hour") == 0)
				{
					if (m_bStartTime)
						m_nStartTimes[m_nEventRecurrence] += Integer.parseInt(m_sBuf.toString()) * 3600;
					else if (m_bEndTime);
						m_nEndTimes[m_nEventRecurrence] += Integer.parseInt(m_sBuf.toString()) * 3600;
				}
				else if (sQname.compareTo("minute") == 0)
				{
					if (m_bStartTime)
						m_nStartTimes[m_nEventRecurrence] += Integer.parseInt(m_sBuf.toString()) * 60;
					else if (m_bEndTime);
						m_nEndTimes[m_nEventRecurrence] += Integer.parseInt(m_sBuf.toString()) * 60;
				}
				else if (sQname.compareTo("second") == 0)
				{
					if (m_bStartTime)
						m_nStartTimes[m_nEventRecurrence] += Integer.parseInt(m_sBuf.toString());
					else if (m_bEndTime);
						m_nEndTimes[m_nEventRecurrence] += Integer.parseInt(m_sBuf.toString());
				}	
			}
			else
			{
				if (sQname.compareTo("year") == 0)
				{
					if (m_bStartTime)
						m_oStartDate.set(Calendar.YEAR, Integer.parseInt(m_sBuf.toString()));
					else if (m_bEndTime)
						m_oEndDate.set(Calendar.YEAR, Integer.parseInt(m_sBuf.toString()));
				}
				else if (sQname.compareTo("month") == 0)
				{
					if (m_bStartTime)
						m_oStartDate.set(Calendar.MONTH, Integer.parseInt(m_sBuf.toString()) - 1); // months are zero based
					else if (m_bEndTime)
						m_oEndDate.set(Calendar.MONTH, Integer.parseInt(m_sBuf.toString()) - 1); // months are zero based
				}
				else if (sQname.compareTo("day") == 0)
				{
					if (m_bStartTime)
						m_oStartDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m_sBuf.toString()));
					else if (m_bEndTime)
						m_oEndDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m_sBuf.toString()));
				}
				else if (sQname.compareTo("hour") == 0)
				{
					if (m_bStartTime)
						m_oStartDate.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m_sBuf.toString()));
					else if (m_bEndTime)
						m_oEndDate.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m_sBuf.toString()));
				}
				else if (sQname.compareTo("minute") == 0)
				{
					if (m_bStartTime)
						m_oStartDate.set(Calendar.MINUTE, Integer.parseInt(m_sBuf.toString()));
					else if (m_bEndTime)
						m_oEndDate.set(Calendar.MINUTE, Integer.parseInt(m_sBuf.toString()));
				}
				else if (sQname.compareTo("offset") == 0)
				{
					if (m_bStartTime)
						m_oStartDate.setTimeZone(new SimpleTimeZone(Integer.parseInt(m_sBuf.toString()) * 60 * 1000, "")); // convert minute offset to milliseconds for timezone constructor
					else if (m_bEndTime)
						m_oEndDate.setTimeZone(new SimpleTimeZone(Integer.parseInt(m_sBuf.toString()) * 60 * 1000, "")); // convert minute offset to milliseconds for timezone constructor
				}
			}
		}
		
		if (m_bRszContainer)
		{
			if (m_bRszRegion)
			{
				if (sQname.compareTo("lat") == 0)
				{
					int nLat = Integer.parseInt(m_sBuf.toString()) + m_nLonLatAdjustment[1];
					m_nRszPaths[m_nRszPathIndex] = Arrays.add(m_nRszPaths[m_nRszPathIndex], nLat);
				}
				else if (sQname.compareTo("long") == 0)
				{
					int nLon = Integer.parseInt(m_sBuf.toString()) + m_nLonLatAdjustment[0];
					m_nRszPaths[m_nRszPathIndex] = Arrays.add(m_nRszPaths[m_nRszPathIndex], nLon);
				}
				else if (sQname.compareTo("pathWidth") == 0)
					m_nRszPathWidths[m_nRszPathIndex] = Integer.parseInt(m_sBuf.toString());
			}
			else
			{
				if (sQname.compareTo("speed") == 0)
					m_nSpeedLimit = Integer.parseInt(m_sBuf.toString());
				else if (sQname.compareTo("vehicleMaxSpeed") == 0)
					m_sSpeedLimitType = "vehicleMaxSpeed";
				else if (sQname.compareTo("unknown") == 0)
					m_sSpeedLimitType = "unknown";
				else if (sQname.compareTo("maxSpeedInSchoolZone") == 0)
					m_sSpeedLimitType = "maxSpeedInSchoolZone";
				else if (sQname.compareTo("maxSpeedInSchoolZoneWhenChildrenArePresent") == 0)
					m_sSpeedLimitType = "maxSpeedInSchoolZoneWhenChildrenArePresent";
				else if (sQname.compareTo("maxSpeedInConstructionZone") == 0)
					m_sSpeedLimitType = "maxSpeedInConstructionZone";
				else if (sQname.compareTo("vehicleMinSpeed") == 0)
					m_sSpeedLimitType = "vehicleMinSpeed";
				else if (sQname.compareTo("vehicleNightMaxSpeed") == 0)
					m_sSpeedLimitType = "vehicleNightMaxSpeed";
				else if (sQname.compareTo("truckMinSpeed") == 0)
					m_sSpeedLimitType = "truckMinSpeed";
				else if (sQname.compareTo("truckMaxSpeed") == 0)
					m_sSpeedLimitType = "truckMaxSpeed";
				else if (sQname.compareTo("truckNightMaxSpeed") == 0)
					m_sSpeedLimitType = "truckNightMaxSpeed";
				else if (sQname.compareTo("vehiclesWithTrailerMinSpeed") == 0)
					m_sSpeedLimitType = "vehiclesWithTrailerMinSpeed";
				else if (sQname.compareTo("vehiclesWithTrailerMaxSpeed") == 0)
					m_sSpeedLimitType = "vehiclesWithTrailerMaxSpeed";
				else if (sQname.compareTo("vehiclesWithTrailerNightMaxSpeed") == 0)
					m_sSpeedLimitType = "vehiclesWithTrailerNightMaxSpeed";
			}
		}
		
		if (m_bLaneClosureContainer)
		{
			if (m_bClosureRegion)
			{
				if (sQname.compareTo("lat") == 0)
				{
					int nLat = Integer.parseInt(m_sBuf.toString()) + m_nLonLatAdjustment[1];
					m_nLaneClosurePaths[m_nLaneClosurePathIndex] = Arrays.add(m_nLaneClosurePaths[m_nLaneClosurePathIndex], nLat);
				}
				else if (sQname.compareTo("long") == 0)
				{
					int nLon = Integer.parseInt(m_sBuf.toString()) + m_nLonLatAdjustment[1];
					m_nLaneClosurePaths[m_nLaneClosurePathIndex] = Arrays.add(m_nLaneClosurePaths[m_nLaneClosurePathIndex], nLon);
				}					
				else if (sQname.compareTo("pathWidth") == 0)
					m_nLaneClosurePathWidths[m_nLaneClosurePathIndex] = Integer.parseInt(m_sBuf.toString());
			}
			else
			{
				if (sQname.compareTo("lanePosition") == 0)
					m_nLaneStatus[m_nLaneStatusIndex] = Integer.parseInt(m_sBuf.toString());
				else if (sQname.compareTo("True") == 0)
					m_nLaneStatus[m_nLaneStatusIndex + 1] = 1;
				else if (sQname.compareTo("False") == 0)
					m_nLaneStatus[m_nLaneStatusIndex + 1] = 0;
			}
			
		}
	}
	
	
	public ArrayList<TrafCtrl> parseRequest(InputStream oIn)
	   throws Exception
	{
		XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.parse(new InputSource(oIn));
		
		ArrayList<TrafCtrl> oReturn = new ArrayList();
		Proj oProj = new Proj("epsg:4326", "epsg:3785"); // convert geo coordinates to spherical mercator
		double[] dPoint = new double[2];
		ArrayList<TrafCtrl> oCtrls = new ArrayList(m_oCtrls.size());
		ArrayList<int[]> oTiles = new ArrayList();
		if (m_nSpeedLimit != Integer.MIN_VALUE)
		{
			for (int nRszPath = 0; nRszPath <= m_nRszPathIndex; nRszPath++)
			{
				int[] nPath = m_nRszPaths[nRszPath];
				double dWidth = m_nRszPathWidths[nRszPath] / 100.0; // convert cm to meters
				int nSize = Arrays.size(nPath);
				double[] dLineArcs = Arrays.newDoubleArray((int)(nSize / 2.0 * 3) + 4);
				if (nSize < 5) // not valid geometry
					continue;
				dLineArcs = Arrays.add(dLineArcs, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});	
				if (nSize == 5)
				{
					oProj.cs2cs(Geo.fromIntDeg(nPath[2]), Geo.fromIntDeg(nPath[1]), dPoint);
					dLineArcs = Arrays.addAndUpdate(dLineArcs, dPoint[0], dPoint[1]);
					dLineArcs = Arrays.add(dLineArcs, dWidth);
					oProj.cs2cs(Geo.fromIntDeg(nPath[4]), Geo.fromIntDeg(nPath[3]), dPoint);
					dLineArcs = Arrays.addAndUpdate(dLineArcs, (dLineArcs[5] + dPoint[0]) / 2, (dLineArcs[6] + dPoint[1]) / 2);
					dLineArcs = Arrays.add(dLineArcs, dWidth);
					dLineArcs = Arrays.addAndUpdate(dLineArcs, dPoint[0], dPoint[1]);
					dLineArcs = Arrays.add(dLineArcs, dWidth);
				}
				else
				{
					Iterator<int[]> oIt = Arrays.iterator(nPath, new int[2], 1, 2);
					while (oIt.hasNext())
					{
						int[] nPathPt = oIt.next();
						oProj.cs2cs(Geo.fromIntDeg(nPathPt[1]), Geo.fromIntDeg(nPathPt[0]), dPoint);
						dLineArcs = Arrays.addAndUpdate(dLineArcs, dPoint[0], dPoint[1]);
						dLineArcs = Arrays.add(dLineArcs, dWidth);
					}
				}
				TrafCtrl oCtrl = new TrafCtrl("maxspeed", m_nSpeedLimit, null, System.currentTimeMillis(), m_oStartDate.getTimeInMillis(), dLineArcs, "rsm", true, ProcCtrl.RSM);
				oCtrls.add(oCtrl);
				oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom, ProcCtrl.RSM);
				updateTiles(oTiles, oCtrl.m_oFullGeo.m_oTiles);
			}
			ProcMaxSpeed.renderTiledData(oCtrls, oTiles);
			oReturn.addAll(oCtrls);
		}
		oCtrls.clear();
		oTiles.clear();
		for (int nLaneClosurePath = 0; nLaneClosurePath <= m_nLaneClosurePathIndex; nLaneClosurePath++)
		{
			int nStatus = m_nLaneStatus[nLaneClosurePath * 2 + 1];
			if (nStatus == 0) // lane is open, don't need a control
				continue;
			int[] nPath = m_nLaneClosurePaths[nLaneClosurePath];
			double dWidth = m_nLaneClosurePathWidths[nLaneClosurePath] / 100.0;
			int nSize = Arrays.size(nPath);
			if (nSize < 5) // not valid geometry
				continue;		
			double[] dLineArcs = Arrays.newDoubleArray((int)(nSize / 2.0 * 3) + 4);
			dLineArcs = Arrays.add(dLineArcs, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});	
			if (nSize == 5)
			{
				oProj.cs2cs(Geo.fromIntDeg(nPath[2]), Geo.fromIntDeg(nPath[1]), dPoint);
				dLineArcs = Arrays.addAndUpdate(dLineArcs, dPoint[0], dPoint[1]);
				dLineArcs = Arrays.add(dLineArcs, dWidth);
				oProj.cs2cs(Geo.fromIntDeg(nPath[4]), Geo.fromIntDeg(nPath[3]), dPoint);
				dLineArcs = Arrays.addAndUpdate(dLineArcs, (dLineArcs[5] + dPoint[0]) / 2, (dLineArcs[6] + dPoint[1]) / 2);
				dLineArcs = Arrays.add(dLineArcs, dWidth);
				dLineArcs = Arrays.addAndUpdate(dLineArcs, dPoint[0], dPoint[1]);
				dLineArcs = Arrays.add(dLineArcs, dWidth);
			}
			else
			{
				Iterator<int[]> oIt = Arrays.iterator(nPath, new int[2], 1, 2);
				while (oIt.hasNext())
				{
					int[] nPathPt = oIt.next();
					oProj.cs2cs(Geo.fromIntDeg(nPathPt[1]), Geo.fromIntDeg(nPathPt[0]), dPoint);
					dLineArcs = Arrays.addAndUpdate(dLineArcs, dPoint[0], dPoint[1]);
					dLineArcs = Arrays.add(dLineArcs, dWidth);
				}
			}
			TrafCtrl oCtrl = new TrafCtrl("closed", TrafCtrlEnums.getCtrlVal("closed", "notopen"), null, System.currentTimeMillis(), m_oStartDate.getTimeInMillis(), dLineArcs, "rsm", true, ProcCtrl.RSM);
			oCtrls.add(oCtrl);
			oCtrl.write(g_sTrafCtrlDir, g_dExplodeStep, g_nDefaultZoom, ProcCtrl.RSM);
			updateTiles(oTiles, oCtrl.m_oFullGeo.m_oTiles);
		}
		ProcClosed.renderTiledData(oCtrls, oTiles);
		oReturn.addAll(oCtrls);
		return oReturn;
	}
	
	
//	public static void main(String[] sArgs)
//		throws Exception
//	{
//		new RsmParser().parseRequest(new BufferedInputStream(Files.newInputStream(Paths.get("C:/Users/aaron.cherney/Documents/cc/rsms/rsm-xml--accuracy-test-1--prairie-center-cir--1-of-3.xml"))));
//	}
}
