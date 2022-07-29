/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.CtrlIndex;
import cc.ctrl.TrafCtrl;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcCtrl;
import cc.ctrl.proc.ProcMaxSpeed;
import cc.geosrv.Mercator;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.Introsort;
import cc.util.MathUtil;
import cc.util.Units;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
			Units oUnits = Units.getInstance();
			String[] sUnits = TrafCtrlEnums.UNITS[TrafCtrlEnums.getCtrl("maxspeed")];
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
				
				double[] dMercBB = new double[]{Mercator.lonToMeters(dPolyBB[0]), Mercator.latToMeters(dPolyBB[1]), Mercator.lonToMeters(dPolyBB[2]), Mercator.latToMeters(dPolyBB[3])};

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

						try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oPath))))
						{
							while (oIn.available() > 0)
							{
								CtrlIndex oIndex = new CtrlIndex(oIn);
								
								if (oIndex.m_nType == nMaxSpeed && oIndex.m_lStart >= lNow || oIndex.m_lEnd > lNow && Geo.boundingBoxesIntersect(dMercBB, oIndex.m_dBB))
									oCurrentIds.add(oIndex.m_yId);
							}
						}
					}
				}
				
				Introsort.usort(oCurrentIds, TrafCtrl.ID_COMP);
				ArrayList<TrafCtrl> oCtrls = new ArrayList();
				ArrayList<int[]> oTiles = new ArrayList();
				byte[] yPrev = new byte[16];
				yPrev[0] = -1;
				for (byte[] yId : oCurrentIds)
				{
					if (TrafCtrl.ID_COMP.compare(yPrev, yId) == 0)
						continue;
					yPrev = yId;
					String sId = TrafCtrl.getId(yId);
					TrafCtrl oCtrl;
					Path oFile = Paths.get(CtrlTiles.g_sCtrlDir + sId + ".bin");
					try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oFile))))
					{
						oCtrl = new TrafCtrl(oIn, true, false);
					}
					if (!oCtrl.m_bRegulatory)
						continue;
					
					int nSpeedMph = (int)Math.round(oUnits.convert(sUnits[0], sUnits[1], MathUtil.bytesToInt(oCtrl.m_yControlValue))) - 10;
					int nNewSpeed = (int)Math.round(oUnits.convert(sUnits[1], sUnits[0], nSpeedMph));
					TrafCtrl oSpdLimit = new TrafCtrl("maxspeed", nNewSpeed, lNow, oCtrl, "weather", false, ProcCtrl.CC);
					oSpdLimit.m_lEnd = lNow + 1800000;
					oSpdLimit.write(CtrlTiles.g_sCtrlDir, ProcCtrl.g_dExplodeStep, CtrlTiles.g_nZoom, ProcCtrl.CC);
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
