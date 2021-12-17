/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.rsm.RsmCollect;
import cc.rsm.RsmParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.Timer;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 *
 * @author aaron.cherney
 */
public class RsmServlet extends HttpServlet
{
	private RsmCollect m_oCollector;
	private Timer m_oTimer;
	private String m_sRsmFile;
	
	@Override
	public void init(ServletConfig oConfig)
		throws ServletException
	{
		String sUser = oConfig.getInitParameter("user");
		String sPw = oConfig.getInitParameter("pw");
		String sBaseUrl = oConfig.getInitParameter("url");
		String sFile = oConfig.getInitParameter("file");
		HashMap<String, double[]> oAdjustmentMap = new HashMap();
		Enumeration<String> oParas = oConfig.getInitParameterNames();
		while (oParas.hasMoreElements())
		{
			String sPara = oParas.nextElement();
			if (sPara.startsWith("rsm-"))
			{
				String[] sVals = oConfig.getInitParameter(sPara).split(",");
				String sName = sPara.substring("rsm-".length());
				oAdjustmentMap.put(sName, new double[]{Double.parseDouble(sVals[0]), Double.parseDouble(sVals[1])});
			}
		}
		String[] sVals = oConfig.getInitParameter("defaultoffsets").split(",");
		oAdjustmentMap.put("default", new double[]{Double.parseDouble(sVals[0]), Double.parseDouble(sVals[1])});

		m_sRsmFile = sFile;
		
		int nPeriod = Integer.parseInt(oConfig.getInitParameter("period")); // unit is minutes
		if (sUser == null || sPw == null || sBaseUrl == null || sFile == null)
		{
			throw new ServletException("Invalid configuration");
		}
		
		m_oTimer = new Timer();
		m_oCollector = new RsmCollect(sUser, sPw, sBaseUrl, sFile, oAdjustmentMap);
		try
		{
			Files.createDirectories(Paths.get(sFile).getParent());
			m_oCollector.readCurrent();
		}
		catch (IOException oEx)
		{
			throw new ServletException(oEx);
		}
		m_oCollector.run();
		GregorianCalendar oCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		oCal.set(Calendar.MILLISECOND, 0);
		oCal.set(Calendar.SECOND, 0);
		oCal.set(Calendar.MINUTE, (oCal.get(Calendar.MINUTE) / nPeriod) * nPeriod + nPeriod); // determine the next execution period with no midnight offset
		m_oTimer.scheduleAtFixedRate(m_oCollector, oCal.getTime(), 60 * 1000 * nPeriod);
//		try
//		{
//			new RsmParser().parseRequest(new BufferedInputStream(Files.newInputStream(Paths.get("/home/cherneya/rsm-xml--tfhrc-example--main-road--1-of-3.xml"))));
//			new RsmParser().parseRequest(new BufferedInputStream(Files.newInputStream(Paths.get("/home/cherneya/rsm-xml--accuracy-test-1--prairie-center-cir--1-of-3.xml"))));
//		}
//		catch (Exception oEx)
//		{
//			throw new ServletException(oEx);
//		}
	}
	
	@Override
	public void destroy()
	{
		try
		{
			m_oCollector.writeCurrent();
		}
		catch (IOException oEx)
		{
		}
		m_oTimer.cancel();
	}
}
