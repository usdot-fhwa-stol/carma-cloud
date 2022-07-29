/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import java.io.DataInputStream;
import java.io.IOException;

/**
 *
 * @author aaron.cherney
 */
public class CtrlIndex
{
	public byte[] m_yId;
	public int m_nType;
	public long m_lStart;
	public long m_lEnd;
	public double[] m_dBB;
	
	public CtrlIndex(DataInputStream oIn)
		throws IOException
	{
		m_nType = oIn.readInt();
		m_yId = new byte[16];
		oIn.read(m_yId);
		m_lStart = oIn.readLong();
		m_lEnd = oIn.readLong();
		m_dBB = new double[]{oIn.readInt() / 100.0, oIn.readInt() / 100.0, oIn.readInt() / 100.0, oIn.readInt() / 100.0};
	}
}
