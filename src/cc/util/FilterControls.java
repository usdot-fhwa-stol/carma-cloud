package cc.util;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.io.IOException;
import cc.ctrl.proc.TdFeature;
import cc.ctrl.proc.TdLayer;
import cc.ctrl.CtrlIndex;
import cc.ctrl.TrafCtrl;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Federal Highway Administration
 */
public class FilterControls 
{
	public static void main(String[] sArgs)
		throws Exception
	{
		String sInputDir = sArgs[0];
		if (!sInputDir.endsWith("/"))
			sInputDir += "/";
		String sOutputDir = sArgs[1];
		if (!sOutputDir.endsWith("/"))
			sOutputDir += "/";
		Files.createDirectories(Paths.get(sOutputDir));
		Files.createDirectories(Paths.get(sOutputDir + "linearcs/direction"));
		Files.createDirectories(Paths.get(sOutputDir + "linearcs/rdmks"));
		Files.createDirectories(Paths.get(sOutputDir + "linearcs/pavement"));
		Files.createDirectories(Paths.get(sOutputDir + "xodr/"));
		Files.createDirectories(Paths.get(sOutputDir + "td/"));
		Files.createDirectories(Paths.get(sOutputDir + "geolanes/"));
		Files.createDirectories(Paths.get(sOutputDir + "traf_ctrls/"));
		ArrayList<byte[]> oIds = new ArrayList();
		StringBuilder sBuf = new StringBuilder();
		for (int n = 2; n < sArgs.length; n++)
		{
			String sId = sArgs[n];
			sBuf.setLength(0);
			sBuf.append(sId);
			oIds.add(Text.fromHexString(sBuf));
		}
		
		Collections.sort(oIds, TrafCtrl.ID_COMP);
		
		Path oTdDir = Paths.get(sInputDir + "td/");
		Path oOutputTdDir = Paths.get(sOutputDir + "td/");
		Path oOutputGeoLanes = Paths.get(sOutputDir + "geolanes");
		Path oOutputCtrl = Paths.get(sOutputDir + "traf_ctrls");
		
		List<Path> oPaths = Files.walk(oTdDir).filter(Files::isRegularFile).collect(Collectors.toList());
		byte[] yIdBuf = new byte[16];
		for (Path oFile : oPaths)
		{
			String sFilename = oFile.toString();
			ByteArrayOutputStream oBaos = new ByteArrayOutputStream();
			if (sFilename.endsWith(".bin")) // td file
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(oBaos));
					 DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(oFile))))
				{
					while (oIn.available() > 0)
					{
						TdLayer oTemp = new TdLayer(oIn, false);
						int nFeatures = oIn.readInt();
						for (int nIndex = 0; nIndex < nFeatures; nIndex++)
						{
							oIn.read(yIdBuf);
							int nBytesToSkip = oIn.readInt();
							int nSearch = Collections.binarySearch(oIds, yIdBuf, TrafCtrl.ID_COMP);
							if (nSearch < 0)
								oIn.skipBytes(nBytesToSkip);
							else
								oTemp.add(new TdFeature(oIn, oTemp.m_oKeys.length * 2, yIdBuf));
						}
						oTemp.write(oOut);
					}
				}
				
			}
			else if (sFilename.endsWith(".ndx")) // index file
			{
				try (DataOutputStream oOut = new DataOutputStream(new BufferedOutputStream(oBaos));
					DataInputStream oIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(oFile))))
				{
					
					while (oIn.available() > 0)
					{
						CtrlIndex oIndex = new CtrlIndex(oIn);
						int nSearch = Collections.binarySearch(oIds, oIndex.m_yId, TrafCtrl.ID_COMP);
						if (nSearch >= 0)
							writeIndex(oIndex, oOut);
					}
				}
			}
			
			if (oBaos.size() > 0)
			{
				String sPath = oOutputTdDir.toString() + "/" + oFile.subpath(oFile.getNameCount() - 2, oFile.getNameCount()).toString();
				Path oPath = Paths.get(sPath);
				Files.createDirectories(oPath.getParent());
				try (BufferedOutputStream oOut = new BufferedOutputStream(Files.newOutputStream(oPath)))
				{
					oOut.write(oBaos.toByteArray());
				}
			}
		}
		
		for (byte[] yId : oIds)
		{
			createFilePath(sBuf, yId);

			Path oInTraf = Paths.get(sInputDir + "traf_ctrls/" + sBuf.toString() + ".bin");
			Path oOutTraf = Paths.get(oOutputCtrl.toString() + "/" + sBuf.toString() + ".bin");
			Files.createDirectories(oOutTraf.getParent());
			Files.copy(oInTraf, oOutTraf);
			
			oInTraf = Paths.get(sInputDir + "traf_ctrls/" + sBuf.toString() + ".bin.json");
			oOutTraf = Paths.get(oOutputCtrl.toString() + "/" + sBuf.toString() + ".bin.json");
			Files.copy(oInTraf, oOutTraf);
		}
	}
	
	
	public static void createFilePath(StringBuilder sBuf, byte[] oId)
	{
		sBuf.setLength(0);
		int nOffset = 0;
		int nCount = 3;
		while (nCount-- > 0)
		{
			Text.toHexString(oId, nOffset, nOffset + 4, sBuf);
			nOffset += 4;
			sBuf.append("/");
		}
		Text.toHexString(oId, nOffset, nOffset + 4, sBuf);
	}
		
	public static void writeIndex(CtrlIndex oIndex, DataOutputStream oOut)
		throws IOException
	{
		oOut.writeInt(oIndex.m_nType);
		oOut.write(oIndex.m_yId);
		oOut.writeLong(oIndex.m_lStart);
		oOut.writeLong(oIndex.m_lEnd);
		for (int nIndex = 0; nIndex < oIndex.m_dBB.length; nIndex++)
			oOut.writeInt((int)(oIndex.m_dBB[nIndex] * 100.0 + 0.5));
	}
}
