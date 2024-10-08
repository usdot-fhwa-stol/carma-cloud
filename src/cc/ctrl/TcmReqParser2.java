/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import java.io.InputStream;
import java.util.ArrayList;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author aaron.cherney
 */
public class TcmReqParser2 extends TcmReqParser
{
	public TcmReqParser2()
	{
		super();
	}
	
	
	@Override
 	public void startElement(String sUri, String sLocalName, 
		String sQname, Attributes iAtt)
	   throws SAXException
	{
		switch (sQname)
		{
			case "TrafficControlRequest":
			{
				String sVal;
				m_nPort = (sVal = iAtt.getValue("port")) != null ? Integer.parseInt(sVal) : m_nPort;
				m_bList = (sVal = iAtt.getValue("list")) != null ? Boolean.parseBoolean(sVal) : m_bList;
				break;
			}

			case "bounds":
			{
				m_oBounds = new TcBounds();
				m_nXCount = 0;
				m_nYCount = 0;
				m_oReq.m_oBounds = new ArrayList();
				break;
			}
				
			default:
		}
	}
	
	
	@Override
 	public void endElement(String sUri, String sLocalName, String sQname)
	   throws SAXException
	{
		switch (sQname)
		{
			case "reqid":
			{
				m_oReq.m_sReqId = m_sBuf.toString();
				break;
			}
			
			case "reqseq":
			{
				m_oReq.m_nReqSeq = Integer.parseInt(m_sBuf.toString());
				break;
			}
			
			case "scale":
			{
				m_oReq.m_nScale = Integer.parseInt(m_sBuf.toString());
				break;
			}
			
			case "deltax":
			{
				m_nXs[m_nXCount++] = Integer.parseInt(m_sBuf.toString());
				break;
			}
			
			case "deltay":
			{
				m_nYs[m_nYCount++] = Integer.parseInt(m_sBuf.toString());
				break;
			}
			
			case "reflat":
			{
				m_oBounds.m_nCorners[1] = Integer.parseInt(m_sBuf.toString());
				break;
			}
			
			case "reflon":
			{
				m_oBounds.m_nCorners[0] = Integer.parseInt(m_sBuf.toString());
				break;
			}
			
			case "oldest":
			{
				m_oBounds.m_lOldest = Long.parseLong(m_sBuf.toString()) * 60 * 1000; // convert epoch minutes to epoch milliseconds
				break;
			}
			
			case "bounds":
			{
				m_oBounds.setCorners(m_nXs, m_nYs, m_oReq.m_nScale);
				m_oReq.m_oBounds.add(m_oBounds);
				break;
			}
				
			default:
				break;
		}
	}

	
	public TcmReq parseRequest(InputStream oIn)
	   throws Exception
	{
		m_oReq = new TcmReq();
		m_oReq.m_sVersion = "0.1";
		XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.parse(new InputSource(oIn));
		
		return m_oReq;
	}
}
