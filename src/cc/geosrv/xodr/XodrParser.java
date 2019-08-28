package cc.geosrv.xodr;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import cc.geosrv.Proj;
import cc.util.Arrays;
import cc.util.Text;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collections;


/**
 *
 * @author Federal Highway Administration
 */
public class XodrParser extends DefaultHandler2
{
	private boolean m_bCdata;
	private double m_dS;
	private double m_dX;
	private double m_dY;
	private double m_dH;
	private double m_dL;
	private Road m_oRoad;
	private LaneSection m_oLaneSection;
	private Lane m_oLane;
	private RoadMark m_oRoadMark;
	private Proj m_oProj;
	private final double[] m_dPoint = new double[2];
	private final StringBuilder m_sBuf = new StringBuilder();
	private final ArrayList<Road> m_oRoads = new ArrayList();


	public XodrParser()
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
			m_oProj = new Proj(m_sBuf.toString(), "epsg:4326");
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
			if (sQname.compareTo("road") == 0)
			{
				m_oRoad = new Road(Integer.parseInt(iAtt.getValue("id")), Double.parseDouble(iAtt.getValue("length"))); // new road geometry
				Arrays.add(m_oRoad.m_dTrack, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				Arrays.add(m_oRoad.m_dLaneZero, new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
			}

			if (sQname.compareTo("geometry") == 0)
			{
				m_dS = Double.parseDouble(iAtt.getValue("s"));
				m_dX = Double.parseDouble(iAtt.getValue("x"));
				m_dY = Double.parseDouble(iAtt.getValue("y"));
				m_dH = Double.parseDouble(iAtt.getValue("hdg"));
				m_dL = Double.parseDouble(iAtt.getValue("length"));
			}

			if (sQname.compareTo("line") == 0) // derive line end point
				m_oRoad.m_oGeometries.add(new Line(m_dS, m_dX, m_dY, m_dH, m_dL));
				

			if (sQname.compareTo("arc") == 0)
				m_oRoad.m_oGeometries.add(new Arc(m_dS, m_dX, m_dY, m_dH, m_dL,Double.parseDouble(iAtt.getValue("curvature"))));

			if (sQname.compareTo("laneOffset") == 0)
			{
				double dS = Double.parseDouble(iAtt.getValue("s"));
				double dA = Double.parseDouble(iAtt.getValue("a"));
				double dB = Double.parseDouble(iAtt.getValue("b"));
				double dC = Double.parseDouble(iAtt.getValue("c"));
				double dD = Double.parseDouble(iAtt.getValue("d"));
				m_oRoad.m_oLaneOffsets.add(new LaneOffset(dS, dA, dB, dC, dD));
			}
			
			if (sQname.compareTo("laneSection") == 0)
			{
				m_oLaneSection = new LaneSection(Double.parseDouble(iAtt.getValue("s")));
			}
			
			if (sQname.compareTo("lane") == 0)
			{
				m_oLane = new Lane(Integer.parseInt(iAtt.getValue("id")), iAtt.getValue("type"));
			}
			
			if (sQname.compareTo("roadMark") == 0)
			{
				m_oRoadMark = new RoadMark(iAtt.getValue("type"), iAtt.getValue("color"), Double.parseDouble(iAtt.getValue("sOffset")));
			}
			
			if (sQname.compareTo("width") == 0)
			{
				double dS = Double.parseDouble(iAtt.getValue("sOffset"));
				double dA = Double.parseDouble(iAtt.getValue("a"));
				double dB = Double.parseDouble(iAtt.getValue("b"));
				double dC = Double.parseDouble(iAtt.getValue("c"));
				double dD = Double.parseDouble(iAtt.getValue("d"));
				m_oLane.add(new LaneWidth(dS, dA, dB, dC, dD));
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
		if (sQname.compareTo("road") == 0)
		{
			if (m_oRoad.m_oLaneOffsets.isEmpty())
				m_oRoad.m_oLaneOffsets.add(new LaneOffset(0.0, 0.0, 0.0, 0.0, 0.0));
			Collections.sort(m_oRoad.m_oLaneOffsets);
			Collections.sort(m_oRoad.m_oGeometries);

			m_oRoad.createPoints(0.1, m_oProj, m_dPoint);
			m_oRoad.createPolygons();
			m_oRoads.add(m_oRoad);
		}
		
		if (sQname.compareTo("laneSection") == 0)
		{
			m_oLaneSection.sortLanes();
			m_oRoad.add(m_oLaneSection);
		}
		
		if (sQname.compareTo("lane") == 0)
		{
			Collections.sort(m_oLane);
			if (m_oLane.m_nId == 0)
				m_oLaneSection.m_oCenter = m_oLane;
			Collections.sort(m_oLane.m_oRoadMarks);
			m_oLaneSection.add(m_oLane);
		}
		
		if (sQname.compareTo("roadMark") == 0)
		{
			m_oLane.m_oRoadMarks.add(m_oRoadMark);
		}
	}


	public ArrayList<Road> readXodr(String sXodrFile) throws Exception
	{
		XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
		iXmlReader.parse(new InputSource(new BufferedInputStream(new FileInputStream(sXodrFile))));

		return m_oRoads;
	}
	
	
	public static void main(String[] sArgs) throws Exception
	{
		XodrParser oParser = new XodrParser();
		ArrayList<Road> dSegs = oParser.readXodr("/home/cherneya/AOI_1_Leidos_Type_Update.xodr");
//		ArrayList<Road> dSegs = oParser.readXodr("/home/cherneya/motorway.xodr");
		
		try (BufferedWriter oTrack = new BufferedWriter(new FileWriter(sArgs[0]));
		     BufferedWriter oLaneZero = new BufferedWriter(new FileWriter(sArgs[1]));
			 BufferedWriter oLLanes = new BufferedWriter(new FileWriter(sArgs[2]));
		     BufferedWriter oRLanes = new BufferedWriter(new FileWriter(sArgs[3]));
			 BufferedWriter oLLanesPolys = new BufferedWriter(new FileWriter(sArgs[4]));
			 BufferedWriter oRLanesPolys = new BufferedWriter(new FileWriter(sArgs[5]));
		     BufferedWriter oCenter = new BufferedWriter(new FileWriter(sArgs[6])))
		{
			for (Road oRoad : dSegs)
			{
				oRoad.writePolyline(oTrack, oRoad.m_dTrack, oParser.m_dPoint, 5, 2, false);
				oRoad.writePolyline(oLaneZero, oRoad.m_dLaneZero, oParser.m_dPoint, 5, 2, false);
				oRoad.writeLanes(oLLanes, oRLanes, oCenter, oParser.m_dPoint, true);
				oRoad.writeLanes(oLLanesPolys, oRLanesPolys, oCenter, oParser.m_dPoint, false);
				for (LaneSection oSection : oRoad)
				{
					for (Lane oLane : oSection.m_oLeft)
						if (!oLane.isClockwise())
							System.out.println("counter");
					
					for (Lane oLane : oSection.m_oRight)
						if (!oLane.isClockwise())
							System.out.println("coutner");
				}
			}
		}
	}
}
