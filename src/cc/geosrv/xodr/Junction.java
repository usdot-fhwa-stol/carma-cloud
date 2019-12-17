/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

/**
 *
 * @author Federal Highway Administration
 */
public class Junction
{
	public final static byte DEFAULT_TYPE = 0;
	public final static byte VIRTUAL_TYPE = 1;
	public String m_sName;
	public String m_sId;
	public byte m_yType = DEFAULT_TYPE;
	
	Junction()
	{
	}
	
	public Junction(String sName, String sId, String sType)
	{
		if (sName != null)
			m_sName = sName;
		
		m_sId = sId;
		
		if (sType != null)
		{
			if (sType.toLowerCase().compareTo("virtual") == 0)
				m_yType = VIRTUAL_TYPE;
		}
	}
}
