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
	 * @param sReq New RSU request sent by v2xhub
	 * @param existing_rsus The list of existing RSU locations
	 * @return The updated list of RSU locations
	 */
	public static ArrayList<RSULocation> RegisteringRSU(StringBuilder sReq, ArrayList<RSULocation> existing_rsus) {
		RSULocationParser parser = new RSULocationParser();
		RSULocation new_rsu = parser
				.parseRequest(new ByteArrayInputStream(sReq.toString().getBytes(StandardCharsets.UTF_8)));
		if (new_rsu != null) {
			if (existing_rsus == null) {
				existing_rsus = new ArrayList<RSULocation>();
			} else {
				// check if the new RSU is already registered. If yes, replace existing RSU with
				// new request RSU. Each RSU is connected to one v2xhub which identified v2xhub
				// port
				for (int i = 0; i < existing_rsus.size(); i++) {
					if (existing_rsus.get(i).v2xhub_port.equals(new_rsu.v2xhub_port)) {
						existing_rsus.remove(i);
					}
				}
			}

			// Update RSU register timestamp
			new_rsu.last_update_at = Instant.now();
			existing_rsus.add(new_rsu);
		} else {
			LOGGER.debug("Cannot parse new RSU Register request.");
		}
		return existing_rsus;
	}

	/**
	 * 
	 * @param rsu_list An array list of RSULocation object
	 * @return Serialized JSONArray that contains the list of RSUs
	 */

	public static JSONArray serializeRSUList(ArrayList<RSULocation> rsu_list) {
		JSONArray rsuArr = new JSONArray();
		if (rsu_list != null) {
			for (RSULocation item : rsu_list) {
				JSONObject rsuJson = new JSONObject();
				rsuJson.put("id", item.id);
				rsuJson.put("latitude", item.latitude);
				rsuJson.put("longitude", item.longitude);
				rsuJson.put("v2xhub_port", item.v2xhub_port);
				rsuJson.put("last_update_at", item.last_update_at);
				rsuArr.put(rsuJson);
			}
		}
		return rsuArr;
	}
}
