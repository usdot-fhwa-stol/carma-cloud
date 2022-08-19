/**
 * @file Introsort.java
 */
package cc.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Introsort algorithm. Modified quicksort algorithm, that attempts to avoid the
 * degenerate case when supplied a nearly-sorted input. The modification is that
 * when this degenerate case is detected, heap-sort and insertion sort take over
 * to avoid this worst case. Operates in the worst case O(nlgn).
 * <p/>
 * <p>
 * This class also provides search methods, and number manipulators.
 * </p>
 */
public class Introsort
{

	/**
	 * Introsort only sorts sections greater than or equal to this size.
	 */
	private static int SIZE_THRESHOLD = 17;


	/**
	 * <b> Default Constructor </b>
	 * <p>
	 * Creates new instances of {@code Introsort}.
	 * </p>
	 */
	private Introsort()
	{
	}


	public static <T extends Comparable<? super T>> void usort(List<T> iList)
	{
		Comparator<T> oComp = (T o1, T o2) -> o1.compareTo(o2);
		usort(iList, oComp);
	}
	
	
	/**
	 * Wraps {@link Introsort#usort(List, Comparator, int, int)} passing the
	 * appropriate begin-end values.
	 *
	 * @param <T> template type.
	 * @param iList list to sort.
	 * @param iCompare comparator to sort the list by.
	 */
	public static <T> void usort(List<T> iList, Comparator<T> iCompare)
	{
		usort(iList, iCompare, 0, iList.size());
	}


	/**
	 * Calculates a recursion depth and uses introsort for that depth, then uses
	 * insertion sort for the remaining unordered portion of the list.
	 *
	 * @param <T> template type.
	 * @param iList list to sort.
	 * @param iCompare comparator to sort list by.
	 * @param nBegin lower index of the section of the list to sort.
	 * @param nEnd upper index of the section of the list to sort.
	 */
	public static <T> void usort(List<T> iList, Comparator<T> iCompare,
	   int nBegin, int nEnd)
	{
		if (nBegin < nEnd)
		{
			int nSize = 0;
			for (int nValue = nEnd - nBegin; nValue != 1; nValue >>= 1)
				++nSize;

			introsortLoop(iList, iCompare, nBegin, nEnd, 2 * nSize);
			insertionsort(iList, iCompare, nBegin, nEnd);
		}
	}


	/**
	 * Quicksort algorithm modified for Introsort. While the section to sort is
	 * greater than the configured size-threshold, this method recursively
	 * partitions the list ordering the value as it partitions, until the
	 * recursion depth is met, then heapsort is invoked to finish the sorting.
	 *
	 * @param <T> template type.
	 * @param iList list to sort.
	 * @param iCompare comparator to sort the list by.
	 * @param nLo lower index of the section of the list to sort.
	 * @param nHi upper index of the section of the list to sort.
	 * @param nDepthLimit
	 */
	private static <T> void introsortLoop(List<T> iList,
	   Comparator<T> iCompare, int nLo, int nHi, int nDepthLimit)
	{
		while (nHi - nLo > SIZE_THRESHOLD)
		{
			if (nDepthLimit == 0)
			{
				heapsort(iList, iCompare, nLo, nHi);
				return;
			}
			--nDepthLimit;

			int p = partition(iList, iCompare, nLo, nHi, medianof3(iList,
			   iCompare, nLo, nLo + ((nHi - nLo) / 2) + 1, nHi - 1));
			introsortLoop(iList, iCompare, p, nHi, nDepthLimit);
			nHi = p;
		}
	}


	/**
	 * Moves all values that are less than the partition value to the left
	 * (smaller index) of the partition value, values greater are moved to the
	 * right (bigger index) of the partition value.
	 *
	 * @param <T> templated type
	 * @param iList list to partition
	 * @param iCompare comparator to partition the list by
	 * @param nLo lower index of the section of the list to partition
	 * @param nHi upper index of the section of the list to partition
	 * @param oT value to partition the list by
	 * @return index of the partition value, after partitioning
	 */
	private static <T> int partition(List<T> iList, Comparator<T> iCompare,
	   int nLo, int nHi, T oT)
	{
		for (;;)
		{
			while (iCompare.compare(iList.get(nLo), oT) < 0)
				nLo++;

			--nHi;
			while (iCompare.compare(oT, iList.get(nHi)) < 0)
				--nHi;

			if (nLo >= nHi)
				return nLo;

			Collections.swap(iList, nLo, nHi);
			nLo++;
		}
	}


	/**
	 * Determines the median value of three list items at the provided indices.
	 *
	 * @param <T> templated type.
	 * @param iList list containing the items to
	 * @param iCompare comparartor the list items are ordered by.
	 * @param nLo index 1 of the median test.
	 * @param mid index 2 of the median test.
	 * @param nHi index 3 of the median test.
	 * @return the median of the three values in the lists at the 3 provided
	 * indices.
	 */
	private static <T> T medianof3(List<T> iList, Comparator<T> iCompare,
	   int nLo, int mid, int nHi)
	{
		if (iCompare.compare(iList.get(mid), iList.get(nLo)) < 0)
		{
			if (iCompare.compare(iList.get(nHi), iList.get(mid)) < 0)
				return iList.get(mid);

			if (iCompare.compare(iList.get(nHi), iList.get(nLo)) < 0)
				return iList.get(nHi);

			return iList.get(nLo);
		}

		if (iCompare.compare(iList.get(nHi), iList.get(mid)) < 0)
		{
			if (iCompare.compare(iList.get(nHi), iList.get(nLo)) < 0)
				return iList.get(nLo);

			return iList.get(nHi);
		}

		return iList.get(mid);
	}


	/**
	 * Heapsort algorithm. Sorts list[nLo...nHi] in the order defined by the
	 * comparator.
	 *
	 * @param <T> templated type.
	 * @param iList the list to sort.
	 * @param iCompare comparator to order the list by.
	 * @param nLo smallest index.
	 * @param nHi largest index.
	 */
	private static <T> void heapsort(List<T> iList, Comparator<T> iCompare,
	   int nLo, int nHi)
	{
		int n = nHi - nLo;
		for (int i = n / 2; i >= 1; i--)
		{
			downheap(iList, iCompare, i, n, nLo);
		}

		for (int i = n; i > 1; i--)
		{
			Collections.swap(iList, nLo, nLo + i - 1);
			downheap(iList, iCompare, 1, i - 1, nLo);
		}
	}


	/**
	 * Down heap algorithm. Bubbles items down.
	 *
	 * @param <T> templated type.
	 * @param iList list to sort.
	 * @param iCompare comparator the list items are ordered by.
	 * @param i where to start.
	 * @param n how far down to heap down.
	 * @param nLo low value.
	 */
	private static <T> void downheap(List<T> iList, Comparator<T> iCompare,
	   int i, int n, int nLo)
	{
		T oT = iList.get(nLo + i - 1);
		int child = 0;

		while (i <= n / 2)
		{
			child = 2 * i;
			if (child < n && iCompare.compare(iList.get(nLo + child - 1),
			   iList.get(nLo + child)) < 0)
				child++;

			if (iCompare.compare(oT, iList.get(nLo + child - 1)) >= 0)
				break;

			iList.set(nLo + i - 1, iList.get(nLo + child - 1));
			i = child;
		}
		iList.set(nLo + i - 1, oT);
	}


	/**
	 * Insertion sort algorithm. Sorts list in ascending order.
	 *
	 * @param <T> templated type.
	 * @param iList list to sort.
	 * @param iCompare comparator to compare objects in the list.
	 * @param nLo lower bound of the section of the list to sort.
	 * @param nHi upper bound of the section of the list to sort.
	 */
	private static <T> void insertionsort(List<T> iList, Comparator<T> iCompare,
	   int nLo, int nHi)
	{
		int j;
		T oT;

		for (int i = nLo; i < nHi; i++)
		{
			j = i;
			oT = iList.get(i);
			while (j != nLo && iCompare.compare(oT, iList.get(j - 1)) < 0)
			{
				iList.set(j, iList.get(j - 1));
				j--;
			}

			iList.set(j, oT);
		}
	}


	/**
	 * Wraps {@link Introsort#binarySearch(List, Object, int, int, Comparator)},
	 * providing a high and low value that enclose the entire list.
	 *
	 * @param <T> templated type.
	 * @param iList list to search, sorted in ascending order.
	 * @param oKey item to search for.
	 * @param iCompare comparator the list items are ordered by.
	 * @return negative value if the key isn't found, otherwise the index of the
	 * key in the provided list.
	 */
	public static <T> int binarySearch(List<T> iList, T oKey,
	   Comparator<T> iCompare)
	{
		return binarySearch(iList, oKey, 0, iList.size(), iCompare);
	}


	/**
	 * Binary search algorithm. Searches the provided array for the supplied
	 * key.
	 *
	 * @param <T> templated type.
	 * @param iList list to search, sorted in ascending order.
	 * @param oKey item to search for.
	 * @param nLow low value for midpoint calculation.
	 * @param nHigh high value for midpoint calculation.
	 * @param iCompare comparator the list items are ordered by.
	 * @return negative value if the key isn't found, otherwise the index of the
	 * key in the provided list.
	 */
	public static <T> int binarySearch(List<T> iList, T oKey,
	   int nLow, int nHigh, Comparator<T> iCompare)
	{
		--nHigh;
		while (nLow <= nHigh)
		{
			int nMid = (nLow + nHigh) >>> 1;
			int nCompare = iCompare.compare(iList.get(nMid), oKey);

			if (nCompare < 0)
				nLow = nMid + 1;
			else if (nCompare > 0)
				nHigh = nMid - 1;
			else
				return nMid; // key found
		}
		return -(nLow + 1); // key not found
	}


	/**
	 * Maps the provided value to the nearest multiple of the supplied
	 * precision, that's less than the value.
	 * <p>
	 * Ex: given a value of 17, and a grid size (precision) of 5, the value
	 * would be mapped into index 15.
	 * </p>
	 *
	 * @param nValue the value to floor
	 * @param nPrecision the grid size.
	 * @return the floored index.
	 */
	public static int floor(int nValue, int nPrecision)
	{
		// this integer flooring method returns the next smallest integer
		int nFlooredValue = nValue / nPrecision * nPrecision;

		// correct for negative numbers
		// ensure the value was not previously floored,
		// or this operation will return the wrong result
		if (nValue < 0 && nFlooredValue != nValue)
			nFlooredValue -= nPrecision;

		return nFlooredValue;
	}
}
