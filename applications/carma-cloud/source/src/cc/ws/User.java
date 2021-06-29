package cc.ws;

import cc.util.CsvReader;
import cc.util.Text;
import java.util.Comparator;


public class User implements Comparable<String>, Comparator<User>
{
	byte[] m_ySalt;
	String m_sUser;
	String m_sPass;
	String m_sGroup;


	User()
	{
	}


	User(CsvReader oCsv)
	{
		StringBuilder sCol = new StringBuilder();
		oCsv.parseString(sCol, 0);
		m_sUser = sCol.toString(); // save username

		oCsv.parseString(sCol, 1);
		m_ySalt = Text.fromHexString(sCol);

		oCsv.parseString(sCol, 2);
		m_sPass = sCol.toString(); // keep password as hexadecimal string

		oCsv.parseString(sCol, 3);
		m_sGroup = sCol.toString().intern(); // very few group patterns
	}


	@Override
	public int compareTo(String sUser)
	{
		return m_sUser.compareTo(sUser);
	}


	@Override
	public int compare(User oLhs, User oRhs)
	{
		return oLhs.m_sUser.compareTo(oRhs.m_sUser);
	}
}
