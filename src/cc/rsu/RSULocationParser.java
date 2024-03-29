package cc.rsu;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Parse RSU location request in XML format and populate RSULocation object
 */
public class RSULocationParser extends DefaultHandler2 {
	protected RSULocation rsu_loc;
	protected StringBuilder m_sbuf = new StringBuilder();
	protected double HUNDREDTH_MICRO_DEG_PER_DEG = 100000000.0;

	public RSULocationParser() {
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
			case "rsulocationrequest":
				break;
			case "id":
				break;
			case "latitude":
				break;
			case "longitude":
				break;
			case "v2xhubport":
				break;
			default:
				break;
		}
	}

	@Override
	public void endElement(String sUri, String sLocalName, String sQname) throws SAXException {
		switch (sQname.toLowerCase()) {
			case "rsulocationrequest":
				break;
			case "id":
				rsu_loc.id = m_sbuf.toString();
				break;
			case "latitude":
				rsu_loc.latitude = (Long.parseLong(m_sbuf.toString()) / HUNDREDTH_MICRO_DEG_PER_DEG);
				break;
			case "longitude":
				rsu_loc.longitude = (Long.parseLong(m_sbuf.toString()) / HUNDREDTH_MICRO_DEG_PER_DEG);
				break;
			case "v2xhubport":
				rsu_loc.v2xhub_port = m_sbuf.toString();
				break;
			default:
				break;
		}
	}

	public RSULocation parseRequest(InputStream oIn) {
		try {
			rsu_loc = new RSULocation();
			XMLReader iXmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
			iXmlReader.setContentHandler(this);
			iXmlReader.parse(new InputSource(oIn));
			return rsu_loc;
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
