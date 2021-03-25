/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import cc.ctrl.proc.ProcClosed;
import cc.ctrl.proc.ProcClosing;
import cc.ctrl.proc.ProcCtrl;
import cc.ctrl.proc.ProcDirection;
import cc.ctrl.proc.ProcLatPerm;
import cc.ctrl.proc.ProcMaxSpeed;
import cc.ctrl.proc.ProcOpening;
import cc.ctrl.proc.ProcSignal;
import cc.ctrl.proc.ProcStop;
import cc.ctrl.proc.ProcYield;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import cc.util.CsvReader;
import cc.util.FileUtil;
import cc.util.Geo;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class CreateCtrls
{
	public static void createCtrl(Path oFile)
	   throws Exception
	{
		double[] dCenter = Arrays.newDoubleArray();
		String sControlType;
		int nControlValue;
		long lTime = System.currentTimeMillis();
		try (CsvReader oIn = new CsvReader(FileUtil.newInputStream(oFile)))
		{
			int nCols;
			while ((nCols = oIn.readLine()) > 0)
			{
				sControlType = oIn.parseString(0);
				nControlValue = oIn.parseInt(1);
				int nOrder = oIn.parseInt(2);
				boolean bReg = oIn.parseInt(3) == 1;
				dCenter[0] = 1;
				dCenter = Arrays.add(dCenter, new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
				for (int nIndex = 4; nIndex < nCols;)
				{
					double dX1 = Mercator.lonToMeters(oIn.parseDouble(nIndex++));
					double dY1 = Mercator.latToMeters(oIn.parseDouble(nIndex++));
					double dX2 = Mercator.lonToMeters(oIn.parseDouble(nIndex++));
					double dY2 = Mercator.latToMeters(oIn.parseDouble(nIndex++));
					double dW = Geo.distance(dX1, dY1, dX2, dY2);
					double dXc = (dX1 + dX2) / 2;
					double dYc = (dY1 + dY2) / 2;
					dCenter = Arrays.addAndUpdate(dCenter, dXc, dYc);
					dCenter = Arrays.add(dCenter, dW);
				}
				CtrlLineArcs oCla = new CtrlLineArcs(-1, -1, nOrder, -1, XodrUtil.getLaneType("driving"), dCenter, 0.1);
				TrafCtrl oCtrl = new TrafCtrl(sControlType, nControlValue, lTime, oCla.m_dLineArcs);
				oCtrl.m_bRegulatory = bReg;
				ArrayList<int[]> nTiles = new ArrayList();
				oCtrl.write(ProcCtrl.g_sTrafCtrlDir, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom);
//				oCtrl.m_oFullGeo = new CtrlGeo(oCtrl, ProcCtrl.g_dExplodeStep, ProcCtrl.g_nDefaultZoom);
				ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
				ArrayList<TrafCtrl> oCtrls = new ArrayList();
				oCtrls.add(oCtrl);
				String sCtrl = TrafCtrlEnums.CTRLS[oCtrl.m_nControlType][0];
				switch (sCtrl)
				{
					case "signal":
					{
						ProcSignal.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "stop":
					{
						ProcStop.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "yield":
					{
						ProcYield.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "notowing":
					{
						break;
					}
					case "restricted":
					{
						break;
					}
					case "closed":
					{
						ProcClosed.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "chains":
					{
						break;
					} 
					case "direction":
					{
						ProcDirection.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "lataffinity":
					{
						break;
					}
					case "latperm":
					{
						ProcLatPerm.renderTiledData(oCtrls, nTiles, new int[]{2, 4, 4});
						break;
					}
					case "opening":
					{
						ProcOpening.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "closing":
					{
						ProcClosing.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "parking":
					{
						break;
					}
					case "minspeed":
					{
						break;
					}
					case "maxspeed":
					{
						ProcMaxSpeed.renderTiledData(oCtrls, nTiles);
						break;
					}
					case "minhdwy":
					{
						break;
					}
					case "maxvehmass":
					{
						break;
					}
					case "maxvehheight":
					{
						break;
					}
					case "maxvehwidth":
					{
						break;
					}
					case "maxvehlength":
					{
						break;
					} 
					case "maxaxles":
					{
						break;
					} 
					case "minvehocc":
					{
						break;
					}
				}
			}
			
		}
	}
	
	public static void main(String[] sArgs)
	   throws Exception
	{
		int nVal = 1;
		nVal <<= 16;
		nVal |= (1 & 0xff);
		System.out.println(nVal);
//		double dX1 = Mercator.lonToMeters(-77.1485213);
//		double dY1 = Mercator.latToMeters(38.9545447);
//		double dX3 = Mercator.lonToMeters(-77.1486383);
//		double dY3 = Mercator.latToMeters(38.9544829);
//		double dX2 = Mercator.lonToMeters(-77.1485321);
//		double dY2 = Mercator.latToMeters(38.9545655);
//		double dX4 = Mercator.lonToMeters(-77.1486508);
//		double dY4 = Mercator.latToMeters(38.9545185);
		
//		double dX1 = Mercator.lonToMeters(-77.1479245);
//		double dY1 = Mercator.latToMeters(38.9546716);
//		double dX3 = Mercator.lonToMeters(-77.1480271);
//		double dY3 = Mercator.latToMeters(38.9546754);
//		double dX2 = Mercator.lonToMeters(-77.1479144);
//		double dY2 = Mercator.latToMeters(38.9546446);
//		double dX4 = Mercator.lonToMeters(-77.1480184);
//		double dY4 = Mercator.latToMeters(38.9546513);
//		
//		double dHdg1 = Geo.heading(dX1, dY1, dX3, dY3);
//		double dDist1 = Geo.distance(dX1, dY1, dX3, dY3);
//		int nLimit1 = (int)(dDist1 / 0.06) + 1;
//		
//		
//		double dHdg2 = Geo.heading(dX2, dY2, dX4, dY4);
//		double dDist2 = Geo.distance(dX2, dY2, dX4, dY4);
//		int nLimit2 = (int)(dDist2 / 0.06) + 1;
//		
//		int nLimit = Math.max(nLimit1, nLimit2);
//		
//		double dStep1 = dDist1 / nLimit;
//		double dDeltaX1 = dStep1 * Math.cos(dHdg1);
//		double dDeltaY1 = dStep1 * Math.sin(dHdg1);
//		double dStep2 = dDist2 / nLimit;
//		double dDeltaX2 = dStep2 * Math.cos(dHdg2);
//		double dDeltaY2 = dStep2 * Math.sin(dHdg2);
//		   
//		double[] dPoints = Arrays.newDoubleArray();
//		
//		for (int i = 0; i < nLimit; i++)
//		{
//			double dX = dX1 + dDeltaX1 * i;
//			double dY = dY1 + dDeltaY1 * i;
//			
//			dPoints = Arrays.add(dPoints, dX, dY);
//			
//			dX = dX2 + dDeltaX2 * i;
//			dY = dY2 + dDeltaY2 * i;
//			
//			dPoints = Arrays.add(dPoints, dX, dY);
//		}
//		
//		Iterator<double[]> oIt = Arrays.iterator(dPoints, new double[2], 1, 2);
//		try (BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get("C:\\Users\\aaron.cherney\\Documents\\CarmaCloud\\ctrlpts2.csv"), FileUtil.WRITE), "UTF-8")))
//		{
//			while (oIt.hasNext())
//			{
//				double[] dPt = oIt.next();
//				oOut.append(String.format("%2.7f,%2.7f,", Mercator.xToLon(dPt[0]), Mercator.yToLat(dPt[1])));
//			}
//		}
	}
}
