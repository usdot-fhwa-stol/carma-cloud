package cc.ws;

import java.util.Comparator;


public class Session implements Comparable<String>, Comparator<Session>
{
	protected long m_lUpdate;
	protected String m_sToken;
	protected User m_oUser;


	protected Session()
	{
	}


	Session(String sKey)
	{
		m_sToken = sKey;
	}


	@Override
	public int compareTo(String sKey)
	{
		return m_sToken.compareTo(sKey);
	}


	@Override
	public int compare(Session oLhs, Session oRhs)
	{
		return oLhs.m_sToken.compareTo(oRhs.m_sToken);
	}
}
