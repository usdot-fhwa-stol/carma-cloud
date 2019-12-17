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
public class Connection
{
	public final static byte START_CONTACT = 0;
	public final static byte END_CONTACT = 1;
	public int m_nId;
	public int m_nInRoad;
	public int m_nConnRoad;
	public byte m_yContactPoint = Byte.MIN_VALUE;
	public String m_sConnMaster;
	public byte m_yType = Junction.DEFAULT_TYPE;
	
	Connection()
	{	
	}
	
	public Connection(int nId, int nInRoad, int nConnRoad, String sContactPoint, String sConnMaster, String sType)
	{
		m_nId = nId;
		m_nInRoad = nInRoad;
		m_nConnRoad = nConnRoad;
		if (sContactPoint.toLowerCase().compareTo("start") == 0)
			m_yContactPoint = START_CONTACT;
		else if (sContactPoint.toLowerCase().compareTo("end") == 0)
			m_yContactPoint = END_CONTACT;
		m_sConnMaster = sConnMaster;
		if (sType != null)
		{
			if (sType.toLowerCase().compareTo("virtual") == 0)
				m_yType = Junction.VIRTUAL_TYPE;
		}
	}
}
