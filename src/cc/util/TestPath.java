/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

import cc.geosrv.Mercator;
import cc.geosrv.Proj;
import java.util.Iterator;

/**
 *
 * @author aaron.cherney
 */
public class TestPath
{
	public static void main(String[] sArgs)
	{
		double[] dPts = new double[]{-8588202.545792961, 4715219.955264221, -8588102.536362432, 4715319.946248281, -8588102.536362432, 4715119.950950808, -8588202.545792961, 4715319.946248281, -8588202.545792961, 4715219.955264221};
		double dPrevX = dPts[0];
		double dPrevY = dPts[1];
		for (int nIndex = 0; nIndex < dPts.length; nIndex += 2)
		{
			double dX = dPts[nIndex];
			double dY = dPts[nIndex + 1];
			System.out.append(String.format("[%2.7f,%2.7f],", Mercator.xToLon(dX), Mercator.yToLat(dY)));
//			double dXd = dX - dPrevX;
//			double dYd = dY - dPrevY;
//			dPrevX = dX;
//			dPrevY = dY;
//			System.out.println(dXd);
//			System.out.println(dYd);
		}
		System.out.println();
		Geo.untwist(dPts);
		for (int nIndex = 0; nIndex < dPts.length; nIndex += 2)
		{
			double dX = dPts[nIndex];
			double dY = dPts[nIndex + 1];
			System.out.append(String.format("[%2.7f,%2.7f],", Mercator.xToLon(dX), Mercator.yToLat(dY)));
		}
		
	}
	
	public static void main1(String[] sArgs)
	{
		int nRefLon = Integer.parseInt(sArgs[0]);
		int nRefLat = Integer.parseInt(sArgs[1]);
		int[] nNodes = Arrays.newIntArray(sArgs.length);
		for (int nIndex = 2; nIndex < sArgs.length;)
			nNodes = Arrays.add(nNodes, Integer.parseInt(sArgs[nIndex++]));
		double[] dPts = Arrays.newDoubleArray(Arrays.size(nNodes));
		Proj oProj = new Proj("epsg:4326", "epsg:3785");
		double[] dPoint = new double[2];
		dPoint[0] = Mercator.lonToMeters(Geo.fromIntDeg(nRefLon));
		dPoint[1] = Mercator.latToMeters(Geo.fromIntDeg(nRefLat));
		oProj.cs2cs(Geo.fromIntDeg(nRefLon), Geo.fromIntDeg(nRefLat), dPoint);
		Proj oMerc = new Proj("epsg:3785", "epsg:4326");
		StringBuilder sBuf = new StringBuilder();
		sBuf.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
		Iterator<int[]> oIt = Arrays.iterator(nNodes, new int[2], 1, 2);
		double dPrevX = dPoint[0];
		double dPrevY = dPoint[1];
		while (oIt.hasNext())
		{
			int[] nNode = oIt.next();
			double dX = nNode[0] / 100.0 + dPrevX;
			double dY = nNode[1] / 100.0 + dPrevY;
			dPrevX = dX;
			dPrevY = dY;
			oMerc.cs2cs(dX, dY, dPoint);
			dPts = Arrays.add(dPts, dPoint[0], dPoint[1]);
		}
		Iterator<double[]> oD = Arrays.iterator(dPts, new double[2], 1, 2);
		while (oD.hasNext())
		{
			double[] dPt = oD.next();
			sBuf.append(String.format("[%2.7f,%2.7f],", dPt[0],dPt[1]));
		}
		
		sBuf.setLength(sBuf.length() - 1);
		sBuf.append("]}}");
		System.out.append(sBuf);
	}
}
