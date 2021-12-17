/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class TdLayer extends ArrayList<TdFeature> implements Comparable<TdLayer>
{
	public static final byte POINT = 0;
	public static final byte LINESTRING = 1;
	public static final byte POLYGON = 2;
	public String m_sName;
	public String[] m_oKeys = null;
	public String[] m_oValues = null;
	public byte m_yGeoType;
	
	public TdLayer(String sName, String[] oKeys, CharSequence[] oValues, byte yGeoType)
	{
		super();
		m_sName = sName;
		m_oKeys = new String[oKeys.length];
		System.arraycopy(oKeys, 0, m_oKeys, 0, oKeys.length);
		m_oValues = new String[oValues.length];
		System.arraycopy(oValues, 0, m_oValues, 0, oValues.length);
		m_yGeoType = yGeoType;
	}
	
	
	public TdLayer(DataInputStream oIn, boolean bReadFeatures)
	   throws IOException
	{
		super();
		m_sName = oIn.readUTF();
		int nSize = oIn.readInt();
		int nTags = nSize * 2;
		m_oKeys = new String[nSize];
		for (int nIndex = 0; nIndex < nSize; nIndex++)
			m_oKeys[nIndex] = oIn.readUTF();
		nSize = oIn.readInt();
		m_oValues = new String[nSize];
		for (int nIndex = 0; nIndex < nSize; nIndex++)
			m_oValues[nIndex] = oIn.readUTF();
		m_yGeoType = oIn.readByte();
		if (bReadFeatures)
		{
			nSize = oIn.readInt();
			for (int nIndex = 0; nIndex < nSize; nIndex++)
				add(new TdFeature(oIn, nTags));
		}
	}
	
	
	public void write(DataOutputStream oOut)
	   throws IOException
	{
		if (isEmpty())
			return;
		oOut.writeUTF(m_sName);
		oOut.writeInt(m_oKeys.length);
		for (String sKey : m_oKeys)
			oOut.writeUTF(sKey);
		oOut.writeInt(m_oValues.length);
		for (String sValue : m_oValues)
			oOut.writeUTF(sValue);
		oOut.writeByte(m_yGeoType);
		oOut.writeInt(size());
		for (TdFeature oFeature : this)
			oFeature.write(oOut);
	}


	@Override
	public int compareTo(TdLayer o)
	{
		return m_sName.compareTo(o.m_sName);
	}
}
