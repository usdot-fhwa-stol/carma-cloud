/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.CtrlGeo;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.Units;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

				byte[] yIdBuf = new byte[16];
				try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
				{
					while (oIn.available() > 0)
					{
						int nTempType = oIn.readInt();
						oIn.read(yIdBuf);
						long lStart = oIn.readLong();
						long lEnd = oIn.readLong();
						if ((lStart >= lNow || lEnd > lNow) && nType == nTempType) // everything valid now and in the future add to tile
						{
							int nIndex = Collections.binarySearch(oLoadedIds, yIdBuf, TrafCtrl.ID_COMP);
							if (nIndex < 0)
							{
								byte[] yId = new byte[16];
								System.arraycopy(yIdBuf, 0, yId, 0, 16);
								oLoadedIds.add(~nIndex, yId);
								nIndex = Collections.binarySearch(oIdsToLoad, yId, TrafCtrl.ID_COMP);
								if (nIndex < 0)
									oIdsToLoad.add(~nIndex, yId);
							}	
						}
					}
				}
			}

			StringBuilder sBuf = new StringBuilder();
			
			sBuf.append("{");
			boolean bAdded = false;
			for (byte[] yId : oIdsToLoad)
			{
				String sId = TrafCtrl.getId(yId);
				String sFile = CtrlTiles.g_sCtrlDir + sId + ".bin";
				if (!Files.exists(Paths.get(sFile)))
					continue;
				sBuf.append("\"").append(sId).append("\":{\"a\":[");
				TrafCtrl oCtrl;
				try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
				{
					oCtrl = new TrafCtrl(oIn, false);
				}
				try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
				{
					oCtrl.m_oFullGeo = new CtrlGeo(oIn, CtrlTiles.g_nZoom);
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
					int nDisplay = Integer.parseInt(sVals.get(1));
					String[] sUnits = TrafCtrlEnums.UNITS[oCtrl.m_nControlType];
					if (sUnits.length > 0)
					{
						nDisplay = (int)Math.round(Units.getInstance().convert(sUnits[0], sUnits[1], nDisplay));
					}
					sBuf.append(",\"display\":").append(nDisplay);
				}
				sBuf.append("},");
				bAdded = true;
			}
			if (bAdded)
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
