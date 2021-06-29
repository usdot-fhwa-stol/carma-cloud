/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.util.CsvReader;
import cc.util.Text;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author Federal Highway Administration
 */
public class RopMgr extends Handler implements Runnable
{
	protected String m_sRopFile;
	protected ArrayList<String[]> m_oRops = new ArrayList();
	
	
	@Override
	public void init()
	{
		String sRopFile = getServletConfig().getInitParameter("ropfile");
		if (sRopFile != null && sRopFile.length() > 0)
		{
			m_sRopFile = sRopFile;
			new Thread(this).start();
		}
	}


	@Override
	public void run()
	{
		try (CsvReader oIn = new CsvReader(new FileInputStream(m_sRopFile), '\t'))
		{
			int nCols;
			String[] sSearch = new String[1];
			synchronized(m_oRops)
			{
				while ((nCols = oIn.readLine()) > 0)
				{
					sSearch[0] = oIn.parseString(0);
					int nIndex = Collections.binarySearch(m_oRops, sSearch, STR_ARR_COMP); // search for id in list
					if (nIndex < 0)
					{
						nIndex = ~nIndex;
						m_oRops.add(nIndex, new String[nCols]); // right now we have uuid,user,timestamp,status,JSON
					}
					update(m_oRops.get(nIndex), nCols, oIn); // replace with most recent update
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
		synchronized (m_oRops)
		{
			int nSize = m_oRops.size();
			if (nSize > 0)
			{
				String[] sRop = m_oRops.get(0);
				writeJson(oOut, sRop[0], sRop[sRop.length - 1]);
				for (int i = 1; i < nSize; i++)
				{
					sRop = m_oRops.get(i);
					oOut.write(",");
					writeJson(oOut, sRop[0], sRop[sRop.length - 1]); // {"uuid" : JSON, "uuid" : JSON, ...}
				}
			}
		}
		oOut.write("}");
	}
	
	
	protected void doSave(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	   throws IOException
	{
		String[] sRop;
		synchronized (m_oRops)
		{
			String[] sSearch = new String[1];
			sSearch[0] = oReq.getParameter("id");
			if (sSearch[0] == null || sSearch[0].length() == 0) // no id so create a new one
				sSearch[0] = Text.getUUID();

			StringBuilder sBuffer = new StringBuilder(oReq.getParameter("data"));
			int nBufferIndex = sBuffer.length();
			while (nBufferIndex-- > 0)
			{
				char cChar = sBuffer.charAt(nBufferIndex);
				if (cChar == '\t' || cChar == '\n')
					sBuffer.deleteCharAt(nBufferIndex);
			}
			if (sBuffer.indexOf("\"status\"") < 0) // add default status of unused to json
			{
				Text.removeWhitespace(sBuffer);
				sBuffer.insert(sBuffer.lastIndexOf("}"), ",\"status\":\"U\"");
			}

			int nIndex = Collections.binarySearch(m_oRops, sSearch, STR_ARR_COMP);
			if (nIndex < 0)
			{
				nIndex = ~nIndex;
				m_oRops.add(nIndex, new String[]{sSearch[0], oSess.m_oUser.m_sUser, null, null}); // create new array 
			}
			sRop = m_oRops.get(nIndex);
			synchronized (ISO8601Sdf)
			{
				sRop[2] = ISO8601Sdf.format(System.currentTimeMillis());
			}
			sRop[3] = sBuffer.toString();
			
			if (sRop[3].contains("\"status\":\"D\"")) // remove from list if the status is deleted
				m_oRops.remove(nIndex);

			try (BufferedWriter oFileOut = new BufferedWriter(new FileWriter(m_sRopFile, true)))
			{
				oFileOut.write(sRop[0]);
				for (int i = 1; i < sRop.length; i++)
				{
					oFileOut.write("\t");
					oFileOut.write(sRop[i]);
				}
				oFileOut.write("\n");
			}
		}
		oOut.write("{");
		writeJson(oOut, sRop[0], sRop[sRop.length - 1]); // id:data
		oOut.write("}");
	}
	
	
	protected void doDetails(Session oSess, HttpServletRequest oReq, PrintWriter oOut)
	   throws IOException
	{
		String sRopIds = oReq.getParameter("rops");
		if (sRopIds == null || m_oRops.isEmpty())
		{
			oOut.write("{}");
			return;
		}
		
		String[] sIds = sRopIds.split(",");
		oOut.write("{");
		String[] sSearch = new String[1];
		int nCount = 0;
		synchronized (m_oRops)
		{
			for (String sId : sIds)
			{
				sSearch[0] = sId;
				int nIndex = Collections.binarySearch(m_oRops, sSearch, STR_ARR_COMP);
				if (nIndex < 0)
					continue;
				
				String[] sRop = m_oRops.get(nIndex);
				if (nCount++ > 0)
					oOut.write(",");
				writeJson(oOut, sRop[0], sRop[sRop.length - 1]);
			}
		}
		oOut.write("}");
	}
}
