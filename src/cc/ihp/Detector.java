/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.util.Arrays;

/**
 *
 * @author aaron.cherney
 */
public class Detector implements Comparable<Detector>
{
	String m_sId;
	double[] m_dSpeeds = Arrays.newDoubleArray();
	int m_nVolume = 0;
	double m_dLat;
	double m_dLon;
	int m_nMaxSpeed = Integer.MIN_VALUE;
	double m_dOcc;
	private int m_nCount = 0;
	
	Detector()
	{
	}
	
	Detector(String sId, double dLon, double dLat)
	{
		m_sId = sId;
		m_dLon = dLon;
		m_dLat = dLat;
	}
	
	public void add(double dSpeed, int nVolume, double dOcc)
	{
		m_nVolume += nVolume;
		m_dSpeeds = Arrays.add(m_dSpeeds, dSpeed);
		double dTempOcc = m_nCount++ * m_dOcc;
		m_dOcc /= m_nCount;
	}
		

	@Override
	public int compareTo(Detector o)
	{
		return m_sId.compareTo(o.m_sId);
	}
}
