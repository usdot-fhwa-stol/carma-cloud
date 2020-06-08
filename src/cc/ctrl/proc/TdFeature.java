/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.TrafCtrl;
import cc.util.Arrays;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class TdFeature
{
	public double[] m_dGeo;
	public int[] m_nTags;
	public byte[] m_yCtrlId;
	
	public TdFeature(double[] dGeo, int[] nTags, TrafCtrl oCtrl)
	{
		this(dGeo, nTags, oCtrl.m_yId);
	}
	
	
	public TdFeature(double[] dGeo, int[] nTags, byte[] yId)
	{
		m_dGeo = Arrays.newDoubleArray(Arrays.size(dGeo) -1 );
		System.arraycopy(dGeo, 0, m_dGeo, 0, m_dGeo.length);
		m_nTags = new int[nTags.length];
		System.arraycopy(nTags, 0, m_nTags, 0, nTags.length);
		m_yCtrlId = new byte[yId.length];
		System.arraycopy(yId, 0, m_yCtrlId, 0, yId.length);
	}
	
	
	public TdFeature(DataInputStream oIn, int nTags, byte[] yId)
	   throws IOException
	{
		m_yCtrlId = new byte[yId.length];
		System.arraycopy(yId, 0, m_yCtrlId, 0, yId.length);
		m_nTags = new int[nTags];
		for (int nIndex = 0; nIndex < nTags; nIndex++)
			m_nTags[nIndex] = oIn.readInt();
		int nOrdinates = oIn.readInt();
		double[] dGeo = Arrays.newDoubleArray(nOrdinates + 1);
		for (int nIndex = 0; nIndex < nOrdinates; nIndex++)
			dGeo = Arrays.add(dGeo, oIn.readDouble());
		
		m_dGeo = dGeo; // save local variable to member variable
	}
	
	
	public TdFeature(DataInputStream oIn, int nTags)
	   throws IOException
	{
		m_yCtrlId = new byte[16];
		oIn.read(m_yCtrlId);
		oIn.skipBytes(4); // skip the bytes to skip number
		m_nTags = new int[nTags];
		for (int nIndex = 0; nIndex < nTags; nIndex++)
			m_nTags[nIndex] = oIn.readInt();
		int nOrdinates = oIn.readInt();
		double[] dGeo = Arrays.newDoubleArray(nOrdinates + 1);
		for (int nIndex = 0; nIndex < nOrdinates; nIndex++)
			dGeo = Arrays.add(dGeo, oIn.readDouble());
		
		m_dGeo = dGeo; // save local variable to member variable
	}
	
	
	public void write(DataOutputStream oOut)
	   throws IOException
	{
		oOut.write(m_yCtrlId);
		oOut.writeInt(getBytesToSkip());
		for (int nTag : m_nTags)
			oOut.writeInt(nTag);
		oOut.writeInt(Arrays.size(m_dGeo) - 1);
		Iterator<double[]> oIt = Arrays.iterator(m_dGeo, new double[1], 1, 1);
		while (oIt.hasNext())
		{
			oOut.writeDouble(oIt.next()[0]);
		}
	}
	
	public int getBytesToSkip()
	{
		int nTotalBytes = m_nTags.length * 4; // 4 bytes for each int in tags
		nTotalBytes += 4; // 4 bytes for number of ordinates
		nTotalBytes += (Arrays.size(m_dGeo) - 1) * 8; // 8 bytes for each double ordinate
		
		return nTotalBytes;
	}
}
