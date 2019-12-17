/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import cc.geosrv.Mercator;
import cc.geosrv.xodr.Lane;
import cc.geosrv.xodr.LaneSection;
import cc.geosrv.xodr.Road;
import cc.geosrv.xodr.RoadMark;
import cc.geosrv.xodr.XodrParser;
import cc.util.Arrays;
import cc.util.Geo;
import cc.util.MathUtil;
import cc.util.TileUtil;
import cc.vector_tile.VectorTile;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.io.File;

/**
 *
 * @author Federal Highway Administration
 */
public class XodrTiles extends HttpServlet
{
	private ArrayList<Road> m_oXodrRoads = new ArrayList();
	private String[] m_sRoadMarkKeys = new String[]{"roadmark_color"};
	private String[] m_sLaneKeys = new String[]{"lane_type"};
	private String[] m_sRoadMarkValues = new String[]{"unknown", "white", "yellow", "none"};
	private String[] m_sLaneValues = new String[]{"unknown", "none", "driving", "restricted", "shoulder"};
	
	@Override
	public void init(ServletConfig oSConfig)
	   throws ServletException
	{
		try
		{
			RoadMark.setSolidSolid(Double.parseDouble(oSConfig.getInitParameter("solidsolid")));
			RoadMark.setBrokenSpace(Double.parseDouble(oSConfig.getInitParameter("brokenspace")));
			RoadMark.setBrokenLine(Double.parseDouble(oSConfig.getInitParameter("brokenline")));
			for (File oXodrFile : new File(oSConfig.getInitParameter("xodrdir")).listFiles((oFile) -> {return oFile.getName().endsWith(".xodr");}))
			{
				XodrParser oParser = new XodrParser();
				m_oXodrRoads.addAll(oParser.readXodr(oXodrFile.getAbsolutePath()));
			}
		}
		catch (Exception oEx)
		{
			throw new ServletException(oEx);
		}
	}
	
	
	@Override
	protected void doGet(HttpServletRequest oRequest, HttpServletResponse oResponse)
	   throws ServletException, IOException
	{
		String[] sUriParts = oRequest.getRequestURI().split("/");
		int nZ = Integer.parseInt(sUriParts[sUriParts.length - 3]);
		int nX = Integer.parseInt(sUriParts[sUriParts.length - 2]);
		int nY = Integer.parseInt(sUriParts[sUriParts.length - 1]);

		double[] dBounds = new double[4];
		double[] dLonLatBounds = new double[4];
		Mercator oM = new Mercator();
		oM.tileBounds(nX, nY, nZ, dBounds); // get the meter bounds of the requested tile
		oM.lonLatBounds(nX, nY, nZ, dLonLatBounds); // get the lon lat bounds of the requested tile

		double dDeltaLon = (dLonLatBounds[2] - dLonLatBounds[0]) * 0.1;
		double dDeltaLat = (dLonLatBounds[3] - dLonLatBounds[1]) * 0.1;
		double[] dLineClippingBounds = new double[]{dLonLatBounds[0] - dDeltaLon, dLonLatBounds[1] - dDeltaLat, dLonLatBounds[2] + dDeltaLon, dLonLatBounds[3] + dDeltaLat};
		double[] dLineSeg = new double[4];
		double[] dPoint = new double[2];
		ArrayList<double[]> oPaths = new ArrayList();
		ArrayList<double[]> oRoadMarks = new ArrayList();
		ArrayList<LaneArea> oLanes = new ArrayList();
		ArrayList<Lane> oLaneList = new ArrayList();
		Path2D.Double oTilePath = new Path2D.Double(); // create clipping boundary
		oTilePath.moveTo(dBounds[0], dBounds[3]);
		oTilePath.lineTo(dBounds[2], dBounds[3]);
		oTilePath.lineTo(dBounds[2], dBounds[1]);
		oTilePath.lineTo(dBounds[0], dBounds[1]);
		oTilePath.closePath();
		Area oTile = new Area(oTilePath);
		for (int nRoadIndex = 0; nRoadIndex < m_oXodrRoads.size(); nRoadIndex++)
		{
			Road oRoad = m_oXodrRoads.get(nRoadIndex);
			if (!Geo.boundingBoxesIntersect(dLonLatBounds[0], dLonLatBounds[1], dLonLatBounds[2], dLonLatBounds[3], oRoad.m_dBounds[0], oRoad.m_dBounds[1], oRoad.m_dBounds[2], oRoad.m_dBounds[3])) // check if the road is within the tile
				continue;

			for (LaneSection oSection : oRoad)
			{
				oSection.getLanes(oLaneList);
				for (Lane oLane : oLaneList)
				{
					for (RoadMark oRoadMark : oLane.m_oRoadMarks)
					{
						boolean bNotDone;
						for (double[] dRoadMark : oRoadMark.m_oTileLines)
						{
							Iterator<double[]> oIt = Arrays.iterator(dRoadMark, dLineSeg, 5, 2); // start at 5 because of insertion index and bounding box in first 5 positions
							if (oIt.hasNext())
							{
								oIt.next();
								boolean bPrevInside = Geo.isInside(dLineSeg[0], dLineSeg[1], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], 0);
								double[] dLine = Arrays.newDoubleArray(65);
								dLine = Arrays.add(dLine, (double)oRoad.m_nId);
								boolean bAdded = false;
								String sColor = oRoadMark.m_sType.compareTo("none") == 0 ? "none" : oRoadMark.m_sColor;
								for (int i = 0; i < m_sRoadMarkValues.length; i++)
								{
									if (sColor.compareTo(m_sRoadMarkValues[i]) == 0)
									{
										dLine = Arrays.add(dLine, (double)i);
										bAdded = true;
										break;
									}
								}
								if (!bAdded)
									dLine = Arrays.add(dLine, 0.0); // add unknown

								if (bPrevInside)
									dLine = Arrays.add(dLine, dLineSeg[0], dLineSeg[1]);

								do
								{
									bNotDone = false;
									if (bPrevInside) // previous point was inside 
									{
										if (Geo.isInside(dLineSeg[2], dLineSeg[3], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], 0)) // current point is inside
											dLine = Arrays.add(dLine, dLineSeg[2], dLineSeg[3]); // so add the current point
										else // current point is ouside
										{ // so need to calculate the intersection with the tile
											dLine = addIntersection(dLine, dLineSeg[0], dLineSeg[1], dLineSeg[2], dLineSeg[3], dLonLatBounds); // add intersection points
											bPrevInside = false;
											double[] dFinished = new double[(int)dLine[0] - 1]; // now that the line is outside finish the current line
											System.arraycopy(dLine, 1, dFinished, 0, dFinished.length);
											oRoadMarks.add(dFinished);
											dLine[0] = 3; // reset point buffer
										}
									}
									else // previous point was outside
									{
										if (Geo.isInside(dLineSeg[2], dLineSeg[3], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], 0)) // current point is inside
										{
											dLine = addIntersection(dLine, dLineSeg[0], dLineSeg[1], dLineSeg[2], dLineSeg[3], dLonLatBounds); // add the intersection
											bPrevInside = true;
											dLine = Arrays.add(dLine, dLineSeg[2], dLineSeg[3]); // and the next points
										}
										else // previous point and current point are outside, so check if the line segment intersects the tile
										{
											MathUtil.getIntersection(dLineSeg[0], dLineSeg[1], dLineSeg[2], dLineSeg[3], dLineClippingBounds[0], dLineClippingBounds[1], dLineClippingBounds[0], dLineClippingBounds[3], dPoint); // check left edge
											if (!Double.isNaN(dPoint[0]))
												dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);

											MathUtil.getIntersection(dLineSeg[0], dLineSeg[1], dLineSeg[2], dLineSeg[3], dLineClippingBounds[0], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[3], dPoint); // check top edge
											if (!Double.isNaN(dPoint[0]))
												dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);

											MathUtil.getIntersection(dLineSeg[0], dLineSeg[1], dLineSeg[2], dLineSeg[3], dLineClippingBounds[2], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dPoint); // check right edge
											if (!Double.isNaN(dPoint[0]))
												dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);

											MathUtil.getIntersection(dLineSeg[0], dLineSeg[1], dLineSeg[2], dLineSeg[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], dLineClippingBounds[1], dPoint); // check bot edge
											if (!Double.isNaN(dPoint[0]))
												dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);
										}
									}

									if (oIt.hasNext())
									{
										dLineSeg = oIt.next();
										bNotDone = true;
									}
								} while (bNotDone);

								if (dLine[0] > 4) // if the line has more that one point
								{
									double[] dFinished = new double[(int)dLine[0] - 1];
									System.arraycopy(dLine, 1, dFinished, 0, dFinished.length);
									oRoadMarks.add(dFinished);
								}
							}
						}
					}
					
					boolean bNotDone;
					Iterator<double[]> oIt = oLane.m_oControl.segmentIterator();
					if (oIt.hasNext())
					{
						double[] dLaneSeg = oIt.next();
						boolean bPrevInside = Geo.isInside(dLaneSeg[0], dLaneSeg[1], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], 0);
						double[] dLine = Arrays.newDoubleArray(65);
						dLine = Arrays.add(dLine, (double)oRoad.m_nId);

						if (bPrevInside)
							dLine = Arrays.add(dLine, dLaneSeg[0], dLaneSeg[1]);

						do
						{
							bNotDone = false;
							if (bPrevInside) // previous point was inside 
							{
								if (Geo.isInside(dLaneSeg[4], dLaneSeg[5], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], 0)) // current point is inside
									dLine = Arrays.add(dLine, dLaneSeg[4], dLaneSeg[5]); // so add the current point
								else // current point is ouside
								{ // so need to calculate the intersection with the tile
									dLine = addIntersection(dLine, dLaneSeg[0], dLaneSeg[1], dLaneSeg[4], dLaneSeg[5], dLonLatBounds); // add intersection points
									bPrevInside = false;
									double[] dFinished = new double[(int)dLine[0] - 1]; // now that the line is outside finish the current line
									System.arraycopy(dLine, 1, dFinished, 0, dFinished.length);
									oPaths.add(dFinished);
									dLine[0] = 1; // reset point buffer
								}
							}
							else // previous point was outside
							{
								if (Geo.isInside(dLaneSeg[4], dLaneSeg[5], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], 0)) // current point is inside
								{
									dLine = addIntersection(dLine, dLaneSeg[0], dLaneSeg[1], dLaneSeg[4], dLaneSeg[5], dLonLatBounds); // add the intersection
									bPrevInside = true;
									dLine = Arrays.add(dLine, dLaneSeg[4], dLaneSeg[5]); // and the next points
								}
								else // previous point and current point are outside, so check if the line segment intersects the tile
								{
									MathUtil.getIntersection(dLaneSeg[0], dLaneSeg[1], dLaneSeg[4], dLaneSeg[5], dLineClippingBounds[0], dLineClippingBounds[1], dLineClippingBounds[0], dLineClippingBounds[3], dPoint); // check left edge
									if (!Double.isNaN(dPoint[0]))
										dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);

									MathUtil.getIntersection(dLaneSeg[0], dLaneSeg[1], dLaneSeg[4], dLaneSeg[5], dLineClippingBounds[0], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[3], dPoint); // check top edge
									if (!Double.isNaN(dPoint[0]))
										dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);

									MathUtil.getIntersection(dLaneSeg[0], dLaneSeg[1], dLaneSeg[4], dLaneSeg[5], dLineClippingBounds[2], dLineClippingBounds[3], dLineClippingBounds[2], dLineClippingBounds[1], dPoint); // check right edge
									if (!Double.isNaN(dPoint[0]))
										dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);

									MathUtil.getIntersection(dLaneSeg[0], dLaneSeg[1], dLaneSeg[4], dLaneSeg[5], dLineClippingBounds[2], dLineClippingBounds[1], dLineClippingBounds[0], dLineClippingBounds[1], dPoint); // check bot edge
									if (!Double.isNaN(dPoint[0]))
										dLine = Arrays.add(dLine, dPoint[0], dPoint[1]);
								}
							}

							if (oIt.hasNext())
							{
								dLaneSeg = oIt.next();
								bNotDone = true;
							}
						} while (bNotDone);

						if (dLine[0] > 4) // if the line has more that one point
						{
							double[] dFinished = new double[(int)dLine[0] - 1];
							System.arraycopy(dLine, 1, dFinished, 0, dFinished.length);
							oPaths.add(dFinished);
						}
					}
					
					
					
					double[] dLane = oLane.m_dPolygon;
					Path2D.Double oPath = new Path2D.Double();
					oPath.moveTo(Mercator.lonToMeters(dLane[5]), Mercator.latToMeters(dLane[6]));
					oIt = Arrays.iterator(dLane, dPoint, 7, 2);
					while (oIt.hasNext())
					{
						oIt.next();
						oPath.lineTo(Mercator.lonToMeters(dPoint[0]), Mercator.latToMeters(dPoint[1]));
					}
					oPath.closePath();
					int nType = 0;
					for (int i = 0; i < m_sLaneValues.length; i++)
					{
						if (oLane.m_sType.compareTo(m_sLaneValues[i]) == 0)
						{
							nType = i;
							break;
						}
					}
					LaneArea oLaneArea = new LaneArea(oPath, nType);
					oLaneArea.intersect(oTile);
					if (!oLaneArea.isEmpty())
						oLanes.add(oLaneArea);
				}
			}
		}
		
		int nExtent = Mercator.getExtent(nZ);
		VectorTile.Tile.Builder oTileBuilder = VectorTile.Tile.newBuilder();
		VectorTile.Tile.Layer.Builder oLayer = VectorTile.Tile.Layer.newBuilder();
		VectorTile.Tile.Value.Builder oValue = VectorTile.Tile.Value.newBuilder();
		
		for (int i = 0; i < m_sRoadMarkKeys.length; i++)
			oLayer.addKeys(m_sRoadMarkKeys[i]);
		
		for (int i = 0; i < m_sRoadMarkValues.length; i++)
		{
			oValue.setStringValue(m_sRoadMarkValues[i]);
			oLayer.addValues(oValue.build());
			oValue.clear();
		}
		
		oLayer.setVersion(2);
		oLayer.setExtent(nExtent);
		
		VectorTile.Tile.Feature.Builder oFeatureBuilder = VectorTile.Tile.Feature.newBuilder();
		int[] nCur = new int[2]; // reusable arrays for feature methods
		int[] nPoints = new int[65];
		oLayer.setName("roadmarks");
		for (double[] dRoadMark : oRoadMarks)
		{
			TileUtil.addLinestring(oFeatureBuilder, nCur, dBounds, nExtent, 2, dRoadMark, nPoints);
			oFeatureBuilder.setId((long)dRoadMark[0]);
			oFeatureBuilder.addTags(0);
			oFeatureBuilder.addTags((int)dRoadMark[1]);
			oLayer.addFeatures(oFeatureBuilder.build());
			oFeatureBuilder.clear();
			nCur[0] = nCur[1] = 0;	
		}
		if (oLayer.getFeaturesCount() > 0)
			oTileBuilder.addLayers(oLayer.build());
		
		oLayer.clear();
		oLayer.setVersion(2);
		oLayer.setName("lanepaths");
		oLayer.setExtent(nExtent);
		for (double[] dPath : oPaths)
		{
			TileUtil.addLinestring(oFeatureBuilder, nCur, dBounds, nExtent, 1, dPath, nPoints);
			oLayer.addFeatures(oFeatureBuilder.build());
			oFeatureBuilder.clear();
			nCur[0] = nCur[1] = 0;
		}
		if (oLayer.getFeaturesCount() > 0)
			oTileBuilder.addLayers(oLayer.build());
		
		oLayer.clear();
		for (int i = 0; i < m_sLaneKeys.length; i++)
			oLayer.addKeys(m_sLaneKeys[i]);
		
		for (int i = 0; i < m_sLaneValues.length; i++)
		{
			oValue.setStringValue(m_sLaneValues[i]);
			oLayer.addValues(oValue.build());
			oValue.clear();
		}
		
		
		oLayer.setVersion(2);
		oLayer.setName("xodrlanes");
		oLayer.setExtent(nExtent);
		int nIndex = oLanes.size();
		for (int i = 0; i < nIndex; i++)
		{
			LaneArea oLane = oLanes.get(i);
			oFeatureBuilder.setType(VectorTile.Tile.GeomType.POLYGON);
			TileUtil.addPolygon(oFeatureBuilder, nCur, dBounds, nExtent, oLane, nPoints);
			oFeatureBuilder.addTags(0);
			oFeatureBuilder.addTags(oLane.m_nType);
			oLayer.addFeatures(oFeatureBuilder.build());
			oFeatureBuilder.clear();
			nCur[0] = nCur[1] = 0;
		}
		if (oLayer.getFeaturesCount() > 0)
			oTileBuilder.addLayers(oLayer.build());
		
		oResponse.setContentType("application/x-protobuf");
		if (oTileBuilder.getLayersCount() > 0)
			oTileBuilder.build().writeTo(oResponse.getOutputStream());
	}	
	
	static double[] addIntersection(double[] dPoints, double dX1, double dY1, double dX2, double dY2, double[] dLonLats)
	{
		double[] dPoint = new double[2];
		MathUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[0], dLonLats[1], dLonLats[0], dLonLats[3], dPoint); // check left edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.add(dPoints, dPoint[0], dPoint[1]);
		
		MathUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[0], dLonLats[3], dLonLats[2], dLonLats[3], dPoint); // check top edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.add(dPoints, dPoint[0], dPoint[1]);
		
		MathUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[2], dLonLats[3], dLonLats[2], dLonLats[1], dPoint); // check right edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.add(dPoints, dPoint[0], dPoint[1]);
		
		MathUtil.getIntersection(dX1, dY1, dX2, dY2, dLonLats[2], dLonLats[1], dLonLats[0], dLonLats[1], dPoint); // check bot edge
		if (!Double.isNaN(dPoint[0]))
			return Arrays.add(dPoints, dPoint[0], dPoint[1]);
		
		return dPoints; // no intersections
	}
	
	
	private class LaneArea extends Area
	{
		int m_nType;
		
		LaneArea(Path2D.Double oPath, int nType)
		{
			super(oPath);
			m_nType = nType;
		}
	}
}
