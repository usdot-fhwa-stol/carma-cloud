package cc.geosrv;


public class Proj
{
	private long m_lJniProj;
	public static NullProj NULLPROJ = new NullProj();
	
	{
		System.loadLibrary("cs2cswrapper");
	}


	protected Proj()
	{
	}


	public Proj(String sFromCs, String sToCs)
		throws NullPointerException
	{
		m_lJniProj = init(sFromCs, sToCs);
		if (m_lJniProj == 0L)
			throw new NullPointerException();
	}


	public void cs2cs(double dX, double dY, double[] dPoint)
	{
		proj(m_lJniProj, dX, dY, dPoint);
	}


	@Override
	public void finalize()
		throws Throwable
	{
		super.finalize();
		if (m_lJniProj != 0L)
			free(m_lJniProj);

		m_lJniProj = 0L;
	}


	public native void proj(long lJniProj, double dX, double dY, double[] dPoint);


	private native long init(String sFromCs, String sToCs);


	private native void free(long lJniProj);
	
	private static class NullProj extends Proj
	{
		private NullProj()
		{
		}
		
		
		@Override
		public void cs2cs(double dX, double dY, double[] dPoint)
		   throws NullPointerException
		{
			dPoint[0] = dX;
			dPoint[1] = dY;
		}
	}
}
