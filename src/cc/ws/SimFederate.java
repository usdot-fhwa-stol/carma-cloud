package cc.ws;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONTokener;


public class SimFederate extends HttpServlet implements Runnable
{
	protected static final Logger LOGGER = LogManager.getRootLogger();

	private long m_lRetryInterval = 60000L; // registration retry 60 seconds
	private static final Integer M_TIMEOUT = 10000;
	private String m_sAmbassadorAddress;
	private Integer m_sAmbassadorPort;
	private String m_sCarmaCloudUrl = "";
	private TimeSource m_oTs;
	private boolean m_simulationMode = false;
	private String m_carmaCloudId = "";


	public SimFederate()
	{
	}


	@Override
	public void init()
	{
		ServletConfig oConf = getServletConfig();

		String sRetry = oConf.getInitParameter("retry");
		if (sRetry != null)
			m_lRetryInterval = Integer.parseInt(sRetry) * 1000L;

		m_sAmbassadorAddress = oConf.getInitParameter("ambassador");
		m_sAmbassadorPort = Integer.parseInt(oConf.getInitParameter("ambassadorPort"));
		m_simulationMode = Boolean.parseBoolean(oConf.getInitParameter("simulationMode"));
		m_sCarmaCloudUrl = oConf.getInitParameter("url");
		m_carmaCloudId = oConf.getInitParameter("carmaCloudId");
		m_oTs = new TimeSource(m_simulationMode);
		if (m_simulationMode)
		{
			LOGGER.debug("Run in simulation mode.");
			new Thread(this).start(); // begin registration cycle
		}
	}


	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws ServletException, IOException
	{
		JSONObject simTimeJson;
		try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream()))
		{
			simTimeJson = new JSONObject(new JSONTokener(oIn));
		}

		long lSeq = -1L;
		long lStep = -1L;
		if (simTimeJson.has("seq") && simTimeJson.has("timestep"))
		{
			lSeq = simTimeJson.getLong("seq");
			lStep = simTimeJson.getLong("timestep");
			m_oTs.m_lSimTime = lStep;
		}
		this.getServletContext().setAttribute(TimeSource.class.getName(), m_oTs);
		LOGGER.debug("seq: {} timestep: {} timesource: {}", lSeq, lStep, m_oTs.currentTimeMillis());		
	}

	/***
	 * @brief A new thread to send registration request to simulation ambassador
	 */
	@Override
	public void run()
	{
		boolean bRegistered = false;
		while (!bRegistered)
		{
			try (Socket oSock = new Socket())
			{
				oSock.connect(new InetSocketAddress(m_sAmbassadorAddress, m_sAmbassadorPort), M_TIMEOUT);
				DataOutputStream oOut = new DataOutputStream(oSock.getOutputStream());
				JSONObject json = new JSONObject();
				json.put("id", m_carmaCloudId);
				json.put("url", m_sCarmaCloudUrl);
				LOGGER.debug("Ambassador registration request: {}", json);
				oOut.writeUTF(json.toString());
				bRegistered = true;
			}
			catch (Exception oEx)
			{
				LOGGER.error(oEx);
			}

			if (!bRegistered)
			{
				try
				{
					Thread.sleep(m_lRetryInterval);
				}
				catch (InterruptedException oEx)
				{
					LOGGER.error("Ambassador registration task is interrupted.");
					Thread.currentThread().interrupt();
				}
			}
		}
	}
}
