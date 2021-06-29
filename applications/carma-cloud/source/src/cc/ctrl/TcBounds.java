/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import cc.geosrv.Mercator;
import cc.util.Geo;

/**
 *
 * @author aaron.cherney
 */
public class TcBounds
{
	public long m_lOldest; // epoch minutes
	public int[] m_nCorners = new int[10]; // stores the corners in int geo coords, repeating the first corner so the isInsidePoly function can be used 
	public int[] m_nBB = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
	
	public void setCorners(int[] nXs, int[] nYs, int nScale)
	{
		double dX = Mercator.lonToMeters(Geo.fromIntDeg(m_nCorners[0])); // index 0 is origin lon
		double dY = Mercator.latToMeters(Geo.fromIntDeg(m_nCorners[1])); // index 1 is origin lat
		double dMultiplier = Math.pow(10, nScale);
		for (int i = 0; i < nXs.length; i++)
		{
			m_nCorners[i * 2 + 2] = Geo.toIntDeg(Mercator.xToLon(dX + nXs[i] * dMultiplier));
			m_nCorners[i * 2 + 3] = Geo.toIntDeg(Mercator.yToLat(dY + nYs[i] * dMultiplier));	
		}
		m_nCorners[8] = m_nCorners[0];
		m_nCorners[9] = m_nCorners[1];
		setBB();
	}
	
	
	private void setBB()
	{
		for (int nIndex = 2; nIndex < m_nCorners.length;)
		{
			int nLon = m_nCorners[nIndex++];
			int nLat = m_nCorners[nIndex++];
			if (nLon < m_nBB[0])
				m_nBB[0] = nLon;
			if (nLat < m_nBB[1])
				m_nBB[1] = nLat;
			if (nLon > m_nBB[2])
				m_nBB[2] = nLon;
			if (nLat > m_nBB[3])
				m_nBB[3] = nLat;
		}
	}
}
