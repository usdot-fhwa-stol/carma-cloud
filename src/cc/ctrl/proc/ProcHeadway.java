/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlGeo;
import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.geosrv.Mercator;
import cc.util.Arrays;
import cc.util.FileUtil;
import cc.util.Geo;
import cc.util.MathUtil;
import cc.util.TileUtil;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author aaron.cherney
 */
public class ProcHeadway extends ProcCtrl
{
	
//	private static double[] SYMBOL = new double[]{-0.1, 0.3, 0.1, 0.3, 0.2, 0.2, 0.2, -0.2, 0.1, -0.3, -0.1, -0.3, -0.2, -0.2, -0.2, 0.2, -0.1, 0.3};
	private final static double[] SYMBOL = new double[]{0.6, 0.4, -0.6, 0.4, 0.0, 0.4, -0.2, 0.2, 0.0, 0.4, 0.0, -0.4};
	public ProcHeadway(String sLineArcDir)
	{
		super(sLineArcDir);
	}
	
	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		String[] sEmpty = new String[0];
		TdLayer oLayer = new TdLayer("minhdwy", sEmpty, sEmpty, TdLayer.LINESTRING);
		int[] nTags = new int[0];
		double[] dPt = new double[2];
		double[] dPoint = new double[2];
		double[][] dNumbers = new double[NUMBERS.length][];
		int[] nNumbersPts = new int[NUMBERS.length];
		for (int nIndex = 0; nIndex < NUMBERS.length; nIndex++)
		{
			double[] dTemp = new double[NUMBERS[nIndex].length + 1];
			dTemp[0] = dTemp.length;
			dNumbers[nIndex] = dTemp;
			nNumbersPts[nIndex] = NUMBERS[nIndex].length / 2;
		}
		HashMap<String, double[]> oChars = new HashMap();
		HashMap<String, Integer> oCharPts = new HashMap();
		for (Map.Entry<String, double[]> oEntry : CHARS.entrySet())
		{
			double[] dTemp = new double[oEntry.getValue().length + 1];
			dTemp[0] = dTemp.length;
			oChars.put(oEntry.getKey(), dTemp);
			oCharPts.put(oEntry.getKey(), oEntry.getValue().length / 2);
		}
		
		double[] dSymbol1 = new double[SYMBOL.length + 1];
		double[] dSymbol2 = new double[SYMBOL.length + 1];
		dSymbol1[0] = dSymbol1.length;
		dSymbol2[0] = dSymbol2.length;
		int nSymbolPts = SYMBOL.length / 2;
		
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
			oLayer.clear();
			double[] dClipBounds = TileUtil.getClippingBounds(g_nDefaultZoom, nX, nY);
			DecimalFormat oDf = new DecimalFormat("#.#");
			for (TrafCtrl oCtrl : oCtrls)
			{
				CtrlGeo oFullGeo = oCtrl.m_oFullGeo;
				if (Collections.binarySearch(oFullGeo.m_oTiles, nTile, Mercator.TILECOMP) < 0 || oFullGeo.m_dLength < LENLOWTH || oFullGeo.m_dAverageWidth < WIDTHTH)
					continue;

				double dHdg = getCoordAndHeadingAtLength(oFullGeo.m_dC, oFullGeo.m_dLength / 2.0, false, dPt);
				if (Double.isNaN(dHdg))
					continue;
				
				
				double dPTAngle = dHdg + Mercator.PI_OVER_TWO;
				double dXc = dPt[0];
				double dYc = dPt[1];
				
				dPt[0] += Math.cos(dHdg) * 0.8;
				dPt[1] += Math.sin(dHdg) * 0.8;
				
				AffineTransform oAt = new AffineTransform();
				oAt.translate(dPt[0], dPt[1]);
				oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
				oAt.transform(SYMBOL, 0, dSymbol1, 1, nSymbolPts);
				
				dPt[0] = dXc;
				dPt[1] = dYc;
				dPt[0] -= Math.cos(dHdg) * 0.8;
				dPt[1] -= Math.sin(dHdg) * 0.8;
				oAt = new AffineTransform();
				oAt.translate(dPt[0], dPt[1]);
				oAt.rotate(dHdg + Mercator.PI_OVER_TWO, 0, 0);
				oAt.transform(SYMBOL, 0, dSymbol2, 1, nSymbolPts);
				

				double[] dBounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};		
				Iterator<double[]> oIt = Arrays.iterator(dSymbol1, dPoint, 1, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					Geo.updateBounds(dPoint[0], dPoint[1], dBounds);
				}
				oIt = Arrays.iterator(dSymbol2, dPoint, 1, 2);
				while (oIt.hasNext())
				{
					oIt.next();
					Geo.updateBounds(dPoint[0], dPoint[1], dBounds);
				}

				if (Geo.boundingBoxesIntersect(dClipBounds[0], dClipBounds[1], dClipBounds[2], dClipBounds[3], dBounds[0], dBounds[1], dBounds[2], dBounds[3]))
				{
					oLayer.add(new TdFeature(dSymbol1, nTags, oCtrl));
					oLayer.add(new TdFeature(dSymbol2, nTags, oCtrl));
					
					String sVal = oDf.format(MathUtil.bytesToInt(oCtrl.m_yControlValue)) + "s";
					int nLimit = sVal.length();
					double dStep = -0.3;
					double dOffset = -0.45 + (nLimit * 0.3);
					for (int nIndex = 0; nIndex < sVal.length(); nIndex++)
					{
						char cChar = sVal.charAt(nIndex);
						double dMultiplier = dOffset + (dStep * nIndex);
						dPt[0] = dXc;
						dPt[1] = dYc;
						dPt[0] += Math.cos(dPTAngle) * dMultiplier;
						dPt[1] += Math.sin(dPTAngle) * dMultiplier;
						
						oAt = new AffineTransform();
						oAt.translate(dPt[0], dPt[1]);
						oAt.rotate(dHdg - Mercator.PI_OVER_TWO, 0, 0);
						if (Character.isDigit(cChar))
						{
							oAt.scale(0.45, 0.45);
							int nNumber = Character.getNumericValue(cChar);
							oAt.transform(NUMBERS[nNumber], 0, dNumbers[nNumber], 1, nNumbersPts[nNumber]);
							oLayer.add(new TdFeature(dNumbers[nNumber], nTags, oCtrl));
						}
						else
						{
							oAt.scale(0.65, 0.65);
							String sChar = Character.toString(cChar);
							oAt.transform(CHARS.get(sChar), 0, oChars.get(sChar), 1, oCharPts.get(sChar));
							oLayer.add(new TdFeature(oChars.get(sChar), nTags, oCtrl));
						}
					}
				}
			}
			if (!oLayer.isEmpty())
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(FileUtil.newOutputStream(Paths.get(String.format(g_sTdFileFormat, nX, g_nDefaultZoom, nX, nY)), FileUtil.APPENDTO, FileUtil.FILEPERS))))
				{
					oLayer.write(oOut);
				}
			}
		}
	}


	// headway is not derived from the xodr files so these methods do nothing at the moment
	@Override
	public void parseMetadata(Path oSource) throws Exception
	{
	}

	@Override
	protected void proc(String sLineArcsFile, double dTol) throws Exception
	{
	}

	@Override
	public ArrayList<CtrlLineArcs> combine(ArrayList<CtrlLineArcs> oLanes, double dTol)
	{
		return null;
	}
}
