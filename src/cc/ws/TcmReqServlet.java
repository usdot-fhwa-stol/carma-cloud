/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.TrafCtrl;
import cc.ctrl.CtrlIndex;
import cc.ctrl.TcBounds;
import cc.ctrl.TcmReqParser2;
import cc.ctrl.TcmReq;
import cc.ctrl.TcmReqParser;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcCtrl;
import cc.geosrv.Mercator;
import cc.util.FileUtil;
import cc.util.Geo;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 *
 * @author aaron.cherney
 */
public class TcmReqServlet extends HttpServlet implements Runnable
{
	protected static final Logger LOGGER = LogManager.getLogger(TcmReqServlet.class);

	private int m_nExplodeDistForXml = 0;
	private boolean m_bRemoveWidth = false;
	private int m_nPolyStep = 50;
	private static int[] IGNORE_CTRLS;
	static
	{
		IGNORE_CTRLS = new int[]{TrafCtrlEnums.getCtrl("pavement"), TrafCtrlEnums.getCtrl("debug")};
		java.util.Arrays.sort(IGNORE_CTRLS);
	}
	
	
	@Override
	public void init(ServletConfig oConfig)
	{
		String sRemoveWidth = oConfig.getInitParameter("removewidth");
		if (sRemoveWidth != null)
			m_bRemoveWidth = Boolean.parseBoolean(sRemoveWidth);
		
		String sExplodeDistForXml = oConfig.getInitParameter("xmldist");
		if (sExplodeDistForXml != null)
		{
			int nExplode = Integer.parseInt(sExplodeDistForXml);
			if (nExplode == 0)
				return;
			if (nExplode < ProcCtrl.g_dExplodeStep * 100)
				nExplode = (int)(ProcCtrl.g_dExplodeStep * 100);
			m_nExplodeDistForXml = nExplode;
		}
		
		String sPolyStep = oConfig.getInitParameter("polystep");
		if (sPolyStep != null)
			m_nPolyStep = Integer.parseInt(sPolyStep);
	}
	
	
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws ServletException, IOException
	{
		StringBuilder sReq = new StringBuilder(1024);
		try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream()))
		{
			int nByte;
			while ((nByte = oIn.read()) >= 0)
				sReq.append((char)nByte);
		}
		
		new Thread(this, sReq.toString()).start();
		LOGGER.debug(sReq);
	}

	
	@Override
	public void run()
	{
		try
		{
			long lNow = System.currentTimeMillis();
			String sReq = Thread.currentThread().getName();
			TcmReqParser oReqParser;
			if (sReq.indexOf("<tcrV01>") >= 0)
				oReqParser = new TcmReqParser();
			else
				oReqParser = new TcmReqParser2();

			TcmReq oTcmReq = oReqParser.parseRequest(new ByteArrayInputStream(sReq.getBytes(StandardCharsets.UTF_8)));

			ArrayList<TrafCtrl> oResCtrls = new ArrayList();
			ArrayList<TileIds> oTiles = new ArrayList(50);
			ArrayList<byte[]> oProcessed = new ArrayList(1000);
			TrafCtrl oTrafCtrlSearch = new TrafCtrl();
			oTrafCtrlSearch.m_yId = new byte[16];
			TileIds oTileIdsSearch = new TileIds();
			int nMsgTot = 0;
			for (TcBounds oBounds : oTcmReq.m_oBounds)
			{
				double[] dTcMercBounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
				double[] dCorners = new double[oBounds.m_nCorners.length];
				for (int nIndex = 0; nIndex < oBounds.m_nCorners.length;)
				{
					double dX = Mercator.lonToMeters(Geo.fromIntDeg(oBounds.m_nCorners[nIndex]));
					dCorners[nIndex++] = dX;
					double dY = Mercator.latToMeters(Geo.fromIntDeg(oBounds.m_nCorners[nIndex]));
					dCorners[nIndex++] = dY;
					if (dX < dTcMercBounds[0])
						dTcMercBounds[0] = dX;
					if (dY < dTcMercBounds[1])
						dTcMercBounds[1] = dY;
					if (dX > dTcMercBounds[2])
						dTcMercBounds[2] = dX;
					if (dY > dTcMercBounds[3])
						dTcMercBounds[3] = dY;
				}
				Geo.untwist(dCorners);
				Mercator oM = Mercator.getInstance();
				int[] nTiles = new int[2];
				int[] nTileIndices = new int[4];
				oM.metersToTile(dTcMercBounds[0], dTcMercBounds[3], CtrlTiles.g_nZoom, nTiles); // determine the tiles the bounds intersect
				nTileIndices[0] = nTiles[0];
				nTileIndices[1] = nTiles[1];
				oM.metersToTile(dTcMercBounds[2], dTcMercBounds[1], CtrlTiles.g_nZoom, nTiles);
				nTileIndices[2] = nTiles[0];
				nTileIndices[3] = nTiles[1];
				for (int nTileX = nTileIndices[0]; nTileX <= nTileIndices[2]; nTileX++) // for each tile
				{
					for (int nTileY = nTileIndices[1]; nTileY <= nTileIndices[3]; nTileY++)
					{
						Path oIndexFile = Paths.get(String.format(CtrlTiles.g_sTdFileFormat, nTileX, CtrlTiles.g_nZoom, nTileX, nTileY) + ".ndx");
						if (!Files.exists(oIndexFile)) // check if the index file exists
							continue;

						oTileIdsSearch.setIndices(nTileX, nTileY);
						int nIndex = Collections.binarySearch(oTiles, oTileIdsSearch);
						if (nIndex < 0) // if the tile index file has not been loaded
						{
							TileIds oTile = new TileIds(nTileX, nTileY, 1000);
							nIndex = ~nIndex;
							oTiles.add(nIndex, oTile);
							try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile)))) // read the index file to find which controls are in the tile
							{
								while (oIn.available() > 0)
								{
									CtrlIndex oIndex = new CtrlIndex(oIn);
									if (oBounds.m_lOldest <= oIndex.m_lStart && oIndex.m_lEnd > lNow && java.util.Arrays.binarySearch(IGNORE_CTRLS, oIndex.m_nType) < 0 && Geo.boundingBoxesIntersect(dTcMercBounds, oIndex.m_dBB)) // skip out controls and control types to ignore
										oTile.add(oIndex.m_yId);
								}
							}
						}

						TileIds oTile = oTiles.get(nIndex);
						oResCtrls.ensureCapacity(oResCtrls.size() + oTile.size());
						for (byte[] yId : oTile)
						{
							int nProcessIndex = Collections.binarySearch(oProcessed, yId, TrafCtrl.ID_COMP);
							if (nProcessIndex < 0)
								oProcessed.add(~nProcessIndex, yId);
							else
								continue;
							
							String sFile = CtrlTiles.g_sCtrlDir + TrafCtrl.getId(yId) + ".bin";
							Path oCtrlFile = Paths.get(sFile);
	
							if (!Files.exists(oCtrlFile))
								continue;
							TrafCtrl oCtrl;
							try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oCtrlFile))))
							{
								oCtrl = new TrafCtrl(oIn, true, false);
							}
							
							if (Geo.ctrlIntersectBounds(dCorners, oCtrl, m_nPolyStep))
							{
								oResCtrls.add(oCtrl);
								oCtrl.preparePoints(m_nExplodeDistForXml);
								nMsgTot += (oCtrl.size() / 256 + 1);
							}
						}
					}
				}
			}			

			int nMsgCount = 1;
			if (oReqParser.m_bList)
				nMsgTot = oResCtrls.size();
			
			StringBuilder sBuf = new StringBuilder(nMsgTot * 2048);

			for (int nCtrlIndex = 0; nCtrlIndex < oResCtrls.size(); nCtrlIndex++)
			{
				TrafCtrl oCtrl = oResCtrls.get(nCtrlIndex);
				int nParts = oCtrl.size() / 256 + 1;
				if (oReqParser.m_bList)
					nParts = 1;
				for (int nIndex = 0; nIndex < nParts; nIndex++)
				{
					if (nIndex == 0)
						oCtrl.getXml(sBuf, oTcmReq.m_sReqId, oTcmReq.m_nReqSeq, nMsgCount, nMsgTot, oTcmReq.m_sVersion, true, 0, m_bRemoveWidth, oReqParser.m_bList);
					else
						oCtrl.getXml(sBuf, oTcmReq.m_sReqId, oTcmReq.m_nReqSeq, nMsgCount, nMsgTot, oTcmReq.m_sVersion, false, nIndex * 256 - 1, m_bRemoveWidth, oReqParser.m_bList);
					++nMsgCount;

					if (!oReqParser.m_bList)
					{
						HttpURLConnection oHttpClient = (HttpURLConnection)new URL(String.format("http://tcmreplyhost:%d/tcmreply", oReqParser.m_nPort)).openConnection();
						oHttpClient.setFixedLengthStreamingMode(sBuf.length());
						oHttpClient.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
						oHttpClient.setDoOutput(true);
						oHttpClient.setRequestMethod("POST");
						oHttpClient.setConnectTimeout(1000);

						try
						{
							oHttpClient.connect(); // send post request

							try (BufferedWriter oOut = new BufferedWriter(new OutputStreamWriter(oHttpClient.getOutputStream())))
							{
								oOut.append(sBuf);
							}

							oHttpClient.disconnect();
						}
						catch (Exception oEx)
						{
							oEx.printStackTrace();
						}
						LOGGER.debug(sBuf);
					}
				}
			}
			
			if (oReqParser.m_bList)
			{
				HttpURLConnection oHttpClient = (HttpURLConnection)new URL(String.format("http://tcmreplyhost:%d/tcmreply", oReqParser.m_nPort)).openConnection();
				oHttpClient.setRequestProperty("Content-Encoding", "gzip");
				oHttpClient.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				oHttpClient.setDoOutput(true);
				oHttpClient.setRequestMethod("POST");
				oHttpClient.setConnectTimeout(1000);
				try (ByteArrayOutputStream oBaos = new ByteArrayOutputStream(sBuf.length()))
				{
					try (BufferedWriter oOut = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(oBaos))))
					{
						oOut.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><TrafficControlMessageList>").append(sBuf).append("</TrafficControlMessageList>");
					}
					oHttpClient.setFixedLengthStreamingMode(oBaos.size());
					try
					{
						oHttpClient.connect(); // send post request
						try (BufferedOutputStream oOut = new BufferedOutputStream(oHttpClient.getOutputStream()))
						{
							oOut.write(oBaos.toByteArray());
						}
						
						oHttpClient.disconnect();
					}
					catch (Exception oEx)
					{
						oEx.printStackTrace();
					}
				}
				sBuf.insert(0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><TrafficControlMessageList>");
				sBuf.append("</TrafficControlMessageList>");
				LOGGER.debug(sBuf);
			}
		}
		catch (Exception oEx)
		{
			LOGGER.error(oEx, oEx);
		}
	}


	private class TileIds extends ArrayList<byte[]> implements Comparable<TileIds>
	{
		int m_nX;
		int m_nY;


		TileIds()
		{
		}


		TileIds(int nX, int nY, int nInitSize)
		{
			super(nInitSize);
			setIndices(nX, nY);
		}


		void setIndices(int nX, int nY)
		{
			m_nX = nX;
			m_nY = nY;
		}


		@Override
		public int compareTo(TileIds o)
		{
			int nReturn = Integer.compare(m_nX, o.m_nX);
			if (nReturn == 0)
				nReturn = Integer.compare(m_nY, o.m_nY);
			
			return nReturn;
		}
	}
}
