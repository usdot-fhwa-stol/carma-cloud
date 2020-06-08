package cc.ctrl;

import java.util.ArrayList;


public abstract class TrafCtrlEnums
{
	public static final String[][] CTRLS = new String[][]
	{
		{"signal"}, 
		{"stop"}, 
		{"yield"}, 
		{"notowing"}, 
		{"restricted"}, 
		{"closed"}, 
		{"chains", "no", "permitted", "required"}, 
		{"direction", "forward", "reverse"}, 
		{"lataffinity", "left", "right"}, 
		{"latperm", "none", "permitted", "passing-only", "emergency-only"}, 
		{"opening", "left", "right"}, 
		{"closing", "left", "right"}, 
		{"parking", "no", "parallel", "angled"}, 
		{"minspeed"}, 
		{"maxspeed"}, 
		{"minhdwy"}, 
		{"maxvehmass"}, 
		{"maxvehheight"}, 
		{"maxvehwidth"}, 
		{"maxvehlength"}, 
		{"maxaxles"}, 
		{"minvehocc"},
		{"pavement"},
		{"debug"}
	};

	public static final char[] DAYCHARS = new char[]
	{
		'S', 'M', 'T', 'W', 'R', 'F', 'A'
	};
	
	
	public static final String[] DAYS = new String[]
	{
		"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
	};

	public static final String[] VTYPES = new String[]
	{
		"pedestrian", 
		"bicycle", 
		"micromobile", 
		"motorcycle", 
		"passenger-car", 
		"light-truck-van", 
		"bus", 
		"two-axle-six-tire-single-unit-truck", 
		"three-axle-single-unit-truck", 
		"four-or-more-axle-single-unit-truck", 
		"four-or-fewer-axle-single-trailer-truck", 
		"five-axle-single-trailer-truck", 
		"six-or-more-axle-single-trailer-truck", 
		"five-or-fewer-axle-multi-trailer-truck", 
		"six-axle-multi-trailer-truck", 
		"seven-or-more-axle-multi-trailer-truck", 
		"rail", 
		"unclassified"
	};


	private TrafCtrlEnums()
	{
	}


	public static int getVtype(String sVType)
	{
		int nIndex = VTYPES.length;
		while (nIndex-- > 0)
		{
			if (sVType.compareTo(VTYPES[nIndex]) == 0)
				return nIndex;
		}
		return Integer.MIN_VALUE;
	}


	public static int getCtrl(String sCtrl)
	{
		int nIndex = CTRLS.length;
		while (nIndex-- > 0)
		{
			if (sCtrl.compareTo(CTRLS[nIndex][0]) == 0)
				return nIndex;
		}
		return Integer.MIN_VALUE;
	}


	public static int getCtrlVal(String sCtrl, String sVal)
	{
		int nIndex = getCtrl(sCtrl);
		if (nIndex >= 0)
		{
			String[] sVals = CTRLS[nIndex];
			nIndex = sVals.length;
			while (nIndex-- > 1) // first value (index 0) is ctrl type
			{
				if (sVal.compareTo(sVals[nIndex]) == 0)
					return nIndex;
			}
		}
		return Integer.MIN_VALUE;
	}
	
	
	public static void getCtrlValString(String sCtrl, int nVal, ArrayList<String> sVals)
	{
		getCtrlValString(getCtrl(sCtrl), nVal, sVals);
	}
	
	
	public static void getCtrlValString(int nCtrl, int nVal, ArrayList<String> sVals)
	{
		sVals.add(CTRLS[nCtrl][0]); // ctrl name
		if (CTRLS[nCtrl].length == 1) // not enumerated type
		{
			if (nVal >= 0) // negative values aren't valid
				sVals.add(Integer.toString(nVal)); 
			return;
		}
		
		int nLatPerm = getCtrl("latperm");
		if (nCtrl == nLatPerm)
		{
//			sVals.add(CTRLS[nCtrl][0]);
			sVals.add(CTRLS[nCtrl][(nVal & 0xffff)]);
			sVals.add(CTRLS[nCtrl][0]);
			sVals.add(CTRLS[nCtrl][((nVal >> 16) & 0xffff)]);
		}
		else
			sVals.add(CTRLS[nCtrl][nVal]);
	}
}
