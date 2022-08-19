/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ihp;

import java.util.ArrayList;

/**
 * This class represents a Corridor used in the IHP2 Speed Harmonization Algorithm
 * 
 * @author aaron.cherney
 */
public class Corridor extends ArrayList<Subsegment>
{

	/**
	 * Array containing the bounding box of the corridor in Mercator meters in order
	 * min_x, min_y, max_x, max_y
	 */
	public double[] m_dBb = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
	
	
	/**
	 * Add a subsegment to the corridor and updates the bounding box of the corridor
	 * based on the bounding box of the subsegment
	 * @param oSubsegment Subsegment to be added to the Corridor
	 * @return true if the add is successful
	 */
	@Override
	public boolean add(Subsegment oSubsegment)
	{
		double[] dInnerBb = oSubsegment.m_dBb;
		if (dInnerBb[0] < m_dBb[0])
			m_dBb[0] = dInnerBb[0];
		if (dInnerBb[1] < m_dBb[1])
			m_dBb[1] = dInnerBb[1];
		if (dInnerBb[2] > m_dBb[2])
			m_dBb[2] = dInnerBb[2];
		if (dInnerBb[3] > m_dBb[2])
			m_dBb[3] = dInnerBb[3];
		
		return super.add(oSubsegment);
	}
}
