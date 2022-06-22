/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class Corridor extends ArrayList<Subsegment>
{
	public double[] m_dBb = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
	
	@Override
	public boolean add(Subsegment oBounds)
	{
		double[] dInnerBb = oBounds.m_dBb;
		if (dInnerBb[0] < m_dBb[0])
			m_dBb[0] = dInnerBb[0];
		if (dInnerBb[1] < m_dBb[1])
			m_dBb[1] = dInnerBb[1];
		if (dInnerBb[2] > m_dBb[2])
			m_dBb[2] = dInnerBb[2];
		if (dInnerBb[3] > m_dBb[2])
			m_dBb[3] = dInnerBb[3];
		
		return super.add(oBounds);
	}
}
