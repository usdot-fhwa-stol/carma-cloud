package cc.ws;

import java.io.BufferedInputStream;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 *
 * @author bryan.krueger
 */
public class TcmAckServlet extends HttpServlet
{
	protected static final Logger LOGGER = LogManager.getRootLogger();


	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws ServletException, IOException
	{
		StringBuilder sReq = new StringBuilder();
		try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream()))
		{
			int nByte;
			while ((nByte = oIn.read()) >= 0)
				sReq.append((char)nByte);
		}

		LOGGER.debug(sReq);
	}
}
