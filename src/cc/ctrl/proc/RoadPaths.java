/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.geosrv.xodr.Connection;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author aaron.cherney
 */
public class RoadPaths implements Comparable<RoadPaths>
{
	int m_nRoadId;
	ArrayList<Connection> m_oConns;

	RoadPaths()
	{

	}


	RoadPaths(int nRoadId)
	{
		m_nRoadId = nRoadId;
		m_oConns = new ArrayList();
	}


	void addConnection(Connection oConn)
	{
		int nIndex = Collections.binarySearch(m_oConns, oConn);
		if (nIndex < 0)
			m_oConns.add(~nIndex, oConn);
	}


	@Override
	public int compareTo(RoadPaths o)
	{
		return m_nRoadId - o.m_nRoadId;
	}
}
