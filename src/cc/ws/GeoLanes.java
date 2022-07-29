/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.CtrlGeo;
import cc.ctrl.CtrlIndex;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.Introsort;
import cc.util.Units;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author aaron.cherney
 */
public class GeoLanes extends HttpServlet
{
	String m_sBaseDir;
	
	@Override
	public void init(ServletConfig oConfig)
	   throws ServletException
	{
		try
		{
			m_sBaseDir = oConfig.getInitParameter("dir");
			if (!m_sBaseDir.endsWith("/"))
				m_sBaseDir += "/";
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
	@Override
	protected void doGet(HttpServletRequest oRequest, HttpServletResponse oResponse)
	   throws ServletException, IOException
	{
		String[] sUriParts = oRequest.getRequestURI().split("/");
		int nLen = sUriParts.length;
		String sId = sUriParts[nLen - 4] + "/" + sUriParts[nLen - 3] + "/" + sUriParts[nLen - 2] + "/" + sUriParts[nLen - 1];
		Path oPath = Paths.get(m_sBaseDir + sId);
		if (!Files.exists(oPath))
			return;
		
		oResponse.setContentType("text/json");
		try (BufferedInputStream oIn = new BufferedInputStream(Files.newInputStream(oPath));
		     BufferedOutputStream oOut = new BufferedOutputStream(oResponse.getOutputStream()))
		{
			int nByte;
			while ((nByte = oIn.read()) >= 0)
				oOut.write(nByte);
		}
	}
	
	
	@Override
	protected void doPost(HttpServletRequest oRequest, HttpServletResponse oResponse)
	   throws ServletException, IOException
	{
		try
		{
			long lNow = System.currentTimeMillis();
			int nXTile = Integer.parseInt(oRequest.getParameter("x"));
			int nYTile = Integer.parseInt(oRequest.getParameter("y"));
			int nType = Integer.parseInt(oRequest.getParameter("type"));
			Session oSess = SessMgr.getSession(oRequest);
			ArrayList<byte[]> oLoadedIds;
			ArrayList<byte[]> oIdsToLoad = new ArrayList();
			oResponse.setContentType("application/json");
			synchronized (oSess) // lock the session until all of the ids to load are accumulated and saved to the session
			{
				oLoadedIds = oSess.oLoadedIds;

				Path oIndexFile = Paths.get(String.format(CtrlTiles.g_sTdFileFormat, nXTile, CtrlTiles.g_nZoom, nXTile, nYTile) + ".ndx");
				if (!Files.exists(oIndexFile))
				{
					try (BufferedWriter oOut = new BufferedWriter(oResponse.getWriter()))
					{
						oOut.append("{}");
					}
					return;
				}

				try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
				{
					while (oIn.available() > 0)
					{
						CtrlIndex oIndex = new CtrlIndex(oIn);
						if ((oIndex.m_lStart >= lNow || oIndex.m_lEnd > lNow) && nType == oIndex.m_nType) // everything valid now and in the future add to tile
						{
							int nIndex = Collections.binarySearch(oLoadedIds, oIndex.m_yId, TrafCtrl.ID_COMP);
							if (nIndex < 0)
							{
								oLoadedIds.add(~nIndex, oIndex.m_yId);
								oIdsToLoad.add(oIndex.m_yId);
							}
						}
					}
				}
			}

			Introsort.usort(oIdsToLoad, TrafCtrl.ID_COMP);
			StringBuilder sBuf = new StringBuilder();
			
			sBuf.append("{");
			boolean bAdded = false;
			byte[] yPrev = new byte[16];
			yPrev[0] = -1;
			for (byte[] yId : oIdsToLoad)
			{
				if (TrafCtrl.ID_COMP.compare(yPrev, yId) == 0)
					continue;
				yPrev = yId;
				String sId = TrafCtrl.getId(yId);
				String sFile = CtrlTiles.g_sCtrlDir + sId + ".bin";
				if (!Files.exists(Paths.get(sFile)))
					continue;
				sBuf.append("\"").append(sId).append("\":{\"a\":[");
				TrafCtrl oCtrl;
				try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(Paths.get(sFile)))))
				{
					oCtrl = new TrafCtrl(oIn, true, false);
				}
				
				double[] dPts = oCtrl.m_oFullGeo.m_dPT;
				double[] dPt = new double[2];
				Iterator<double[]> oIt = Arrays.iterator(dPts, dPt, 1, 2);
				oIt.next();
				int nPrevX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
				int nPrevY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
				sBuf.append(nPrevX).append(",").append(nPrevY);
				while (oIt.hasNext())
				{
					oIt.next();
					int nX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
					int nY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
					sBuf.append(",").append(nX - nPrevX).append(",").append(nY - nPrevY);
					nPrevX = nX;
					nPrevY = nY;
				}
				sBuf.append("],");
				sBuf.append("\"b\":[");
				dPts = oCtrl.m_oFullGeo.m_dNT;
				oIt = Arrays.iterator(dPts, dPt, 1, 2);
				oIt.next();
				nPrevX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
				nPrevY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
				sBuf.append(nPrevX).append(",").append(nPrevY);
				while (oIt.hasNext())
				{
					oIt.next();
					int nX = Geo.toIntDeg(Mercator.xToLon(dPt[0]));
					int nY = Geo.toIntDeg(Mercator.yToLat(dPt[1]));
					sBuf.append(",").append(nX - nPrevX).append(",").append(nY- nPrevY);
					nPrevX = nX;
					nPrevY = nY;
				}
				sBuf.append("],\"label\":\"").append(oCtrl.m_sLabel).append("\"");
				sBuf.append(",\"reg\":").append(oCtrl.m_bRegulatory);
				sBuf.append(",\"vtypes\":[");
				for (int nVTypeIndex = 0; nVTypeIndex < oCtrl.m_nVTypes.size(); nVTypeIndex++)
					sBuf.append(oCtrl.m_nVTypes.get(nVTypeIndex)).append(',');
				sBuf.setLength(sBuf.length() - 1);
				sBuf.append("],\"vals\":[");
				ArrayList<String> sVals = new ArrayList(4);
				TrafCtrlEnums.getCtrlValString(oCtrl.m_nControlType, oCtrl.m_yControlValue, sVals);
				for (String sVal : sVals)
					sBuf.append("\"").append(sVal).append("\",");
				sBuf.setLength(sBuf.length() - 1);
				sBuf.append("]");
				if (TrafCtrlEnums.CTRLS[oCtrl.m_nControlType].length == 1)
				{
					double dDisplay = Double.parseDouble(sVals.get(1));
					String[] sUnits = TrafCtrlEnums.UNITS[oCtrl.m_nControlType];
					if (sUnits.length > 0)
					{
						dDisplay = Units.getInstance().convert(sUnits[0], sUnits[1], dDisplay);
					}
					sBuf.append(",\"display\":").append(new DecimalFormat("#.##").format(dDisplay));
				}
				sBuf.append("},");
			}
			if (!oIdsToLoad.isEmpty())
				sBuf.setLength(sBuf.length() - 1);
			sBuf.append("}");
			
			try (BufferedWriter oOut = new BufferedWriter(oResponse.getWriter()))
			{
				oOut.append(sBuf);
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
}
