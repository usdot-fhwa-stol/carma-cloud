/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcCtrl;
import cc.ctrl.proc.ProcMaxSpeed;
import cc.geosrv.Mercator;
import cc.util.FileUtil;
import cc.util.MathUtil;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author aaron.cherney
 */
public class WxPoly extends HttpServlet
{
	HashMap<String, ArrayList<byte[]>> m_oCurrentIds = new HashMap();
	static double REDUCE = 0.6;
	
	
	@Override
	protected void doPost(HttpServletRequest oRequest, HttpServletResponse oResponse)
	   throws ServletException
	{
		try
		{
			long lNow = System.currentTimeMillis();
			StringBuilder sBuf = new StringBuilder();
			try (BufferedInputStream oIn = new BufferedInputStream(oRequest.getInputStream()))
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					sBuf.append((char)nByte);
			}
			String sLatLonId = sBuf.toString();
			if (m_oCurrentIds.containsKey(sLatLonId))
			{
				for (byte[] yId : m_oCurrentIds.get(sLatLonId))
				{
					updateCtrl(yId, lNow);
				}

				m_oCurrentIds.remove(sLatLonId);
			}
			else
			{
				ArrayList<byte[]> oCurrentIds = new ArrayList();
				double[] dPolyBB = new double[4];
				String[] sOrds = sLatLonId.split(",");
				for (int nIndex = 0; nIndex < dPolyBB.length; nIndex++)
					dPolyBB[nIndex] = Double.parseDouble(sOrds[nIndex]);

				int[] nTiles = new int[2];
				Mercator.getInstance().lonLatToTile(dPolyBB[0], dPolyBB[3], CtrlTiles.g_nZoom, nTiles); // determine the correct tiles for the default zoom level
				int nStartX = nTiles[0];
				int nStartY = nTiles[1];
				Mercator.getInstance().lonLatToTile(dPolyBB[2], dPolyBB[1], CtrlTiles.g_nZoom, nTiles);
				int nEndX = nTiles[0];
				int nEndY = nTiles[1];
				int nMaxSpeed = TrafCtrlEnums.getCtrl("maxspeed");
				byte[] sIdBuf = new byte[16];
				for (int nX = nStartX; nX <= nEndX; nX++)
				{
					for (int nY = nStartY; nY <= nEndY; nY++)
					{
						Path oPath = Paths.get(String.format(CtrlTiles.g_sTdFileFormat, nX, CtrlTiles.g_nZoom, nX, nY) + ".ndx");
						if (!Files.exists(oPath))
							continue;

						ArrayList<byte[]> oTileIds = new ArrayList();
						try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oPath))))
						{
							while (oIn.available() > 0)
							{
								int nType = oIn.readInt();
								if (nType != nMaxSpeed)
									oIn.skipBytes(32); // skip id and 2 timestamps
								else
								{
									oIn.read(sIdBuf);
									long lStart = oIn.readLong();
									long lEnd = oIn.readLong();
									if (lStart >= lNow || lEnd > lNow)
									{
										
										int nSearch = Collections.binarySearch(oTileIds, sIdBuf, TrafCtrl.ID_COMP);
										if (nSearch < 0)
										{
											byte[] yId = new byte[16];
											System.arraycopy(sIdBuf, 0, yId, 0 , yId.length);
											oTileIds.add(~nSearch, yId);
										}
									}
								}
							}
						}

						
						for (byte[] yId : oTileIds)
						{
							int nSearch = Collections.binarySearch(oCurrentIds, yId, TrafCtrl.ID_COMP);
							if (nSearch < 0)
								oCurrentIds.add(~nSearch, yId);
						}
					}
				}
				
				ArrayList<TrafCtrl> oCtrls = new ArrayList();
				ArrayList<int[]> oTiles = new ArrayList();
				for (byte[] yId : oCurrentIds)
				{
					String sId = TrafCtrl.getId(yId);
					TrafCtrl oCtrl;
					try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(CtrlTiles.g_sCtrlDir + sId + ".bin"))))
					{
						oCtrl = new TrafCtrl(oIn, false);
					}
					int nSpeed = MathUtil.bytesToInt(oCtrl.m_yControlValue);
//							if (nSpeed <= 25)
//								continue;
					TrafCtrl oSpdLimit = new TrafCtrl("maxspeed", nSpeed - 10, lNow, oCtrl, "weather", false);
					oSpdLimit.m_lEnd = lNow + 1800000;
					oSpdLimit.write(CtrlTiles.g_sCtrlDir, ProcCtrl.g_dExplodeStep, CtrlTiles.g_nZoom);
					ProcCtrl.updateTiles(oTiles, oSpdLimit.m_oFullGeo.m_oTiles);
					oCtrls.add(oSpdLimit);
				}
				ProcMaxSpeed.renderTiledData(oCtrls, oTiles);
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
	
	
	private void updateCtrl(byte[] yId, long lNow)
	{
		
	}
}
