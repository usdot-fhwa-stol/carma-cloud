/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

import cc.geosrv.Mercator;
import cc.vector_tile.VectorTile;
import java.awt.geom.Area;
import java.awt.geom.Path2D;

/**
 *
 * @author aaron.cherney
 */
public class TileLayerBuilder
{
	public VectorTile.Tile.Layer.Builder m_oLayerBuilder;
	public VectorTile.Tile.Feature.Builder m_oFeatureBuilder;
	public VectorTile.Tile.Value.Builder m_oValueBuilder;
	public double[] m_dTileBounds;
	public double[] m_dClippingBounds;
	public int m_nExtent;
	public int[] m_nCur;
	public int[] m_nPointBuffer;
	public Area m_oPolyClip;
	public int m_nX;
	public int m_nY;
	public int m_nZ;
	
	public TileLayerBuilder(int nX, int nY, int nZ)
	{
		m_nX = nX;
		m_nY = nY;
		m_nZ = nZ;
		m_dTileBounds = new double[4];
		Mercator oM = Mercator.getInstance();
		oM.tileBounds(nX, nY, nZ, m_dTileBounds); // get the meter bounds of the requested tile
		double dPadding = oM.resolution(nZ) * 8;
		m_dClippingBounds = new double[4];
		m_dClippingBounds[0] = m_dTileBounds[0] - dPadding;
		m_dClippingBounds[1] = m_dTileBounds[1] - dPadding;
		m_dClippingBounds[2] = m_dTileBounds[2] + dPadding;
		m_dClippingBounds[3] = m_dTileBounds[3] + dPadding;
		m_oLayerBuilder = VectorTile.Tile.Layer.newBuilder();
		m_oFeatureBuilder = VectorTile.Tile.Feature.newBuilder();
		m_oValueBuilder = VectorTile.Tile.Value.newBuilder();
		m_nExtent = 4096;
		m_nCur = new int[2];
		m_nPointBuffer = Arrays.newIntArray(1024);
		Path2D.Double oTilePath = new Path2D.Double(); // create clipping boundary
		oTilePath.moveTo(m_dClippingBounds[0], m_dClippingBounds[3]);
		oTilePath.lineTo(m_dClippingBounds[2], m_dClippingBounds[3]);
		oTilePath.lineTo(m_dClippingBounds[2], m_dClippingBounds[1]);
		oTilePath.lineTo(m_dClippingBounds[0], m_dClippingBounds[1]);
		oTilePath.closePath();
		m_oPolyClip = new Area(oTilePath);
		m_oLayerBuilder.setVersion(2);
		m_oLayerBuilder.setExtent(m_nExtent);
	}
}
