package cc.util;


public class Gpc
{
	public static final int DIFF = 0;
	public static final int INT = 1;
	public static final int XOR = 2;
	public static final int UNION = 3;


	static
	{
		System.loadLibrary("gpcwrapper");
	}


	private Gpc()
	{
	}


	/**
	 * Allocates a new polygon in the native library, and copies the number of 
	 * ordinates specified by length from the array ordinates in x/y order 
	 * starting at the offset position.
	 *
	 * @param nLen		the number of double values to copy
	 * @param nOffset	starting index of double array
	 * @param dOrds		double array of values in x/y order
	 * 
	 * @return				handle to native library allocated polygon
	 */
	private static native long newPolygon(int nLen, int nOffset, double[] dOrds);


	/**
	 * Copies the set of external polygon vertices specified by handle to an 
	 * array of double array ordinates in x/y order, and inserts the number of 
	 * ordinates and computed bounding box in the first five values.
	 * 
	 * The returned integer array specifies if a polygon at the corresponding 
	 * index is considered a hole. 0 indicates a solid polygon and 1 indicates a hole.
	 * 
	 * Note that additional array elements may contain random data.
	 *
	 * @param lGpcPolygon		a handle to the native library polygon
	 * @param nExtra				reserve additional values for custom purposes
	 * @param dPolygons			an array of polygons represented by 
	 *											double arrays of x/y ordinates, the top level array 
	 *											should be allocated in Java, the native library 
	 *											allocates the second level arrays
	 * 
	 * @return							integer array that matches the count of polygons 
	 *											and indicates if a polygon is considered a hole
	 */
	private static native int[] getBoundedPolygon(long lGpcPolygon, int nExtra, double[][] dPolygons);


	/**
	 * Copies the set of external polygon vertices specified by handle to an 
	 * array of double array ordinates in x/y order, reserving any additional  
	 * elements requested at the beginning of the array.
	 * 
	 * The returned integer array specifies if a polygon at the corresponding 
	 * index is considered a hole. 0 indicates a solid polygon and 1 indicates a hole.
	 * 
	 * Note that additional array elements may contain random data.
	 *
	 * @param lGpcPolygon		a handle to the native library polygon
	 * @param nReserve			reserve additional values for custom purposes
	 * @param dPolygons			an array of polygons represented by 
	 *											double arrays of x/y ordinates, the top level array 
	 *											should be allocated in Java, the native library 
	 *											allocates the second level arrays
	 * 
	 * @return							integer array that matches the count of polygons 
	 *											and indicates if a polygon is considered a hole
	 */
	private static native int[] getPolygon(long lGpcPolygon, int nReserve, double[][] dPolygons);


	/**
	 * Perform polygon clipping operation. Supported operations are difference, 
	 * intersection, exclusive or, and union. The subject polygon handle will 
	 * remain unchanged and point to the resulting polygon. The clip polygon will 
	 * be automatically released.
	 *
	 * @param nOp									enumerated value specifying the operation to perform
	 * @param lGpcPolygonSubject	a handle to the first native library polygon for the operation
	 * @param lGpcPolygonClip			a handle to the second native library polygon for the operation
	 * 
	 * @return										the count of polygons in the resulting set
	 */
	private static native int op(int nOp, long lGpcPolygonSubject, long lGpcPolygonClip);


	/**
	 * Release native library allocated polygon. Typically the subject polygon 
	 * of performed operations.
	 *
	 * @param lGpcPolygon	the handle to the native library polygon to be freed
	 */
	private static native void freePolygon(long lGpcPolygon);


	public static void main(String[] sArgs)
		throws Exception
	{
		double[] dOrds1 = new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 2.0, 1.0, 2.0, 0.0, 1.0, 0.0};
		double[] dOrds2 = new double[]{0.0, 1.0, 0.0, 2.0, 1.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0};
		long lPoly1 = Gpc.newPolygon(dOrds1.length, 0, dOrds1);
		long lPoly2 = Gpc.newPolygon(dOrds2.length, 0, dOrds2);

		int nCount = Gpc.op(Gpc.UNION, lPoly1, lPoly2);
		double[][] dPolygons = new double[nCount][];
		int[] nHoles = Gpc.getPolygon(lPoly1, 0, dPolygons);
		Gpc.freePolygon(lPoly1);

		for (int nIndex = 0; nIndex < nHoles.length; nIndex++)
			System.out.println(nHoles[nIndex]);

		for (double[] dPolygon : dPolygons)
		{
			System.out.println();
			for (int nIndex = 0; nIndex < dPolygon.length; nIndex++)
				System.out.println(dPolygon[nIndex]);
		}
	}
}
