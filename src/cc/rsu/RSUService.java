package cc.rsu;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

public class RSUService {
	protected static final Logger LOGGER = LogManager.getRootLogger();

	public RSUService() {
		super();
	}

	/**
	 * 
	 * @param sReq         New RSU request sent by v2xhub
	 * @param existingRsus The list of existing RSU locations and their bounding
	 *                     boxes
	 * @param boundingBoxRadius in meter
	 * @return The updated list of RSU locations
	 */
	public static ArrayList<RSUBoundingbox> RegisteringRSU(StringBuilder sReq, ArrayList<RSUBoundingbox> existingRsus,
			double boundingBoxRadius) {
		RSULocationParser parser = new RSULocationParser();
		RSULocation newRSULoc = parser
				.parseRequest(new ByteArrayInputStream(sReq.toString().getBytes(StandardCharsets.UTF_8)));
		if (newRSULoc != null) {
			if (existingRsus == null) {
				existingRsus = new ArrayList<RSUBoundingbox>();
			} else {
				// check if the new RSU is already registered. If yes, replace existing RSU with
				// new request RSU. Each RSU is connected to one v2xhub which identified v2xhub
				// port
				for (int i = 0; i < existingRsus.size(); i++) {
					if (existingRsus.get(i).getCenterLoc().v2xhub_port.equals(newRSULoc.v2xhub_port)) {
						existingRsus.remove(i);
					}
				}
			}

			// Update RSU register timestamp
			newRSULoc.last_update_at = Instant.now();
			RSUBoundingbox newRSUBoundingBox = new RSUBoundingbox(newRSULoc, boundingBoxRadius);
			existingRsus.add(newRSUBoundingBox);
		} else {
			LOGGER.debug("Cannot parse new RSU Register request.");
		}
		return existingRsus;
	}

	/**
	 * 
	 * @param rsuList An array list of RSUBoundingbox object
	 * @return Serialized JSONArray that contains the list of RSUs
	 */

	public static JSONArray serializeRSUList(ArrayList<RSUBoundingbox> rsuList) {
		JSONArray rsuArr = new JSONArray();
		if (rsuList != null) {
			for (RSUBoundingbox item : rsuList) {
				JSONObject rsuJson = new JSONObject();
				rsuJson.put("id", item.getCenterLoc().id);
				rsuJson.put("latitude", item.getCenterLoc().latitude);
				rsuJson.put("longitude", item.getCenterLoc().longitude);
				rsuJson.put("v2xhub_port", item.getCenterLoc().v2xhub_port);
				rsuJson.put("last_update_at", item.getCenterLoc().last_update_at);
				rsuJson.put("bounding_box_coordinates", item.getBoundingBoxLatLngCoordinates());
				rsuArr.put(rsuJson);
			}
		}
		return rsuArr;
	}

	/**
	 * @param bsmReqList An array list of BSMRequest object
	 * @return Serialized JSONArray that contains the list of BSM Request
	 */
	public static JSONArray serializeBSMList(ArrayList<BSMRequest> bsmReqList) {
		JSONArray rsuArr = new JSONArray();
		if (bsmReqList != null) {
			for (BSMRequest item : bsmReqList) {
				JSONObject rsuJson = new JSONObject();
				rsuJson.put("id", item.getId());
				JSONArray routeArr = new JSONArray();
				for (Position p : item.getRoute()) {
					JSONArray pointArr = new JSONArray();
					pointArr.put(p.getLatitude());
					pointArr.put(p.getlongitude());
					routeArr.put(pointArr);
				}
				rsuJson.put("route", routeArr);
				rsuJson.put("last_update_at", item.getLast_update_at());
				rsuArr.put(rsuJson);
			}
		}
		return rsuArr;
	}

}
