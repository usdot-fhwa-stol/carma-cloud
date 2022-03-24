/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl.proc;

import cc.ctrl.CtrlLineArcs;
import cc.ctrl.TrafCtrl;
import cc.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class ProcMinPlatoonHdwy extends ProcCtrl
{

	public ProcMinPlatoonHdwy(String sLineArcDir)
	{
		super(sLineArcDir);
	}
	
	public static void renderTiledData(ArrayList<TrafCtrl> oCtrls, ArrayList<int[]> nTiles) throws IOException
	{
		for (int[] nTile : nTiles)
		{
			int nX = nTile[0];
			int nY = nTile[1];
			Files.createDirectories(Paths.get(String.format(g_sTdFileFormat, nX, 0, 0, 0)).getParent(), FileUtil.DIRPERS);
			writeIndexFile(oCtrls, nX, nY);
		}
	}
	
	
	// minplatoonhdwy is not derived from the xodr files so these methods do nothing at the moment
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
