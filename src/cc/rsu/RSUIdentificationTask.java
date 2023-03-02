package cc.rsu;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Process incoming BSM request and determine the emergency response vehicle
 * future path, and identify the RSU along the emergency response vehicle(ERV)
 * future path. Note: The BSM has the route that consist of a series of lat/lon
 * to describe ERV future path
 */
public class RSUIdentificationTask implements Callable<Void> {
	private static final Logger LOGGER = LogManager.getLogger(RSUIdentificationTask.class);
	private BSMRequest incomingBSMReq = null;
	private ArrayList<RSUBoundingbox> registeredRSUs = null;

	public RSUIdentificationTask() {
	}

	public RSUIdentificationTask(BSMRequest bsmReq, ArrayList<RSUBoundingbox> RSUs) {
		incomingBSMReq = bsmReq;
		registeredRSUs = RSUs;
	}

	/****
	 * Loop through the route future locations from BSM Request, and calculate the
	 * bounding boxes for those locations
	 */
	@Override
	public Void call() throws Exception {
		long start_ts = Instant.now().toEpochMilli();
		LOGGER.info("Task to process BSM request: " + incomingBSMReq);

		// Convert geodetic locations to x,y,z
		ArrayList<Point> points = new ArrayList<>();
		GeodesicCartesianConverter converter = new GeodesicCartesianConverter();
		for (Position loc : incomingBSMReq.getRoute()) {
			points.add(converter.geodesic2Cartesian(loc));
		}

		// Calculate bounding boxes with two adjacent points
		ArrayList<BoundingBox> boundingBoxes = new ArrayList<>();
		for (int i = 0; i < points.size() - 1; i++) {
			boundingBoxes.add(new BoundingBox(points.get(i), points.get(i + 1)));
		}
		LOGGER.debug("Generated number of bounding boxes from BSM request: " + boundingBoxes.size());

		// Comparing the bounding boxes from BSM request with register RSU bounding
		// boxes. If an RSU bounding box intersects with any of BSM request bounding
		// boxes, the RSU is along the future path
		if (registeredRSUs != null && registeredRSUs.size() > 0) {
			ArrayList<RSUBoundingbox> identifiedRSUs = new ArrayList<>();
			for (RSUBoundingbox rsuBoundingBox : registeredRSUs) {
				BoundingBox bBox = rsuBoundingBox.getBoundingBox();
				for (BoundingBox bsmBoxOther : boundingBoxes) {
					if (bBox.intersects(bsmBoxOther)) {
						identifiedRSUs.add(rsuBoundingBox);
						LOGGER.debug("Identified RSU location: " + rsuBoundingBox.getCenterLoc().toString());
						break;
					}
				}
			}
			LOGGER.info("Identified numbers of RSU: " + identifiedRSUs.size());

			// sent BSM hex to the identified RSUs
			for (RSUBoundingbox identifiedRSU : identifiedRSUs) {
				ExecutorService singleExector = Executors.newSingleThreadExecutor();
				singleExector.submit(new HTTPClientTask(identifiedRSU, incomingBSMReq));
				singleExector.shutdown();
			}
		} else {
			LOGGER.info("No RSU is registered!");
		}

		long end_ts = Instant.now().toEpochMilli();
		LOGGER.warn("TOTAL BSM PROCESS time (ms): " + (end_ts - start_ts) + "\n");
		return null;
	}

}
