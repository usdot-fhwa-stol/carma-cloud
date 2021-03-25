/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
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
public class XodrSignalParser extends DefaultHandler2
{
	ArrayList<Signal> m_oSignals = new ArrayList();
	ArrayList<Junction> m_oJunctions = new ArrayList();
	HashMap<Integer, ArrayList<String>> m_oSigRefs = new HashMap();
	int m_nRoadId;
	Junction m_oJunction;
	Connection m_oConnection;
	public String[] m_sSignalTypes;

	public XodrSignalParser(String[] sSignalTypes)
	{
		super();
		m_sSignalTypes = sSignalTypes;
		Arrays.sort(sSignalTypes);
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
					m_nRoadId = Integer.parseInt(iAtt.getValue("id"));
					break;
				}
				case "signal":
				{
					Signal oTemp = new Signal(iAtt.getValue("id"), iAtt.getValue("orientation"), m_nRoadId, iAtt.getValue("type"), iAtt.getValue("dynamic"), iAtt.getValue("name"));
					if (Arrays.binarySearch(m_sSignalTypes, oTemp.m_sType) >= 0 || Arrays.binarySearch(m_sSignalTypes, oTemp.m_sName) >= 0) // filter on signal types we want
					{
						int nIndex = Collections.binarySearch(m_oSignals, oTemp);
						if (nIndex < 0)
							m_oSignals.add(~nIndex, oTemp);
					}

					break;
				}
				case "signalReference":
				{
					if (!m_oSigRefs.containsKey(m_nRoadId))
						m_oSigRefs.put(m_nRoadId, new ArrayList());
					m_oSigRefs.get(m_nRoadId).add(iAtt.getValue("id"));
					break;
				}
				case "junction":
				{
					m_oJunction = new Junction(iAtt.getValue("name"), iAtt.getValue("id"), iAtt.getValue("type"));
					break;
				}
				case "connection":
				{
					m_oConnection = new Connection(iAtt.getValue("id"), iAtt.getValue("incomingRoad"), iAtt.getValue("connectingRoad"), iAtt.getValue("contactPoint"), iAtt.getValue("connectionMaster"), iAtt.getValue("type"));
					break;
				}
				case "laneLink":
				{
					m_oConnection.m_nFromLane = Integer.parseInt(iAtt.getValue("from"));
					m_oConnection.m_nToLane = Integer.parseInt(iAtt.getValue("to"));
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
			case "junction":
			{
				m_oJunctions.add(m_oJunction);
			}
			case "connection":
			{
				m_oJunction.add(m_oConnection);
			}
			default:
				break;
		}
	}
	
	
	public void parseSignalData(Path oXodrFile, ArrayList<Junction> oJunctions, ArrayList<Signal> oSignals)
	   throws Exception
	{
		XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
		iXmlReader.parse(new InputSource(new BufferedInputStream(Files.newInputStream(oXodrFile))));
		
		Signal oSearch = new Signal();
		for (Entry<Integer, ArrayList<String>> oEntry : m_oSigRefs.entrySet())
		{
			for (String sSigId : oEntry.getValue())
			{
				oSearch.m_sId = sSigId;
				int nIndex = Collections.binarySearch(m_oSignals, oSearch);
				if (nIndex < 0)
					continue;
				
				m_oSignals.get(nIndex).addRoadId(oEntry.getKey());
			}
		}
		oJunctions.addAll(m_oJunctions);
		oSignals.addAll(m_oSignals);
	}
}
