var g_oMap;
var g_sCurLane;
var g_aCurPath = [];
var g_oLanes = {};
var g_oPaths = [];
const g_oSourceLayers = $.getJSON('mapbox/validatexodr_sourcelayers.json').promise();
var g_oPopup;
var g_bDebug = false;
var g_oSources;

function pointToPaddedBounds(oPoint, dPad)
{
	return [{x: oPoint.x - dPad, y: oPoint.y - dPad}, {x: oPoint.x + dPad, y: oPoint.y + dPad}];
}

class AoiControl
{
  onAdd(map)
  {
    this._map = map;
    this._container = document.createElement('div');
    this._container.className = 'mapboxgl-ctrl mapboxgl-ctrl-group six-button-control';

    this._container.innerHTML = [{t:'AOI0', i:'AOI0'}, {t:'AOI1', i:'AOI1'}, {t:'AOI2', i:'AOI2'}, {t:'AOI3', i:'AOI3'}, {t:'AOI4', i:'AOI4'}, {t:'AOI5', i:'AOI5'}, {t:'AOI6', i:'AOI6'}, {t:'AOI7', i:'AOI7'}]
      .reduce((accum, opts) => accum +
          `<button class="mapboxgl-ctrl-icon" type="button" title="${opts.t}">
             <img src="images/button-control/${opts.i}.png" />
           </button>`, '');

    return this._container;
  }

  onRemove()
  {
    this._container.parentNode.removeChild(this._container);
    this._map = undefined;
  }
}

class DebugControl
{
  onAdd(map)
  {
    this._map = map;
    this._container = document.createElement('div');
    this._container.className = 'mapboxgl-ctrl mapboxgl-ctrl-group six-button-control';

    this._container.innerHTML = [{t:'Toggle Pavement',i:'groupctrl'}, {t:'Toggle Debug',i:'debug'}]
      .reduce((accum, opts) => accum +
          `<button class="mapboxgl-ctrl-icon" type="button" title="${opts.t}">
             <img src="images/button-control/${opts.i}.png" />
           </button>`, '');

    return this._container;
  }

  onRemove()
  {
    this._container.parentNode.removeChild(this._container);
    this._map = undefined;
  }
}

function buildSourceMap(sourceData)
{
	 const oSources = new Map();
	 Object.entries(sourceData).forEach(([sourceId, {source, layers}]) =>
	 {
		  const fullLayerDefs = [];
		  var type = '';
		  var defaults;
		  for (let layer of layers)
		  {
				const fullLayer = {};
				if (layer.hasOwnProperty('type') && layer.type != type)
				{
					 type = layer.type;
					 defaults = {}
					 for (let key of Object.keys(layer))
					 {
						  fullLayer[key] = layer[key];
						  defaults[key] = layer[key];
					 }
				}
				else
				{
					 layer['type'] = type;
					 for (let key of Object.keys(layer))
					 {
						  fullLayer[key] = layer[key];
					 }
					 for (let key of Object.keys(defaults))
					 {
						  if (!fullLayer.hasOwnProperty(key))
								fullLayer[key] = defaults[key];
					 }
				}

				fullLayer['source'] = sourceId;
				if (!fullLayer.hasOwnProperty('id'))
					 fullLayer['id'] = fullLayer['source-layer'];
				fullLayerDefs.push(fullLayer);

				let fullSource;
				if (source.type === 'vector')
				{
					fullSource = {type, layers: fullLayerDefs, id: sourceId, mapboxSource: Object.assign({}, source, {tiles: source.tiles})};
				}
				else if (source.type === 'geojson')
				{
					fullSource = {type, layers: fullLayerDefs, id: sourceId, mapboxSource: Object.assign({}, source)}
				}
				if (fullSource)
					oSources.set(sourceId, fullSource);
		  }
	 });
	 return oSources;
}


async function initialize()
{
	g_oMap = new mapboxgl.Map({"container": "mapid", "style": "mapbox://styles/mapbox/satellite-v8", "attributionControl": false,
                "minZoom": 4, "maxZoom": 24, "center": [-77.1488930, 38.9562550], "zoom": 18, "accessToken": "pk.eyJ1Ijoia3J1ZWdlcmIiLCJhIjoiY2tuajlwYWZ5MGI0ZTJ1cGV1bTk5emtsaCJ9.En7O3cNsbmy7Gk555ZjmVQ"});
//	g_oMap = new mapboxgl.Map({"container": "mapid", "style": "mapbox/satellite-v8.json", "attributionControl": false,
//                "minZoom": 4, "maxZoom": 24, "center": [-76.856, 38.750], "zoom": 18});

	g_oMap.dragRotate.disable(); // disable map rotation using right click + drag
	g_oMap.touchZoomRotate.disableRotation(); // disable map rotation using touch rotation gesture
	g_oMap.addControl(new mapboxgl.NavigationControl({showCompass: false}));
	g_oMap.showTileBoundaries = false;
	
	g_oMap.on('load', async function()
	{
		g_oSources = buildSourceMap(await g_oSourceLayers);
		for (let oSrc of g_oSources.values())
		{
			g_oMap.addSource(oSrc.id, oSrc.mapboxSource);
			for (let oLayer of oSrc.layers)
				g_oMap.addLayer(oLayer);
		}
		g_oMap.removeLayer('debug-c');
		g_oMap.removeLayer('debug-p');
		g_oPopup = new mapboxgl.Popup({closeButton: false, closeOnClick: false});
		
		g_oMap.on('click', mouseClickStartPoly);
	});
	g_oMap.addControl(new AoiControl(), 'top-right');
	g_oMap.addControl(new DebugControl(), 'top-right');
	$("button[title|='Toggle Debug'").click(function(){
				g_bDebug = !g_bDebug;
				g_oMap.showTileBoundaries = g_bDebug;
				if (g_bDebug)
				{
					g_oMap.on('mousemove', mouseMoveEditFree);
					let oXodr = g_oSources.get('ctrl');
					for (let oLayer of oXodr.layers)
					{
						if (oLayer.id === 'debug-c' || oLayer.id === 'debug-p')
							g_oMap.addLayer(oLayer);
					}
				}
				else
				{
					g_oMap.off('mousemove', mouseMoveEditFree);
					g_oMap.removeLayer('debug-c');
					g_oMap.removeLayer('debug-p');
				}
				});
				
	$("button[title|='AOI0'").click(function() {g_oMap.jumpTo({center: [-77.1488930, 38.9562550], zoom: 18});});
	$("button[title|='AOI1'").click(function() {g_oMap.jumpTo({center: [-76.1691952, 39.4763684], zoom: 18});});
	$("button[title|='AOI2'").click(function() {g_oMap.jumpTo({center: [-77.1702623, 38.9417249], zoom: 18});});
	$("button[title|='AOI3'").click(function() {g_oMap.jumpTo({center: [-77.3542585, 38.5356000], zoom: 18});});
	$("button[title|='AOI4'").click(function() {g_oMap.jumpTo({center: [-77.2205829, 38.8260356], zoom: 18});});
	$("button[title|='AOI5'").click(function() {g_oMap.jumpTo({center: [-76.8552198, 38.7474584], zoom: 18});});
	$("button[title|='AOI6'").click(function() {g_oMap.jumpTo({center: [-76.2052773, 39.4449447], zoom: 18});});
	$("button[title|='AOI7'").click(function() {g_oMap.jumpTo({center: [-77.9684147, 39.2367993], zoom: 18});});
	$("button[title|='Toggle Pavement'").click(function() {g_oMap.setPaintProperty('pavement', 'fill-opacity', g_oMap.getPaintProperty('pavement', 'fill-opacity') === 1 ? 0 : 1);});
}


function geoSuccess(oJsonData, sStatus, oJqXHR)
{
	let aLines = ['a', 'b', 'c'];
	let oCoords = {aCLanes: []};
	for (let sLine of aLines.values())
	{
		let oLine = oJsonData[sLine];
		oCoords[sLine] = [[fromIntDeg(oLine[0]), fromIntDeg(oLine[1])]];
		let aCoords = oCoords[sLine];
		for (let nIndex = 2; nIndex < oLine.length; nIndex += 2)
		{
			oLine[nIndex] += oLine[nIndex - 2];
			oLine[nIndex + 1] += oLine[nIndex - 1];
			aCoords.push([fromIntDeg(oLine[nIndex]), fromIntDeg(oLine[nIndex + 1])]);
		}
	}
	
	let nFirst = this.url.indexOf('/', 1) + 1;
	let sUrl = this.url.substring(this.url.indexOf('/', nFirst));
	g_oLanes[sUrl] = oCoords;
	updateCurrent(sUrl);
	createPolyStart(sUrl, this.startPoint);
	setCurCLane(sUrl, this.startPoint);
	setCursor('e-resize');
}

function updateCurrent(sUrl)
{
	if (sUrl !== g_sCurLane)
		return;
	
	g_oMap.setPaintProperty('hl-pt', 'circle-opacity', 0);
	let oLayer = g_oMap.getSource('hl-line-gjson');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.geometry.coordinates = createWholePoly(g_oLanes[sUrl]['a'], g_oLanes[sUrl]['b']);
		oLayer.setData(oData);
		g_oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 1);
	}
}


function createPolyStart(sUrl, oLngLat)
{
	let oLane = g_oLanes[sUrl];
	let aC = oLane['c'];
	let aA = oLane['a'];
	let aB = oLane['b'];
	let oSnapInfo = getSnapInfo(aC, oLngLat);
	let nIndex = oSnapInfo.nIndex;
	let dLength = length(aA[nIndex][0], aA[nIndex][1], aB[nIndex][0], aB[nIndex][1]);
	let dTotalDist = 0;
	let nEnd = nIndex;
	let nStart = nIndex;
	let oCLane = {'o': nIndex, 's': nIndex, 'e': nIndex, 'sl': 0, 'el': aC.length - 1};
	oLane['aCLanes'].push(oCLane);
	setCLaneLimits(sUrl);
	let nLimit = oCLane['el'] - 1;
	while (dTotalDist < dLength && nIndex < nLimit)
	{
		dTotalDist += length(aC[nIndex][0], aC[nIndex++][1], aC[nIndex][0], aC[nIndex][1]);
		nEnd = nIndex;
	}
	
	if (dTotalDist < dLength)
	{
		nLimit = oLane['sl'];
		nIndex = nStart;
		while (nIndex > 0 && dTotalDist < dLength)
		{
			dTotalDist += length(aC[nIndex][0], aC[nIndex--][1], aC[nIndex][0], aC[nIndex][1]);
			nStart = nIndex;
		}
	}
	
	oCLane['s'] = nStart;
	oCLane['e'] = nEnd;
	
	oLayer = g_oMap.getSource('hl-poly-gjson');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.geometry.coordinates = createPoly(aA, aB, nStart, nEnd);
		oLayer.setData(oData);
		g_oMap.setPaintProperty('hl-poly', 'fill-opacity', 1);
	}
}


function mouseClickStartPoly({target, lngLat, point})
{
	let sUrl = g_sCurLane;
	if (sUrl !== undefined)
	{
		switchHandler('mousemove', mouseMoveEditFree, mouseMoveDragPoly);
		switchHandler('click', mouseClickStartPoly, mouseClickEndPoly);
		
		if (g_oLanes[sUrl] === undefined)
		{
			g_oLanes[sUrl] = 'processing';
			setCursor('progress');
			$.ajax('/api/lgeolanes' + sUrl, 
				{
					dataType: 'json',
					startPoint: lngLat
				}).done(geoSuccess).always(function() {if (getCursor() === 'progress') setCursor('');});
		}
		else if (g_oLanes[sUrl] !== 'processing')
		{
			updateCurrent(sUrl);
			createPolyStart(sUrl, lngLat);
			setCurCLane(sUrl, lngLat);
			setCursor('e-resize');
		}
	}
}


function mouseClickEndPoly({target, lngLat, point})
{
	switchHandler('mousemove', mouseMoveDragPoly, mouseMoveEditFree);
	switchHandler('click', mouseClickEndPoly, mouseClickStartPoly);
	g_oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
	g_oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
	let oLayer = g_oMap.getSource('created-lanes');
	let sUrl = g_sCurLane;
	let oLane = g_oLanes[sUrl];
	
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.features.push({'type': 'Feature', 'properties': {'url': sUrl}, 'geometry': {'type': 'Polygon', 'coordinates': createPoly(oLane['a'], oLane['b'], oLane['oCurCLane']['s'], oLane['oCurCLane']['e'])}});
		oLayer.setData(oData);
		g_oMap.setPaintProperty('lanes', 'fill-opacity', 1);
	}
	setCursor('');
}


function switchHandler(sType, fnFrom, fnTo)
{
	g_oMap.off(sType, fnFrom);
	g_oMap.on(sType, fnTo);
}


function setCursor(sStyle)
{
	g_oMap.getCanvas().style.cursor = sStyle;
}


function getCursor()
{
	return g_oMap.getCanvas().style.cursor;
}


function mouseMoveEditFree({target, lngLat, point})
{
//	let oPolys = target.queryRenderedFeatures(point, {layers: ['lanes']});
//	if (oPolys.length > 0)
//	{
//		
//	}
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
	if (oFeature !== undefined)
	{
		if (getCursor() !== 'progress')
			setCursor('pointer');
		let oLayer = g_oMap.getSource('hl-pt-gjson');
		if (oLayer !== undefined)
		{
			let oData = oLayer._data;
			oData.geometry.coordinates = oFeature.oSnapInfo.aPt;
			oLayer.setData(oData);
			g_oMap.setPaintProperty('hl-pt', 'circle-opacity', 1);
			if (g_bDebug && !Number.isNaN(oData.geometry.coordinates[0]))
				g_oPopup.setLngLat(oData.geometry.coordinates).setHTML('<p>' + oFeature.oMBoxFeature.properties.sponge_id + '<p>').addTo(g_oMap);
		}
		g_sCurLane = oFeature.oMBoxFeature.properties.sponge_id;
	}
	else
	{
		g_sCurLane = undefined;
		g_oMap.setPaintProperty('hl-pt', 'circle-opacity', 0);
		if (getCursor() !== 'progress')
			setCursor('');
		if (g_bDebug)
			g_oPopup.remove();
	}		
}


function setCurCLane(sUrl, lngLat)
{
	let oLane = g_oLanes[sUrl];
	let oInfo = getSnapInfo(oLane['c'], lngLat);

	for (let nIndex = 0; nIndex < oLane['aCLanes'].length; nIndex++)
	{
		let oTemp = oLane['aCLanes'][nIndex];
		if (oInfo.nIndex >= oTemp['s'] && oInfo.nIndex < oTemp['e'])
		{
			oLane['oCurCLane'] = oTemp;
			break;
		}
	}
}


function setCLaneLimits(sUrl)
{
	let oLane = g_oLanes[sUrl];
	if (oLane['aCLanes'].length == 1)
	{
		oLane['aCLanes'][0]['sl'] = 0;
		oLane['aCLanes'][0]['el'] = oLane['c'].length - 1;
		return;
	}
	oLane['aCLanes'].sort(function(o1, o2){return o1['s'] - o2['s'];});
	
	for (let nOuter = 0; nOuter < oLane['aCLanes'].length; nOuter++)
	{
		let oOuter = oLane['aCLanes'][nOuter];
		for (let nInner = 0; nInner < oLane['aCLanes'].length; nInner++)
		{
			if (nInner === nOuter)
				continue;
			let oInner = oLane['aCLanes'][nInner];
			if (oOuter['e'] < oInner['s'])
			{
				oOuter['el'] = oInner['s'];
				break;
			}
		}
		
		let nInner = oLane['aCLanes'].length;
		while (nInner-- > 0)
		{
			if (nInner === nOuter)
				continue;
			
			let oInner = oLane['aCLanes'][nInner];
			if (oOuter['s'] > oInner['e'])
			{
				oOuter['sl'] = oInner['e'];
				break;
			}
		}
	}
}


function mouseMoveDragPoly({target, lngLat, point})
{
	let sUrl = g_sCurLane;
	if (sUrl !== undefined)
	{
		if (target.queryRenderedFeatures(point, {layers: ['hl-line']}).length == 0)
		{
			setCursor('not-allowed');
			return;
		}
		else
		{
			if (getCursor() !== 'e-resize')
				setCursor('e-resize');
		}
		let oLane = g_oLanes[sUrl];
		let oInfo = getSnapInfo(oLane['c'], lngLat);
		if (oInfo.nIndex < 0)
			return;
		let dWidth = length(oLane['a'][oInfo.nIndex][0], oLane['a'][oInfo.nIndex][1], oLane['b'][oInfo.nIndex][0], oLane['b'][oInfo.nIndex][1]);
		if (length(lngLat.lng, lngLat.lat, oLane['c'][oInfo.nIndex][0], oLane['c'][oInfo.nIndex][1]) > dWidth)
			return;
		let oCLane = oLane['oCurCLane'];
		
		if (oInfo.nIndex > oCLane['el'] || oInfo.nIndex < oCLane['sl'])
		{
			setCursor('not-allowed');
			return;
		}
		if (oInfo.nIndex > oCLane['o'])
		{
			oCLane['s'] = oCLane['o'];
			oCLane['e'] = oInfo.nIndex;
		}

		if (oInfo.nIndex < oCLane['o'])
		{
			oCLane['s'] = oInfo.nIndex;
			oCLane['e'] = oCLane['o'];
		}

		if (oCLane['s'] < oCLane['sl'])
			oCLane['s'] = oCLane['sl'];
		if (oCLane['e'] > oCLane['el'])
			oCLane['e'] = oCLane['el'];

		let oLayer = g_oMap.getSource('hl-poly-gjson');
		if (oLayer !== undefined)
		{
			let oData = oLayer._data;
			oData.geometry.coordinates = createPoly(oLane['a'], oLane['b'], oCLane['s'], oCLane['e']);
			oLayer.setData(oData);
		}
	}
}


function getClosestLineFeature(lngLat, point, aLayers, dTol)
{
	let oFeatures = g_oMap.queryRenderedFeatures(pointToPaddedBounds(point, Math.round(dTol / g_aMETERS_PER_PIXEL[Math.round(g_oMap.getZoom())])), {layers: aLayers});
	let dClosest = Number.MAX_VALUE;
	let oClosest;
	let oFeatureInfo;
	for (let oFeature of oFeatures.values())
	{
		let oInfo = getSnapInfoForFeature(oFeature, lngLat);
		if (oInfo.dDist < dClosest)
		{
			dClosest = oInfo.dDist;
			oClosest = oFeature;
			oFeatureInfo = oInfo;
		}
	}
	
	if (oClosest !== undefined)
		return {oMBoxFeature: oClosest, oSnapInfo: oFeatureInfo};
}

$(document).ready(initialize);
