/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.ctrl.CtrlLineArcs;
import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.BufferedInStream;
import cc.util.FileUtil;
import cc.util.Text;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/**
 *
 * @author aaron.cherney
 */
public class XodrRoadMarkParser extends DefaultHandler2
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
	ArrayList<int[]> m_oRdMkInfo = new ArrayList();


	public XodrRoadMarkParser()
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
					m_oRdMkInfo.clear();
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
					m_oLane = new Lane(Integer.parseInt(iAtt.getValue("id")), iAtt.getValue("type"), m_oRoad.m_nId, m_oRoad.size());
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
				case "roadMark":
				{
					m_oLane.m_oOuterRoadMarks.add(new RoadMark(iAtt.getValue("type"), iAtt.getValue("color"), Double.parseDouble(iAtt.getValue("sOffset"))));
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
//				m_oRoad.setInnerRoadMarks();
				m_oRoad.createPoints(m_dMaxStep, m_oProj);
				int nLanes = 0;
				if (m_oRoad.m_dLength < m_dMaxStep)
				{
					System.out.println(String.format("%d\t\t%f", m_oRoad.m_nId, m_oRoad.m_dLength));
					break;
				}
				for (LaneSection oS : m_oRoad)
				{
					int nTemp = oS.m_oLeft.size() + oS.m_oRight.size();
					if (nTemp > nLanes)
						nLanes = nTemp;
				}
				if (nLanes < 3)
				{
//					System.out.println(String.format("%d\t\t%d", m_oRoad.m_nId, nLanes));
					break;
				}
				m_oRoad.setLaneIds(m_dMaxStep);
				m_oRoad.getRdMkCtrlLineArcs(m_oCtrlLineArcs, m_dLineTol, m_oRdMkInfo);
				writeClas("rdmks");
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
				break;
			}
			default:
				break;
		}
	}
	
	
	private void writeClas(String sType)
	{
		try 
		(
		   DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(m_sBaseDir + sType + "/" + m_sCtrlFile), FileUtil.APPENDTO, FileUtil.FILEPERS)));
		   DataOutputStream oMeta = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(m_sBaseDir + sType + "/" + m_sCtrlFile + ".meta"), FileUtil.APPENDTO, FileUtil.FILEPERS)))
		)
		{
			for (CtrlLineArcs oCLA : m_oCtrlLineArcs)
			{
				oCLA.write(oOut);
			}
			for (int[] nTags : m_oRdMkInfo)
			{
				Iterator<int[]> oIt = Arrays.iterator(nTags, new int[1], 1, 1);
				oMeta.writeInt(Arrays.size(nTags));
				while (oIt.hasNext())
				{
					oMeta.writeInt(oIt.next()[0]);
				}
			}
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
		Path oDir = Paths.get(sDir);
		Files.createDirectories(oDir, FileUtil.DIRPERS);
		Files.createDirectories(Paths.get(m_sBaseDir + "rdmks"), FileUtil.DIRPERS);
		m_sCtrlFile = oXodrFile.getFileName().toString().replace(".xodr", ".bin");
		Path oRdMkFile = Paths.get(m_sBaseDir + "rdmks/" + m_sCtrlFile);

		if (Files.exists(oRdMkFile) && Files.isRegularFile(oRdMkFile))
		   Files.delete(oRdMkFile);
		XMLReader iXmlReader = SAXParserFactory.newInstance().
		newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
		iXmlReader.parse(new InputSource(new BufferedInputStream(Files.newInputStream(oXodrFile))));

	}
}
