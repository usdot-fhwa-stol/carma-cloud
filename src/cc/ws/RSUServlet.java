package cc.ws;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import cc.rsu.BSMRequest;
import cc.rsu.BSMRequestParser;
import cc.rsu.RSUBoundingbox;
import cc.rsu.RSUIdentificationTask;
import cc.rsu.RSUService;

/***
 * Registering RSU with carma-cloud and keep track of all connected RSUs.
 * <RSULocationRequest><id>XXXXXX</id><latitude>3895510833</latitude><longitude>-7714955667</longitude><v2xhubPort>44444</v2xhubPort></RSULocationRequest>
 * 
 * Receive BSM request used to identify emergency response vehicle future path.
 * <BSMRequest><id>00146e604043030280ffdbfba868b3584ec40824646400320032000c888fc834e37fff0aaa960fa0040d082408804278d693a431ad275c7c6b49d9e8d693b60e35a4f0dc6b49deef1ad27a6235a4f16b8d693e2b1ad279afc6b49f928d693d54e35a5007c6b49ee8f1ad2823235a4f93b8"</id><route><point><latitude>12</latitude><longitude>1312</longitude></point><point><latitude>1012</latitude><longitude>2312</longitude></point><point><latitude>2012</latitude><longitude>3312</longitude></point><point><latitude>3012</latitude><longitude>4312</longitude></point><point><latitude>4012</latitude><longitude>5312</longitude></point><point><latitude>5012</latitude><longitude>6312</longitude></point><point><latitude>6012</latitude><longitude>7312</longitude></point><point><latitude>7012</latitude><longitude>8312</longitude></point></route></BSMRequest>
 */
public class RSUServlet extends HttpServlet {
	protected static final Logger LOGGER = LogManager.getLogger(RSUServlet.class);
	private static final String V2XHUB_PORT = "v2xhub_port";
	private static final String RSULIST = "RSUList";
	private static final String BSMREQLIST = "BSMReqList";
	private static final String BSMTIMER = "bsmtimer";
	private int bsmReqDuration = 0;
	private int bsmReqCheckPeriod = 0;
	private double boundingBoxRadius = 0;

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		bsmReqDuration = Integer.parseInt(config.getInitParameter("bsmReqDuration"));
		bsmReqCheckPeriod = Integer.parseInt(config.getInitParameter("bsmReqCheckPeriod"));
		boundingBoxRadius = Double.parseDouble(config.getInitParameter("boundingBoxRadius"));
		LOGGER.info("bsm duration parameter (second):" + bsmReqDuration);
		LOGGER.info("bsm check period parameter (second):" + bsmReqCheckPeriod);
		LOGGER.info("RSU Boundingbox Radius:" + boundingBoxRadius);

		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				ArrayList<BSMRequest> bsmReqList = (ArrayList<BSMRequest>) getServletContext().getAttribute(BSMREQLIST);
				ArrayList<BSMRequest> newBsmReqList = new ArrayList<BSMRequest>();
				// clear up old BSM request in the list
				if (bsmReqList != null) {
					for (BSMRequest bsmReq : bsmReqList) {
						if ((Instant.now().toEpochMilli() - bsmReq.getLast_update_at().toEpochMilli())
								/ 1000 < bsmReqDuration) {
							newBsmReqList.add(bsmReq);
						}
					}
					getServletContext().setAttribute(BSMREQLIST, newBsmReqList);
				}
				if (bsmReqList != null && bsmReqList.size() > 0) {
					LOGGER.debug("BSM Request List size: " + bsmReqList.size() + "\n");
				}
			}
		}, 0, bsmReqCheckPeriod * 1000L);
		getServletContext().setAttribute(BSMTIMER, timer);
	}

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
			} else if (sMethod.compareTo("bsm") == 0) {
				nStatus = listBSMReq(oReq, oResponse);
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
			String sErr = oEx.getMessage() + " during " + sMethod;
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
				ArrayList<RSUBoundingbox> rsuList = (ArrayList<RSUBoundingbox>) getServletContext()
						.getAttribute(RSULIST);
				ArrayList<RSUBoundingbox> rsuListUpdate = new ArrayList<RSUBoundingbox>();
				if (rsuList != null) {
					for (RSUBoundingbox rsu : rsuList) {
						if (rsu.getCenterLoc().v2xhub_port.equals(oReq.getParameter(V2XHUB_PORT))) {
							continue;
						}
						rsuListUpdate.add(rsu);
					}
					getServletContext().setAttribute(RSULIST, rsuListUpdate);
					JSONArray rsuArr = RSUService.serializeRSUList(rsuListUpdate);
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

	/***
	 * Release resource after service is destroyed
	 */
	@Override
	public void destroy() {
		Timer timer = (Timer) getServletContext().getAttribute(BSMTIMER);
		if (timer != null) {
			timer.cancel();
		}
		getServletContext().removeAttribute(BSMTIMER);
		if (getServletContext().getAttribute(RSULIST) != null) {
			getServletContext().removeAttribute(RSULIST);
		}
		if (getServletContext().getAttribute(BSMREQLIST) != null) {
			getServletContext().removeAttribute(BSMREQLIST);
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
		try {
			ArrayList<RSUBoundingbox> rsu_list = (ArrayList<RSUBoundingbox>) getServletContext().getAttribute(RSULIST);
			JSONArray rsuArr = RSUService.serializeRSUList(rsu_list);
			oResponse.put(RSULIST, rsuArr);
		} catch (NullPointerException oEx) {
			oResponse.put(RSULIST, new JSONArray());
			return HttpServletResponse.SC_OK;
		}
		return HttpServletResponse.SC_OK;
	}

	/***
	 * Request for identified RSUs
	 */
	private int requestRSU(HttpServletRequest oReq, JSONObject oResponse) throws Exception {
		long start_ts = Instant.now().toEpochMilli();
		ArrayList<RSUBoundingbox> registeredRSUs = (ArrayList<RSUBoundingbox>) getServletContext()
				.getAttribute(RSULIST);
		if (registeredRSUs == null || registeredRSUs.size() == 0) {
			LOGGER.error("No RSU is registered!");
			return HttpServletResponse.SC_ACCEPTED;
		}
		StringBuilder sReq = new StringBuilder();
		try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream())) {
			int nByte = 0;
			while ((nByte = oIn.read()) >= 0) {
				sReq.append((char) nByte);
			}
		}

		// Tracking list for BSM request
		BSMRequestParser parser = new BSMRequestParser();
		BSMRequest newBsmReq = parser
				.parseRequest(new ByteArrayInputStream(sReq.toString().getBytes(StandardCharsets.UTF_8)));
		newBsmReq.setLast_update_at(Instant.now());
		LOGGER.info("Received: " + newBsmReq);
		if (newBsmReq.getRoute().size() == 1) {
			LOGGER.info(
					"Ignore BSM Request: Only 1 point from BSM request route (Note: BSM Request route includes vehicle current location) ");
			// If there is only one point in the BSM request, it means the vehicle arrived
			// at the destination
			return HttpServletResponse.SC_OK;
		}
		ArrayList<BSMRequest> updatedBsmReqsList = new ArrayList<BSMRequest>();
		Boolean isIgnore = false;
		try {
			// Already processed existing BSM list
			ArrayList<BSMRequest> existingBsmReqList = (ArrayList<BSMRequest>) getServletContext()
					.getAttribute(BSMREQLIST);
			if (existingBsmReqList != null) {
				// Check if the new BSM already processed. If processed, ignore the BSM request
				// and update existing matching BSM request update time to now
				for (BSMRequest bsmReq : existingBsmReqList) {
					if (bsmReq.getId().equals(newBsmReq.getId())) {
						LOGGER.debug("IGNORE already Processed BSM: " + newBsmReq.getId());
						bsmReq.setLast_update_at(Instant.now());
						isIgnore = true;
					}
					updatedBsmReqsList.add(bsmReq);
				}
			}
		} catch (NullPointerException oEx) {
			LOGGER.error("No existing BSM");
		}

		if (isIgnore) {
			// Update the tracking list
			getServletContext().setAttribute(BSMREQLIST, updatedBsmReqsList);
			return HttpServletResponse.SC_CONFLICT;
		}
		
		// Processing new BSM request
		ExecutorService singleExector = Executors.newSingleThreadExecutor();
		singleExector.submit(new RSUIdentificationTask(newBsmReq, registeredRSUs));
		singleExector.shutdown();

		// Add newly processed BSM request to tracking list
		updatedBsmReqsList.add(newBsmReq);
		getServletContext().setAttribute(BSMREQLIST, updatedBsmReqsList);
		long end_ts = Instant.now().toEpochMilli();
		LOGGER.warn("FER-13-1: TOTAL BSM REQUEST time (ms): " + (end_ts - start_ts) + "; BSM:" + newBsmReq.getId());
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
		ArrayList<RSUBoundingbox> existingRSUs = new ArrayList<RSUBoundingbox>();
		try {
			existingRSUs = (ArrayList<RSUBoundingbox>) getServletContext().getAttribute(RSULIST);
		} catch (NullPointerException oEx) {
			LOGGER.debug("No existing RSU");
		}
		ArrayList<RSUBoundingbox> updatedRSUs = RSUService.RegisteringRSU(sReq, existingRSUs, boundingBoxRadius);
		if (updatedRSUs != null) {
			getServletContext().setAttribute(RSULIST, updatedRSUs);
			LOGGER.debug(updatedRSUs);
		}
		return HttpServletResponse.SC_OK;
	}

	/***
	 * Get BSM Request list
	 * 
	 * @param oReq
	 * @param oResponse
	 * @return http status code
	 * @throws Exception
	 */
	private int listBSMReq(HttpServletRequest oReq, JSONObject oResponse) throws Exception {
		Session oSession = SessMgr.getSession(oReq);
		if (oSession == null) // request must contain a valid session token
		{
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		try {
			ArrayList<BSMRequest> bsmReqList = (ArrayList<BSMRequest>) getServletContext().getAttribute(BSMREQLIST);
			JSONArray bsmReqArr = RSUService.serializeBSMList(bsmReqList);
			oResponse.put(BSMREQLIST, bsmReqArr);
		} catch (NullPointerException oEx) {
			oResponse.put(BSMREQLIST, new JSONArray());
			return HttpServletResponse.SC_OK;
		}
		return HttpServletResponse.SC_OK;
	}
}
