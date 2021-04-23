package cc.geosrv.xodr.geo;

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
import cc.util.Geo;
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
public class XodrGeoParser extends DefaultHandler2
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
	protected String m_sTrackFile;
	protected boolean m_bSkip = false;


	public XodrGeoParser(String sTrackFile)
	{
		super();
		m_sTrackFile = sTrackFile;
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
					m_bSkip = false;
					m_oCtrlLineArcs.clear();
					m_oRoad = new Road(Integer.parseInt(iAtt.getValue("id")), Double.parseDouble(iAtt.getValue("length"))); // new road geometry
//					System.out.println(String.format("%d\t%5.2f\t%s", m_oRoad.m_nId, m_oRoad.m_dLength, m_oSDF.format(System.currentTimeMillis())));
					Arrays.add(m_oRoad.m_dTrack, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
					Arrays.add(m_oRoad.m_dLaneZero, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
					Arrays.add(m_oRoad.m_dNoProj, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
					break;
				}
				case "geometry":
				{
					m_dS = XodrUtil.parseDouble(iAtt.getValue("s"));
					m_dX = XodrUtil.parseDouble(iAtt.getValue("x"));
					m_dY = XodrUtil.parseDouble(iAtt.getValue("y"));
					m_dH = XodrUtil.parseDouble(iAtt.getValue("hdg"));
					m_dL = XodrUtil.parseDouble(iAtt.getValue("length"));
					break;
				}
				case "spiral":
				case "line":
				{
					if (Double.isFinite(m_dS) && Double.isFinite(m_dX) && Double.isFinite(m_dY) && Double.isFinite(m_dH) && Double.isFinite(m_dL))
						m_oRoad.m_oGeometries.add(new Line(m_dS, m_dX, m_dY, m_dH, m_dL));
					break;
				}
				case "arc":
				{
					double dCurvature = XodrUtil.parseDouble(iAtt.getValue("curvature"));
					if (Double.isFinite(m_dS) && Double.isFinite(m_dX) && Double.isFinite(m_dY) && Double.isFinite(m_dH) && Double.isFinite(m_dL))
						m_oRoad.m_oGeometries.add(new Arc(m_dS, m_dX, m_dY, m_dH, m_dL, dCurvature));
					break;
				}
				case "laneOffset":
				{
					double dS = XodrUtil.parseDouble(iAtt.getValue("s"));
					double dA = XodrUtil.parseDouble(iAtt.getValue("a"));
					double dB = XodrUtil.parseDouble(iAtt.getValue("b"));
					double dC = XodrUtil.parseDouble(iAtt.getValue("c"));
					double dD = XodrUtil.parseDouble(iAtt.getValue("d"));
					if (Double.isFinite(dS) && Double.isFinite(dA) && Double.isFinite(dB) && Double.isFinite(dC) && Double.isFinite(dD))
						m_oRoad.m_oLaneOffsets.add(new LaneOffset(dS, dA, dB, dC, dD));
					break;
				}
				case "laneSection":
				{
					double dS = XodrUtil.parseDouble(iAtt.getValue("s"));
					if (Double.isFinite(dS))
						m_oLaneSection = new LaneSection(dS, m_oRoad.size());
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
					double dS = XodrUtil.parseDouble(iAtt.getValue("sOffset"));
					double dA = XodrUtil.parseDouble(iAtt.getValue("a"));
					double dB = XodrUtil.parseDouble(iAtt.getValue("b"));
					double dC = XodrUtil.parseDouble(iAtt.getValue("c"));
					double dD = XodrUtil.parseDouble(iAtt.getValue("d"));
					if (Double.isFinite(dS) && Double.isFinite(dA) && Double.isFinite(dB) && Double.isFinite(dC) && Double.isFinite(dD))
						m_oLane.add(new LaneWidth(dS, dA, dB, dC, dD));
					break;
				}
				case "speed":
				{
					double dMax = XodrUtil.parseDouble(iAtt.getValue("max"));
					if (Double.isFinite(dMax))
					{
						if (m_bLane)
							m_oLane.m_nMaxSpeed = (int)dMax;
						else
							m_oRoad.m_nMaxSpeed = (int)dMax;
					}
				}
				default:
					break;

			}
			
			
		}
		catch (Exception oEx)
		{
			System.out.println("Error parsing Road " + m_oRoad.m_nId);
			oEx.printStackTrace();
			m_bSkip = true;
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
				if (m_bSkip)
					break;
//				if (++m_nRoads > 1)
//					break;
				if (m_oRoad.m_oLaneOffsets.isEmpty())
					m_oRoad.m_oLaneOffsets.add(new LaneOffset(0.0, 0.0, 0.0, 0.0, 0.0));
				Collections.sort(m_oRoad.m_oLaneOffsets);
				Collections.sort(m_oRoad.m_oGeometries);
				m_oRoad.adjustSections(m_dMaxStep);
				if (m_oRoad.m_oGeometries.isEmpty())
				{
					System.out.println(String.format("Road %d has no geometries.", m_oRoad.m_nId));
					break;
				}
				m_oRoad.createPoints(m_dMaxStep, m_oProj);
				m_oRoad.setLaneIds(m_dMaxStep);
				m_oRoad.getDirectionCtrlLineArcs(m_oCtrlLineArcs, m_dLineTol);
//				int nLanes = 0;
//				System.out.println(String.format("%2.7f, %2.7f", Mercator.xToLon(m_oRoad.m_dTrack[5]), Mercator.yToLat(m_oRoad.m_dTrack[6])));
//				++m_nRoads;
				
//				if (m_oRoad.m_dLength >= 5)
//					break;
//				if (m_oRoad.m_dLength < 5)
//					++m_nShort;
				if (m_oRoad.m_dLength < m_dMaxStep)
				{
					System.out.println(String.format("%d\t\t%f", m_oRoad.m_nId, m_oRoad.m_dLength));
					break;
				}
//				for (LaneSection oS : m_oRoad)
//				{
//					int nTemp = oS.m_oLeft.size() + oS.m_oRight.size();
//					if (nTemp > nLanes)
//						nLanes = nTemp;
//				}
//				if (nLanes < 3)
//				{
//					System.out.println(String.format("%d\t\t%d", m_oRoad.m_nId, nLanes));
//					break;
//				}
//				System.out.println(String.format("%d has %d lanes", m_oRoad.m_nId, nLanes));
				writeClas("direction");
				m_oCtrlLineArcs.clear();
//				m_oRoad.getPavementCtrlLineArcs(m_oCtrlLineArcs, m_dLineTol);
//				writeClas("pavement");
////				writeLaneTypes(m_oRoad);
//				writeTrack();
				writeSpeeds(m_oRoad);
//				System.out.println(String.format("%d\t\t%s", m_oRoad.m_nId, m_oSDF.format(System.currentTimeMillis())));
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
	
	
	private void writeTrack()
	{
		ArrayList<double[]> dLines = new ArrayList();
		dLines.add(m_oRoad.m_dTrack);
//		Iterator<double[]> it = Arrays.iterator(m_oRoad.m_dNoProj, new double[6], 5, 3);
//		int n = 0;
//		while (it.hasNext())
//		{
//			double[] dSeg = it.next();
//			double dDist = Geo.distance(dSeg[0], dSeg[1], dSeg[3], dSeg[4]);
//			double dHdg = Geo.heading(dSeg[0], dSeg[1], dSeg[3], dSeg[4]);
//			System.out.println(String.format("%d\t%3.4f\t%3.4f\t%3.4f\t%3.4f", n++, dDist, dHdg, dSeg[0], dSeg[1]));
//		}
//		dLines.add(m_oRoad.m_dLaneZero);
		for (LaneSection oS : m_oRoad)
		{
			ArrayList<Lane> oLanes = new ArrayList();
			oS.getLanes(oLanes);
			for (Lane oLane : oLanes)
			{
				if (oLane.m_nLaneIndex == 0)
					continue;
				dLines.add(oLane.m_dCenter);
//				dLines.add(oLane.m_dOuterW);
			}
		}
		
		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(m_sTrackFile), FileUtil.APPENDTO, FileUtil.FILEPERS))))
		{
			oOut.writeInt(m_oRoad.m_dGeoPoints.size());
			for (double[] dPt : m_oRoad.m_dGeoPoints)
			{
				oOut.writeDouble(dPt[0]);
				oOut.writeDouble(dPt[1]);
			}
			
			oOut.writeInt(m_oRoad.m_dLSPoints.size());
			for (double[] dPt : m_oRoad.m_dLSPoints)
			{
				oOut.writeDouble(dPt[0]);
				oOut.writeDouble(dPt[1]);
			}
			
			oOut.writeInt(m_oRoad.m_dLWPoints.size());
			for (double[] dPt : m_oRoad.m_dLWPoints)
			{
				oOut.writeDouble(dPt[0]);
				oOut.writeDouble(dPt[1]);
			}
			oOut.writeInt(m_oRoad.m_dPerps.size());
			for (double[] dPt : m_oRoad.m_dPerps)
			{
				oOut.writeDouble(dPt[0]);
				oOut.writeDouble(dPt[1]);
				oOut.writeDouble(dPt[2]);
				oOut.writeDouble(dPt[3]);
			}
			oOut.writeInt(dLines.size());
			for (double[] dTrack : dLines)
			{
	//			double[] dTrack = m_oRoad.m_dTrack;
				if (dTrack[1] < m_dBB[0])
					m_dBB[0] = dTrack[1];
				if (dTrack[2] < m_dBB[1])
					m_dBB[1] = dTrack[2];
				if (dTrack[3] > m_dBB[2])
					m_dBB[2] = dTrack[3];
				if (dTrack[4] > m_dBB[3])
					m_dBB[3] = dTrack[4];

					oOut.writeInt(m_oRoad.m_nId);
					int nOrds = (Arrays.size(dTrack) - 5) / 3 * 2;
					oOut.writeInt(nOrds);
					for (int i = 1; i < 5; i++)
						oOut.writeDouble(dTrack[i]);
					Iterator<double[]> oIt = Arrays.iterator(dTrack, new double[3], 5, 3);
					double[] dPt = oIt.next();
					int nCount = 0;
					double dPx = dPt[0];
					double dPy = dPt[1];
					oOut.writeDouble(dPx);
					oOut.writeDouble(dPy);
					while (oIt.hasNext())
					{
						oIt.next();
						double dX = dPt[0];
						double dY = dPt[1];
						oOut.writeDouble(dX);
						oOut.writeDouble(dY);
//						System.out.println(String.format("%d\t%1.4f\t%1.4f", nCount++, Geo.distance(dPx, dPy, dX, dY), Geo.heading(dPx, dPy, dX, dY)));
						dPx = dX;
						dPy = dY;
					}
				}
		}
		catch (IOException oEx)
		{
			oEx.printStackTrace();
		}
	}
	
	
	private void writeSpeeds(Road oRoad)
	{
		ArrayList<Lane> oLaneList = new ArrayList();
		ArrayList<Lane> oLanesToWrite = new ArrayList();
		for (LaneSection oS : oRoad)
		{
			oS.getLanes(oLaneList);
			for (Lane oLane : oLaneList)
			{
				if (oLane.m_nLaneIndex == 0)
					continue;
				
				int nIndex = Collections.binarySearch(oLanesToWrite, oLane);
				if (nIndex < 0)
					oLanesToWrite.add(~nIndex, oLane);
			}
		}
		
		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(m_sBaseDir + "direction/" + m_sCtrlFile + ".spd"), FileUtil.APPENDTO, FileUtil.FILEPERS))))
		{
			for (Lane oLane : oLanesToWrite)
			{
				oOut.writeInt(XodrUtil.getLaneId(oRoad.m_nId, oLane.m_nLaneIdByRoad));
				oOut.writeInt(oLane.m_nMaxSpeed);
			}
		}
		catch (IOException oEx)
		{
			oEx.printStackTrace();
		}		
	}
//	
//	private void writeLaneTypes(Road oRoad)
//	{
//		ArrayList<Lane> oLaneList = new ArrayList();
//		ArrayList<Lane> oLanesToWrite = new ArrayList();
//		for (LaneSection oS : oRoad)
//		{
//			oS.getLanes(oLaneList);
//			for (Lane oLane : oLaneList)
//			{
//				if (oLane.m_nLaneIndex == 0)
//					continue;
//				
//				int nIndex = Collections.binarySearch(oLanesToWrite, oLane);
//				if (nIndex < 0)
//					oLanesToWrite.add(~nIndex, oLane);
//			}
//		}
//		
//		try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(m_sBaseDir + "direction/" + m_sCtrlFile + ".lanes"), FileUtil.APPENDTO, FileUtil.FILEPERS))))
//		{
//			for (Lane oLane : oLanesToWrite)
//			{
//				oOut.writeInt(XodrUtil.getLaneId(oRoad.m_nId, oLane.m_nLaneIdByRoad));
//				oOut.writeInt(oLane.m_nLaneType);
//			}
//		}
//		catch (IOException oEx)
//		{
//			oEx.printStackTrace();
//		}
//		
//	}
	
	
	public boolean parseXodrToCLA(Path oXodrFile, String sDir)
	   throws Exception
	{
		if (!sDir.endsWith("/"))
			sDir += '/';
		m_sBaseDir = sDir;
		Path oDir = Paths.get(sDir);
		Files.createDirectories(oDir, FileUtil.DIRPERS);
		Files.createDirectories(Paths.get(m_sBaseDir + "pavement"), FileUtil.DIRPERS);
		Files.createDirectories(Paths.get(m_sBaseDir + "direction"), FileUtil.DIRPERS);
		m_sCtrlFile = oXodrFile.getFileName().toString().replace(".xodr", ".bin");
		boolean bProcess = true;
		Path oPavementFile = Paths.get(m_sBaseDir + "pavement/" + m_sCtrlFile);
		Path oLaneFile = Paths.get(m_sBaseDir + "direction/" + m_sCtrlFile);
		if (Files.exists(oLaneFile) && Files.isRegularFile(oLaneFile))
			bProcess = false;
		if (bProcess)
		{
			System.out.println(oXodrFile.toString());
			if (Files.exists(oPavementFile) && Files.isRegularFile(oPavementFile))
			   Files.delete(oPavementFile);
			if (Files.exists(oLaneFile) && Files.isRegularFile(oLaneFile))
				Files.delete(oLaneFile);
			XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
			iXmlReader.setContentHandler(this);
			iXmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
			iXmlReader.parse(new InputSource(new BufferedInputStream(Files.newInputStream(oXodrFile))));
		}
		
//		double[] dBounds = new double[4];
//		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get("/dev/shm/19tiles.csv"), FileUtil.APPENDTO, FileUtil.FILEPERS), "UTF-8")))
//		{
//			for (int nX = 149783; nX <= 149787; nX++)
//			{
//				for (int nY = 200452; nY <= 200474; nY++)
//				{
//					oOut.append(String.format("19,%d,%d", nX, nY));
//					Mercator.getInstance().tileBounds(nX, nY, 19, dBounds);
//					oOut.append(String.format(",%7.2f,%7.2f,%7.2f,%7.2f\n", dBounds[0], dBounds[1], dBounds[2], dBounds[3]));
//				}
//			}
//		}
//		double dMidX = (m_dBB[0] + m_dBB[2]) / 2;
//		double dMidY = (m_dBB[1] + m_dBB[3]) / 2;
		
//		System.out.println(Mercator.xToLon(dMidX) + " " + Mercator.yToLat(dMidY));
//		System.out.println(String.format("%d roads out of %d less than 5 meters", m_nShort, m_nRoads));
		return bProcess;
	}
}
