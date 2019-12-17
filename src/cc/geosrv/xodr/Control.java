/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Geo;
import java.util.Iterator;

/**
 *
 * @author Federal Highway Administration
 */
public class Control
{
	private double[] m_dPath = Arrays.newDoubleArray();
	
	public void addToPath(double dX, double dY, double dZ, double dWidth, Proj oProj, double[] dPoint)
	{
		m_dPath = Geo.addPoint(dX, dY, m_dPath, oProj, dPoint);
		m_dPath = Arrays.add(m_dPath, dZ, dWidth);
	}
	
	
	public Iterator<double[]> segmentIterator()
	{
		return Arrays.iterator(m_dPath, new double[8], 1, 4);
	}
	
	
	public Iterator<double[]> pointIterator()
	{
		return Arrays.iterator(m_dPath, new double[4], 1, 4);
	}
}
