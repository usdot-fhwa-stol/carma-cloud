package cc.util;

import java.util.ArrayList;
import java.util.Collections;


public class StringPool extends ArrayList<StringPool.Group>
{
	protected char[] m_oSearch = new char[2];


	public StringPool()
	{
	}


	public String intern(String sVal)
	{
		int nIndex = m_oSearch.length;
		while (nIndex-- > 0) // create search key
		{
			if (nIndex < sVal.length())
				m_oSearch[nIndex] = Character.toUpperCase(sVal.charAt(nIndex));
			else
				m_oSearch[nIndex] = 0;
		}

		nIndex = Collections.binarySearch(this, m_oSearch);
		if (nIndex < 0) // completely new string group array
		{
			nIndex = ~nIndex;
			Group oGroup = new Group(m_oSearch);
			add(nIndex, oGroup);
		}

		Group oGroup = get(nIndex);
		nIndex = Collections.binarySearch(oGroup, sVal);
		if (nIndex < 0)
		{
			oGroup.add(~nIndex, sVal);
			return sVal;
		}
		return oGroup.get(nIndex);
	}


	public ArrayList<String> toList()
	{
		int nSize = 0; // determine space requirements
		for (Group oGroup : this)
			nSize += oGroup.size();

		ArrayList<String> oList = new ArrayList(nSize);
		for (Group oGroup : this)
			oList.addAll(oGroup);

		Collections.sort(oList); // correct pool grouping order
		return oList;
	}


	@Override
	public void clear()
	{
		for (Group oGroup : this)
			oGroup.clear();

		super.clear();
	}


	class Group extends ArrayList<String> implements Comparable<char[]>
	{
		char[] m_oKey;


		private Group()
		{
		}


		Group(char[] oKey)
		{
			m_oKey = new char[]{oKey[0], oKey[1]};
		}


		@Override
		public int compareTo(char[] oRhs)
		{
			int nComp = m_oKey[0] - oRhs[0];
			if (nComp == 0)
				nComp = m_oKey[1] - oRhs[1];
				
			return nComp;
		}
	}
}
