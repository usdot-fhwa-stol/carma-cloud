package cc.geosrv;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Federal Highway Administration
 */
public class Mercator 
{
	private static final int[] POW = new int[24];
	private final double[] RES = new double[24];
	private static final double R_MAJOR = 6378137.0;
	private static final double R_MINOR = 6356752.3142;
	private static final double R_RATIO = R_MINOR / R_MAJOR;
	private static final double ECC = Math.sqrt(1.0 - (R_RATIO * R_RATIO));
	private static final double ECC_OVER_TWO = ECC / 2.0;
	public static final double PI_OVER_TWO = Math.PI / 2.0;
	private static final double ORIGIN_SHIFT = Math.PI * R_MAJOR;
	private static final double ORIGIN_SHIFT_DIVIDED_BY_180 = ORIGIN_SHIFT / 180.0;
	private static final double PI_OVER_180 = Math.PI / 180.0;
	private static final double PI_OVER_360 = PI_OVER_180 / 2.0;
	public static final double MAX_LAT = 85.05112877980659;
	public static final double MIN_LAT = -MAX_LAT;
	public static final double MAX_LON = 180;
	public static final double MIN_LON = -MAX_LON;
	
	int m_nTileSize;
	double m_dInitRes;

	
	static
	{
		for (int nIndex = 0; nIndex < POW.length; nIndex++)
			POW[nIndex] = (int)Math.pow(2.0, nIndex);
	}
	
	
	public Mercator()
	{
		this(256);
	}
	
	
	public Mercator(int nTileSize)
	{
		m_nTileSize = nTileSize;
		m_dInitRes = 2.0 * ORIGIN_SHIFT / m_nTileSize;
		for (int nIndex = 0; nIndex < RES.length; nIndex++)
			RES[nIndex] = m_dInitRes / POW[nIndex];
	}
	
	
	public static int getExtent(int nZoom)
	{
		return POW[nZoom] * 256;
	}
	
	
	public static double lonToMeters(double dLon)
	{
		return dLon * ORIGIN_SHIFT_DIVIDED_BY_180;
	}
	
	
	public static double latToMeters(double dLat)
	{
		return Math.log(Math.tan((90.0 + dLat) * PI_OVER_360)) * R_MAJOR;
	}
	
	
	public static double xToLon(double dX)
	{
		return dX / ORIGIN_SHIFT * 180.0;
	}
	
	
	public static double yToLat(double dY)
	{
		double dLat = dY / ORIGIN_SHIFT * 180.0;
		return 180.0 / Math.PI * (2 * Math.atan(Math.exp(dLat * PI_OVER_180)) - PI_OVER_TWO);
	}
	
	
	public static void lonLatToMeters(double dLon, double dLat, double[] dMeters)
	{
		dMeters[0] = lonToMeters(dLon);
		dMeters[1] = latToMeters(dLat);
	}
	

	public void metersToLonLat(double dX, double dY, double[] dLatLon)
	{
		dLatLon[0] = dX / ORIGIN_SHIFT * 180.0;
		dLatLon[1] = dY / ORIGIN_SHIFT * 180.0;
		dLatLon[1] = 180.0 / Math.PI * (2 * Math.atan(Math.exp(dLatLon[1] * PI_OVER_180)) - PI_OVER_TWO);
	}
	
	public void pixelsToMeters(double dXp, double dYp, int nZoom, double[] dMeters)
	{
		double dRes = resolution(nZoom);
		dMeters[0] = dXp * dRes - ORIGIN_SHIFT;
		dMeters[1] = -(dYp * dRes - ORIGIN_SHIFT);
	}
	
	public void metersToPixels(double dXm, double dYm, int nZoom, double[] dPixels)
	{
		double dRes = resolution(nZoom);
		dPixels[0] = (dXm + ORIGIN_SHIFT) / dRes;
		dPixels[1] = (dYm + ORIGIN_SHIFT) / dRes;
	}
	
	public void pixelsToTile(double dXp, double dYp, int[] nTiles)
	{
		nTiles[0] = (int)((Math.ceil(dXp / m_nTileSize)) - 1);
		nTiles[1] = (int)((Math.ceil(dYp / m_nTileSize)) - 1);
	}
	
	public void tileBounds(double dXt, double dYt, int nZoom, double[] dBounds)
	{
		double[] dMeters = new double[2];
		pixelsToMeters(dXt * m_nTileSize, dYt * m_nTileSize, nZoom, dMeters);
		dBounds[0] = dMeters[0];
		dBounds[3] = dMeters[1];
		pixelsToMeters((dXt + 1) * m_nTileSize, (dYt + 1) * m_nTileSize, nZoom, dMeters);
		dBounds[2] = dMeters[0];
		dBounds[1] = dMeters[1];
	}
	
	public void lonLatBounds(double dXt, double dYt, int nZoom, double[] dBounds)
	{
		double[] dMeterBounds = new double[4];
		tileBounds(dXt, dYt, nZoom, dMeterBounds);
		double[] dLonLat = new double[2];
		metersToLonLat(dMeterBounds[0], dMeterBounds[1], dLonLat);
		dBounds[0] = dLonLat[0];
		dBounds[1] = dLonLat[1];
		metersToLonLat(dMeterBounds[2], dMeterBounds[3], dLonLat);
		dBounds[2] = dLonLat[0];
		dBounds[3] = dLonLat[1];
	}
	
	
	public void lonLatToTile(double dLon, double dLat, int nZoom, int[] nTiles)
	{
		double[] dTemp = new double[2];
		lonLatToMeters(dLon, dLat, dTemp);
		metersToPixels(dTemp[0], dTemp[1], nZoom, dTemp);
		pixelsToTile(dTemp[0], dTemp[1], nTiles);
		nTiles[1] = POW[nZoom] - nTiles[1] - 1;
	}
	
	
	public void metersToTile(double dXm, double dYm, int nZoom, int[] nTiles)
	{
		double[] dPixels = new double[2];
		metersToPixels(dXm, dYm, nZoom, dPixels);
		pixelsToTile(dPixels[0], dPixels[1], nTiles);
		nTiles[1] = POW[nZoom] - nTiles[1] - 1;
	}
	
	
	public double resolution(int nZoom)
	{
//		return m_dInitRes / POW[nZoom];
		return RES[nZoom];
	}
	

    public static double eMercX(double lon) {
        return R_MAJOR * Math.toRadians(lon);
    }

    public static double eMercY(double lat) {
        if (lat > 89.5) {
            lat = 89.5;
        }
        if (lat < -89.5) {
            lat = -89.5;
        }
        double phi = Math.toRadians(lat);
        double con = ECC * Math.sin(phi);
        con = Math.pow(((1.0-con)/(1.0+con)), ECC_OVER_TWO);
        double ts = Math.tan(0.5 * ((PI_OVER_TWO) - phi))/con;
        double y = 0 - R_MAJOR * Math.log(ts);
        return y;
    }
	
	
	public static double eLon(double dX)
	{
		return Math.toDegrees(dX / R_MAJOR);
	}
	
	
	public static double eLat(double dY)
	{
		double ts = Math.exp(-dY / R_MAJOR);
		double phi = PI_OVER_TWO - 2 * Math.atan(ts);
		double dphi = 1.0;
		for (int i = 0; Math.abs(dphi) > 0.000000001 && i < 15; i++)
		{
			double con = ECC * Math.sin(phi);
			dphi = PI_OVER_TWO - 2 * Math.atan(ts * Math.pow((1.0 - con) / (1.0 + con), ECC_OVER_TWO)) - phi;
			phi += dphi;
		}
		
		return Math.toDegrees(phi);
	}
}
