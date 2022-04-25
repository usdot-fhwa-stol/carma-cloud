package cc.ctrl;

import cc.ctrl.proc.ProcCtrl;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.MathUtil;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import com.github.aelstad.keccakj.fips202.Shake256;
import cc.util.Text;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;


public class TrafCtrl extends ArrayList<TrafCtrlPt> implements Comparable<TrafCtrl>
{
	String m_sVersion = "0.1";
	public byte[] m_yId;
	long m_lUpdated;
	public final ArrayList<Integer> m_nVTypes = new ArrayList();

	public long m_lStart; // defaults to max range
	public long m_lEnd = Long.MAX_VALUE;
	int m_nDoW = 127; // every day of week
	ArrayList<int[]> m_nBetween = new ArrayList();
	int m_nOffset = Integer.MIN_VALUE; // flag for null
	int m_nPeriod;
	int m_nSpan;

	public boolean m_bRegulatory = true;
	public int m_nControlType;
	public byte[] m_yControlValue;

	String m_sProj = "epsg:3785"; // spherical mercator for map tiles
	String m_sDatum = "WGS84";
	long m_lTime;
	public int m_nLon;
	public int m_nLat;
	int m_nAlt;
	int m_nHeading;
	public int m_nWidth;
	public String m_sLabel;
	public CtrlGeo m_oFullGeo = null;
	public static Comparator<byte[]> ID_COMP = (byte[] y1, byte[] y2) -> 
	{
		int nReturn = 0;
		for (int nIndex = 0; nIndex < y1.length; nIndex++)
		{
			nReturn = Byte.compare(y1[nIndex], y2[nIndex]);
			if (nReturn != 0)
				return nReturn;
		}
		
		return nReturn;
	};


	public TrafCtrl()
	{
		super();
		m_bRegulatory = true;
		m_sLabel = "";
		if (m_nVTypes.isEmpty())
		{
			for (int nIndex = 3; nIndex < TrafCtrlEnums.VTYPES.length - 2; nIndex++)
				m_nVTypes.add(nIndex); // exclude pedestrians, rail, and unknown
			
			Collections.sort(m_nVTypes);
		}
	}
	
	
	public TrafCtrl(String sControlType, int nControlValue, long lTime, TrafCtrl oCtrl, String sLabel, boolean bReg, byte ySrc)
	{
		this(sControlType, nControlValue, null, lTime, 0, oCtrl, sLabel, bReg, ySrc);
	}
	
	
	public TrafCtrl(String sControlType, int nControlValue, ArrayList<Integer> nVTypes, long lTime, long lStart, TrafCtrl oCtrl, String sLabel, boolean bReg, byte ySrc)
	{
		this();
		m_nControlType = TrafCtrlEnums.getCtrl(sControlType);
		if (nControlValue == Integer.MIN_VALUE)
			m_yControlValue = new byte[0];
		else
			m_yControlValue = MathUtil.intToBytes(nControlValue, new byte[4]);
		m_lTime = lTime;
		m_lStart = lStart;
		m_nLon = oCtrl.m_nLon;
		m_nLat = oCtrl.m_nLat;
		m_nHeading = oCtrl.m_nHeading;
		m_sLabel = sLabel;
		m_bRegulatory = bReg;
		m_nWidth = oCtrl.m_nWidth;
		for (TrafCtrlPt oPt : oCtrl)
			add(new TrafCtrlPt(oPt.m_nX, oPt.m_nY, oPt.m_nZ, oPt.m_nW));
		
		if (nVTypes != null)
		{
			m_nVTypes.clear();
			Collections.sort(nVTypes);
			for (Integer nVtype : nVTypes)
				m_nVTypes.add(nVtype);
			
		}
		
		generateId(ySrc);
	}
	
	
	public TrafCtrl(String sControlType, String sControlValue, long lTime, TrafCtrl oCtrl, String sLabel, boolean bReg, byte ySrc)
	{
		this(sControlType, TrafCtrlEnums.getCtrlVal(sControlType, sControlValue), lTime, oCtrl, sLabel, bReg, ySrc);
	}
	
	public TrafCtrl(String sControlType, int nControlValue, ArrayList<Integer> nVtypes, long lTime, long lStart, double[] dLineArcs, String sLabel, boolean bReg, byte ySrc)
	{
		this(sControlType, lTime, dLineArcs);
		m_sLabel = sLabel;
		m_bRegulatory = bReg;
		m_lStart = lStart;
		if (nControlValue == Integer.MIN_VALUE)
			m_yControlValue = new byte[0];
		else
			m_yControlValue = MathUtil.intToBytes(nControlValue, new byte[4]);
		
		if (nVtypes != null)
		{
			m_nVTypes.clear();
			Collections.sort(nVtypes);
			for (Integer nVtype : nVtypes)
				m_nVTypes.add(nVtype);
		}
		
		generateId(ySrc);
	}
	
	public TrafCtrl(String sControlType, int nControlValue, long lTime, double[] dLineArcs, String sLabel, boolean bReg, byte ySrc)
	{
		this(sControlType, nControlValue, null, lTime, 0, dLineArcs, sLabel, bReg, ySrc);
	}
	
	
	public TrafCtrl(String sControlType, byte[] yControlValue, long lTime, double[] dLineArcs, byte ySrc)
	{
		this(sControlType, lTime, dLineArcs);
		m_yControlValue = new byte[yControlValue.length];
		System.arraycopy(yControlValue, 0, m_yControlValue, 0, m_yControlValue.length);
		
		generateId(ySrc);
	}
	
	
	public TrafCtrl(String sControlType, long lTime, double[] dLineArcs)
	{
		this();
		m_nControlType = TrafCtrlEnums.getCtrl(sControlType);
		m_lTime = lTime;
		m_nLon = Geo.toIntDeg(Mercator.xToLon(MathUtil.round(dLineArcs[5], 2)));
		m_nLat = Geo.toIntDeg(Mercator.yToLat(MathUtil.round(dLineArcs[6], 2)));
		m_nWidth = Mercator.mToCm(dLineArcs[7]);
		int[] nPrevPt = new int[]{Mercator.mToCm(dLineArcs[5]), Mercator.mToCm(dLineArcs[6]), m_nWidth};
		double dHdg = Geo.heading(nPrevPt[0], nPrevPt[1], dLineArcs[8], dLineArcs[9]);
		dHdg = Math.toDegrees(dHdg);
		m_nHeading = (int)(dHdg * 10 + 0.5);
		Iterator<double[]> oIt = Arrays.iterator(dLineArcs, new double[3], 5, 3);
		while (oIt.hasNext())
		{
			double[] dPt = oIt.next();
			int nX = Mercator.mToCm(dPt[0]);
			int nY = Mercator.mToCm(dPt[1]);
			int nW = Mercator.mToCm(dPt[2]);
			int nXd = nX - nPrevPt[0];
			int nYd = nY - nPrevPt[1];
			int nWd = nW - nPrevPt[2];
			TrafCtrlPt oTemp = new TrafCtrlPt(nXd, nYd, nWd);
			add(oTemp);
			nPrevPt[0] = nX;
			nPrevPt[1] = nY;
			nPrevPt[2] = nW;
		}
	}
	
	
	public TrafCtrl(String sControlType, String sControlValue, long lTime, double[] dLineArcs, String sLabel, boolean bReg, byte ySrc)
	{
		this(sControlType, TrafCtrlEnums.getCtrlVal(sControlType, sControlValue), lTime, dLineArcs, sLabel, bReg, ySrc);
	}
	
	
	public TrafCtrl(DataInputStream oIn, boolean bConcat)
		throws Exception
	{
		m_sVersion = oIn.readUTF();
		m_yId = new byte[16];
		oIn.read(m_yId); // might need read loop
		m_lUpdated = oIn.readLong();

		int nCount = oIn.readInt();
		m_nVTypes.ensureCapacity(nCount);
		while (nCount-- > 0)
			m_nVTypes.add(oIn.readInt());
		Collections.sort(m_nVTypes);

		m_lStart = oIn.readLong();
		m_lEnd = oIn.readLong();
		m_nDoW = oIn.readInt();
		nCount = oIn.readInt();
		m_nBetween.ensureCapacity(nCount);
		while (nCount-- > 0)
			m_nBetween.add(new int[]{oIn.readInt(), oIn.readInt()});

		m_nOffset = oIn.readInt();
		m_nPeriod = oIn.readInt();
		m_nSpan = oIn.readInt();

		m_bRegulatory = oIn.readBoolean();
		m_nControlType = oIn.readInt();

		m_yControlValue = new byte[oIn.readInt()];
		oIn.read(m_yControlValue);


		m_sProj = oIn.readUTF();
		m_sDatum = oIn.readUTF();
		m_lTime = oIn.readLong();
		m_nLon = oIn.readInt();
		m_nLat = oIn.readInt();
		m_nAlt = oIn.readInt();
		m_nWidth = oIn.readInt();
		m_nHeading = oIn.readInt();
		m_sLabel = oIn.readUTF();

		nCount = oIn.readInt();
		ensureCapacity(nCount);
		while (nCount-- > 0)
			add(new TrafCtrlPt(oIn));

		if (bConcat)
		{
			oIn.skip(16); // skip length and average width, both doubles
			for (int nIndex = 0; nIndex < 3; nIndex++) // there are 3 sets of points: center, nt, pt
			{
				int nLen = oIn.readInt(); // read array length
				oIn.skip(8); // skip start x and start y, both ints
				oIn.skip(nLen * 2 - 2); // skip the rest of the point which are deltas written as bytes
			}
		}
	}


	private void generateId(byte ySrc)
	{
		if (m_yId != null)
			return;

		Shake256 oMd = new Shake256();
		try
		(
			DataOutputStream oAbsorb = new DataOutputStream(oMd.getAbsorbStream());
			InputStream oSqueeze = oMd.getSqueezeStream();
		)
		{
			oAbsorb.writeLong(m_lUpdated);
			for (Integer nVal : m_nVTypes)
				oAbsorb.writeByte(nVal); // unbox and force to byte

			oAbsorb.writeLong(m_lStart); // include required schedule parameters
			oAbsorb.writeLong(m_lEnd);
			oAbsorb.writeByte(m_nDoW);

			for (int[] nBegDur : m_nBetween) // auto-skip empty array
			{
				oAbsorb.writeShort(nBegDur[0]);
				oAbsorb.writeShort(nBegDur[1]);
			}

			if (m_nOffset >= 0) // skip null schedule repeat parameters
			{
				oAbsorb.writeShort(m_nOffset);
				oAbsorb.writeShort(m_nPeriod);
				oAbsorb.writeShort(m_nSpan);
			}

			oAbsorb.writeBoolean(m_bRegulatory);
			oAbsorb.writeByte(m_nControlType);
			oAbsorb.write(m_yControlValue);

			oAbsorb.writeLong(m_lTime);
			oAbsorb.writeInt(m_nLon);
			oAbsorb.writeInt(m_nLat);
			oAbsorb.writeInt(m_nAlt);
			oAbsorb.writeInt(m_nWidth);
			oAbsorb.writeShort(m_nHeading);

			for (TrafCtrlPt oPt : this) // include path points
			{
				oAbsorb.writeInt(oPt.m_nX);
				oAbsorb.writeInt(oPt.m_nY);

				if (oPt.m_nW >= 0)
					oAbsorb.writeInt(oPt.m_nW);

				if (oPt.m_nZ >= 0)
					oAbsorb.writeInt(oPt.m_nZ);
			}

			byte[] yId = new byte[15];
			oSqueeze.read(yId);
			m_yId = new byte[16];
			m_yId[0] = ySrc;
			System.arraycopy(yId, 0, m_yId, 1, yId.length);
		}
		catch (Exception oEx)
		{
		}
	}

	
	public void write(String sPath, double dExplodeStep, int nZoom, byte ySrc)
		throws Exception
	{
		generateId(ySrc); // need id to build file path
		StringBuilder sBuf = new StringBuilder(sPath);
		if (sPath.endsWith("/"))
			sBuf.setLength(sBuf.length() - 1); // remove trailing path separator

		int nOffset = 0;
		int nCount = 3;
		while (nCount-- > 0)
		{
			sBuf.append("/");
			Text.toHexString(m_yId, nOffset, nOffset + 4, sBuf);
			nOffset += 4;
		}

		sBuf.append("/"); // finish path with filename
		Text.toHexString(m_yId, nOffset, nOffset + 4, sBuf);

		writeBin(sBuf, dExplodeStep, nZoom);
		writeJson(sBuf);
	}


	protected void writeBin(StringBuilder sBuf, double dExplodeStep, int nZoom)
		throws Exception
	{
		sBuf.append(".bin");
		Path oPath = Paths.get(sBuf.toString());
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		try (DataOutputStream oOut = new DataOutputStream(FileUtil.newOutputStream(oPath)))
		{
			oOut.writeUTF(m_sVersion);
			oOut.write(m_yId);
			oOut.writeLong(m_lUpdated);

			oOut.writeInt(m_nVTypes.size());
			for (Integer nVal : m_nVTypes)
				oOut.writeInt(nVal);

			oOut.writeLong(m_lStart);
			oOut.writeLong(m_lEnd);
			oOut.writeInt(m_nDoW);
			oOut.writeInt(m_nBetween.size());
			for (int[] nBegDur : m_nBetween)
			{
				oOut.write(nBegDur[0]);
				oOut.write(nBegDur[1]);
			}
			oOut.writeInt(m_nOffset);
			oOut.writeInt(m_nPeriod);
			oOut.writeInt(m_nSpan);

			oOut.writeBoolean(m_bRegulatory);
			oOut.writeInt(m_nControlType);
			oOut.writeInt(m_yControlValue.length);
			oOut.write(m_yControlValue);

			oOut.writeUTF(m_sProj);
			oOut.writeUTF(m_sDatum);
			oOut.writeLong(m_lTime);
			oOut.writeInt(m_nLon);
			oOut.writeInt(m_nLat);
			oOut.writeInt(m_nAlt);
			oOut.writeInt(m_nWidth);
			oOut.writeInt(m_nHeading);
			oOut.writeUTF(m_sLabel == null ? "" : m_sLabel);
			
			oOut.writeInt(size());
			for (TrafCtrlPt oPt : this)
				oPt.writeBin(oOut);
			
			
			if (m_oFullGeo == null)
				m_oFullGeo = new CtrlGeo(this, dExplodeStep, nZoom);
			for (int nIndex = 0; nIndex < m_oFullGeo.m_dBB.length; nIndex++)
				oOut.writeInt((int)(MathUtil.round(m_oFullGeo.m_dBB[nIndex] * 100.0, 0)));
			oOut.writeDouble(m_oFullGeo.m_dLength);
			oOut.writeDouble(m_oFullGeo.m_dAverageWidth);
			CtrlGeo.writePts(oOut, m_oFullGeo.m_dC); // center line
			CtrlGeo.writePts(oOut, m_oFullGeo.m_dNT); // negative tangent line
			CtrlGeo.writePts(oOut, m_oFullGeo.m_dPT); // positive tangent line
			
//			if (Math.abs(m_oFullGeo.m_nTileIndices[2] - m_oFullGeo.m_nTileIndices[0]) > 10 ||
//				Math.abs(m_oFullGeo.m_nTileIndices[3] - m_oFullGeo.m_nTileIndices[1]) > 10)
//				 throw new Exception("Ctrl spans too many tiles");
//			for (int nX = m_oFullGeo.m_nTileIndices[0]; nX <= m_oFullGeo.m_nTileIndices[2]; nX++)
//			{
//				for (int nY = m_oFullGeo.m_nTileIndices[1]; nY <= m_oFullGeo.m_nTileIndices[3]; nY++)
//				{
//					Path oFile = Paths.get(String.format("", nX, nZoom, nX, nY));
//					Files.createDirectories(oFile.getParent(), FileUtil.DIRPERS);
//					try (DataOutputStream oIndexFile = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(oFile, FileUtil.APPENDTO, FileUtil.FILEPERS))))
//					{
//						oIndexFile.write(m_yId);
//						oIndexFile.writeInt(m_nControlType);
//					}
//				}
//			}
		}
	}
	
	
	public void writeIndex(DataOutputStream oOut)
	   throws IOException
	{
		oOut.writeInt(m_nControlType);
		oOut.write(m_yId);
		oOut.writeLong(m_lStart);
		oOut.writeLong(m_lEnd);
	}


	protected void writeJson(StringBuilder sBuf)
		throws Exception
	{
		sBuf.append(".json");
		Path oPath = Paths.get(sBuf.toString());
		Files.createDirectories(oPath.getParent(), FileUtil.DIRPERS);
		
		sBuf.setLength(0); // reset JSON output buffer
		sBuf.append("{\n"); // open ctrl object
		sBuf.append("\t\"version\":\"").append(m_sVersion).append("\",\n");

		sBuf.append("\t\"id\":\"");
		Text.toHexString(m_yId, 0, m_yId.length, sBuf);
		sBuf.append("\",\n");

		SimpleDateFormat oFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sBuf.append("\t\"updated\":\"").append(oFormat.format(new Date(m_lUpdated))).append("\",\n");

		sBuf.append("\t\"vtypes\":\n\t["); // start vtype array
		m_nVTypes.forEach((nVType) -> {sBuf.append("\n\t\t\"").append(TrafCtrlEnums.VTYPES[nVType]).append("\",");});
		sBuf.setLength(sBuf.length() - 1); // remove trailing comma
		sBuf.append("\n\t],\n"); // end vtype array

		sBuf.append("\t\"schedule\":\n\t{\n"); // start schedule object
		sBuf.append("\t\t\"start\":\"").append(oFormat.format(new Date(m_lStart))).append("\",\n");
		sBuf.append("\t\t\"end\":\"").append(oFormat.format(new Date(m_lEnd))).append("\",\n");
		sBuf.append("\t\t\"dow\":\"");
		int nCount = 0;
		int nDoW = m_nDoW; // bit-shift day-of-week characters
		while (nDoW != 0)
		{
			if ((nDoW & 1) > 0)
				sBuf.append(TrafCtrlEnums.DAYCHARS[nCount]);

			++nCount;
			nDoW >>= 1;
		}
		sBuf.append("\",\n"); // end day-of-week

		if (!m_nBetween.isEmpty()) // skip empty schedule between parameters
		{
			sBuf.append("\t\t\"between\":\n\t\t["); // start between array
			for (int[] nBegDur : m_nBetween)
			{
				sBuf.append("\n\t\t\t{\"begin\":").append(nBegDur[0]);
				sBuf.append(", \"duration\":").append(nBegDur[1]).append("},");
			}
			sBuf.setLength(sBuf.length() - 1); // remove trailing comma
			sBuf.append("\n\t\t],\n"); // end between array
		}

		if (m_nOffset >= 0) // skip empty schedule repeat parameters
		{
			sBuf.append("\t\t\"repeat\":\n\t\t\t{\n"); // start schedule repeat object
			sBuf.append("\t\t\t\"offset\":").append(m_nOffset).append("\n");
			sBuf.append("\t\t\t\"period\":").append(m_nPeriod).append("\n");
			sBuf.append("\t\t\t\"span\":").append(m_nSpan).append("\n");
			sBuf.append("\t\t},\n"); // end schedule repeat object
		}
		sBuf.setLength(sBuf.length() - 2); // remove comma and newline
		sBuf.append("\n\t},\n"); // end schedule object

		sBuf.append("\t\"regulatory\":").append(m_bRegulatory).append(",\n");
		String[] sCtrl = TrafCtrlEnums.CTRLS[m_nControlType];
		sBuf.append("\t\"controltype\":\"").append(sCtrl[0]).append("\",\n");
		if (m_yControlValue.length > 0) // ignore null values
			sBuf.append("\t\"controlvalue\":\"").append(MathUtil.bytesToInt(m_yControlValue)).append("\",\n");
//			sBuf.append("\t\"controlvalue\":\"").append(sCtrl[m_nControlValue]).append("\",\n");

		sBuf.append("\t\"proj\":\"").append(m_sProj).append("\",\n");
		sBuf.append("\t\"datum\":\"").append(m_sDatum).append("\",\n");
		sBuf.append("\t\"label\":\"").append(m_sLabel).append("\",\n");
		sBuf.append("\t\"time\":\"").append(oFormat.format(new Date(m_lTime))).append("\",\n");
		
		sBuf.append("\t\"lon\":").append(m_nLon).append(",\n");
		sBuf.append("\t\"lat\":").append(m_nLat).append(",\n");
		sBuf.append("\t\"alt\":").append(m_nAlt).append(",\n");
		sBuf.append("\t\"heading\":").append(m_nHeading).append(",\n");
		sBuf.append("\t\"points\":\n\t["); // start point array
		for (TrafCtrlPt oPt : this) // include path points
		{
			sBuf.append("\n\t\t");
			oPt.writeJson(sBuf);
			sBuf.append(",");
		}
		sBuf.setLength(sBuf.length() - 1); // remove trailing comma
		sBuf.append("\n\t]\n"); // end point array
		sBuf.append("}\n"); // close ctrl object

		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(oPath, FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
		{
			for (nCount = 0; nCount < sBuf.length(); nCount++)
				oOut.write(sBuf.charAt(nCount));
		}
	}
	
	
	public void getXml(StringBuilder sBuf, String sReqId, int nReqSeq, int nMsgNum, int nMsgTot, String sVersion, boolean bIncludeParams, int nPtsIndex)
	   throws IOException
	{
		sBuf.setLength(0);
//		if (sVersion.compareTo("1.0") != 0)
//		{
//			sBuf.append("invalid version");
//			return;
//		}
		sBuf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<TrafficControlMessage>\n");
		sBuf.append("\t<tcmV01>\n");
		sBuf.append("\t\t<reqid>").append(sReqId).append("</reqid>\n");
		sBuf.append("\t\t<reqseq>").append(nReqSeq).append("</reqseq>\n");
		sBuf.append("\t\t<msgtot>").append(nMsgTot).append("</msgtot>\n");
		sBuf.append("\t\t<msgnum>").append(nMsgNum).append("</msgnum>\n");
		sBuf.append("\t\t<id>");
		Text.toHexString(m_yId, 0, m_yId.length, sBuf);
		sBuf.append("</id>\n");
		sBuf.append("\t\t<updated>").append(m_lUpdated / 1000 / 60).append("</updated>\n"); // convert to epoch minutes
		
		sBuf.append("\t\t<package>\n");
		if (m_sLabel != null && !m_sLabel.isEmpty())
		{
			sBuf.append("\t\t\t<label>").append(Text.truncate(m_sLabel, 63)).append("</label>\n");
		}
		else
		{
			sBuf.append("\t\t\t<label>null</label>\n");
		}
			
		sBuf.append("\t\t\t<tcids>\n");
		sBuf.append("\t\t\t\t<Id128b>");
		Text.toHexString(m_yId, 0, m_yId.length, sBuf);
		sBuf.append("</Id128b>\n");
		sBuf.append("\t\t\t</tcids>\n");
		sBuf.append("\t\t</package>\n");
			
		if (bIncludeParams)
		{
			sBuf.append("\t\t<params>\n");
			sBuf.append("\t\t\t<vclasses>\n");
			for (int nIndex = 0; nIndex < m_nVTypes.size(); nIndex++)
				sBuf.append("\t\t\t\t<").append(TrafCtrlEnums.VTYPES[m_nVTypes.get(nIndex)]).append("/>\n");
			sBuf.append("\t\t\t</vclasses>\n");
			sBuf.append("\t\t\t<schedule>\n");
			sBuf.append("\t\t\t\t<start>").append(m_lStart / 1000 / 60).append("</start>\n"); // convert to epoch minutes
			sBuf.append("\t\t\t\t<end>").append(m_lEnd / 1000 / 60).append("</end>\n"); // convert to epoch minutes
			sBuf.append("\t\t\t\t<dow>");
			int nDoW = m_nDoW; // bit-shift day-of-week characters
			for (int nDay = 0; nDay < 7; nDay++)
			{
				if ((nDoW & 1) > 0)
					sBuf.append('1');
				else
					sBuf.append('0');
				nDoW >>= 1;
			}

			sBuf.append("</dow>\n");

			if (!m_nBetween.isEmpty())
			{
				sBuf.append("\t\t\t\t<between>\n");
				for (int[] nBegDur : m_nBetween)
				{
					sBuf.append("\t\t\t\t\t<DailySchedule>\n");
					sBuf.append("\t\t\t\t\t\t<begin>").append(nBegDur[0]).append("</begin>\n");
					sBuf.append("\t\t\t\t\t\t<duration>").append(nBegDur[1]).append("</duration>\n");
					sBuf.append("\t\t\t\t\t</DailySchedule>\n");

				}
				sBuf.append("\t\t\t\t</between>\n");
			}

			if (m_nOffset >= 0)
			{
				sBuf.append("\t\t\t\t<repeat>\n");
				sBuf.append("\t\t\t\t\t<offset>").append(m_nOffset).append("</offset>\n");
				sBuf.append("\t\t\t\t\t<period>").append(m_nPeriod).append("</period>\n");
				sBuf.append("\t\t\t\t\t<span>").append(m_nSpan).append("</span>\n");
				sBuf.append("\t\t\t\t</repeat>\n");
			}

			sBuf.append("\t\t\t</schedule>\n");
			sBuf.append("\t\t\t<regulatory><").append(m_bRegulatory).append("/></regulatory>\n");
			sBuf.append("\t\t\t<detail>\n");
			ArrayList<String> sVals = new ArrayList(4);
			TrafCtrlEnums.getCtrlValString(m_nControlType, m_yControlValue, sVals);
			String sTag = sVals.get(0);
			sBuf.append("\t\t\t\t<").append(sTag);
			if (sVals.size() == 1) // null value so empty tag
				sBuf.append("/>");
			else
			{
				if (TrafCtrlEnums.CTRLS[m_nControlType].length == 1)
					sBuf.append(">").append(sVals.get(1)).append("</").append(sTag).append(">");
				else // enumerated value that needs to be an empty tag, not a value
				{
					if (sVals.size() == 2)
						sBuf.append("><").append(sVals.get(1)).append("/></").append(sTag).append(">");
					if (sVals.size() == 4)
						sBuf.append("><").append(sVals.get(1)).append("/><").append(sVals.get(3)).append("/></").append(sTag).append(">");
				}
			}
			sBuf.append("\n");
			sBuf.append("\t\t\t</detail>\n");
			sBuf.append("\t\t</params>\n");
		}
		sBuf.append("\t\t<geometry>\n");
		
		sBuf.append("\t\t\t<proj>").append(m_sProj).append("</proj>\n");
		sBuf.append("\t\t\t<datum>").append(m_sDatum).append("</datum>\n");
		sBuf.append("\t\t\t<reftime>").append(m_lTime / 1000 / 60).append("</reftime>\n"); // convert to EpochMins
		sBuf.append("\t\t\t<reflon>").append(m_nLon).append("</reflon>\n");
		sBuf.append("\t\t\t<reflat>").append(m_nLat).append("</reflat>\n");
		sBuf.append("\t\t\t<refelv>").append(m_nAlt).append("</refelv>\n");
		sBuf.append("\t\t\t<refwidth>").append(m_nWidth).append("</refwidth>\n");
		sBuf.append("\t\t\t<heading>").append(m_nHeading).append("</heading>\n");
		sBuf.append("\t\t\t<nodes>\n");
		int nEnd = Math.min(size(), (nPtsIndex / 256 + 1) * 256);
		for (int nIndex = nPtsIndex; nIndex < nEnd; nIndex++)
		{
			sBuf.append("\t\t\t\t");
			get(nIndex).writeXml(sBuf);
		}
		sBuf.append("\t\t\t</nodes>\n");
		sBuf.append("\t\t</geometry>\n");
		sBuf.append("\t</tcmV01>\n");
		sBuf.append("</TrafficControlMessage>");
	}
	
	
	public static String getId(byte[] yId)
	{
		StringBuilder sBuf = new StringBuilder(16);
		int nOffset = 0;
		int nCount = 4;
		while (nCount-- > 0)
		{
			sBuf.append("/");
			Text.toHexString(yId, nOffset, nOffset + 4, sBuf);
			nOffset += 4;
		}
		return sBuf.toString();
	}
	
	
	public static void getId(byte[] yId, StringBuilder sBuf)
	{
		sBuf.setLength(0);
		int nOffset = 0;
		int nCount = 4;
		while (nCount-- > 0)
		{
			sBuf.append("/");
			Text.toHexString(yId, nOffset, nOffset + 4, sBuf);
			nOffset += 4;
		}
	}
	
	
	public double[] toMercatorPoints()
	{
		double[] dRet = Arrays.newDoubleArray(size() * 2 + 4);
		dRet = Arrays.add(dRet, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
		int nPrevX = Mercator.lonToCm(Geo.fromIntDeg(m_nLon));
		int nPrevY = Mercator.latToCm(Geo.fromIntDeg(m_nLat));
		for (TrafCtrlPt oPt : this)
		{
			int nCurX = nPrevX + oPt.m_nX;
			int nCurY = nPrevY + oPt.m_nY;
			dRet = Arrays.addAndUpdate(dRet, nCurX / 100.0, nCurY / 100.0);
			nPrevX = nCurX;
			nPrevY = nCurY;
		}
		
		return dRet;
	}
	
	
	public void preparePoints(int nExplodeDist)
	{
		if (nExplodeDist == 0)
			return;
		double[] dC = m_oFullGeo.m_dC;
		double[] dNT = m_oFullGeo.m_dNT;
		int nLimit = Arrays.size(dC);
		ArrayList<TrafCtrlPt> oNewPts = new ArrayList();
		int[] nPrevPt = new int[]{Mercator.lonToCm(Geo.fromIntDeg(m_nLon)), Mercator.latToCm(Geo.fromIntDeg(m_nLat)), m_nWidth};
		int nStep = (int)(nExplodeDist / (ProcCtrl.g_dExplodeStep * 100)) * 2;
		for (int nIndex = 1; nIndex < nLimit; nIndex += nStep)
		{
			int nX = Mercator.mToCm(dC[nIndex]);
			int nY = Mercator.mToCm(dC[nIndex + 1]);
			int nW = Mercator.mToCm(Geo.distance(dC[nIndex], dC[nIndex + 1], dNT[nIndex], dNT[nIndex + 1]) * 2);
			int nXd = nX - nPrevPt[0];
			int nYd = nY - nPrevPt[1];
			int nWd = nW - nPrevPt[2];
			TrafCtrlPt oTemp = new TrafCtrlPt(nXd, nYd, nWd);
			oNewPts.add(oTemp);
			nPrevPt[0] = nX;
			nPrevPt[1] = nY;
			nPrevPt[2] = nW;
		}
		
		int nX = Mercator.mToCm(dC[nLimit - 2]);
		int nY = Mercator.mToCm(dC[nLimit - 1]);
		int nW = Mercator.mToCm(Geo.distance(dC[nLimit - 2], dC[nLimit - 1], dNT[nLimit - 2], dNT[nLimit - 1]) * 2);
		int nXd = nX - nPrevPt[0];
		int nYd = nY - nPrevPt[1];
		int nWd = nW - nPrevPt[2];
		if (nXd != 0 || nYd != 0 || nWd != 0)
			oNewPts.add(new TrafCtrlPt(nXd, nYd, nWd));
		
		clear();
		addAll(oNewPts);
	}


	@Override
	public int compareTo(TrafCtrl o)
	{
		return TrafCtrl.ID_COMP.compare(m_yId, o.m_yId);
	}
}
