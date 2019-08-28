package cc.ws;

import cc.util.CsvReader;
import cc.util.Text;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.SimpleTimeZone;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;


public class Handler extends HttpServlet
{
	protected static Comparator<String[]> STR_ARR_COMP = (String[] o1, String[] o2) -> o1[0].compareTo(o2[0]);
	protected final static SimpleDateFormat ISO8601Sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	
	static
	{
		ISO8601Sdf.setTimeZone(new SimpleTimeZone(0, "")); // set utc time
	}
	
	@Override
	protected void doPost(HttpServletRequest oReq, HttpServletResponse oRep) 
	   throws ServletException, IOException
	{
		try
		{
			Session oSess = SessMgr.getSession(oReq);
			if (oSess == null)
			{
				oRep.sendError(401);
				return;
			}
		
		
			StringBuilder sMethod = new StringBuilder("do");
			String sAction = oReq.getPathInfo();
			sMethod.append(sAction);
			if (sMethod.charAt(2) == '/') // remove leading "/"
				sMethod.deleteCharAt(2); 
			
			sMethod.setCharAt(2, Character.toUpperCase(sMethod.charAt(2))); // upper case the first character of the action
			if (Text.compare(sMethod, "doPost") == 0)
			{
				oRep.sendError(401);
				return;
			}
			
			for (Method oMethod : getClass().getDeclaredMethods())
			{
				if (Text.compare(sMethod, oMethod.getName()) == 0)
				{
					try (PrintWriter oOut = oRep.getWriter())
					{
						oMethod.invoke(this, oSess, oReq, oOut);
						return;
					}
				}
			}
			oRep.sendError(401);
		}
		catch (Exception oEx)
		{
			oRep.sendError(409);
		}
	}
	
	
	public static void update(String[] sObj, int nCols, CsvReader oIn)
	{
		int nLimit = Math.min(nCols, sObj.length);
		for (int i = 0; i < nLimit; i++)
			sObj[i] = oIn.parseString(i);
	}
	
	
	public static void writeJson(PrintWriter oOut, String sId, String sData)
	{
		oOut.write(String.format("\"%s\":%s", sId, sData));
	}
}
