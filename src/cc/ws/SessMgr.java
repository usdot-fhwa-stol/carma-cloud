package cc.ws;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import cc.util.Text;


public class SessMgr extends HttpServlet
{
	static long TIMEOUT = 1800000; // default 30-minute sessions
	static final ArrayList<Session> SESSIONS = new ArrayList();


	public SessMgr()
	{
	}


	@Override
	public void init()
	{
		ServletConfig oConf = getServletConfig();
		String sExp = oConf.getInitParameter("timeout");
		if (sExp != null)
			TIMEOUT = Text.parseInt(sExp);
	}


	public static Session getSession(HttpServletRequest oReq)
	{
		return getSession(oReq, false);
	}

	
	static Session getSession(HttpServletRequest oReq, boolean bCreate)
	{
		String sToken = oReq.getParameter("token");
		if (sToken == null)
			sToken = "";

		Session oSess = null;
		synchronized(SESSIONS)
		{
			int nIndex = Collections.binarySearch(SESSIONS, sToken);
			if (nIndex >= 0)
			{
				oSess = SESSIONS.get(nIndex);
				if (oSess.m_lUpdate < System.currentTimeMillis())
				{
					SESSIONS.remove(nIndex);
					return null;
				}
			}
			else if (bCreate)
			{
				byte[] yBytes = new byte[16];
				try
				{
					SecureRandom oRng = SecureRandom.getInstance("SHA1PRNG");
					do
					{
						oRng.nextBytes(yBytes); // ensure no duplicates
						oSess = new Session(Text.toHexString(yBytes));
						nIndex = Collections.binarySearch(SESSIONS, oSess, oSess);
					}
					while (nIndex >= 0);
					SESSIONS.add(~nIndex, oSess); // save new session
				}
				catch (Exception oEx)
				{
				}
			}
		}

		if (oSess != null)
			oSess.m_lUpdate = System.currentTimeMillis() + TIMEOUT;
			
		return oSess;
	}


	static void removeSession(Session oSess)
	{
		if (oSess == null)
			return;

		synchronized(SESSIONS)
		{
			int nIndex = Collections.binarySearch(SESSIONS, oSess, oSess);
			if (nIndex >= 0)
				SESSIONS.remove(nIndex);
		}
	}
}
