/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
public class XodrJunctionParser extends DefaultHandler2
{
	HashMap<String, String> m_oJunctionMap = new HashMap();
	
	public XodrJunctionParser()
	{
		super();
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
					String sJunc = iAtt.getValue("junction");
					if (sJunc != null && sJunc.compareTo("-1") != 0)
						m_oJunctionMap.put(iAtt.getValue("id"), sJunc);
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
	
	
	public HashMap<String, String> parseXodrIntersections(Path oXodrFile)
	   throws Exception
	{
		XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
		iXmlReader.parse(new InputSource(new BufferedInputStream(Files.newInputStream(oXodrFile))));
		
		return m_oJunctionMap;
	}
}
