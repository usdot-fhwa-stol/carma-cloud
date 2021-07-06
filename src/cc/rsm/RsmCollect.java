/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.rsm;

import cc.util.CsvReader;
import cc.util.FileUtil;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author aaron.cherney
 */
public class RsmCollect extends TimerTask
{
	private String m_sToken = "";
	private String m_sUser;
	private String m_sPw;
	private String m_sBaseUrl = "https://wzdc-rest-api.azurewebsites.net";
	private String m_sOutputFile;
	private final String REQTOKEN = "/auth/token/";
	private final String RSMXML = "/rsm-xml";
	private final ArrayList<RsmRecord> m_oCurrentRsms = new ArrayList();
	private final int ENDTAG = 1;
	private final int VALUES = 2;
	private final AtomicBoolean m_bRunning = new AtomicBoolean();
	
	
	
	public RsmCollect(String sUser, String sPw, String sBaseUrl, String sOutputFile)
	{
		m_sUser = sUser;
		m_sPw = sPw;
		m_sBaseUrl = sBaseUrl;
		m_sOutputFile = sOutputFile;
		m_bRunning.set(false);
	}
	
	@Override
	public void run()
	{
		System.out.println("Collecting rsm");
		if (!m_bRunning.compareAndSet(false, true))
		{
			System.out.println(String.format("%d\tDid not run. Already running", System.currentTimeMillis()));
			return;
		}
		if (!xmlList(false))
			getToken();
		
		if (xmlList(true))
		{
			int nIndex = m_oCurrentRsms.size();
			while (nIndex-- > 0)
			{
				RsmRecord oRec = m_oCurrentRsms.get(nIndex);
				if (oRec.m_bNew)
					xmlFile(oRec.m_sFile);
				else if (!oRec.m_bCurrent)
				{
					m_oCurrentRsms.remove(nIndex);
					oRec.cleanup();
				}
			}
		}
		m_bRunning.set(false);
	}
	
	public void writeCurrent()
		throws IOException
	{
		int nLimit = m_oCurrentRsms.size();
		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sOutputFile), FileUtil.WRITE), "UTF-8")))
		{
			for (int nIndex = 0; nIndex < nLimit; nLimit++)
			{
				RsmRecord oRec = m_oCurrentRsms.get(nIndex);
				oOut.append(oRec.m_sId).append(',').append(oRec.m_sFile).append('\n');
			}
		}
	}
	
	public void readCurrent()
		throws IOException
	{
		Path oPath = Paths.get(m_sOutputFile);
		if (!Files.exists(oPath))
			return;
		try (CsvReader oIn = new CsvReader(Files.newInputStream(oPath)))
		{
			while (oIn.readLine() > 0)
			{
				m_oCurrentRsms.add(new RsmRecord(oIn.parseString(0), oIn.parseString(1)));
			}
		}
		Collections.sort(m_oCurrentRsms);
	}
	
	public static void main(String[] sArgs)
		throws Exception
	{
		RsmCollect oCollect = new RsmCollect("aaron.cherney@synesis-partners.com", "8f98b962-9b6a-4ecf-bcf8-48a83c80074f", "https://wzdc-rest-api.azurewebsites.net", "C:/Users/aaron.cherney/Documents/cc/rsms");
		oCollect.getToken();
		oCollect.xmlList(true);
		for (RsmRecord oRec : oCollect.m_oCurrentRsms)
		{
			if (oRec.m_bNew)
			{
				System.out.println(oRec.m_sFile);
				oCollect.xmlFile(oRec.m_sFile);
			}
		}
	}
	
	
	public boolean getToken()
	{
		try
		{
			URL oUrl = new URL(m_sBaseUrl + REQTOKEN);
			HttpURLConnection oConn = (HttpURLConnection)oUrl.openConnection();
			oConn.setRequestMethod("POST");
			oConn.setDoOutput(true);
			oConn.addRequestProperty("accept", "application/json");
			oConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			StringBuilder sReq = new StringBuilder();
			sReq.append("grant_type=&");
			sReq.append("username=").append(URLEncoder.encode(m_sUser, StandardCharsets.UTF_8)).append('&');
			sReq.append("password=").append(URLEncoder.encode(m_sPw, StandardCharsets.UTF_8)).append('&');
			sReq.append("scope=&client_secret=");
			byte[] yOut = sReq.toString().getBytes(StandardCharsets.UTF_8);
			oConn.setFixedLengthStreamingMode(yOut.length);
			oConn.connect();
			try (OutputStream oOut = oConn.getOutputStream())
			{
				oOut.write(yOut);
			}
			
			StringBuilder sBuf = new StringBuilder();
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					sBuf.append((char)nByte);
			}
			int nStart = sBuf.indexOf("\"access_token\":\"") + "\"access_token\":\"".length();
			m_sToken = sBuf.substring(nStart, sBuf.indexOf("\"", nStart));
			return true;
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
			return false;
		}
	}
	
	
	public boolean xmlList(boolean bLog)
	{
		try
		{
			URL oUrl = new URL(m_sBaseUrl + RSMXML);
			HttpURLConnection oConn = (HttpURLConnection)oUrl.openConnection();
			oConn.setRequestMethod("GET");
			oConn.addRequestProperty("accept", "application/json");
			oConn.setRequestProperty("Authorization", "Bearer " + m_sToken);
			StringBuilder sBuf = new StringBuilder();
			m_oCurrentRsms.forEach(oRec -> {oRec.m_bCurrent = false; oRec.m_bNew = false;});
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					sBuf.append((char)nByte);
			}
			int nStart = sBuf.indexOf("\"data\":[");
			while (nStart >= 0)
			{
				nStart = sBuf.indexOf("\"name\":\"", nStart);
				if (nStart < 0)
					break;
				
				nStart += "\"name\":\"".length();
				String sName = sBuf.substring(nStart, sBuf.indexOf("\"", nStart));
				nStart = sBuf.indexOf("\"id\":\"", nStart) + "\"id\":\"".length();
				RsmRecord oRec = new RsmRecord(sBuf.substring(nStart, sBuf.indexOf("\"", nStart)), sName);
				int nIndex = Collections.binarySearch(m_oCurrentRsms, oRec);
				if (nIndex < 0)
					m_oCurrentRsms.add(~nIndex, oRec);
				else
					m_oCurrentRsms.get(nIndex).m_bCurrent = true;
			}
			return true;
		}
		catch (Exception oEx)
		{
			if (bLog)
				oEx.printStackTrace();
			return false;
		}
	}
	
	public boolean xmlFile(String sFile)
	{
		try
		{
			URL oUrl = new URL(m_sBaseUrl + RSMXML + "/" + sFile);
			HttpURLConnection oConn = (HttpURLConnection)oUrl.openConnection();
			oConn.setRequestMethod("GET");
			oConn.addRequestProperty("accept", "application/json");
			oConn.setRequestProperty("Authorization", "Bearer " + m_sToken);
			StringBuilder sBuf = new StringBuilder();
			try (BufferedInputStream oIn = new BufferedInputStream(oConn.getInputStream()))
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					sBuf.append((char)nByte);
			}
			int nStart = 0;
			while (nStart >= 0)
			{
				nStart = sBuf.indexOf("\"source_name\":\"", nStart);
				if (nStart < 0)
					break;
				nStart += "\"source_name\":\"".length();
				int nEnd = sBuf.indexOf("\"", nStart);
				int nSlash = sBuf.lastIndexOf("/", nEnd);
				if (nSlash > nStart)
					nStart = nSlash + 1;
				String sFilename = sBuf.substring(nStart, nEnd);
				nStart = sBuf.indexOf("\"data\":\"", nStart) + "\"data\":\"".length();
				nEnd = sBuf.indexOf("</RoadsideSafetyMessage>", nStart) + "</RoadsideSafetyMessage>".length();
				int nState = ENDTAG;
				System.out.println(sFilename);
				StringBuilder sXml = new StringBuilder();
				for (int nIndex = nStart; nIndex < nEnd; nIndex++)
				{
					char cChar = sBuf.charAt(nIndex);
					switch (cChar)
					{
						case '>':
						{
							nState = ENDTAG;
							sXml.append(cChar);
							break;
						}
						case '\\':
						{
							char cNext = sBuf.charAt(++nIndex);
							switch (cNext)
							{
								case 'n':
								{
									if (nState != ENDTAG)
										sXml.append('\\').append('n');
									break;
								}
								case 't':
								{
									if (nState != ENDTAG)
										sXml.append('\\').append('t');
									break;
								}
								default:
								{
									sXml.append(cNext);
									break;
								}
							}
							break;
						}
						case ' ':
						{
							if (nState == ENDTAG)
								break;
						}
						default:
						{
							sXml.append(cChar);
							nState = VALUES;
							break;
						}
					}
				}
//				try (BufferedWriter oOut = Files.newBufferedWriter(Paths.get(m_sOutputDir + "/" + sFilename), StandardCharsets.UTF_8))
//				{
//					oOut.append(sXml);
//				}
				new RsmParser().parseRequest(new ByteArrayInputStream(sXml.toString().getBytes(StandardCharsets.UTF_8)));
			}
			return true;
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
			return false;
		}
	}
	
	private class RsmRecord implements Comparable<RsmRecord>
	{
		String m_sId;
		String m_sFile;
		boolean m_bCurrent = true;
		boolean m_bNew = true;
		
		RsmRecord(String sId, String sFile)
		{
			m_sId = sId;
			m_sFile = sFile;
		}

		@Override
		public int compareTo(RsmRecord o)
		{
			return m_sId.compareTo(o.m_sId);
		}
		
		public void cleanup()
		{
			
		}
	}
}
