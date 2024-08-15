package cc.ws;


public class TimeSource
{
	private static TimeSource g_oInstance;

	private boolean m_bSim; // determines if instance is in simulation mode
	long m_lSimTime; // package private time set by simulation federate


	protected TimeSource()
	{
	}


	TimeSource(boolean bSim)
	{
		m_bSim = bSim;
		g_oInstance = this;
	}


	public static TimeSource getInstance()
	{
		return g_oInstance;
	}


	public long currentTimeMillis()
	{
		if (m_bSim)
			return m_lSimTime;

		return System.currentTimeMillis();
	}
}
