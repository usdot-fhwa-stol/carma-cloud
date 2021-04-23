/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
public class TcmReqParser extends DefaultHandler2
{
	private TcmReq m_oReq;
	private StringBuilder m_sBuf = new StringBuilder();
	private TcBounds m_oBounds;
	private int[] m_nXs = new int[3];
	private int[] m_nYs = new int[3];
	private int m_nXCount = 0;
	private int m_nYCount = 0;
	public TcmReqParser()
	{
		super();
	}
	
	
	@Override
	public void characters(char[] cBuf, int nPos, int nLen)
	{
		m_sBuf.setLength(0);
		m_sBuf.append(cBuf, nPos, nLen);
	}
	
	
	@Override
 	public void startElement(String sUri, String sLocalName, 
		String sQname, Attributes iAtt)
	   throws SAXException
	{
		switch (sQname)
		{
			case "tcrV01":
			{
				m_oReq.m_sVersion = "1.0";
				break;
			}
			case "TrafficControlBounds":
			{
				m_oBounds = new TcBounds();
				m_nXCount = 0;
				m_nYCount = 0;
				break;
			}
			case "bounds":
			{
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
				m_oBounds.m_lOldest = Long.parseLong(m_sBuf.toString());
				break;
			}
			
			case "TrafficControlBounds":
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
		XMLReader iXmlReader = SAXParserFactory.newInstance().
			newSAXParser().getXMLReader();
		iXmlReader.setContentHandler(this);
		iXmlReader.parse(new InputSource(oIn));
		
		return m_oReq;
	}
	
	
	public static void main(String[] sArgs)
	   throws Exception
	{
		System.out.println(String.valueOf(false));
//		TcmReq oReq = new TcmReqParser().parseRequest(Files.newInputStream(Paths.get("C:/Users/aaron.cherney/Documents/CarmaCloud/traf_ctrl/request.xml")));
//		System.out.println();
	}
}
