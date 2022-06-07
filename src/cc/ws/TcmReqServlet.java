/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.ctrl.TrafCtrl;
import cc.ctrl.CtrlGeo;
import cc.ctrl.TcBounds;
import cc.ctrl.TcmReqParser2;
import cc.ctrl.TcmReq;
import cc.ctrl.TcmReqParser;
import cc.ctrl.TrafCtrlEnums;
import cc.ctrl.proc.ProcCtrl;
import cc.geosrv.Mercator;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.Text;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
	protected static final Logger LOGGER = LogManager.getRootLogger();

	private int m_nExplodeDistForXml = 0;
	private final ArrayDeque<StringBuilder> REPLYBUFS = new ArrayDeque();
	private final ExecutorService THREADPOOL = Executors.newFixedThreadPool(53);
	private boolean m_bRemoveWidth = false;
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
	}
	
	@Override
	public void destroy()
	{
		THREADPOOL.shutdown();
	}
	
	
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws ServletException, IOException
	{
		try
		{
			long lNow = System.currentTimeMillis();
			
			StringBuilder sReq = new StringBuilder();
			try (BufferedInputStream oIn = new BufferedInputStream(oReq.getInputStream()))
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					sReq.append((char)nByte);
			}

			TcmReqParser oReqParser;
			if (sReq.indexOf("<tcrV01>") >= 0)
				oReqParser = new TcmReqParser();
			else
				oReqParser = new TcmReqParser2();

			TcmReq oTcmReq = oReqParser.parseRequest(new ByteArrayInputStream(sReq.toString().getBytes(StandardCharsets.UTF_8)));
			Text.removeCtrlChars(sReq); // pack chars into one line
			sReq.insert(0, "TCR ");
			LOGGER.debug(sReq);

			ArrayList<TrafCtrl> oResCtrls = new ArrayList();
			ArrayList<TrafCtrl> oCtrls = new ArrayList();
			ArrayList<TileIds> oTiles = new ArrayList();
			TrafCtrl oTrafCtrlSearch = new TrafCtrl();
			oTrafCtrlSearch.m_yId = new byte[16];
			TileIds oTileIdsSearch = new TileIds();
			byte[] yIdBuf = new byte[16];
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
				for (int n = 0; n < dCorners.length; n += 2) 
					System.out.print(String.format("[%2.7f,%2.7f],", Mercator.xToLon(dCorners[n]), Mercator.yToLat(dCorners[n + 1])));
				System.out.println();
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
							TileIds oTile = new TileIds(nTileX, nTileY);
							nIndex = ~nIndex;
							oTiles.add(nIndex, oTile);
							try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(FileUtil.newInputStream(oIndexFile)))) // read the index file to find which controls are in the tile
							{
								while (oIn.available() > 0)
								{
									int nType = oIn.readInt();
									oIn.read(yIdBuf);
									long lStart = oIn.readLong();
									long lEnd = oIn.readLong();
									if (oBounds.m_lOldest <= lStart && lEnd > lNow && java.util.Arrays.binarySearch(IGNORE_CTRLS, nType) < 0) // skip out controls and control types to ignore
									{
										byte[] yId = new byte[16];
										System.arraycopy(yIdBuf, 0, yId, 0, 16);
										int nSearchIndex = Collections.binarySearch(oTile, yId, TrafCtrl.ID_COMP);
										if (nSearchIndex < 0) // only add ids once
											oTile.add(~nSearchIndex, yId);
									}
								}
							}
						}
						
						TileIds oTile = oTiles.get(nIndex);
						for (byte[] yId : oTile)
						{
							System.arraycopy(yId, 0, oTrafCtrlSearch.m_yId, 0, yId.length);
							int nSearchIndex = Collections.binarySearch(oCtrls, oTrafCtrlSearch);
							if (nSearchIndex < 0) // only load controls once
							{
								String sFile = CtrlTiles.g_sCtrlDir + TrafCtrl.getId(yId) + ".bin";
								TrafCtrl oCtrl;
								try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
								{
									oCtrl = new TrafCtrl(oIn, false);
								}
								try (DataInputStream oIn = new DataInputStream(FileUtil.newInputStream(Paths.get(sFile))))
								{
									oCtrl.m_oFullGeo = new CtrlGeo(oIn, CtrlTiles.g_nZoom);
								}
								nSearchIndex = ~nSearchIndex;
								oCtrls.add(nSearchIndex, oCtrl);
							}
							
							TrafCtrl oCtrl = oCtrls.get(nSearchIndex);
							nSearchIndex = Collections.binarySearch(oResCtrls, oTrafCtrlSearch);
							if (nSearchIndex < 0)
							{
								if (!Geo.boundingBoxesIntersect(dTcMercBounds, oCtrl.m_oFullGeo.m_dBB))
									continue;
								
								if (!Geo.polylineInside(dCorners, oCtrl.m_oFullGeo.m_dC) && !Geo.polylineInside(dCorners, oCtrl.m_oFullGeo.m_dNT) && !Geo.polylineInside(dCorners, oCtrl.m_oFullGeo.m_dPT))
									continue;
								
								System.out.println(TrafCtrl.getId(oCtrl.m_yId));
								oResCtrls.add(~nSearchIndex, oCtrl);
								oCtrl.preparePoints(m_nExplodeDistForXml);
								nMsgTot += (oCtrl.size() / 256 + 1);
							}
						}
					}
				}
			}
			
			
			int nMsgCount = 1;
			for (TrafCtrl oCtrl : oResCtrls)
			{
				int nParts = oCtrl.size() / 256 + 1;
				StringBuilder sBuf = new StringBuilder();
				for (int nIndex = 0; nIndex < nParts; nIndex++)
				{
					if (nIndex == 0)
						oCtrl.getXml(sBuf, oTcmReq.m_sReqId, oTcmReq.m_nReqSeq, nMsgCount, nMsgTot, oTcmReq.m_sVersion, true, 0);
					else
						oCtrl.getXml(sBuf, oTcmReq.m_sReqId, oTcmReq.m_nReqSeq, nMsgCount, nMsgTot, oTcmReq.m_sVersion, false, nIndex * 256 - 1);
					++nMsgCount;
					if (m_bRemoveWidth)
					{
						int nStart = sBuf.indexOf("<refwidth>");
						int nEnd = sBuf.indexOf("</refwidth>", nStart) + "</refwidth>".length();
						sBuf.delete(nStart, nEnd);
					}
					synchronized (REPLYBUFS)
					{
						REPLYBUFS.push(sBuf);
						THREADPOOL.execute(this);
					}
					try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(String.format("/dev/shm/testreqreply%d.xml", nMsgCount - 1)), FileUtil.WRITE, FileUtil.FILEPERS), "UTF-8")))
					{
						oOut.append(sBuf);
					}
				}
			}
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}
	
	@Override
	public void run()
	{
		StringBuilder sBuf = null;
		synchronized (REPLYBUFS)
		{
			if (!REPLYBUFS.isEmpty())
				sBuf = REPLYBUFS.removeFirst();
		}
		if (sBuf == null)
			return;
		
		try
		{
//			Thread.sleep(10000);
			HttpURLConnection oHttpClient = (HttpURLConnection)new URL("http://tcmreplyhost:10001/tcmreply").openConnection();
			oHttpClient.setDoOutput(true);
			oHttpClient.setRequestMethod("POST");
			oHttpClient.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			oHttpClient.setFixedLengthStreamingMode(sBuf.length());
			
			oHttpClient.connect(); // send post request
			
			try (OutputStreamWriter oOut = new OutputStreamWriter(oHttpClient.getOutputStream()))
			{
				oOut.append(sBuf);
			}
			Text.removeCtrlChars(sBuf); // pack chars into one line
			sBuf.insert(0, ' '); // build log msg in reverse order
			sBuf.insert(0, oHttpClient.getResponseCode());
			sBuf.insert(0, "TCM ");
			LOGGER.debug(sBuf);
			oHttpClient.disconnect();
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}


	private class TileIds extends ArrayList<byte[]> implements Comparable<TileIds>
	{
		int m_nX;
		int m_nY;


		TileIds()
		{
		}


		TileIds(int nX, int nY)
		{
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
