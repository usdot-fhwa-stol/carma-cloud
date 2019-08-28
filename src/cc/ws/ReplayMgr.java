package cc.ws;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import cc.util.CsvReader;


public class ReplayMgr extends HttpServlet implements Runnable
{
	protected String m_sStormFile;
	private final ArrayList<Storm> m_oStorms = new ArrayList();


	public ReplayMgr()
	{
	}


	@Override
	public void init()
	{
		String sStormFile = getServletConfig().getInitParameter("stormfile");
		if (sStormFile != null && sStormFile.length() > 0)
		{
			m_sStormFile = sStormFile;
			new Thread(this).start();
		}
	}


	@Override
	public void run()
	{
		int nCells = Integer.MIN_VALUE;
		try (CsvReader oIn = new CsvReader(new FileInputStream(m_sStormFile)))
		{
			SimpleDateFormat oFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			StringBuilder sBuf = new StringBuilder();

			oIn.readLine(); // skip header: start end avg max min
			while (oIn.readLine() > 0)
			{
				oIn.parseString(sBuf, 0); // get start date as string
				String sStart = sBuf.toString();
				
				oIn.parseString(sBuf, 1); // get end date as string
				String sEnd = sBuf.toString();

				double dDur = oFormat.parse(sEnd).getTime() - oFormat.parse(sStart).getTime();

				Storm oStorm = new Storm();
				oStorm.m_sStart = sStart;
				oStorm.m_sEnd = sEnd;
				oStorm.m_sHours = String.format("%03.1f", dDur / 3600000.0);
				oStorm.m_nAvg = (int)Math.round(oIn.parseDouble(2));
				oStorm.m_nMax = oIn.parseInt(3);
				oStorm.m_nMin = oIn.parseInt(4);
				m_oStorms.add(oStorm);

				if (oStorm.m_nMax > nCells)
					nCells = oStorm.m_nMax; // find greatest cell count
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}

		if (nCells <= 0)
			return;

		for (Storm oStorm : m_oStorms)
		{
			oStorm.m_nAvg = 100 * oStorm.m_nAvg / nCells;
			oStorm.m_nMin = 100 * oStorm.m_nMin / nCells;
			oStorm.m_nMax = 100 * oStorm.m_nMax / nCells;
		}
	}


	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRep)
		throws IOException
	{
		Session oSess = SessMgr.getSession(oReq);
		if (oSess == null)
		{
			oRep.sendError(401);
			return;
		}

		try (JsonGenerator oJson = Json.createGenerator(oRep.getOutputStream()))
		{
			oJson.writeStartArray(); // start outer JSON array
			for (Storm oStorm : m_oStorms)
			{
				oJson.writeStartArray(); // start record
				oJson.write(oStorm.m_sStart).write(oStorm.m_sEnd).write(oStorm.m_sHours).
					write(oStorm.m_nAvg).write(oStorm.m_nMax).write("");
				oJson.writeEnd(); // end record
			}
			oJson.writeEnd(); // end outer JSON array
		}
	}


	private class Storm
	{
		public String m_sStart;
		public String m_sEnd;
		public String m_sHours;
		public int m_nMin;
		public int m_nMax;
		public int m_nAvg;


		Storm()
		{
		}
	}


//	public static void main(String[] sArgs)
//	{
//		ReplayMgr oMgr = new ReplayMgr();
//		oMgr.m_sStormFile = "C:/Users/bryan.krueger/2018storms.csv";
//		oMgr.run();
//		System.out.println(oMgr.m_oStorms.size());
//	}
}
