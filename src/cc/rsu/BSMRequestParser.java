package cc.rsu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
/***
 * Process incoming BSMRequest payload in xml format
 */
public class BSMRequestParser extends DefaultHandler2 {
	protected BSMRequest bsmReq;
	protected Position loc = null;
	protected ArrayList<Position> route = null;
	protected StringBuilder m_sbuf = new StringBuilder();

	public BSMRequestParser() {
		super();
	}

	@Override
	public void characters(char[] cBuf, int nPos, int nLen) {
		m_sbuf.setLength(0);
		m_sbuf.append(cBuf, nPos, nLen);
	}

	@Override
	public void startElement(String sUri, String sLocalName, String sQname, Attributes iAtt) {
		switch (sQname.toLowerCase()) {
		case "bsmrequest":
			break;
		case "id":
			break;
		case "route":
			if (route == null) {
				route = new ArrayList<>();
			}
			break;
		case "point":
			loc = new Position();
		default:
			break;
		}
	}

	@Override
	public void endElement(String sUri, String sLocalName, String sQname) throws SAXException {
		switch (sQname.toLowerCase()) {
		case "bsmrequest":
			break;
		case "id":
			bsmReq.setId(m_sbuf.toString());
			break;
		case "route":
			bsmReq.setRoute(route);
			break;
		case "point":
			route.add(loc);
			break;
		case "latitude":
			loc.setLatitude((Long.parseLong(m_sbuf.toString()) / 100000000.0));
			break;
		case "longitude":
			loc.setlongitude((Long.parseLong(m_sbuf.toString()) / 100000000.0));
			break;
		default:
			break;
		}
	}

	public BSMRequest parseRequest(InputStream oIn) {
		try {
			bsmReq = new BSMRequest();
			XMLReader iXmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
			iXmlReader.setContentHandler(this);
			iXmlReader.parse(new InputSource(oIn));
			return bsmReq;
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
