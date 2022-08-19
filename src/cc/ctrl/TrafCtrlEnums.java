package cc.ctrl;

import cc.util.MathUtil;
import cc.util.Text;
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
		{"closed", "open", "notopen", "taperleft", "taperright", "openleft", "openright"}, 
		{"chains", "no", "permitted", "required"}, 
		{"direction", "forward", "reverse"}, 
		{"lataffinity", "left", "right"}, 
		{"latperm", "none", "permitted", "passing-only", "emergency-only"}, 
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
		{"debug"},
		{"maxplatoonsize"},
		{"minplatoonhdwy"}
	};
	
	
	public static final String[][] UNITS = new String[][]
	{
		new String[0], // signal
		new String[0], // stop 
		new String[0], // yield 
		new String[0], //notowing 
		new String[0], //restricted 
		new String[0], //closed", "open", "closed", "taperleft", "taperright", "openleft", "openright 
		new String[0], //chains", "no", "permitted", "required 
		new String[0], //direction", "forward", "reverse 
		new String[0], //lataffinity", "left", "right 
		new String[0], //latperm", "none", "permitted", "passing-only", "emergency-only 
		new String[0], //parking", "no", "parallel", "angled 
		new String[]{"dm/s", "mph", "kph"}, //minspeed 
		new String[]{"dm/s", "mph", "kph"}, //maxspeed 
		new String[]{"dm", "ft", "m"}, //minhdwy 
		new String[]{"kg", "lb", "kg"}, //maxvehmass 
		new String[]{"dm", "ft", "m"}, //maxvehheight 
		new String[]{"dm", "ft", "m"}, //maxvehwidth 
		new String[]{"dm", "ft", "m"}, //maxvehlength 
		new String[0], //maxaxles 
		new String[0], //minvehocc
		new String[0], //pavement
		new String[0], //debug"
		new String[0], //maxplatoonsize
		new String[]{"dm", "ft", "m"}
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
	
	
	public static final String[][] VTYPEGROUPS = new String[][]
	{
		new String[]{"no motor", "pedestrian", "bicycle"},
		new String[]{"motor bikes", "micromobile", "motorcycle"},
		new String[]{"small vehicles", "passenger-car", "light-truck-van"},
		new String[]{"large vehicles", "bus", "two-axle-six-tire-single-unit-truck", "three-axle-single-unit-truck", "four-or-more-axle-single-unit-truck", "four-or-fewer-axle-single-trailer-truck", "five-axle-single-trailer-truck", "six-or-more-axle-single-trailer-truck", "five-or-fewer-axle-multi-trailer-truck", "six-axle-multi-trailer-truck", "seven-or-more-axle-multi-trailer-truck"},
		new String[]{"other", "rail", "unclassified"}
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
	
	
	public static void getCtrlValString(String sCtrl, byte[] yVal, ArrayList<String> sVals)
	{
		getCtrlValString(getCtrl(sCtrl), yVal, sVals);
	}
	
	
	public static void getCtrlValString(int nCtrl, byte[] yVal, ArrayList<String> sVals)
	{
		sVals.add(CTRLS[nCtrl][0]); // ctrl name
		if (nCtrl == getCtrl("signal"))
		{
			sVals.add(Text.toHexString(yVal));
			return;
		}
		if (CTRLS[nCtrl].length == 1) // not enumerated type
		{
			if (yVal.length > 0) // negative values aren't valid
				sVals.add(Integer.toString(MathUtil.bytesToInt(yVal))); 
			return;
		}
		
		int nLatPerm = getCtrl("latperm");
		if (nCtrl == nLatPerm)
		{
			sVals.add(CTRLS[nCtrl][((yVal[2] & 0xff) << 8) | (yVal[3] & 0xff)]); // outer
//			sVals.add(CTRLS[nCtrl][(nVal & 0xffff)]);
			sVals.add(CTRLS[nCtrl][0]);
			sVals.add(CTRLS[nCtrl][((yVal[0] & 0xff) << 8) | (yVal[1] & 0xff)]); // inner
//			sVals.add(CTRLS[nCtrl][((nVal >> 16) & 0xffff)]);
		}
		else
		{
			sVals.add(CTRLS[nCtrl][MathUtil.bytesToInt(yVal)]);
		}
	}
}
