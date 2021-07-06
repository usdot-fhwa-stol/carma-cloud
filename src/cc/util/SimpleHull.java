package cc.util;

import cc.geosrv.Mercator;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;


public class SimpleHull implements Comparator<Point2D.Double>
{
	protected Point2D.Double m_oInPt;
	protected BottomLeft m_oComp = new BottomLeft();


	public SimpleHull()
	{
	}


	public void getConvexHull(boolean bClockwise, int nOffset, double[] dPts)
	{
		int nSize = (dPts.length - nOffset) >> 1;
		if (nSize < 3)
			return; // need at least 3 points

		int nPos = nOffset; // convert to Point2D.Double array
		Point2D.Double[] oPts = new Point2D.Double[nSize];
		for (int nIndex = 0; nIndex < nSize; nIndex++)
			oPts[nIndex] = new Point2D.Double(dPts[nPos++], dPts[nPos++]);

		getConvexHull(oPts); // adjust points for convex hull

		nPos = nOffset; // copy reordered points back to array
		for (int nIndex = 0; nIndex < nSize; nIndex++) // keep counter-clockwise order
		{
			Point2D.Double oPt = oPts[nIndex];
			System.out.append(String.format("[%2.7f,%2.7f],", Mercator.xToLon(oPt.x), Mercator.yToLat(oPt.y)));
//			dPts[nPos++] = oPt.x;
//			dPts[nPos++] = oPt.y;
		}
		System.out.println();
		if (bClockwise)
		{
			while (nSize-- > 0) // reverse default counter-clockwise order
			{
				Point2D.Double oPt = oPts[nSize];
				System.out.append(String.format("[%2.7f,%2.7f],", Mercator.xToLon(oPt.x), Mercator.yToLat(oPt.y)));
				dPts[nPos++] = oPt.x;
				dPts[nPos++] = oPt.y;
			}
		}
		else
		{
			for (int nIndex = 0; nIndex < nSize; nIndex++) // keep counter-clockwise order
			{
				Point2D.Double oPt = oPts[nIndex];
				System.out.append(String.format("[%2.7f,%2.7f],", Mercator.xToLon(oPt.x), Mercator.yToLat(oPt.y)));
				dPts[nPos++] = oPt.x;
				dPts[nPos++] = oPt.y;
			}
		}
		System.out.println();
	}


	public void getConvexHull(Point2D.Double[] oPts)
	{
		Arrays.sort(oPts, m_oComp); // sort extreme bottom-left point to top
		m_oInPt = oPts[0]; // exclude bottom-left point
		Arrays.sort(oPts, 1, oPts.length, this); // sort by increasing angles counter-clockwise
	}


	@Override
	public int compare(Point2D.Double oLhs, Point2D.Double oRhs)
	{
    double dCp = (oLhs.x - m_oInPt.x) * (oRhs.y - m_oInPt.y) -
			(oRhs.x - m_oInPt.x) * (oLhs.y - m_oInPt.y);

    if (dCp < 0.0)
        return 1;

		return -1;
	}


	protected class BottomLeft implements Comparator<Point2D.Double>
	{
		public BottomLeft()
		{
		}


		@Override
		public int compare(Point2D.Double oLhs, Point2D.Double oRhs)
		{
			if (oRhs.y < oLhs.y) // bottom-most
				return 1;
			else if (oRhs.y > oLhs.y)
				return -1;
			else
			{
				if (oRhs.x < oLhs.x) // bottom and left-most
					return 1;
				else if (oRhs.x > oLhs.x)
					return -1;
			}
			return 0;
		}
	}
}
