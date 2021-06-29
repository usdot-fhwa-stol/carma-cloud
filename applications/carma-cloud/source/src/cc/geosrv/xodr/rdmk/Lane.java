/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.geosrv.xodr.rdmk;

import cc.geosrv.xodr.XodrUtil;
import cc.util.Arrays;
import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class Lane extends ArrayList<LaneWidth>
{
	private static String[] TYPES = new String[]
	{
		"none",
		"driving",
		"stop",
		"shoulder",
		"biking",
		"sidewalk",
		"border",
		"restricted",
		"parking",
		"bidirectional",
		"median",
		"special1",
		"special2",
		"special3",
		"roadWorks",
		"tram",
		"rail",
		"entry",
		"exit",
		"offRamp",
		"onRamp",
		"connectingRamp",
		"bus",
		"taxi",
		"HOV"
	};
	public int m_nLaneIndex;
	public ArrayList<double[]> m_dCenters = new ArrayList();
	public int m_nRoadId;
	public int m_nSectionId;
	public int m_nLaneIdByRoad;
	public ArrayList<RoadMark> m_oOuterRoadMarks = new ArrayList();
	public ArrayList<RoadMark> m_oInnerRoadMarks;
	public double m_dLastOuterRdMkS = Double.MAX_VALUE;
	public double m_dLastInnerRdMkS = Double.MAX_VALUE;
	public int[] m_nRdMkTags = Arrays.newIntArray();
	public int m_nRdMkCnt = 0;
	public int m_nType;
	
	public Lane()
	{
		super();
	}
	
	
	public Lane(int nId, String sType, int nRoadId, int nSectionId)
	{
		this();
		m_nType = XodrUtil.getLaneType(sType);
		m_nLaneIndex = nId;
		m_nRoadId = nRoadId;
		m_nSectionId = nSectionId;
	}
}

