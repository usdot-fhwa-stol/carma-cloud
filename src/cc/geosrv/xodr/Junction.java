/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class Junction extends ArrayList<Connection> implements Comparable<Junction>
{
	public final static byte DEFAULT_TYPE = 0;
	public final static byte VIRTUAL_TYPE = 1;
	public String m_sName;
	public String m_sId;
	public byte m_yType = DEFAULT_TYPE;
	
	public Junction(String sName, String sId, String sType)
	{
		super();
		if (sName != null)
			m_sName = sName;
		
		m_sId = sId;
		
		if (sType != null)
		{
			if (sType.toLowerCase().compareTo("virtual") == 0)
				m_yType = VIRTUAL_TYPE;
		}
	}


	@Override
	public int compareTo(Junction o)
	{
		return m_sId.compareTo(o.m_sId);
	}
}
