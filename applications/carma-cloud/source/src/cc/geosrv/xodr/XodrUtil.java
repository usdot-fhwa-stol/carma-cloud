/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr;

import cc.util.Text;

/**
 *
 * @author aaron.cherney
 */
public abstract class XodrUtil
{
	public static String[] LANETYPES = new String[]
	{
		"none",
		"driving",
		"stop",
		"shoulder",
		"biking",
		"sidewalk",
		"border",
		"restricted",
		"parking",
		"bidirectional",
		"median",
		"special1",
		"special2",
		"special3",
		"roadWorks",
		"tram",
		"rail",
		"entry",
		"exit",
		"offRamp",
		"onRamp",
		"connectingRamp",
		"bus",
		"taxi",
		"HOV"
	};
	
	public static int getLaneType(String sType)
	{
		int nIndex = LANETYPES.length;
		while (nIndex-- > 0)
		{
			if (LANETYPES[nIndex].compareTo(sType) == 0)
				return nIndex;
		}
		
		return 0; // return 0 which is "none" if there is no match
	}
	
	
	public static String getLaneType(int nType)
	{
		return LANETYPES[nType];
	}
	
	
	public static int getLaneId(int nRoadId, int nLaneIdByRoad)
	{
		int nId = nRoadId;
		nId <<= 16;
		nId |= (nLaneIdByRoad & 0xff);
		
		return nId;
	}
	
	
	public static long getLaneSectionId(int nRoadId, int nSectionId, int nLaneIndex)
	{
		long lId = nRoadId;
		lId <<= 16;
		lId |= (nSectionId & 0xff);
		lId <<= 8;
		lId |= (nLaneIndex & 0xff);

		return lId;
	}
	
	
	public static int getLaneIndex(long lLaneSectionId)
	{
		return (byte)(lLaneSectionId & 0xff);
	}
	
	
	public static int getRoadId(int nLaneId)
	{
		return nLaneId >> 16;
	}
	
	
	public static void splitLaneSectionId(long lLaneSectionId, int nPos, int[] nArray)
	{
		nArray[nPos] = (int)((lLaneSectionId >> 32) & 0xffffffff);
		nArray[nPos + 1] = (int)(lLaneSectionId & 0xffffffff);
	}
	
	
	public static void main(String[] sArgs)
	{
		long lId = getLaneSectionId(272, 1, -1);
		int[] nSearch = new int[2];
		nSearch[0] = (int)((lId >> 32) & 0xffffffff);
		nSearch[1] = (int)(lId & 0xffffffff);
		System.out.println(nSearch[0]);
		System.out.println(nSearch[1]);
		long lNew = nSearch[0];
		lNew <<= 32;
		lNew |= nSearch[1];
		System.out.println(lNew);
	}
	
	
	public static double parseDouble(String sVal)
	{
		if (sVal.endsWith("inf"))
			sVal = sVal.replace("inf", "Infinity");
		
		return Double.parseDouble(sVal);
	}
}