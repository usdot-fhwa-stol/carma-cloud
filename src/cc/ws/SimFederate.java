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
	private String m_sAmbassadorAddress;
	private String m_sCarmaCloudUrl = "";
	private TimeSource m_oTs;


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

		String sAddress = oConf.getInitParameter("ambassador");
		if (sAddress != null)
		{
			try
			{
				InetAddress.getByName(sAddress); // validate network address
				m_sAmbassadorAddress = sAddress;
				m_oTs = new TimeSource(true);

				String sUrl = oConf.getInitParameter("url");
				if (sUrl != null)
					m_sCarmaCloudUrl = sUrl;

				new Thread(this).start(); // begin registration cycle
			}
			catch (Exception oEx)
			{
			}
		}

		if (m_oTs == null) // simulation time source not created
			m_oTs = new TimeSource(false);
	}


	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws ServletException, IOException
	{
		JSONObject oJson;
		try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream()))
		{
			oJson = new JSONObject(new JSONTokener(oIn));
		}

		long lSeq = -1L;
		long lStep = -1L;
		if (oJson.has("seq") && oJson.has("timestep"))
		{
			lSeq = oJson.getLong("seq");
			lStep = oJson.getLong("timestep");
			m_oTs.m_lSimTime = lStep;
		}
		LOGGER.debug(String.format("seq: %d timestep: %d", lSeq, lStep));
	}


	@Override
	public void run()
	{
		boolean bRegistered = false;
		while (!bRegistered)
		{
			try (Socket oSock = new Socket())
			{
				oSock.connect(new InetSocketAddress(m_sAmbassadorAddress, 1617), 10000);
				DataOutputStream oOut = new DataOutputStream(oSock.getOutputStream());
				oOut.writeUTF(m_sCarmaCloudUrl);
				bRegistered = true;
				Thread.sleep(m_lRetryInterval);
			}
			catch (Exception oEx)
			{
				LOGGER.error(oEx);
			}
		}
	}
}
