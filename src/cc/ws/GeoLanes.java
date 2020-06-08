/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.geosrv.Mercator;
import cc.util.FileUtil;
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
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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
		StringBuilder sBuf = new StringBuilder();
		try (BufferedInputStream oIn = new BufferedInputStream(oRequest.getInputStream()))
		{
			int nByte;
			while ((nByte = oIn.read()) >= 0)
				sBuf.append((char)nByte);
		}
		String[] sLatLon = sBuf.toString().split(",");
		double dX = Double.parseDouble(sLatLon[0]);
		double dY = Double.parseDouble(sLatLon[1]);
		int[] nTiles = new int[2];
		Mercator.getInstance().lonLatToTile(dX, dY, CtrlTiles.g_nZoom, nTiles);
		HttpSession oSess = oRequest.getSession();
		ArrayList<byte[]> oLoadedIds;
		ArrayList<byte[]> oIdsToLoad = new ArrayList();
		synchronized (oSess) // lock the session until all of the ids to load are accumulated and saved to the session
		{
			if (oSess.getAttribute("ids") == null)
			{
				oLoadedIds = new ArrayList();
				oSess.setAttribute("ids", oLoadedIds);
			}
			oLoadedIds = (ArrayList)oSess.getAttribute("ids");
			int nDebug = TrafCtrlEnums.getCtrl("debug");
			for (int nX = nTiles[0] - 1; nX <= nTiles[0] + 1; nX++)
			{
				for (int nY = nTiles[1] - 1; nY <= nTiles[1] + 1; nY++)
				{
					Path oIndexFile = Paths.get(String.format(CtrlTiles.g_sTdFileFormat, nX, CtrlTiles.g_nZoom, nX, nY) + ".ndx");
					if (!Files.exists(oIndexFile))
						continue;

					byte[] yIdBuf = new byte[16];
					try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile))))
					{
						while (oIn.available() > 0)
						{
							int nType = oIn.readInt();
							oIn.read(yIdBuf);
							oIn.skipBytes(16);
							if (nType == nDebug) 
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
			}
		}
		
		StringBuilder sResBuf = new StringBuilder();
		oResponse.setContentType("text/json");
		sResBuf.append("{");
		for (byte[] yId : oIdsToLoad)
		{
			String sId = TrafCtrl.getId(yId);
			sResBuf.append("\"").append(sId);
			try (BufferedInputStream oIn = new BufferedInputStream(Files.newInputStream(Paths.get(m_sBaseDir + sId))))
			{
				int nByte;
				while ((char)(nByte = oIn.read()) != 'c'); // skip to center lane
				while ((nByte = oIn.read()) >= 0)
					sResBuf.append((char)nByte);
			}
			sResBuf.setLength(sResBuf.length() -1 ); // remove brace at end of file
			sResBuf.append(",");
		}
		sResBuf.setLength(sResBuf.length() - 1); // remove trailing comma
		sResBuf.append("}");
		
		try (BufferedWriter oOut = new BufferedWriter(oResponse.getWriter()))
		{
			if (oIdsToLoad.isEmpty())
				oOut.append("{}");
			else
				oOut.append(sResBuf);
		}
	}
}
