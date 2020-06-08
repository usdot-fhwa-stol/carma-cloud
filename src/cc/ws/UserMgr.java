package cc.ws;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import cc.util.CsvReader;
import cc.util.Text;


public class UserMgr extends HttpServlet
{
	protected static final Object LOCK = new Object();
	protected static MessageDigest DIGEST;
	protected ArrayList<User> m_oCreds = new ArrayList();

	static
	{
		try
		{
			DIGEST = MessageDigest.getInstance("SHA-256");
		}
		catch (Exception oEx)
		{
		}
	}
	public UserMgr()
	{
	}


	@Override
	public void init()
	{
		ServletConfig oConf = getServletConfig();
		try (CsvReader oCsv = new CsvReader(new FileInputStream(oConf.getInitParameter("pwdfile"))))
		{
			while (oCsv.readLine() > 0)
				m_oCreds.add(new User(oCsv));
		}
		catch (Exception oEx)
		{
		}
		Collections.sort(m_oCreds, new User());
	}


	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRep)
	{
		StringBuilder sBuf = new StringBuilder("{\n");
		String sPath = oReq.getPathInfo();
		if (sPath.contains("login"))
		{
			String sUname = oReq.getParameter("uname");
			String sPword = oReq.getParameter("pword");
			if (sUname != null && sUname.length() > 0 && sPword != null && sPword.length() > 0)
			{
				int nIndex = Collections.binarySearch(m_oCreds, sUname);
				if (nIndex >= 0)
				{
					User oUser = m_oCreds.get(nIndex);
					StringBuilder sSecPass = new StringBuilder();
					getSecurePassword(sPword, oUser.m_ySalt, sSecPass);
					if (Text.compare(oUser.m_sPass, sSecPass) == 0)
					{
						Session oSess = SessMgr.getSession(oReq, true);
						oSess.m_oUser = oUser; // save credentials in session
						sBuf.append("\t\"token\": \"").append(oSess.m_sToken).append("\"\n");
					}
				}
			}
		}
		else
		{
			Session oSess = SessMgr.getSession(oReq);
			if (sPath.contains("check"))
			{
				if (oSess != null)
					sBuf.append("\t\"token\": \"").append(oSess.m_sToken).append("\"\n");
			}
			else if (sPath.contains("logout"))
			{
					SessMgr.removeSession(oSess);
			}
			else if (sPath.contains("update"))
			{
			}
		}
		sBuf.append("}\n");

		try (ServletOutputStream oOut = oRep.getOutputStream())
		{
			for (int nIndex = 0; nIndex < sBuf.length(); nIndex++)
				oOut.print(sBuf.charAt(nIndex));
		}
		catch (Exception oEx)
		{
		}
	}


	static void getSecurePassword(String sPass, byte[] ySalt, StringBuilder sBuf)
	{
		synchronized(LOCK)
		{
			DIGEST.reset();
			DIGEST.update(ySalt);
			Text.toHexString(DIGEST.digest(sPass.getBytes()), sBuf);
		}
	}


	public static void main(String[] sArgs)
	{
		String sUser = sArgs[0];
		String sPass = sArgs[1];

		byte[] ySalt = new byte[32]; // use 256-bit algorithm
		try
		{
			java.security.SecureRandom.getInstance("SHA1PRNG").nextBytes(ySalt);
			StringBuilder sBuf = new StringBuilder();
			UserMgr.getSecurePassword(sPass, ySalt, sBuf);

			System.out.print(sUser);
			System.out.print(",");
			System.out.print(Text.toHexString(ySalt));
			System.out.print(",");
			System.out.print(sBuf.toString());
			System.out.print(",");
			System.out.println("abcdefghijklmnopqrstWvwxyz");
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
}
