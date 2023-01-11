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

import cc.erv.RSULocation;
import cc.erv.ERVRSUService;

/***
 * <RSULocationRequest><id>XXXXXX</id><latitude>3895510833</latitude><longitude>-7714955667</longitude><v2xhubPort>44444</v2xhubPort></RSULocationRequest>
 * Registering RSU with carma-cloud and keep track of all connected RSUs.
 */
public class ERVRSUServlet extends HttpServlet {
	protected static final Logger LOGGER = LogManager.getRootLogger();

	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes) throws IOException {
		String[] sUriParts = oReq.getRequestURI().split("/");
		String sMethod = sUriParts[sUriParts.length - 1]; // determine what type of request is made
		oRes.setContentType("application/json"); // all responses are json
		JSONObject oResponse = new JSONObject();
		int nStatus;
		try // call the correct method depending on the request
		{
			if (sMethod.compareTo("register") == 0) {
				nStatus = registerRSU(oReq, oResponse);
				oResponse.put("status", nStatus);
			} else if (sMethod.compareTo("req") == 0) {
				nStatus = requestRSU(oReq, oResponse);
			} else if (sMethod.compareTo("list") == 0) {
				nStatus = listRSU(oReq, oResponse);
			} else
				nStatus = HttpServletResponse.SC_UNAUTHORIZED;

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

	private int listRSU(HttpServletRequest oReq, JSONObject oResponse) {
		Session oSession = SessMgr.getSession(oReq);
		if (oSession == null) // request must contain a valid session token
		{
			return HttpServletResponse.SC_UNAUTHORIZED;
		}

		// Get RSU location list
		ArrayList<RSULocation> rsu_list = (ArrayList<RSULocation>) getServletContext().getAttribute("RSUList");
		JSONArray rsuArr = ERVRSUService.serializeRSUList(rsu_list);
		oResponse.put("RSUList", rsuArr);
		return HttpServletResponse.SC_OK;
	}

	private int requestRSU(HttpServletRequest oReq, JSONObject oResponse) throws Exception {
		return HttpServletResponse.SC_OK;
	}

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
		ArrayList<RSULocation> existing_rsus = (ArrayList<RSULocation>) getServletContext().getAttribute("RSUList");
		ArrayList<RSULocation> updated_rsus = ERVRSUService.RegisteringRSU(sReq, existing_rsus);
		if (updated_rsus != null) {
			getServletContext().setAttribute("RSUList", updated_rsus);
			LOGGER.debug(updated_rsus);
		}
		return HttpServletResponse.SC_OK;
	}
}
