package cc.rsu;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/***
 * A task to forward BSM Hex to identified RSU
 */
public class HTTPClientTask implements Callable<Void> {
	private static final Logger LOGGER = LogManager.getLogger(HTTPClientTask.class);
	private List<RSUBoundingbox> identifiedRSUs;
	private BSMRequest bsmReq;

	public HTTPClientTask(List<RSUBoundingbox> identifiedRSUs, BSMRequest bsmReq) {
		this.identifiedRSUs = identifiedRSUs;
		this.bsmReq = bsmReq;
	}

	/***
	 * Forward BSM Hex to identified RSU
	 */
	@Override
	public Void call() throws Exception {
		for (RSUBoundingbox identifiedRSU : identifiedRSUs) {
			HttpURLConnection oHttpClient;
			try {
				URL url = new URL(
						String.format("http://localhost:%s/bsmforward", identifiedRSU.getCenterLoc().v2xhub_port));
				LOGGER.info("Trying to send BSM Hex to V2xHub with port: " + identifiedRSU.getCenterLoc().v2xhub_port);
				oHttpClient = (HttpURLConnection) url.openConnection();
				oHttpClient.setFixedLengthStreamingMode(bsmReq.getId().length()); // Id is BSM hex
				oHttpClient.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				oHttpClient.setDoOutput(true);
				oHttpClient.setRequestMethod("POST");
				oHttpClient.setConnectTimeout(1000);
				oHttpClient.connect(); // send post request
				try (BufferedWriter oOut = new BufferedWriter(new OutputStreamWriter(oHttpClient.getOutputStream()))) {
					oOut.append(bsmReq.getId());
				}
				oHttpClient.disconnect();
				LOGGER.info("FER-15: Successfully sent BSM Hex: " + bsmReq.getId() + " to V2xHub port: "
						+ identifiedRSU.getCenterLoc().v2xhub_port + "!\n");
			} catch (MalformedURLException e) {
				LOGGER.error("ERROR sending BSM Hex due to malformated URL!");
			} catch (IOException e) {
				LOGGER.error("ERROR sending BSM Hex! Error message: " + e.getMessage());
			}
		}
		return null;
	}

}
