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
import java.util.Iterator;


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
	private Junction m_oJunction;
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
			
			if (sQname.compareTo("junction") == 0)
			{
				m_oJunction = new Junction(iAtt.getValue("name"), iAtt.getValue("id"), iAtt.getValue("type"));
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
			m_oRoad.createPolygons(m_oProj, m_dPoint);
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
		ArrayList<Road> dSegs = oParser.readXodr("/home/cherneya/testtrack.xodr");
		int nId = Integer.MIN_VALUE;
		if (sArgs.length > 0)
			nId = Integer.parseInt(sArgs[0]);
		
		ArrayList<Iterator<double[]>> oLines = new ArrayList();
		for (Road oRoad : dSegs)
		{
			if (nId != Integer.MIN_VALUE && oRoad.m_nId != nId)
				continue;
			oLines.clear();
			for (LaneSection oSection : oRoad)
			{
				ArrayList<Lane> oLaneList = new ArrayList();
				oSection.getLanes(oLaneList);
				for (Lane oLane : oLaneList)
				{
					if (oLane.m_sType.compareTo("driving") == 0)
						oLines.add(oLane.m_oControl.pointIterator());
				}
			}
			try (BufferedWriter oOut = new BufferedWriter(new FileWriter(String.format("/home/cherneya/kml/control_%d.kml", oRoad.m_nId))))
			{
				writeKML(oOut, oLines);
			}
		}

	}

	public static void writeKML(BufferedWriter oOut, ArrayList<Iterator<double[]>> oLines) throws Exception
	{
		oOut.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n");
		oOut.write("<Style id=\"highlight\"><LabelStyle><color>ff000000></color><scale>1.5</scale></LabelStyle><LineStyle><gx:labelVisibility>1</gx:labelVisibility><color>ffffffff</color><width>3</width></LineStyle><PolyStyle><color>ffffffff</color><colorMode>random</colorMode><fill>1</fill><outline>1</outline></PolyStyle></Style>\n");
		oOut.write("<Style id=\"normal\"><LabelStyle><color>ff000000></color><scale>0</scale></LabelStyle><LineStyle><gx:labelVisibility>0</gx:labelVisibility><color>ff0000ff</color><width>3</width></LineStyle><PolyStyle><color>ffffffff</color><colorMode>random</colorMode><fill>1</fill><outline>1</outline></PolyStyle></Style>\n");
		oOut.write("<StyleMap id=\"stylemap\"><Pair><key>normal</key><styleUrl>#normal</styleUrl></Pair><Pair><key>highlight</key><styleUrl>#highlight</styleUrl></Pair></StyleMap>\n");
		
		for (int nIndex = 0; nIndex < oLines.size(); nIndex++)
		{
			Iterator<double[]> oIt = oLines.get(nIndex);
			oOut.write("<Placemark><styleUrl>#stylemap</styleUrl>\n");
			oOut.write(String.format("<name>Line%d</name>\n", nIndex));
			oOut.write("<LineString><tessellate>1</tessellate><extrude>1</extrude>\n<altitudeMode>clampedToGround</altitudeMode>\n<coordinates>\n");
			while (oIt.hasNext())
			{
				double[] dPoint = oIt.next();
				oOut.write(String.format("%2.7f,%2.7f,%d\n", dPoint[0], dPoint[1], 0));
			}
			oOut.write("</coordinates>\n</LineString>\n</Placemark>\n");
		}
		oOut.write("</Document>\n</kml>");
	}
}
