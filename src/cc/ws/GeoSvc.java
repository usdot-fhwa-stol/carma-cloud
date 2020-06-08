package cc.ws;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import cc.util.BufferedInStream;
import cc.util.Geo;
import cc.util.StringPool;
import cc.util.Text;
import javax.servlet.ServletConfig;


public class GeoSvc extends HttpServlet implements Runnable
{
	private static final ByteBuffer ID_BUFFER = ByteBuffer.allocate(8);
	private static final StringPool STRING_POOL = new StringPool();
	private static SecureRandom RNG;

	private final ArrayList<SimWay> m_oSimWays = new ArrayList();
	private final Timer m_oTimer = new Timer();
	protected String m_sOsmFile; // directory of pre-processed way files
	ArrayList<HashWays> m_oHashWays;
	ArrayList<Way> m_oInitWays;
	double[] m_dInitBounds = new double[]{-77.209426, 38.931905, -77.127627, 38.967285};
	int m_nInitSims = 100;


	static
	{
		try
		{
			RNG = SecureRandom.getInstance("SHA1PRNG");
		}
		catch (Exception oEx)
		{
		}
	}


	public GeoSvc()
	{
	}


	@Override
	public void init()
	{
		ServletConfig oConfig = getServletConfig();
		String sInitBounds = oConfig.getInitParameter("initbounds");
		if (sInitBounds != null && sInitBounds.length() > 0)
		{
			String[] sBounds = sInitBounds.split(",");
			for (int i = 0; i < m_dInitBounds.length; i++)
				m_dInitBounds[i] = Double.parseDouble(sBounds[i]);
		}
		String sInitSims = oConfig.getInitParameter("initsims");
		if (sInitSims != null && sInitSims.length() > 0)
			m_nInitSims = Integer.parseInt(sInitSims);
		String sOsmFile = oConfig.getInitParameter("osmfile");
		if (sOsmFile != null && sOsmFile.length() > 0)
		{
			m_sOsmFile = sOsmFile;
			new Thread(this).start();
		}
	}


	@Override
	public void run()
	{
		try
		{
			File oDir = new File(m_sOsmFile);
			if (oDir.isFile())
				return;

			int[] nHash = new int[1]; // hash mutable integer wrapper
			ArrayList<HashWays> oHashWays = new ArrayList();
			ArrayList<Way> oInitWays = new ArrayList();
			ArrayList<String> oPool = new ArrayList(); // reused local string pool

			File[] oFiles = oDir.listFiles();
			for (File oFile : oFiles)
			{
				if (!oFile.getName().endsWith(".bin"))
					continue; // ignore other files

				System.out.print(oFile.getName());
				long lNow = System.currentTimeMillis();
				DataInputStream oIn = new DataInputStream(new BufferedInStream(new FileInputStream(oFile)));

				int nCount = oIn.readInt(); // local pool string count
				oPool.clear(); // reuse local string pool
				oPool.ensureCapacity(nCount);
				while (nCount-- > 0) // merge local strings into main pool
					oPool.add(STRING_POOL.intern(oIn.readUTF()));

				nCount = oIn.readInt(); // number of ways to read
				while (nCount-- > 0)
				{
					Way oWay = new Way(oPool, oIn);
//					String sType = oWay.m_sType; // filter non-highways
//					if (sType == null || (sType.compareTo("motorway") != 0 && sType.compareTo("trunk") != 0))
//						continue;

					int nXmin = Geo.scale(oWay.m_nLonMin);
					int nXmax = Geo.scale(oWay.m_nLonMax);
					int nYmin = Geo.scale(oWay.m_nLatMin);
					int nYmax = Geo.scale(oWay.m_nLatMax);
					
					if (Geo.boundingBoxesIntersect(m_dInitBounds[0], m_dInitBounds[1], m_dInitBounds[2], m_dInitBounds[3], 
												Geo.fromIntDeg(oWay.m_nLonMin), Geo.fromIntDeg(oWay.m_nLatMin), Geo.fromIntDeg(oWay.m_nLonMax), Geo.fromIntDeg(oWay.m_nLatMax)))
						oInitWays.add(oWay);
					for (int nX = nXmin; nX <= nXmax; nX++) // include max endpoint
					{
						for (int nY = nYmin; nY <= nYmax; nY++) // include max endpoint
						{
							nHash[0] = (nX << 16) + nY; // lon shift left 16 bits and add lat
							int nIndex = Collections.binarySearch(oHashWays, nHash);
							if (nIndex < 0)
							{
								nIndex = ~nIndex;
								oHashWays.add(nIndex, new HashWays(nHash[0]));
							}
							oHashWays.get(nIndex).add(oWay); // child class maintains set
						}
					}
				}
				System.out.print(" ");
				System.out.println(System.currentTimeMillis() - lNow);
			}
			m_oHashWays = oHashWays; // set servlet reference
			m_oInitWays = oInitWays;
			int nSims = 1000;
			while (nSims-- > 0) // create simulated vehicles
				m_oSimWays.add(new SimWay());

			m_oTimer.scheduleAtFixedRate(new Sim(), 0L, 1000L); // update positions
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
	}


	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRep)
		throws IOException
	{
		Session oSess = SessMgr.getSession(oReq);
		if (oSess == null)
		{
			oRep.sendError(401);
			return;
		}

		String sPath = oReq.getPathInfo();
		if (sPath.contains("bounds") || sPath.contains("segpts"))
		{
			try (JsonGenerator oJson = Json.createGenerator(oRep.getOutputStream()))
			{
				oJson.writeStartObject(); // start JSON object
				int nZoom = Integer.parseInt(oReq.getParameter("zoom"));
				int[] nHash = new int[]{Integer.parseInt(oReq.getParameter("hash"))};
				int nIndex = Collections.binarySearch(m_oHashWays, nHash);
				if (nIndex >= 0)
				{
					HashWays oWays = m_oHashWays.get(nIndex);
					if (sPath.contains("bounds"))
					{
						if (nZoom >= 13) // should be highways and highway links
						{
							oJson.writeStartObject("13"); // min acceptable zoom level
							for (Way oWay : oWays)
							{
								if (oWay.m_sType.contains("motorway") || oWay.m_sType.contains("trunk"))
									oWay.printBounds(oJson);
							}
							oJson.writeEnd();
						}

						if (nZoom >= 16) // should be surface streets
						{
							oJson.writeStartObject("16"); // min acceptable zoom level
							for (Way oWay : oWays)
							{
								if (!oWay.m_sType.contains("motorway") && !oWay.m_sType.contains("trunk"))
									oWay.printBounds(oJson);
							}
							oJson.writeEnd();
						}
					}
					else if (sPath.contains("segpts"))
					{
						oWays.forEach(oWay -> {oWay.printDetail(oJson);});
					}
				}
				oJson.writeEnd(); // end JSON object
			}
		}
		else if (sPath.contains("sim"))
		{
			try (JsonGenerator oJson = Json.createGenerator(oRep.getOutputStream()))
			{
				oJson.writeStartObject();
				synchronized(m_oSimWays) // ensure updates are complete
				{
					for (SimWay oSimWay : m_oSimWays)
					{
						oJson.writeStartArray(oSimWay.m_sId);
						oJson.write(oSimWay.m_dLat);
						oJson.write(oSimWay.m_dLon);
						oJson.writeEnd();
					}
				}
				oJson.writeEnd();
			}
		}
	}


	private class Way implements Comparable<Way>
	{
		String m_sId;
		String m_sName;
		String m_sType;
		boolean m_bOneWay;
		boolean m_bBridge;
		int m_nLanes;
		int m_nLatMin;
		int m_nLonMin;
		int m_nLatMax;
		int m_nLonMax;
		int[] m_nPoints;
		double[] m_dLens; // length of each line in meters


		private Way()
		{
		}


		Way(ArrayList<String> oPool, DataInputStream oIn)
		{
			try
			{
				ID_BUFFER.clear();
				ID_BUFFER.putLong(oIn.readLong()); // save OSM way id as base64 string
				m_sId = Text.B64ENC.encodeToString(ID_BUFFER.array()); // URL encode no terminators
				StringBuilder sName = new StringBuilder(oPool.get(oIn.readInt()));
				m_sType = oPool.get(oIn.readInt());
				int nFlags = oIn.readByte(); // bit flags 16 = oneway, 32 = bridge
				m_bOneWay = (nFlags & 16) > 0;
				m_bBridge = (nFlags & 32) > 0;
				m_nLanes = nFlags & 15;

				sName.append("</br>").append(m_sId).append(' ').append(m_sType.charAt(0)).
					append(m_bOneWay ? 'o' : ' ').append(m_bBridge ? 'b' : ' ');
				m_sName = sName.toString(); // append debug info to way name
				
				int nLatMin = Integer.MAX_VALUE;
				int nLonMin = Integer.MAX_VALUE;
				int nLatMax = Integer.MIN_VALUE;
				int nLonMax = Integer.MIN_VALUE;
				int[] nPts = new int[oIn.readInt()]; // read ordinate count
				int nIndex = 0;
				while (nIndex < nPts.length)
				{
					int nVal = oIn.readInt(); // set lat and lat bounds
					if (nVal < nLatMin)
						nLatMin = nVal;

					if (nVal > nLatMax)
						nLatMax = nVal;

					nPts[nIndex++] = nVal;

					nVal = oIn.readInt(); // set lon and lon bounds
					if (nVal < nLonMin)
						nLonMin = nVal;

					if (nVal > nLonMax)
						nLonMax = nVal;				

					nPts[nIndex++] = nVal;
				}

				m_nPoints = nPts; // set member reference
				m_nLatMin = nLatMin;
				m_nLonMin = nLonMin;
				m_nLatMax = nLatMax;
				m_nLonMax = nLonMax;

				double dTotalLen = 0.0; // create length array
				double[] dLens = new double[nPts.length / 2]; // first position will hold total
				for (int nPos = 1; nPos < dLens.length; nPos++)
				{
					int nPt = nPos * 2; // points are in lat lon order
					double dLen = Geo.distance(nPts[nPt - 1], nPts[nPt - 2], nPts[nPt + 1], nPts[nPt]);
					dTotalLen += dLen; // accumulate total length
					dLens[nPos] = dLen; // one length for each line (2 points)
				}
				dLens[0] = dTotalLen; // save total length
				m_dLens = dLens; // set member reference
			}
			catch (Exception oEx)
			{
			}
		}


		public void printBounds(JsonGenerator oJson)
		{
			oJson.writeStartArray(m_sId);
			oJson.write(Geo.fromIntDeg(m_nLonMin)).write(Geo.fromIntDeg(m_nLatMin)).
				write(Geo.fromIntDeg(m_nLonMax)).write(Geo.fromIntDeg(m_nLatMax));
			oJson.writeEnd();
		}


		public void printDetail(JsonGenerator oJson)
		{
			oJson.writeStartObject(m_sId);
			oJson.write("name", m_sName);
			oJson.write("oneway", m_bOneWay);
			oJson.writeStartArray("points"); // output lon lat order
			for (int nIndex = 0; nIndex < m_nPoints.length; nIndex++)
				oJson.write(Geo.fromIntDeg(m_nPoints[nIndex]));

			oJson.writeEnd().writeEnd();
		}


		@Override
		public int compareTo(Way oWay)
		{
			return m_sId.compareTo(oWay.m_sId);
		}
	}


	private class HashWays extends ArrayList<Way> implements Comparable<int[]>
	{
		int m_nHash;


		private HashWays()
		{
		}


		HashWays(int nHash)
		{
			m_nHash = nHash;
		}


		@Override
		public boolean add(Way oWay)
		{
			int nIndex = Collections.binarySearch(this, oWay);
			if (nIndex < 0)
				add(~nIndex, oWay);

			return true;
		}


		@Override
		public int compareTo(int[] nHash)
		{
			return m_nHash - nHash[0];
		}
	}


	private class SimWay
	{
		String m_sId;
		Way m_oWay;
		double m_dSpeed;
		double m_dDist;
		double m_dLat;
		double m_dLon;


		private SimWay()
		{
			byte[] yId = new byte[3];
			RNG.nextBytes(yId); // create 4-char base64 random id
			m_sId = Text.B64ENC.encodeToString(yId);
			initWay();
		}


		private void initWay()
		{
			do // may take a while to start
			{
				m_oWay = m_oInitWays.get(RNG.nextInt(m_oInitWays.size()));
			}
			while (m_oWay.m_sType.compareTo("motorway") != 0 && 
				m_oWay.m_sType.compareTo("primary") != 0);

//			Way oNewWay = null;
//			while (oNewWay == null)
//			{
//				for (HashWays oHashWays : m_oHashWays)
//				{
//					for (Way oWay : oHashWays)
//					{
//						if (oWay.m_sId.compareTo("AAAAAALMGiE") == 0)
//							oNewWay = oWay;
//					}
//				}
//			}
//			m_oWay = oNewWay;

			setLoc(RNG.nextInt(100) * m_oWay.m_dLens[0] / 100.0); // random location
			double dSpeed = getSpeed();
			if (!m_oWay.m_bOneWay && RNG.nextBoolean())
					dSpeed = -dSpeed; // random direction of travel

			m_dSpeed = -dSpeed; // negative for testing only
		}

	
		private double getSpeed()
		{
			String sType = m_oWay.m_sType;
			if (sType.compareTo("motorway") == 0)
				return 30.0; // ~67 mph
			else if (sType.compareTo("primary") == 0)
				return 25.0; // ~56 mph
			else if (sType.compareTo("secondary") == 0)
				return 21.0; // ~47 mph
			else
				return 13.0; // ~30 mph
		}


		private void setLoc(double dTrgt)
		{
			int[] nPts = m_oWay.m_nPoints; // set local references
			double[] dLens = m_oWay.m_dLens;

			int nLen = 1; // index 0 contains total length
			double dDist = 0.0;
			while (dDist <= dTrgt && nLen < dLens.length)
				dDist += dLens[nLen++];

			double dCurrLen = dLens[--nLen]; // reverse extra loop increment
			dDist -= dCurrLen;
			int nPos = nLen * 2;

			double dOff = (dTrgt - dDist) / dCurrLen; // percent along current length
			int nX = nPts[nPos - 1]; // lat lon ordering
			int nY = nPts[nPos - 2];
			m_dLat = Geo.fromIntDeg(nY + (int)Math.round(dOff * (nPts[nPos] - nY)));
			m_dLon = Geo.fromIntDeg(nX + (int)Math.round(dOff * (nPts[nPos + 1] - nX)));

			m_dDist = dTrgt; // current distance matches target
		}

	
		private void nextWay(double dTrgt)
		{
			int nLat;
			int nLon;
			if (dTrgt < 0.0) // traveling opposite of node order
			{
				nLat = m_oWay.m_nPoints[0]; // use first point
				nLon = m_oWay.m_nPoints[1]; // for comparison
			}
			else // traveling in node order
			{
				dTrgt -= m_oWay.m_dLens[0]; // leftover distance for next way
				nLat = m_oWay.m_nPoints[m_oWay.m_nPoints.length - 2]; // use last point
				nLon = m_oWay.m_nPoints[m_oWay.m_nPoints.length - 1]; // for comparison
			}

			int[] nHash = new int[]{Geo.getHash(nLat, nLon)}; // find way search set
			int nIndex = Collections.binarySearch(m_oHashWays, nHash);
			if (nIndex >= 0)
			{
				int nInsert = 0;
				ArrayList<Way> oWays = new ArrayList(); // candidate ways
				for (Way oWay : m_oHashWays.get(nIndex))
				{
					int[] nPts = oWay.m_nPoints;
					if (nPts[0] == nLat && nPts[1] == nLon)
						oWays.add(nInsert++, oWay); // insert first point match at top
					else if (nPts[nPts.length - 2] == nLat && 
						nPts[nPts.length - 1] == nLon && !oWay.m_bOneWay)
						oWays.add(oWay); // add last point match to bottom excluding do-not-enter
				}

				nIndex = oWays.size();
				if (nIndex > 1) // remove current way when possible
				{
					while (nIndex-- > 0)
					{
						if (oWays.get(nIndex) == m_oWay)
						{
							oWays.remove(nIndex);
							if (nIndex < nInsert) // adjust insert point
								--nInsert;
						}
					}
				}

				if (!oWays.isEmpty()) // at least the current way should exist
				{
					if (oWays.size() == 1)
						nIndex = 0; // no random choice possible
					else
						nIndex = RNG.nextInt(oWays.size());

					m_oWay = oWays.get(nIndex);
					double dSpeed = getSpeed(); // set distance on new way
					double dLen = m_oWay.m_dLens[0];
					if (dTrgt < 0.0)
					{
						if (nIndex < nInsert) // starting at first point
							dTrgt = -dTrgt; // need positive distance
						else // starting at last point subtract distance
						{
							dTrgt = dLen + dTrgt;
							dSpeed = -dSpeed; // reverse direction
						}
					}
					else
					{
						if (nIndex >= nInsert) // starting at last point
						{
							dTrgt = dLen - dTrgt;
							dSpeed = -dSpeed; // reverse direction
						}
					}
					m_dSpeed = dSpeed; // save way speed
					if (dTrgt < 0.0 || dTrgt > dLen) // chosen way is too short
						dTrgt = dLen / 2.0; // just pick the mid-point

					setLoc(dTrgt); // set position on new way
					return;
				}
			}
			initWay(); // restart somewhere else
		}


		void update()
		{
			double dTrgt = m_dDist + m_dSpeed;
			if (dTrgt < 0.0 || dTrgt > m_oWay.m_dLens[0])
				nextWay(dTrgt);
			else
				setLoc(dTrgt);

//			if (m_oWay.m_bOneWay && m_dSpeed < 0.0) // last resort error state check
//			{ // this will murder the log file
//				System.out.print(m_sId);
//				System.out.print(' ');
//				System.out.print(m_dLat);
//				System.out.print(' ');
//				System.out.print(m_dLon);
//				System.out.print(' ');
//				System.out.println(m_oWay.m_sName);
//			}
		}
	}


	private class Sim extends TimerTask
	{
		Sim()
		{
		}


		@Override
		public void run()
		{
			synchronized(m_oSimWays)
			{
				m_oSimWays.forEach((oSimWay) -> {oSimWay.update();});
			}
		}
	}
}
