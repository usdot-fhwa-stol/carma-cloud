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
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class RenderCtrl
{
	public static void main(String[] sArgs)
		throws Exception
	{
		long lNow = System.currentTimeMillis();
		String sFile = sArgs[0];
		double dExplodeStep = Double.parseDouble(sArgs[1]); // 0.06
		String sTrafCtrlDir = sArgs[2]; // /opt/tomcat/work/carmacloud/traf_ctrls
		String sTdFF = sArgs[3]; // /opt/tomcat/work/carmacloud/td/%d/ctrls_%d_%d_%d.bin
		int nZoom = Integer.parseInt(sArgs[4]);
		ProcCtrl.setStaticVariables(dExplodeStep, sTrafCtrlDir, sTdFF, nZoom);
		TrafCtrl oCtrl = null;
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(sFile)))))
		{
			oCtrl = new TrafCtrl(oIn, false);
		}
		
		try (DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(sFile)))))
		{
			oCtrl.m_oFullGeo = new CtrlGeo(oIn, true, nZoom);
		}
		
		ArrayList<int[]> nTiles = new ArrayList();
		ProcCtrl.updateTiles(nTiles, oCtrl.m_oFullGeo.m_oTiles);
		
		for (int[] nTile : nTiles)
		{
			String sIndex = String.format(sTdFF, nTile[0], nZoom, nTile[0], nTile[1]) + ".ndx";
			ProcCtrl.updateIndex(sIndex, oCtrl.m_yId, lNow);
		}
		
		oCtrl.m_yId = null;
		oCtrl.write(sTrafCtrlDir, dExplodeStep, nZoom, ProcCtrl.CC);
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
