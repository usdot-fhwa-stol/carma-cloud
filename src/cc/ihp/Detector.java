/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import cc.util.Arrays;

/**
 * This class represent a traffic detector that provides speed, volume, and density
 * observations
 * 
 * @author aaron.cherney
 */
public class Detector implements Comparable<Detector>
{

	/**
	 * Detector identifier
	 */
	String m_sId;

	/**
	 * Growable array used to store speed observations within a certain time
	 * interval
	 * 
	 * @see cc.util.Arrays
	 */
	double[] m_dSpeeds = Arrays.newDoubleArray();

	/**
	 * Count of number of vehicles detected in a given time interval
	 */
	int m_nVolume = 0;

	/**
	 * Latitude of the observations
	 */
	double m_dLat;

	/**
	 * Longitude of the observations
	 */
	double m_dLon;

	/**
	 * Density of the traffic detected in a given time interval
	 */
	double m_dDensity;

	/**
	 * Number of observations within a given time interval
	 */
	private int m_nCount = 0;
	
	/**
	 * Default constructor
	 */
	Detector()
	{
	}
	
	/**
	 * Constructor that sets the id, longitude and latitude of the Detector
	 * 
	 * @param sId Detector identifier
	 * @param dLon Longitude of detection point
	 * @param dLat Latitude of detection point
	 */
	Detector(String sId, double dLon, double dLat)
	{
		m_sId = sId;
		m_dLon = dLon;
		m_dLat = dLat;
	}
	
	/**
	 * Adds a speed, volume, and density observation to the detector for the given
	 * time interval. Volumes get added to the existing volume observation, densities
	 * are averaged, and speeds are added to the growable array so that different
	 * statistic values can be calculated later.
	 * 
	 * @param dSpeed detected speed
	 * @param nVolume detected volume
	 * @param dDensity detected density
	 */
	public void add(double dSpeed, int nVolume, double dDensity)
	{
		m_nVolume += nVolume;
		m_dSpeeds = Arrays.add(m_dSpeeds, dSpeed);
		double dTempOcc = m_nCount++ * m_dDensity;
		m_dDensity /= m_nCount;
	}
		

	/**
	 * Compares Detectors by Id
	 * 
	 * @param o another Detector
	 * @return The integer value of String.compareTo() of the two detector's ids
	 */
	@Override
	public int compareTo(Detector o)
	{
		return m_sId.compareTo(o.m_sId);
	}
}
