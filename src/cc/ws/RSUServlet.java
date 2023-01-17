package cc.ws;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import cc.rsu.RSULocation;
import cc.rsu.RSUService;

/***
 * <RSULocationRequest><id>XXXXXX</id><latitude>3895510833</latitude><longitude>-7714955667</longitude><v2xhubPort>44444</v2xhubPort></RSULocationRequest>
 * Registering RSU with carma-cloud and keep track of all connected RSUs.
 */
public class RSUServlet extends HttpServlet {
	protected static final Logger LOGGER = LogManager.getRootLogger();
	private static final String V2XHUB_PORT = "v2xhub_port";
	private static final String RSULIST = "RSUList";

	/**
	 * Handler POST request
	 */
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes) throws IOException {
		String[] sUriParts = oReq.getRequestURI().split("/");
		String sMethod = sUriParts[sUriParts.length - 1]; // determine what type of request is made
		oRes.setContentType("application/json"); // all responses are json
		JSONObject oResponse = new JSONObject();
		int nStatus;
		// call the correct method depending on the request
		try {
			if (sMethod.compareTo("register") == 0) {
				nStatus = registerRSU(oReq, oResponse);
				oResponse.put("status", nStatus);
			} else if (sMethod.compareTo("req") == 0) {
				nStatus = requestRSU(oReq, oResponse);
			} else if (sMethod.compareTo("list") == 0) {
				nStatus = listRSU(oReq, oResponse);
			} else {
				nStatus = HttpServletResponse.SC_UNAUTHORIZED;
			}

			oRes.setStatus(nStatus);
			try (PrintWriter oOut = oRes.getWriter()) // always write the JSON response
			{
				oResponse.write(oOut);
			} catch (IOException oInner) {
				oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				LOGGER.error("Failed to write response for " + sMethod);
			}
		} catch (Exception oEx) {
			oRes.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			LOGGER.error(oEx.getMessage() + " during " + sMethod);
			String sErr = oResponse.getString("error");
			oResponse.put("error", sErr);
			try (PrintWriter oOut = oRes.getWriter()) // write error messages to the JSON response
			{
				oResponse.write(oOut);
			}
		}
	}

	/**
	 * Handler DELETE request
	 */
	@Override
	public void doDelete(HttpServletRequest oReq, HttpServletResponse oRes) throws IOException {
		Session oSession = SessMgr.getSession(oReq);
		JSONObject oResponse = new JSONObject();
		if (oSession == null) // request must contain a valid session token
		{
			oRes.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		} else {
			if (oReq.getParameter(V2XHUB_PORT) != null) {// Get RSU location list
				ArrayList<RSULocation> rsu_list = (ArrayList<RSULocation>) getServletContext().getAttribute(RSULIST);
				ArrayList<RSULocation> rsu_list_update = new ArrayList<RSULocation>();
				if (rsu_list != null) {
					for (RSULocation rsu : rsu_list) {
						if (rsu.v2xhub_port.equals(oReq.getParameter(V2XHUB_PORT))) {
							continue;
						}
						rsu_list_update.add(rsu);
					}
					getServletContext().setAttribute(RSULIST, rsu_list_update);
					JSONArray rsuArr = RSUService.serializeRSUList(rsu_list_update);
					oResponse.put(RSULIST, rsuArr);
					oRes.setStatus(HttpServletResponse.SC_OK);
				}
			} else {
				oRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				LOGGER.error("Cannot delete RSU!");
			}

		}

		try (PrintWriter oOut = oRes.getWriter()) // write error messages to the JSON response
		{
			oResponse.write(oOut);
		}
	}

	/**
	 * Request for a list of RSUs
	 */
	private int listRSU(HttpServletRequest oReq, JSONObject oResponse) {
		Session oSession = SessMgr.getSession(oReq);
		if (oSession == null) // request must contain a valid session token
		{
			return HttpServletResponse.SC_UNAUTHORIZED;
		}

		// Get RSU location list
		ArrayList<RSULocation> rsu_list = (ArrayList<RSULocation>) getServletContext().getAttribute(RSULIST);
		JSONArray rsuArr = RSUService.serializeRSUList(rsu_list);
		oResponse.put(RSULIST, rsuArr);
		return HttpServletResponse.SC_OK;
	}

	/***
	 * Request for identified RSUs
	 */
	private int requestRSU(HttpServletRequest oReq, JSONObject oResponse) throws Exception {
		return HttpServletResponse.SC_OK;
	}

	/**
	 * Request to register RSU with the server
	 */
	private int registerRSU(HttpServletRequest oReq, JSONObject oResponse) throws Exception {
		StringBuilder sReq = new StringBuilder();
		try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream())) {
			int nByte = 0;
			while ((nByte = oIn.read()) >= 0) {
				sReq.append((char) nByte);
			}
		}
		LOGGER.debug(sReq);
		// Update RSU location list
		ArrayList<RSULocation> existing_rsus = (ArrayList<RSULocation>) getServletContext().getAttribute(RSULIST);
		ArrayList<RSULocation> updated_rsus = RSUService.RegisteringRSU(sReq, existing_rsus);
		if (updated_rsus != null) {
			getServletContext().setAttribute(RSULIST, updated_rsus);
			LOGGER.debug(updated_rsus);
		}
		return HttpServletResponse.SC_OK;
	}
}
