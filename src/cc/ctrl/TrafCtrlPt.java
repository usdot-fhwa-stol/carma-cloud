package cc.ctrl;

import java.io.DataInputStream;
import java.io.DataOutputStream;


public class TrafCtrlPt
{
	public int m_nW;
	public int m_nX;
	public int m_nY;
	public int m_nZ;


	private TrafCtrlPt()
	{
	}


	public TrafCtrlPt(int nX, int nY)
	{
		m_nX = nX;
		m_nY = nY;
	}


	public TrafCtrlPt(int nX, int nY, int nW)
	{
		this(nX, nY);
		m_nW = nW;
	}


	public TrafCtrlPt(int nX, int nY, int nZ, int nW)
	{
		this(nX, nY, nW);
		m_nZ = nZ;
	}


	TrafCtrlPt(DataInputStream oIn)
		throws Exception
	{
		m_nW = oIn.readInt();
		m_nX = oIn.readInt();
		m_nY = oIn.readInt();
		m_nZ = oIn.readInt();
	}


	void writeBin(DataOutputStream oOut)
		throws Exception
	{
		oOut.writeInt(m_nW);
		oOut.writeInt(m_nX);
		oOut.writeInt(m_nY);
		oOut.writeInt(m_nZ);
	}


	void writeJson(StringBuilder sBuf)
	{
		sBuf.append("{\"x\":").append(m_nX);
		sBuf.append(", \"y\":").append(m_nY);

		if (m_nZ != 0)
			sBuf.append(", \"z\":").append(m_nZ);

		if (m_nW != 0)
			sBuf.append(", \"w\":").append(m_nW);

		sBuf.append('}');
	}
	
	
	void writeXml(StringBuilder sBuf)
	{
		sBuf.append("<PathNode><x>").append(m_nX).append("</x><y>").append(m_nY).append("</y>");
//		sBuf.append("<PathNode><x>").append("0").append("</x><y>").append("0").append("</y>");
		if (m_nZ != 0)
			sBuf.append("<z>").append(m_nZ).append("</z>");
		sBuf.append("<width>").append(m_nW).append("</width></PathNode>");
//		sBuf.append("<width>").append("10").append("</width></PathNode>\n");
	}
}
