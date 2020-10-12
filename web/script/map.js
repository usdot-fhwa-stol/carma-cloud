var g_oMap;
var g_oLayerDialog;
var g_sCurLane;
var g_aCurPath = [];
var g_oLanes = {};
const g_oSourceLayers = $.getJSON('mapbox/sourcelayers.json').promise();
var g_oPopup;
var g_bDebug = false;
var g_oSources;
var g_oListeners = ['click', 'mousemove']
var g_nIntId;
var g_oPts = {ori: [undefined, undefined], dest: [undefined, undefined], veh: [undefined, undefined]};
var g_oVeh = {aCoord: [undefined, undefined], nSpd: 55, aPlan: [], nIndex: undefined, oCurFeature: undefined, oDestFeature: undefined, bPlanning: false, nPanCount: 0};
var g_oCenters = {};

function pointToPaddedBounds(oPoint, dPad)
{
	return [{x: oPoint.x - dPad, y: oPoint.y - dPad}, {x: oPoint.x + dPad, y: oPoint.y + dPad}];
}

class SimControl
{
  onAdd(map)
  {
    this._map = map;
    this._container = document.createElement('div');
    this._container.className = 'mapboxgl-ctrl mapboxgl-ctrl-group six-button-control';

    this._container.innerHTML = [{t:'Set Route', i:'setroute'}, {t:'Travel',i:'travel'}, {t:'Layer Dialog',i:'layers'}]
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

    this._container.innerHTML = [{t:'Weather Polygon', i:'drawpoly'}, {t:'Tile Boundaries',i:'tilebounds'}]
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


function toggleLayerDialog()
{
	if (g_oLayerDialog.dialog("isOpen"))
		g_oLayerDialog.dialog("close");
	else
		g_oLayerDialog.dialog("open");
}


function showHideLayer()
{
	if (this.checked)
		setLayerOpacity(this.name, 1.0);
	else
		setLayerOpacity(this.name, 0.0);
}


function setLayerOpacity(sLayerName, dOpacity)
{
	let oLayers = g_oSources.get("ctrl").layers;
	for (let oLayer of oLayers)
        {
		if (oLayer.id.indexOf(sLayerName) === 0)
			g_oMap.setPaintProperty(oLayer.id, oLayer.type + '-opacity', dOpacity);
	}
}


function buildLayerDialog()
{
	g_oLayerDialog = $("#dlgLayers");
	g_oLayerDialog.dialog({autoOpen: false, position: {my: "right bottom", at: "right-8 bottom-8", of: "#mapid"}, resizable: false, width: 190});

	const oNameSet = {};
	let sHtml = "<ul style='list-style-type: none'>";
	let oLayers = g_oSources.get("ctrl").layers;
	for (let oLayer of oLayers)
	{
		let sName = oLayer.id;
		let nPos = sName.indexOf("-");
		if (nPos > 0) // capture first part of layer name
			sName = sName.substring(0, nPos);

		if (oNameSet[sName] === undefined) // check layer name in set
		{
			oNameSet[sName] = true;
			sHtml += "<li><input type='checkbox' name='" + sName + "'";

			if (sName.indexOf("debug") < 0) // only non-debug layers are initially on
				sHtml += " checked"

			sHtml += ">&nbsp;<label for='" + sName + "'>" + sName + "</label></li>";
		}
	}
	sHtml += "</ul>";
	g_oLayerDialog.html(sHtml);

	for (let sName in oNameSet) // set checkbox event handler
	{ 	
		let sSelector = ":checkbox[name='" + sName + "']";
		$(sSelector).click(showHideLayer);	
	}

	setLayerOpacity("debug", 0.0); // default debug layers off
}


async function initialize()
{
	g_oMap = new mapboxgl.Map({"container": "mapid", "style": "mapbox/satellite-streets-v11.json", "attributionControl": false,
		"minZoom": 4, "maxZoom": 24, "center": [-77.149, 38.956], "zoom": 18, "accessToken": "<your access token goes here>"});

//	g_oMap = new mapboxgl.Map({"container": "mapid", "style": "mapbox/streets-v11.json", "attributionControl": false,
//			"minZoom": 14, "maxZoom": 22, "center": [-76.8552198, 38.7474584], "zoom": 17});
//			"minZoom": 14, "maxZoom": 22, "center": [-77.20083, 38.9479027-77.149, 38.956], "zoom": 17});

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
		g_oPopup = new mapboxgl.Popup({closeButton: false, closeOnClick: false, anchor: 'bottom', offset: [0, -25]});
		g_oPopup.setHTML('').addTo(g_oMap);
//		g_oMap.on('click', carmaclStartLanePoly);
//		g_oMap.on('mousemove', carmaclSnapToLane);
		buildLayerDialog();
//		g_oMap.on('click', carmaclAddPoint);
//		g_oMap.on('mousemove', carmaclPopupPos);
//		g_oMap.on('mousemove', carmaclDisplayPoint);
		setCursor('pointer');
	});
	g_oMap.addControl(new SimControl(), 'top-right');
	g_oMap.addControl(new DebugControl(), 'top-right');

	$("button[title|='Tile Boundaries']").click(toggleDebug);
	$("button[title|='Weather Polygon']").click(carmaclStartWx);
	$("button[title|='Set Route']").click(carmaclStartOrigin);
	$("button[title|='Travel']").click(carmaclStartTravel);
	$("button[title|='Layer Dialog']").click(toggleLayerDialog);
}


function resetListeners()
{
	for (let sListener of g_oListeners.values())
	{
		if (g_oMap._listeners[sListener] === undefined)
			continue;
		
		let aToRemove = [];
		for (let oListener of g_oMap._listeners[sListener].values())
		{
			if (oListener.name.indexOf('carmacl') == 0)
				aToRemove.push(oListener);
		}
		
		for (let oRemove of aToRemove.values())
			g_oMap.off(sListener, oRemove);
	}
}


function startMode()
{
	resetListeners();
	setCursor('');
	g_oPopup.setHTML('');
	g_oPopup.remove();
}


function endMode()
{
	resetListeners();
	setCursor('');
	g_oPopup.setHTML('');
	g_oPopup.remove();
}


function ptOn(sLayer)
{
	g_oMap.setPaintProperty(sLayer, 'circle-opacity', 1);
	g_oMap.setPaintProperty(sLayer, 'circle-stroke-opacity', 1);
}


function ptOff(sLayer)
{
	g_oMap.setPaintProperty(sLayer, 'circle-opacity', 0);
	g_oMap.setPaintProperty(sLayer, 'circle-stroke-opacity', 0);
}


function setPt(oPt, sSource, dX, dY)
{
	oPt[0] = dX;
	oPt[1] = dY;
	let oLayer = g_oMap.getSource(sSource);
	let oData = oLayer._data;
	oData.geometry.coordinates = [dX, dY];
	oLayer.setData(oData);
}

function carmaclStartTravel()
{
	if (g_nIntId === undefined)
	{
		if (g_oMap.getPaintProperty('origin-pt', 'circle-opacity') == 0) //|| g_oMap.getPaintProperty('dest-pt', 'circle-opacity') == 0)
		{
			alert('Set origin before travel');
			return;
		}
		setPt(g_oVeh.aCoord, 'veh-gjson', g_oPts.ori[0], g_oPts.ori[1]);
		ptOn('veh-pt');
		g_nIntId = setInterval(travel, 100);
	}
	else
	{
		clearInterval(g_nIntId);
		ptOff('veh-pt');
		g_nIntId = undefined;
	}
}


function travel()
{
	let aCoord = g_oVeh.aPlan.shift();
	if (aCoord === undefined)
	{
		console.log('no plan');
		if (g_nIntId !== undefined)
			clearInterval(g_nIntId);
		return;
	}
	
	
	setPt(g_oVeh.aCoord, 'veh-gjson', aCoord[0], aCoord[1]);
	if (g_oVeh.nPanCount++ > 10)
	{
		g_oMap.panTo(aCoord);
		g_oVeh.nPanCount = 0;
	}
	
	if (g_oVeh.aPlan.length < 10 && !g_oVeh.bPlanning)
	{
		g_oVeh.bPlanning = true;
		console.log('planning');
		createPlan(g_oVeh.aPlan);
		console.log('done planning');
		g_oVeh.bPlanning = false;
	}
}


function carmaclStartOrigin()
{
	ptOff('dest-pt');
	ptOff('origin-pt');
	if (hasListener('click', 'carmaclSetOrigin') || hasListener('click', 'carmaclSetDest'))
	{
		endMode();
		return;
	}
	startMode();
	ptOff('dest-pt');
	ptOff('origin-pt');
	setCursor('crosshair');
	g_oMap.on('click', carmaclSetOrigin);
	g_oMap.on('mousemove', carmaclPopupPos);
	g_oMap.on('mousemove', carmaclUpdateOrigin);
	g_oPopup.setHTML('Left-click<br>Start Point').addTo(g_oMap);
}


function carmaclUpdateOrigin({target, lngLat, point})
{
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
	if (oFeature !== undefined)
	{
		setCursor('pointer');
		setPt(g_oPts.ori, 'origin-gjson', oFeature.oSnapInfo.aPt[0], oFeature.oSnapInfo.aPt[1]);
		ptOn('origin-pt');
	}
	else
	{
		setCursor('crosshair');
		ptOff('origin-pt');
	}
}


function carmaclSetOrigin({target, lngLat, point})
{
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
	if (oFeature !== undefined)
	{
		g_oMap.off('mousemove', carmaclUpdateOrigin);
		g_oMap.off('click', carmaclSetOrigin);
		let aPt = oFeature.oSnapInfo.aPt;
		g_oVeh.sCurLane = oFeature.oMBoxFeature.properties.sponge_id;
		g_oVeh.nIndex = oFeature.oSnapInfo.nIndex;
		g_oVeh.oCurFeature = oFeature.oMBoxFeature;
		setPt(g_oPts.ori, 'origin-gjson', aPt[0], aPt[1]);
		ptOn('origin-pt');
//		switchListener('mousemove', carmaclUpdateOrigin, carmaclUpdateDest);
//		switchListener('click', carmaclSetOrigin, carmaclSetDest);
		createPlan([]);
		endMode();
	}
	else
	{
		setCursor('crosshair');
		ptOff('origin-pt');
	}
}


function getPlanBox(oPt, dHdg, dLength, dWidth)
{
	let nZoom = 19;
	let dPixelWidth = dWidth / g_aMETERS_PER_PIXEL[nZoom];
	let dPixelLength = dLength / g_aMETERS_PER_PIXEL[nZoom];
	let dNT = dHdg - Math.PI / 2;
	let dPT = dHdg + Math.PI / 2;
	let aPt1 = [oPt.x + Math.cos(dPT) * dPixelWidth, oPt.y - Math.sin(dPT) * dPixelWidth]; // cos(x + pi/2 = -sin(x) // sin(x + pi/2) = cos(x)
	let aPt2 = [aPt1[0] + Math.cos(dHdg) * dPixelLength, aPt1[1] - Math.sin(dHdg) * dPixelLength];
	let aPt3 = [aPt2[0] + Math.cos(dNT) * dPixelWidth * 2, aPt2[1] - Math.sin(dNT) * dPixelWidth * 2];  // cos(x - pi/2) = sin(x) // sin(x - pi/2) = -cos(x)
	let aPt4 = [oPt.x + Math.cos(dNT) * dPixelWidth, oPt.y +- Math.sin(dNT) * dPixelWidth];
	let oPixels = [aPt1, aPt2, aPt3, aPt4];
	let oSw = [Number.MAX_VALUE, -Number.MAX_VALUE];
	let oNe = [-Number.MAX_VALUE, Number.MAX_VALUE];
	for (let oPixel of oPixels.values())
	{
		if (oPixel[0] < oSw[0])
			oSw[0] = oPixel[0];
		if (oPixel[0] > oNe[0])
			oNe[0] = oPixel[0];
		if (oPixel[1] > oSw[1])
			oSw[1] = oPixel[1];
		if (oPixel[1] < oNe[1])
			oNe[1] = oPixel[1];
	}
	
	
//	let aCoords = [g_oMap.unproject(oSw), g_oMap.unproject([oSw[0], oNe[1]]), g_oMap.unproject(oNe), g_oMap.unproject([oNe[0], oSw[1]]), g_oMap.unproject(oSw)];
////	let aCoords = [g_oMap.unproject(aPt1), g_oMap.unproject(aPt2), g_oMap.unproject(aPt3), g_oMap.unproject(aPt4), g_oMap.unproject(aPt1)];
//	let aLngLats = [];
//	for (let oLngLat of aCoords.values())
//		aLngLats.push([oLngLat.lng, oLngLat.lat]);
//	let oPoly = g_oMap.getSource('weather-polygon');
//	let oData = oPoly._data;
//	oData.geometry.coordinates = [aLngLats];
//	oPoly.setData(oData);
//	g_oMap.setPaintProperty('w-poly', 'fill-opacity', 0.5);
	
	return [oSw, oNe];
}


function createPlan(aPlan)
{
	let aCoords = g_oVeh.oCurFeature.geometry.coordinates;

//	let nIncrease = Math.round(g_oVeh.nSpd * 0.745);
	let nIncrease = Math.round(g_oVeh.nSpd * 1.49);
	g_oVeh.nIndex += nIncrease;
	while (g_oVeh.nIndex >= aCoords.length)
	{
		let oRet = getNextFeature(aCoords, nIncrease, aPlan);
		if (!oRet[0])
		{
			if (oRet[1] !== undefined)
				aPlan = oRet[1];
			break;
		}
	}
	
	aCoords = g_oVeh.oCurFeature.geometry.coordinates;
	if (g_oVeh.nIndex >= aCoords.length)
		return;
	for (;g_oVeh.nIndex < aCoords.length && aPlan.length < 50; g_oVeh.nIndex += nIncrease)
	{
		let aCoord = aCoords[g_oVeh.nIndex];
		aPlan.push(aCoord);
		if (g_oMap.queryRenderedFeatures(g_oMap.project(aCoord), {layers: ['w-poly']}).length > 0)
		{
			g_oVeh.nSpd = 35;
			nIncrease = Math.round(g_oVeh.nSpd * 1.49);
		}
		else
		{
			g_oVeh.nSpd = 55;
			nIncrease = Math.round(g_oVeh.nSpd * 1.49);
		}
	}
	
	g_oVeh.nIndex -= nIncrease;
	
	if (aPlan.length < 50 || g_oVeh.nIndex >= aCoords.length)
	{
		let oRet = getNextFeature(aCoords, nIncrease, aPlan);
		if (oRet[0])
			createPlan(aPlan);
		else if (oRet[1] !== undefined)
			aPlan = oRet[1];
	}
	
	g_oVeh.aPlan = aPlan;
}


function getNextFeature(aCoords, nIncrease, aPlan)
{
	console.log('getting next');
	let nLast = aCoords.length - 1;
	nStart = nLast - 20;
	if (nStart < 0)
		nStart = 0;

	let dHdg = headingA(aCoords[nStart], aCoords[nLast]);
	let aPlanBox = getPlanBox(g_oMap.project(aCoords[nLast]), dHdg, 3, 3);
	let oClosures = g_oMap.queryRenderedFeatures(aPlanBox, {layers: ['closed-outline', 'closing-outline']});
	if (oClosures.length > 0)
	{
		let nZoom = 19;
		let dPixelOffset = 3 / g_aMETERS_PER_PIXEL[nZoom];
		let nPlanSize = aPlan.length;
		let aCurPt = aPlan[0];
		let aLastPt = aPlan[nPlanSize - 1];
		let aPixelPt = g_oMap.project(aLastPt);
		let aShiftPoint = {x: aPixelPt.x + dPixelOffset * Math.cos(dHdg + Math.PI / 2), y: aPixelPt.y - dPixelOffset * Math.sin(dHdg + Math.PI / 2)};
		let oFeature = getClosestLineFeature(g_oMap.unproject(aShiftPoint), aShiftPoint, ['debug-c'], 6);
		aLastPt = oFeature.oMBoxFeature.geometry.coordinates[oFeature.oSnapInfo.nIndex];
		let dDistStep = length(aCurPt[0], aCurPt[1], aLastPt[0], aLastPt[1]) / nPlanSize;
		let dPlanHdg = headingA(aCurPt, aLastPt);
		let dXStep = Math.cos(dPlanHdg) * dDistStep;
		let dYStep = Math.sin(dPlanHdg) * dDistStep;
		let aNewPlan = [aCurPt];
		for (let i = 1 ; i < nPlanSize; i++)
		{
			aNewPlan.push([aCurPt[0] + dXStep * i, aCurPt[1] + dYStep * i]);
		}
		g_oVeh.nIndex = oFeature.oSnapInfo.nIndex;
		g_oVeh.oCurFeature = oFeature.oMBoxFeature;
		return [false, aNewPlan];
		
	}
	let oFeatures = g_oMap.queryRenderedFeatures(aPlanBox, {layers: ['debug-c']});

	let aLastPt = aCoords[nLast];
	let oNextFeat = undefined;
	let nNextIndex = undefined;
	for (let oFeature of oFeatures.values())
	{
		if (oFeature.properties.sponge_id === g_oVeh.oCurFeature.properties.sponge_id && oFeature.geometry.coordinates[0][0] === aCoords[0][0]) // skip current feature
			continue;

		let aNewCoords = oFeature.geometry.coordinates;
		let nLimit = Math.min(500, aNewCoords.length);
		for (let i = 0; i < nLimit; i++)
		{
			let dDist = length(aNewCoords[i][0], aNewCoords[i][1], aLastPt[0], aLastPt[1]);
			if (dDist < 0.000001)
			{
				let nExtra = g_oVeh.nIndex + nIncrease - aCoords.length;
				nNextIndex = i + nExtra;
				console.log(i);
				oNextFeat = oFeature;

				break;
			}
		}
		if (oNextFeat !== undefined)
			break;
	}

	if (oNextFeat !== undefined)
	{
		g_oVeh.oCurFeature = oNextFeat;
		g_oVeh.nIndex = nNextIndex;
		return [true];
//		createPlan(aPlan);
	}
	
	else
		return [false, undefined];
}


function carmaclUpdateDest({target, lngLat, point})
{
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
	if (oFeature !== undefined)
	{
		setCursor('pointer');
		setPt(g_oPts.dest, 'dest-gjson', oFeature.oSnapInfo.aPt[0], oFeature.oSnapInfo.aPt[1]);
		ptOn('dest-pt');
	}
	else
	{
		setCursor('crosshair');
		ptOff('dest-pt');
	}
}


function carmaclSetDest({target, lngLat, point})
{
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
	if (oFeature !== undefined)
	{
		g_oMap.off('mousemove', carmaclUpdateDest);
		g_oMap.off('mousemove', carmaclPopupPos);
		g_oMap.off('click', carmaclSetDest);
		g_oVeh.oDestFeature = oFeature.oMBoxFeature;
		setPt(g_oPts.dest, 'dest-gjson', oFeature.oSnapInfo.aPt[0], oFeature.oSnapInfo.aPt[1]);
		ptOn('dest-pt');
		createPlan([]);
		endMode();
	}
	else
	{
		setCursor('crosshair');
		ptOff('dest-pt');
	}
}


function toggleDebug()
{
	g_bDebug = !g_bDebug;
	g_oMap.showTileBoundaries = g_bDebug;
//	if (g_bDebug)
//	{
//		startMode();
//		g_oMap.on('mousemove', carmaclSnapToLane);
//		g_oMap.on('click', carmaclStartLanePoly);
//		g_oMap.setPaintProperty('debug-c', 'line-opacity', 1);
//		g_oMap.setPaintProperty('debug-p', 'circle-opacity', 0.5);
//	}
//	else
//	{
//		g_oMap.setPaintProperty('debug-c', 'line-opacity', 0);
//		g_oMap.setPaintProperty('debug-p', 'circle-opacity', 0);
//	}
}


function addCtrlSources()
{
	let oSrc = g_oSources.get('ctrl');
	if (g_oMap.getSource(oSrc.id) === undefined)
	{
		g_oMap.addSource(oSrc.id, oSrc.mapboxSource);
		for (let oLayer of oSrc.layers)
		{
			if (g_oMap.getLayer(oLayer.id) === undefined)
				g_oMap.addLayer(oLayer, 'hl-pt');
		}
	}
	$(':checkbox').each(function(index, element) 
	{
		setLayerOpacity(element.name, element.checked ? 1.0 : 0.0);
	});
}


function removeCtrlSources()
{
	let oSrc = g_oSources.get('ctrl');
	for (let oLayer of oSrc.layers)
	{
		if (g_oMap.getLayer(oLayer.id) !== undefined)
			g_oMap.removeLayer(oLayer.id);
	}

	if (g_oMap.getSource(oSrc.id) !== undefined)
		g_oMap.removeSource(oSrc.id);
}


function carmaclStartWx()
{
	if (hasListener('click', 'carmaclFirstWx') || hasListener('click', 'carmaclEndWx'))
	{
		endMode();
		g_oMap.setPaintProperty('w-line', 'line-opacity', 0);
		g_oMap.setPaintProperty('w-poly', 'fill-opacity', 0);
		return;
	}
	startMode();
	setCursor('crosshair');
	g_oMap.on('click', carmaclFirstWx);
	g_oMap.on('mousemove', carmaclPopupPos);
	g_oPopup.setHTML('Left-click<br>Top left corner').addTo(g_oMap);
}


function carmaclPopupPos({target, lngLat, point})
{
	g_oPopup.setLngLat(lngLat);
}


function carmaclFirstWx({target, lngLat, point})
{
	let oLayer = g_oMap.getSource('weather-line');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.geometry.coordinates = [[lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat]];
		oLayer.setData(oData);
		g_oMap.setPaintProperty('w-poly', 'fill-opacity', 0);
		g_oMap.setPaintProperty('w-line', 'line-opacity', 1);
		setCursor('nwse-resize');
		g_oMap.on('mousemove', carmaclUpdateWx);
		switchListener('click', carmaclFirstWx, carmaclEndWx);
		g_oPopup.setHTML('Left-click<br>Bottom right corner')
	}
}


function carmaclUpdateWx({target, lngLat, point})
{
	let oLayer = g_oMap.getSource('weather-line');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		let oTL = oData.geometry.coordinates[0];
		oData.geometry.coordinates[1] = [lngLat.lng, oTL[1]];
		oData.geometry.coordinates[2] = [lngLat.lng, lngLat.lat];
		oData.geometry.coordinates[3] = [oTL[0], lngLat.lat]
		oLayer.setData(oData);
	}
}


function carmaclEndWx({target, lngLat, point})
{
	let oWeatherLine = g_oMap.getSource('weather-line');
	if (oWeatherLine !== undefined)
	{
		let oWeatherPoly = g_oMap.getSource('weather-polygon');
		{
			let aRing = [];
			for (let aPt of oWeatherLine._data.geometry.coordinates.values())
				aRing.push(aPt);

			let oData = oWeatherPoly._data;
			oData.geometry.coordinates = [aRing];
			oWeatherPoly.setData(oData);
			
			g_oMap.setPaintProperty('w-poly', 'fill-opacity', 0.5);
			g_oMap.setPaintProperty('w-line', 'line-opacity', 0);
			endMode();
			$.ajax('/api/wxpoly',
				{
					data: aRing[3][0].toFixed(7) + ',' + aRing[3][1].toFixed(7)+ ',' + aRing[1][0].toFixed(7) + ',' + aRing[1][1].toFixed(7),
					type: 'post',
				}).done(weatherpolySuccess);
		}
	}
}


function weatherpolySuccess(oData, sStatus, oJqXHR)
{
	removeCtrlSources();
	addCtrlSources();
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
	g_oMap.on('click', carmaclEndLanePoly);
	g_oMap.on('mousemove', carmaclUpdateLanePoly);
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


function carmaclStartLanePoly({target, lngLat, point})
{
	let sUrl = g_sCurLane;
	if (sUrl !== undefined)
	{
		g_oMap.off('click', carmaclStartLanePoly);
		g_oMap.off('mousemove', carmaclSnapToLane);

		if (g_oLanes[sUrl] === undefined)
		{
			g_oLanes[sUrl] = 'processing';
			setCursor('progress');
			$.ajax('/api/geolanes' + sUrl,
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


function carmaclEndLanePoly({target, lngLat, point})
{
	switchListener('mousemove', carmaclUpdateLanePoly, carmaclSnapToLane);
	switchListener('click', carmaclEndLanePoly, carmaclStartLanePoly);
	g_oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
	g_oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
	let oLayer = g_oMap.getSource('created-lanes');
	let sUrl = g_sCurLane;
	let oLane = g_oLanes[sUrl];

	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.features.push({'type': 'Feature', 'properties': {'url': sUrl}, 'geometry': {'type': 'Polygon', 'coordinates': createPoly(oLane['a'], oLane['b'], oLane['oCurCLane']['s'], oLane['oCurCLane']['e'])}});
		let sDetail = '';
		let aA = oLane['a'];
		let aB = oLane['b'];
		let nStart = oLane['oCurCLane']['s'];
		let nEnd = oLane['oCurCLane']['e'];
		for (let nIndex = nStart; nIndex <= nEnd; nIndex++)
		{
			sDetail += aA[nIndex][0].toFixed(7) + ',' + aA[nIndex][1].toFixed(7) + ',' + aB[nIndex][0].toFixed(7) + ',' + aB[nIndex][1].toFixed(7) + ',';
		}
		$('#geoDetail').html(sDetail);
		oLayer.setData(oData);
		g_oMap.setPaintProperty('lanes', 'fill-opacity', 1);
	}
	setCursor('');
}


function hasListener(sType, sListener)
{
	if (g_oMap._listeners[sType] === undefined)
		return false;
	
	for (let oListener of g_oMap._listeners[sType].values())
	{
		if (oListener.name === sListener)
			return true;
	}
	
	return false;
}


function switchListener(sType, fnFrom, fnTo)
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


function carmaclSnapToLane({target, lngLat, point})
{
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


function carmaclUpdateLanePoly({target, lngLat, point})
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
		if (oInfo.dDist < dClosest && !Number.isNaN(oInfo.aPt[0]))
		{
			dClosest = oInfo.dDist;
			oClosest = oFeature;
			oFeatureInfo = oInfo;
		}
	}

	if (oClosest !== undefined)
		return {oMBoxFeature: oClosest, oSnapInfo: oFeatureInfo};
}

function carmaclDisplayPoint({target, lngLat, point})
{
	g_oPopup.setHTML(lngLat.lng.toFixed(7) + ',' + lngLat.lat.toFixed(7));
}

function carmaclAddPoint({target, lngLat, point})
{
	$("#geoDetail").append(lngLat.lng.toFixed(7) + ',' + lngLat.lat.toFixed(7) + '<br/>');
}

function polyListeners()
{
	g_oMap.on('click', carmaclStartLanePoly);
	g_oMap.on('mousemove', carmaclSnapToLane);
	setCursor('');
	g_oPopup.remove();
	g_oMap.off('click', carmaclAddPoint);
	g_oMap.off('mousemove', carmaclPopupPos);
	g_oMap.off('mousemove', carmaclDisplayPoint);
}


function pointListeners()
{
	g_oMap.on('click', carmaclAddPoint);
	g_oMap.on('mousemove', carmaclPopupPos);
	g_oMap.on('mousemove', carmaclDisplayPoint);
	setCursor('pointer');
	g_oPopup.addTo(g_oMap);
	g_oMap.off('click', carmaclStartLanePoly);
	g_oMap.off('mousemove', carmaclSnapToLane);
}

$(document).on("initPage", initialize);
