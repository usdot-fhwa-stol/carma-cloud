package cc.util;

import java.io.PrintStream;
import java.util.Iterator;


/**
 *
 * @author bryan.krueger
 */
public abstract class Arrays
{
	private static final int DEFAULT_CAPACITY = 12;


	private Arrays()
	{
	}


	public static double[] newDoubleArray()
	{
		return newDoubleArray(DEFAULT_CAPACITY);
	}


	public static double[] newDoubleArray(int nCapacity)
	{
		double[] dVals = new double[++nCapacity]; // reserve slot for size
		dVals[0] = 1.0; // initial position is always one
		return dVals;
	}


	public static Iterator<double[]> iterator(double[] dSrc, double[] dDest, int nStart, int nStep)
	{
		return new DoubleGroupIterator(dSrc, dDest, nStart, nStep);
	}


	public static double[] ensureCapacity(double[] dVals, int nDemand)
	{
		if ((int)dVals[0] + nDemand < dVals.length)
			return dVals; // no changes needed

		double[] dNew = new double[nDemand + (3 * dVals.length >> 1)];
		System.arraycopy(dVals, 0, dNew, 0, (int)dVals[0]);
		return dNew;
	}


	public static int size(double[] dVals)
	{
		return (int)dVals[0];
	}


	public static double[] add(double[] dVals, double d1)
	{
		dVals = ensureCapacity(dVals, 1);
		dVals[(int)dVals[0]] = d1; // current insertion position
		dVals[0] += 1.0; // update position
		return dVals;
	}


	public static double[] add(double[] dVals, double d1, double d2)
	{
		dVals = ensureCapacity(dVals, 2);
		int nIndex = (int)dVals[0]; // current insertion position
		dVals[nIndex++] = d1;
		dVals[nIndex++] = d2;
		dVals[0] = (double)nIndex; // track insertion position
		return dVals;
	}


	public static double[] add(double[] dVals, double[] dMore)
	{
		dVals = ensureCapacity(dVals, dMore.length);
		int nIndex = (int)dVals[0]; // current insertion position
		System.arraycopy(dMore, 0, dVals, nIndex, dMore.length);
		dVals[0] = nIndex + dMore.length; // track insertion position
		return dVals;
	}


	public static int[] newIntArray()
	{
		return newIntArray(DEFAULT_CAPACITY);
	}


	public static int[] newIntArray(int nCapacity)
	{
		int[] nVals = new int[++nCapacity]; // reserve slot for size
		nVals[0] = 1; // initial position is always one
		return nVals;
	}


	public static Iterator<int[]> iterator(int[] nSrc, int[] nDest, int nStart, int nStep)
	{
		return new IntGroupIterator(nSrc, nDest, nStart, nStep);
	}


	public static int[] ensureCapacity(int[] nVals, int nDemand)
	{
		if (nVals[0] + nDemand < nVals.length)
			return nVals; // no changes needed

		int[] nNew = new int[nDemand + (3 * nVals.length >> 1)];
		System.arraycopy(nVals, 0, nNew, 0, nVals[0]);
		return nNew;
	}


	public static int size(int[] nVals)
	{
		return nVals[0];
	}


	public static int[] add(int[] nVals, int n1)
	{
		nVals = ensureCapacity(nVals, 1);
		nVals[nVals[0]] = n1; // current insertion position
		++nVals[0]; // update position
		return nVals;
	}


	public static int[] add(int[] nVals, int n1, int n2)
	{
		nVals = ensureCapacity(nVals, 2);
		int nIndex = nVals[0]; // current insertion position
		nVals[nIndex++] = n1;
		nVals[nIndex++] = n2;
		nVals[0] = nIndex; // track insertion position
		return nVals;
	}


	public static int[] add(int[] nVals, int[] nMore)
	{
		nVals = ensureCapacity(nVals, nMore.length);
		int nIndex = nVals[0]; // current insertion position
		System.arraycopy(nMore, 0, nVals, nIndex, nMore.length);
		nVals[0] = nIndex + nMore.length; // track insertion position
		return nVals;
	}
	
	
	public static void printArray(double[] dArray, int nStart, PrintStream oPrint) throws Exception
	{
		Iterator<double[]> oIt = iterator(dArray, new double[1], nStart, 1);
		boolean bWrite = oIt.hasNext();
		if (bWrite)
		{
			double[] dVal = oIt.next();
			oPrint.append(Double.toString(dVal[0]));
		}
		while (oIt.hasNext())
		{
			double[] dVal = oIt.next();
			oPrint.append(",").append(Double.toString(dVal[0]));
		}
		if (bWrite)
			oPrint.append("\n");
	}


	public static void printArray(int[] nArray, int nStart, PrintStream oPrint) throws Exception
	{
		Iterator<int[]> oIt = iterator(nArray, new int[1], nStart, 1);
		boolean bWrite = oIt.hasNext();
		if (bWrite)
		{
			int[] nVal = oIt.next();
			oPrint.append(Integer.toString(nVal[0]));
		}
		while (oIt.hasNext())
		{
			int[] nVal = oIt.next();
			oPrint.append(",").append(Integer.toString(nVal[0]));
		}
		if (bWrite)
			oPrint.append("\n");
	}
	
	
	private static abstract class GroupIterator
	{
		protected int m_nPos;
		protected int m_nEnd;
		protected int m_nStep;


		protected GroupIterator()
		{
		}


		protected GroupIterator(int nStart, int nLimit, int nDestSize, int nStep)
		{
			m_nStep = nStep;
			m_nEnd = nLimit - nDestSize; // array end boundary
			m_nPos = nStart;
		}


		public boolean hasNext()
		{
			return (m_nPos <= m_nEnd);
		}


		public void remove()
		{
			throw new UnsupportedOperationException("remove");
		}
	}


	private static class DoubleGroupIterator extends GroupIterator implements Iterator<double[]>
	{
		private double[] m_dSrc;
		private double[] m_dDest;


		protected DoubleGroupIterator()
		{
		}


		public DoubleGroupIterator(double[] dSrc, double[] dDest, int nStart, int nStep)
			throws IllegalArgumentException
		{
			super(nStart, (int)dSrc[0], dDest.length, nStep);
			if (dSrc.length == 0 || dDest.length == 0 || dSrc.length < dDest.length + 1 || nStart < 0 || nStep <= 0)
				throw new IllegalArgumentException();
			m_dDest = dDest;
			m_dSrc = dSrc; // local reference to values
		}


		@Override
		public double[] next()
		{
			System.arraycopy(m_dSrc, m_nPos, m_dDest, 0, m_dDest.length);
			m_nPos += m_nStep; // shift to next group position
			return m_dDest;
		}
	}


	private static class IntGroupIterator extends GroupIterator implements Iterator<int[]>
	{
		private int[] m_nSrc;
		private int[] m_nDest;


		protected IntGroupIterator()
		{
		}


		public IntGroupIterator(int[] nSrc, int[] nDest, int nStart, int nStep)
			throws IllegalArgumentException
		{
			super(nStart, nSrc[0], nDest.length, nStep);
			if (nSrc.length == 0 || nDest.length == 0 || nSrc.length < nDest.length + 1 || nStart < 0 || nStep <= 0)
				throw new IllegalArgumentException();
			m_nDest = nDest;
			m_nSrc = nSrc; // local reference to values
		}


		@Override
		public int[] next()
		{
			System.arraycopy(m_nSrc, m_nPos, m_nDest, 0, m_nDest.length);
			m_nPos += m_nStep; // shift to next group position
			return m_nDest;
		}
	}
}
