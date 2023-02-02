package cc.rsu;

import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Process incoming BSM request and determine the emergency response vehicle future path,
 * and identify the RSU along the emergency response vehicle future path
 * */
public class RSUIdentificationTask implements Callable<Void> {
	private static final Logger LOGGER = LogManager.getLogger(RSUIdentificationTask.class);
	private BSMRequest incomingBSMReq;

	public RSUIdentificationTask() {
	}

	public RSUIdentificationTask(BSMRequest bsmReq) {
		incomingBSMReq = bsmReq;
	}

	@Override
	public Void call() throws Exception {
		LOGGER.info("Scheduled task to process BSM request: " + incomingBSMReq);
		return null;
	}
}
