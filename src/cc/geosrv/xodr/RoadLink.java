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
public class RoadLink
{
	public final static byte LINK_PREDECESSOR = 0;
	public final static byte LINK_SUCCESSOR = 1;
	public final static byte LINK_ROAD = 0;
	public final static byte LINK_JUNCTION = 1;
	public byte m_yElementType;
	
	RoadLink()
	{
	}
	
	public RoadLink(String sType)
	{
		
	}
}
