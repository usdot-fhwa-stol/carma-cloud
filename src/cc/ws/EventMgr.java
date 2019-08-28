package cc.ws;

import cc.util.Arrays;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import cc.util.CsvReader;
import cc.util.Text;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;


public class EventMgr extends Handler implements Runnable
{
	private final ArrayList<String[]> m_oEvents = new ArrayList();
	private final HashMap<String, String> m_oTypes = new HashMap();
	private final HashMap<String, String> m_oLanes = new HashMap();
	private double m_dTol = 0.00001;

	String m_sEventFile; // path to file containing event detail data


	public EventMgr()
	{
		m_oTypes.put("1", "work zone");
		m_oTypes.put("2", "incident");

		m_oLanes.put("8193", "all");
		m_oLanes.put("8195", "left-lane");
		m_oLanes.put("8196", "right-lane");
		m_oLanes.put("81952", "left-2-lanes");
		m_oLanes.put("81953", "left-3-lanes");
		m_oLanes.put("81962", "right-2-lanes");
		m_oLanes.put("81963", "right-3-lanes");
		m_oLanes.put("8197", "center");
		m_oLanes.put("8198", "middle-lane");
		m_oLanes.put("8200", "right-turning-lane");
		m_oLanes.put("8201", "left-turning-lane");
		m_oLanes.put("8239", "right-exit-lane");
		m_oLanes.put("8240", "left-exit-lane");
		m_oLanes.put("8241", "right-merging-lane");
		m_oLanes.put("8242", "left-merging-lane");
		m_oLanes.put("8202", "right-exit-ramp");
		m_oLanes.put("8243", "right-second-exit-ramp");
		m_oLanes.put("8203", "right-entrance-ramp");
		m_oLanes.put("8245", "right-second-entrance-ramp");
		m_oLanes.put("8204", "left-exit-ramp");
		m_oLanes.put("8244", "left-second-exit-ramp");
		m_oLanes.put("8205", "left-entrance-ramp");
		m_oLanes.put("8246", "left-second-entrance-ramp");
		m_oLanes.put("82281", "sidewalk");
		m_oLanes.put("8228", "bike-lane");
		m_oLanes.put("0", "none");
		m_oLanes.put("8247", "unknown");
		m_oLanes.put("81980", "alternate-flow-lane");
		m_oLanes.put("81951", "shift-left");
		m_oLanes.put("81961", "shift-right");		
	}


	@Override
	public void init()
	{
		String sEventFile = getServletConfig().getInitParameter("eventfile");
		if (sEventFile != null && sEventFile.length() > 0)
		{
			m_sEventFile = sEventFile;
			new Thread(this).start();
		}
		String sTol = getServletConfig().getInitParameter("tol");
		if (sTol != null && sTol.length() > 0)
			m_dTol = Double.parseDouble(sTol);
	}


	@Override
	public void run()
	{
		try (CsvReader oIn = new CsvReader(new FileInputStream(m_sEventFile), '\t'))
		{
			int nCols;
			String[] sSearch = new String[1];
			synchronized(m_oEvents)
			{
				while ((nCols = oIn.readLine()) > 0)
				{
					sSearch[0] = oIn.parseString(0);
					int nIndex = Collections.binarySearch(m_oEvents, sSearch, STR_ARR_COMP); // search for id in list
					if (nIndex < 0)
					{
						nIndex = ~nIndex;
						m_oEvents.add(nIndex, new String[nCols]); // right now we have uuid,user,timestamp,status,JSON
					}
					update(m_oEvents.get(nIndex), nCols, oIn); // replace with most recent update
				}
			}
		}
		catch (Exception oEx)
		{
		}
	}

		
	protected void doNull(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	{
		oOut.write("{");
		synchronized(m_oEvents)
		{
			int nCount = 0;
			for (String[] sEvent : m_oEvents)
			{
				String sData = sEvent[sEvent.length - 1]; 
				int nStart = sData.indexOf("\"pts\":[");
				if (nStart < 0)
					continue;
				
				nStart += "\"pts\":[".length();
				int nEnd = sData.indexOf("]", nStart);
				String[] sOrdinates = sData.substring(nStart, nEnd).split(",");
				if (nCount++ > 0)
					oOut.write(",");
				
				oOut.write(String.format("\"%s\":[", sEvent[0]));

				int nSize = sOrdinates.length;
				if (nSize > 0)
				{
					oOut.write(sOrdinates[0]);
					for (int i = 1; i < nSize; i++)
					{
						oOut.write(",");
						oOut.write(sOrdinates[i]); // {"uuid" : pt array, "uuid" : pt array, ...}
					}
				}
				oOut.write("]");
			}
		}
		oOut.write("}");
	}
	
	
	protected void doList(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	{
		double dLat1;
		double dLat2;
		double dLon1;
		double dLon2;
		try
		{
			dLat1 = Double.parseDouble(oReq.getParameter("lat1")); // filter by selected point
			dLat2 = Double.parseDouble(oReq.getParameter("lat2"));
			if (dLat1 > dLat2)
			{
				double dTemp = dLat2;
				dLat2 = dLat1;
				dLat1 = dTemp;
			}

			dLon1 = Double.parseDouble(oReq.getParameter("lon1"));
			dLon2 = Double.parseDouble(oReq.getParameter("lon2"));
			if (dLon1 > dLon2)
			{
				double dTemp = dLon2;
				dLon2 = dLon1;
				dLon1 = dTemp;
			}
		}
		catch (Exception oEx)
		{
			oOut.write("{}"); // send empty object if error getting parameters
			return;
		}
		
		oOut.write("{");
		synchronized(m_oEvents)
		{
			double[] dPoints = Arrays.newDoubleArray();
			double[] dSeg = new double[4];
			int nCount = 0;
			for (String[] sEvent : m_oEvents)
			{
				dPoints[0] = 1;
				String sData = sEvent[sEvent.length - 1]; 
				int nStart = sData.indexOf("\"pts\":[");
				if (nStart < 0)
					continue;
				
				nStart += "\"pts\":[".length();
				int nEnd = sData.indexOf("]", nStart);
				String[] sOrdinates = sData.substring(nStart, nEnd).split(",");
				for (int i = 0; i < sOrdinates.length;)
				{
					dPoints = Arrays.add(dPoints, 
					   Math.round(Double.parseDouble(sOrdinates[i++]) * 10000000.0 + 0.000001) / 10000000.0,
					   Math.round(Double.parseDouble(sOrdinates[i++]) * 10000000.0 + 0.000001) / 10000000.0);
				}

				Iterator<double[]> oIt = Arrays.iterator(dPoints, dSeg, 1, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					double dSegLatMin = dSeg[0];
					double dSegLatMax = dSeg[2];
					double dSegLonMin = dSeg[1];
					double dSegLonMax = dSeg[3];
					
					if (dSegLatMin > dSegLatMax)
					{
						double dTemp = dSegLatMax;
						dSegLatMax = dSegLatMin;
						dSegLatMin = dTemp;
					}
					
					if (dSegLonMin > dSegLonMax)
					{
						double dTemp = dSegLonMax;
						dSegLonMax = dSegLonMin;
						dSegLonMin = dTemp;
					}
					
					dSegLatMin -= m_dTol;
					dSegLatMax += m_dTol;
					dSegLonMin -= m_dTol;
					dSegLonMax += m_dTol;
					
					if (dLon2 < dSegLonMin || dLon1 > dSegLonMax || 
						dLat2 < dSegLatMin || dLat1 > dSegLatMax)
						continue;
					
					if (nCount++ > 0)
						oOut.write(',');
					writeJson(oOut, sEvent[0], sData);
					
					break;
				}
			}
		}
		oOut.write("}");
	}
	
	
	protected void doDetail(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	{
		String sEventId = oReq.getParameter("id");
		if (sEventId == null || m_oEvents.isEmpty())
			return;

		synchronized(m_oEvents) // ensure any changes are committed
		{
			String[] sSearch = new String[]{sEventId};
			int nIndex = Collections.binarySearch(m_oEvents, sSearch, STR_ARR_COMP);
			if (nIndex < 0) // list only contains most recent updates
			{
				oOut.write("{}");
				return;
			}

			String[] sEvent = m_oEvents.get(nIndex);
			oOut.write("{");
			writeJson(oOut, sEvent[0], sEvent[sEvent.length - 1]);
			oOut.write("}");
		}
	}
	
	
	protected void doSave(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	   throws IOException
	{
		String sEventId = oReq.getParameter("id");
		if (sEventId == null || sEventId.length() == 0) // new event condition
			sEventId = Text.getUUID();

		synchronized(m_oEvents)
		{
			String sUsername = oSess.m_oUser.m_sUser; // set event parameters

			String[] sSearch = new String[]{sEventId};
			int nIndex = Collections.binarySearch(m_oEvents, sSearch, STR_ARR_COMP);
			if (nIndex < 0)
			{
				nIndex = ~nIndex;
				m_oEvents.add(nIndex, new String[]{sEventId, sUsername, null, oReq.getParameter("data")}); // insert new event
			}
			String[] sEvent = m_oEvents.get(nIndex);
			synchronized (ISO8601Sdf)
			{
				sEvent[2] = ISO8601Sdf.format(System.currentTimeMillis()); // set update time last
			}

			try (BufferedWriter oFileOut = new BufferedWriter(new FileWriter(m_sEventFile, true)))
			{
				oFileOut.write(sEvent[0]);
				for (int i = 1; i < sEvent.length; i++)
				{
					oFileOut.write("\t");
					oFileOut.write(sEvent[i]);
				}
				oFileOut.write("\n");
			}
			oOut.write("{");
			writeJson(oOut, sEvent[0], sEvent[sEvent.length - 1]); // id:data
			oOut.write("}");
		}
	}
	
	
	protected void doTypes(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	{
		oOut.write("{");
		if (m_oTypes.size() > 0)
		{
			StringBuilder sBuffer = new StringBuilder();
			m_oTypes.entrySet().forEach(oEntry -> sBuffer.append(String.format("\"%s\":\"%s\",", oEntry.getKey(), oEntry.getValue())));
			sBuffer.setLength(sBuffer.length() - 1); // remove last comma
			oOut.write(sBuffer.toString());
		}
		oOut.write("}");
	}
	
	
	protected void doLanes(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	{
		oOut.write("{");
		if (m_oLanes.size() > 0)
		{
			StringBuilder sBuffer = new StringBuilder();
			m_oLanes.entrySet().forEach(oEntry -> sBuffer.append(String.format("\"%s\":\"%s\",", oEntry.getKey(), oEntry.getValue())));
			sBuffer.setLength(sBuffer.length() - 1); // remove last comma
			oOut.write(sBuffer.toString());
		}
		oOut.write("}");
	}
}
