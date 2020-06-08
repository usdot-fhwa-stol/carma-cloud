/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.pvmt;

import cc.ctrl.CtrlLineArcs;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import cc.geosrv.Proj;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Text;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Iterator;


/**
 *
 * @author Federal Highway Administration
 */
public class XodrPvmtParser extends DefaultHandler2
{
	protected boolean m_bCdata;
	protected double m_dS;
	protected double m_dX;
	protected double m_dY;
	protected double m_dH;
	protected double m_dL;
	protected Road m_oRoad;
	protected LaneSection m_oLaneSection;
	protected Lane m_oLane;
	protected Proj m_oProj;
	protected final double[] m_dPoint = new double[2];
	protected final StringBuilder m_sBuf = new StringBuilder();
	protected final ArrayList<CtrlLineArcs> m_oCtrlLineArcs = new ArrayList();
	protected String m_sCtrlFile;
	protected String m_sBaseDir;
	protected double m_dMaxStep = 0.06;
	protected double m_dLineTol = 0.1;
	protected boolean m_bSignal;
	protected double[] m_dBB = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
	protected SimpleDateFormat m_oSDF = new SimpleDateFormat("HH:mm:ss:SSS");
	protected boolean m_bLane = false;
	protected int m_nShort = 0;
	protected int m_nRoads = 0;


	public XodrPvmtParser()
	{
		super();
	}
	
		
	@Override
	public void startCDATA()
	{
		m_sBuf.setLength(0);
		m_bCdata = true;
	}


	@Override
	public void endCDATA()
	   throws SAXException
	{
		try
		{
			Text.removeWhitespace(m_sBuf);
			m_oProj = new Proj(m_sBuf.toString(), "epsg:3785"); // projection object used to convert xodr geometry to spherical mercator
//			m_oProj = new Proj(m_sBuf.toString(), "epsg:4326"); // projection object used to convert xodr geometry to lon/lats
		}
		catch (Exception oEx)
		{
			throw new SAXException(oEx);
		}
		m_bCdata = false;
	}


	@Override
	public void characters(char[] cBuf, int nPos, int nLen)
	{
		if (m_bCdata)
			m_sBuf.append(cBuf, nPos, nLen);
	}


	@Override
 	public void startElement(String sUri, String sLocalName, 
		String sQname, Attributes iAtt)
	   throws SAXException
	{
		try
		{
			switch (sQname)
			{
				case "road":
				{
					m_oCtrlLineArcs.clear();
					m_oRoad = new Road(Integer.parseInt(iAtt.getValue("id")), Double.parseDouble(iAtt.getValue("length"))); // new road geometry
					Arrays.add(m_oRoad.m_dTrack, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
					Arrays.add(m_oRoad.m_dLaneZero, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
					break;
				}
				case "geometry":
				{
					m_dS = Double.parseDouble(iAtt.getValue("s"));
					m_dX = Double.parseDouble(iAtt.getValue("x"));
					m_dY = Double.parseDouble(iAtt.getValue("y"));
					m_dH = Double.parseDouble(iAtt.getValue("hdg"));
					m_dL = Double.parseDouble(iAtt.getValue("length"));
					break;
				}
				case "line":
				{
					m_oRoad.m_oGeometries.add(new Line(m_dS, m_dX, m_dY, m_dH, m_dL));
					break;
				}
				case "arc":
				{
					m_oRoad.m_oGeometries.add(new Arc(m_dS, m_dX, m_dY, m_dH, m_dL, Double.parseDouble(iAtt.getValue("curvature"))));
					break;
				}
				case "laneOffset":
				{
					double dS = Double.parseDouble(iAtt.getValue("s"));
					double dA = Double.parseDouble(iAtt.getValue("a"));
					double dB = Double.parseDouble(iAtt.getValue("b"));
					double dC = Double.parseDouble(iAtt.getValue("c"));
					double dD = Double.parseDouble(iAtt.getValue("d"));
					m_oRoad.m_oLaneOffsets.add(new LaneOffset(dS, dA, dB, dC, dD));
					break;
				}
				case "laneSection":
				{
					m_oLaneSection = new LaneSection(Double.parseDouble(iAtt.getValue("s")), m_oRoad.size());
					break;
				}
				case "lane":
				{
					m_oLane = new Lane(Integer.parseInt(iAtt.getValue("id")), iAtt.getValue("type"), m_oRoad.m_nId, m_oRoad.size(), m_oRoad.m_nMaxSpeed);
					m_bLane = true;
					break;
				}
				case "width":
				{
					double dS = Double.parseDouble(iAtt.getValue("sOffset"));
					double dA = Double.parseDouble(iAtt.getValue("a"));
					double dB = Double.parseDouble(iAtt.getValue("b"));
					double dC = Double.parseDouble(iAtt.getValue("c"));
					double dD = Double.parseDouble(iAtt.getValue("d"));
					m_oLane.add(new LaneWidth(dS, dA, dB, dC, dD));
					break;
				}
				default:
					break;

			}
			
			
		}
		catch (Exception oEx)
		{
			throw new SAXException(oEx);
		}
	}
	
	
	@Override
 	public void endElement(String sUri, String sLocalName, String sQname)
	   throws SAXException
	{
		switch (sQname)
		{
			case "road":
			{
				if (m_oRoad.m_oLaneOffsets.isEmpty())
					m_oRoad.m_oLaneOffsets.add(new LaneOffset(0.0, 0.0, 0.0, 0.0, 0.0));
				Collections.sort(m_oRoad.m_oLaneOffsets);
				Collections.sort(m_oRoad.m_oGeometries);
				m_oRoad.adjustSections(m_dMaxStep);
				m_oRoad.createPoints(m_dMaxStep, m_oProj);
				m_oRoad.setLaneIds(m_dMaxStep);
				for (LaneSection oS : m_oRoad)
					oS.setInnerPaths();
				m_oRoad.getPavementCtrlLineArcs(m_oCtrlLineArcs, m_dLineTol);
				m_oRoad.getShoulderCtrlLineArcs(m_oCtrlLineArcs, m_dLineTol);
				writeClas("pavement");
				break;
			}
			case "laneSection":
			{
				m_oLaneSection.sortLanes();
				m_oRoad.add(m_oLaneSection);
				break;
			}
			case "lane":
			{
				Collections.sort(m_oLane);
				if (m_oLane.m_nLaneIndex == 0)
					m_oLaneSection.m_oCenter = m_oLane;
				m_oLaneSection.add(m_oLane);
				m_bLane = false;
				break;
			}
			default:
				break;
		}
	}
	
	
	private void writeClas(String sType)
	{
		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(m_sBaseDir + sType + "/" + m_sCtrlFile), FileUtil.APPENDTO, FileUtil.FILEPERS))))
		{
			for (CtrlLineArcs oCLA : m_oCtrlLineArcs)
				oCLA.write(oOut);
		}
		catch (IOException oEx)
		{
			oEx.printStackTrace();
		}
	}
	
	
	public void parseXodrToCLA(Path oXodrFile, String sDir)
	   throws Exception
	{
		if (!sDir.endsWith("/"))
			sDir += '/';
		m_sBaseDir = sDir;
		m_sCtrlFile = oXodrFile.getFileName().toString().replace(".xodr.pvmt", ".bin.pvmt");
		
		XMLReader iXmlReader = SAXParserFactory.newInstance().
		newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
		iXmlReader.parse(new InputSource(new BufferedInputStream(Files.newInputStream(oXodrFile))));
	}
}
