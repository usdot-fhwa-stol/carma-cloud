import {MapControlIcons} from './MapControlIcons.js';
import {METERS_PER_PIXEL, getSnapInfoForFeature, fromIntDeg, createWholePoly, getSnapInfo,
	length, createPoly, lon2tile, lat2tile, getPolygonBoundingBox} from './geoutil.js';
import {carmaclStartWx} from './map-wxpoly.js';
import {carmaclStartOrigin, carmaclStartTravel} from './map-travel.js';
import {carmaclStartEdit, closeEditDialog, carmaclStartDelete, closeDeleteDialog, saveEdit, deleteControl, captureLayerState, resetAllLayers, resetTileFlag} from './map-edit.js';

let oMap;
let sCurLane;
let aCurPath = [];
let oLanes = {};
let oPopup;
let bDebug = false;
let oSources;
let oListeners = ['click', 'mousemove'];
let nMode = 0; // {0: no handlers, 1: travel mode, 2: wxpoly mode, 3: add mode, 4: edit mode, 5: delete mode}
let nCtrlZoom;
let aCtrlEnums;
let aEditLayers = ['direction', 'latperm', 'closed', 'maxspeed', 'minhdwy'];
let aDeleteLayers = ['direction', 'stop', 'yield', 'latperm', 'closed', 'maxspeed', 'minhdwy'];
let nCtrlType;
let oCtrlUnits;
let aLabelOpts = ['', 'workzone', 'incident', 'weather', 'other'];
let nOtherIndex = aLabelOpts.indexOf('other');
let aValid = [false, false];


function setCtrlVars(oCtrlInfo)
{
	nCtrlZoom = oCtrlInfo.zoom;
	aCtrlEnums = oCtrlInfo.enums;
	oCtrlUnits = oCtrlInfo.units;
}

function pointToPaddedBounds(oPoint, dPad)
{
	return [{x: oPoint.x - dPad, y: oPoint.y - dPad}, {x: oPoint.x + dPad, y: oPoint.y + dPad}];
}

function refreshVectorTiles()
{
	oSources.forEach(function(oSource)
	{
		if (oSource.mapboxSource.type === 'vector')
		{
			removeSource(oSource.id);
			addSource(oSource);
			$('#all-layers :checkbox').each(function(index, element)
			{
				setLayerOpacity(element.name, element.checked ? 1.0 : 0.0);
			});
			if (nMode === 4)
			{
				$('#edit-layers :checked').each(function(index, element) 
				{
					setLayerOpacity(element.id, 1.0);
				});
			}
			else if (nMode === 5 || nMode === 3)
			{
				$('#delete-layers :checked').each(function(index, element) 
				{
					setLayerOpacity(element.id, 1.0);
				});
			}
		}
		else
		{
			
		}
	});
	oMap.moveLayer('hl-pt');
}

function removeSource(sSource)
{
	if (oMap.getSource(sSource) !== undefined)
	{
		for (let oLayer of oMap.getStyle().layers.values())
			if (oLayer.source === sSource)
				oMap.removeLayer(oLayer.id);

		oMap.removeSource(sSource);
		
		return true;
	}
}


function addSource(oSource)
{
	oMap.addSource(oSource.id, oSource.mapboxSource);
	for (let oLayer of oSource.layers.values())
		oMap.addLayer(oLayer, 'existing-ctrls-outline');
}

function buildSourceMap(oSourceData)
{
	const oSrcs = new Map();
	Object.entries(oSourceData).forEach(([sSourceId, {source, layers}]) =>
	{
	   const aFullLayerDefs = [];
	   let sType = '';
	   let oDefaults;
	   for (let oLayer of layers.values())
	   {
		 const oFullLayer = {};
		 if (oLayer.hasOwnProperty('type') && oLayer.type !== sType)
		 {
			 sType = oLayer.type;
			 oDefaults = {};
			 for (let sKey of Object.keys(oLayer))
			 {
				oFullLayer[sKey] = oLayer[sKey];
				oDefaults[sKey] = oLayer[sKey];
			 }
		 }
		 else
		 {
			 oLayer['type'] = sType;
			 for (let sKey of Object.keys(oLayer))
			 {
				oFullLayer[sKey] = oLayer[sKey];
			 }
			 for (let sKey of Object.keys(oDefaults))
			 {
				if (!oFullLayer.hasOwnProperty(sKey))
					oFullLayer[sKey] = oDefaults[sKey];
			 }
		 }

		 oFullLayer['source'] = sSourceId;
		 if (!oFullLayer.hasOwnProperty('id'))
			 oFullLayer['id'] = oFullLayer['source-layer'];
		 aFullLayerDefs.push(oFullLayer);

		 let oFullSource;
		 if (source.type === 'vector')
		 {
			 oFullSource = {sType, layers: aFullLayerDefs, id: sSourceId, mapboxSource: Object.assign({}, source, {tiles: source.tiles})};
		 }
		 else if (source.type === 'geojson')
		 {
			 oFullSource = {sType, layers: aFullLayerDefs, id: sSourceId, mapboxSource: Object.assign({}, source)};
		 }
		 if (oFullSource)
			 oSrcs.set(sSourceId, oFullSource);
	   }
	});
	return oSrcs;
}


function toggleLayerDialog()
{
	if ($('#dlgEdit').dialog('isOpen') || $('#dlgDelete').dialog('isOpen'))
		return;
	let oDlg = $('#dlgLayers');
	if (nMode !== 0)
	{
		switchMode();
		oDlg.dialog('open');
		return;
	}
	
	if (oDlg.dialog('isOpen'))
		oDlg.dialog('close');
	else
		oDlg.dialog('open');
}


function showHideLayer()
{
	if (this.checked)
		setLayerOpacity(this.name, 1.0);
	else
		setLayerOpacity(this.name, 0.0);
}


function showHideEditLayer()
{
	setLayerOpacity(this.id, 1.0);
	$(this).parent().siblings().children('input').each(function(index, element)
	{
		setLayerOpacity(element.id, 0.0);
	});
}


function setLayerOpacity(sLayerName, dOpacity)
{
	let oLayers = oSources.get('ctrl').layers;
	for (let oLayer of oLayers)
	{
		if (oLayer.id.indexOf(sLayerName) === 0)
			oMap.setPaintProperty(oLayer.id, oLayer.type + '-opacity', dOpacity);
	}
}


function buildLayerDialog()
{
	let oDlg = $('#dlgLayers');
	oDlg.dialog({autoOpen: false, position: {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'}, resizable: false, draggable: false, width: 190});

	const oNameSet = {};
	let sHtml = '<ul id="all-layers">';
	let oLayers = oSources.get('ctrl').layers;
	for (let oLayer of oLayers)
	{
		let sName = oLayer.id;
		let nPos = sName.indexOf('-');
		if (nPos > 0) // capture first part of layer name
			sName = sName.substring(0, nPos);

		if (oNameSet[sName] === undefined) // check layer name in set
		{
			oNameSet[sName] = true;
			sHtml += '<li><input type="checkbox" name="' + sName + '"';

			if (sName.indexOf('debug') < 0) // only non-debug layers are initially on
				sHtml += ' checked';

			sHtml += '>&nbsp;<label for="' + sName + '">' + sName + '</label></li>';
		}
	}
	sHtml += '</ul>';
	sHtml += '<ul id="edit-layers">';
	for (let sName of aEditLayers.values())
	{
		sHtml += `<li><input type="radio" id="${sName}" name="edit" value="${sName}">&nbsp;<label for="${sName}">${sName}</label></li>`;
	}
	sHtml += '</ul>';
	
	sHtml += '<ul id="delete-layers">';
	for (let sName of aDeleteLayers.values())
	{
		sHtml += `<li><input type="radio" id="${sName}" name="delete" value="${sName}">&nbsp;<label for="${sName}">${sName}</label></li>`;
	}
	sHtml += '</ul>';
	
	oDlg.html(sHtml);

	$('#dlgLayers input[type="radio"]').click(showHideEditLayer);
	for (let sName in oNameSet) // set checkbox event handler
	{ 	
		let sSelector = ':checkbox[name="' + sName + '"]';
		$(sSelector).click(showHideLayer);	
	}

	setLayerOpacity('debug', 0.0); // default debug layers off
}


function buildEditDialog()
{
	let oDlg = $('#dlgEdit');
	oDlg.dialog({autoOpen: false, position: {my: 'center', at: 'center', of: '#mapid'}, resizable: false, width: 400, height: 300});
	let sHtml = '<div class="overlay" id="dlg-edit-overlay"><p class="centered-element"></p></div><div id="edit-content"></div><div class="dlg-buttons"><button id="edit-cancel" class="w3-button w3-dark-gray">Cancel</button><button id="edit-save" class="w3-button w3-dark-gray" disabled>Save</button></div>';
	oDlg.html(sHtml);
}

function buildDeleteDialog()
{
	let oDlg = $('#dlgDelete');
	oDlg.dialog({autoOpen: false, position: {my: 'center', at: 'center', of: '#mapid'}, resizable: false, width: 400, height: 250, close: closeDeleteDialog});
	let sHtml = '<div class="overlay" id="dlg-delete-overlay"><p class="centered-element"></p></div><div><p>Are you sure you want to delete this control? This cannot be undone.</p></div><div class="dlg-buttons"><button id="delete-cancel" class="w3-button w3-dark-gray">Cancel</button><button id="delete-confirm" class="w3-button w3-dark-gray">Delete</button></div>';
	oDlg.html(sHtml);
	$('#delete-cancel').on('click', closeDeleteDialog);
	$('#delete-confirm').on('click', deleteControl);
}


async function initialize()
{
	let pCtrlInfo = $.ajax(
	{
		'url': 'api/ctrl',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}
	}).promise();
	let pSourceLayers = $.getJSON('mapbox/sourcelayers.json').promise();
	oMap = new mapboxgl.Map({'container': 'mapid', 'style': 'mapbox/satellite-streets-v11.json', 'attributionControl': false,
		'minZoom': 4, 'maxZoom': 24, 'center': [-77.149, 38.956], 'zoom': 18, 'accessToken': '<your access token goes here>'});
	
	oMap.dragRotate.disable(); // disable map rotation using right click + drag
	oMap.touchZoomRotate.disableRotation(); // disable map rotation using touch rotation gesture
	oMap.addControl(new mapboxgl.NavigationControl({showCompass: false}));
	oMap.showTileBoundaries = false;

	oMap.on('load', async function()
	{
//		oMap.addControl(new MapControlIcons([{t:'Set Route', i:'setroute'}, {t:'Travel',i:'travel'}, {t:'Weather Polygon', i:'drawpoly'}]), 'top-right');
//		oMap.addControl(new MapControlIcons([{t:'Weather Polygon', i:'drawpoly'}, {t:'Tile Boundaries',i:'tilebounds'}]), 'top-right');
		oMap.addControl(new MapControlIcons([{t:'Weather Polygon', i:'drawpoly'}]), 'top-right');
		oMap.addControl(new MapControlIcons([{t:'Layer Dialog',i:'layers'}, {t:'Add Control', i:'add'}, {t:'Edit Control',i:'editpath'}, {t:'Delete Control',i:'delete'}]), 'top-right');
		oSources = buildSourceMap(await pSourceLayers);
		for (let oSrc of oSources.values())
		{
			oMap.addSource(oSrc.id, oSrc.mapboxSource);
			for (let oLayer of oSrc.layers)
				oMap.addLayer(oLayer);
		}
		oPopup = new mapboxgl.Popup({closeButton: false, closeOnClick: false, anchor: 'bottom', offset: [0, -25]});
		oPopup.setHTML('').addTo(oMap);
//		oMap.on('click', carmaclStartLanePoly);
//		oMap.on('mousemove', carmaclSnapToLane);
		buildLayerDialog();
		buildEditDialog();
		buildDeleteDialog();
//		oMap.on('click', carmaclAddPoint);
//		oMap.on('mousemove', carmaclPopupPos);
//		oMap.on('mousemove', carmaclDisplayPoint);
		setCursor('pointer');
//		$('button[title|="Tile Boundaries"]').click(toggleDebug);
		$('button[title|="Weather Polygon"]').click(carmaclStartWx);
//		$('button[title|="Set Route"]').click(carmaclStartOrigin);
//		$('button[title|="Travel"]').click(carmaclStartTravel);
		$('button[title|="Layer Dialog"]').click(toggleLayerDialog);
		$('button[title|="Add Control"]').click(carmaclStartAdd);
		$('button[title|="Edit Control"]').click(carmaclStartEdit);
		$('button[title|="Delete Control"]').click(carmaclStartDelete);
	});
	setCtrlVars(await pCtrlInfo);
}


function resetListeners()
{
	for (let sListener of oListeners.values())
	{
		if (oMap._listeners[sListener] === undefined)
			continue;
		
		let aToRemove = [];
		for (let oListener of oMap._listeners[sListener].values())
		{
			if (oListener.name.indexOf('carmacl') === 0)
				aToRemove.push(oListener);
		}
		
		for (let oRemove of aToRemove.values())
			oMap.off(sListener, oRemove);
	}
}

function switchMode()
{
	switch (nMode)
	{
		case 1:
		{
			carmaclStartOrigin();
			break;
		}
		case 2:
		{
			carmaclStartWx();
			break;
		}
		case 3:
		{
			carmaclStartAdd();
			break;
		}
		case 4:
		{
			carmaclStartEdit();
			break;
		}
		case 5:
		{
			carmaclStartDelete();
			break;
		}
	}
}

function resetMode()
{
	setMode(0);
	resetListeners();
	setCursor('');
	oPopup.setHTML('');
	oPopup.remove();
}


function toggleDebug()
{
	bDebug = !bDebug;
	oMap.showTileBoundaries = bDebug;
//	if (bDebug)
//	{
//		startMode();
//		oMap.on('mousemove', carmaclSnapToLane);
//		oMap.on('click', carmaclStartLanePoly);
//		oMap.setPaintProperty('debug-c', 'line-opacity', 1);
//		oMap.setPaintProperty('debug-p', 'circle-opacity', 0.5);
//	}
//	else
//	{
//		oMap.setPaintProperty('debug-c', 'line-opacity', 0);
//		oMap.setPaintProperty('debug-p', 'circle-opacity', 0);
//	}
}


function addCtrlSources()
{
	let oSrc = oSources.get('ctrl');
	if (oMap.getSource(oSrc.id) === undefined)
	{
		oMap.addSource(oSrc.id, oSrc.mapboxSource);
		for (let oLayer of oSrc.layers)
		{
			if (oMap.getLayer(oLayer.id) === undefined)
				oMap.addLayer(oLayer, 'hl-pt');
		}
	}
	$(':checkbox').each(function(index, element) 
	{
		setLayerOpacity(element.name, element.checked ? 1.0 : 0.0);
	});
}


function removeCtrlSources()
{
	let oSrc = oSources.get('ctrl');
	for (let oLayer of oSrc.layers)
	{
		if (oMap.getLayer(oLayer.id) !== undefined)
			oMap.removeLayer(oLayer.id);
	}

	if (oMap.getSource(oSrc.id) !== undefined)
		oMap.removeSource(oSrc.id);
}


function carmaclStartAdd()
{
	if ($('#dlgEdit').dialog('isOpen') || $('#dlgDelete').dialog('isOpen'))
		return;
	if (nMode !== 0 && nMode !== 3)
		switchMode();
	if (nMode === 3)
	{
		resetMode();
		$('#delete-layers input[type="radio"]').off('click', setType);
		$('#delete-layers').hide();
		$('#all-layers').show();
		$('#dlgLayers').dialog('option', 'position', {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'});
		$('#edit-save').off('click', addControl);
		$('#edit-cancel').off('click', cancelAdd);
		$('#dlgLayers').dialog('option', 'title', 'Show/Hide');
		oMap.setPaintProperty('debug-c', 'line-opacity', 0.0);
		oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
		oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
		resetAllLayers();
		$('#dlgEdit').siblings('.ui-dialog-titlebar').children('button').off('click', cancelAdd);
		return;
	}
	resetMode();
	setMode(3);
	$('#all-layers').hide();
	$('#delete-layers input[type="radio"]').prop('checked', false).on('click', setType);
	nCtrlType = -1;
	$('#delete-layers').show();
	$('#dlgLayers').dialog('option', 'position', {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'});
	oPopup.setHTML('<p>Select control type to add to<br>Click "Layer Dialog" to exit mode</p>').addTo(oMap);
	$('#dlgLayers').dialog('option', 'title', 'Control Types');
	$('#dlgLayers').dialog('open');
	oPopup.addTo(oMap);
	oMap.on('mousemove', carmaclPopupPos);
	captureLayerState();
	oMap.setPaintProperty('debug-c', 'line-opacity', 1);
	oMap.setPaintProperty('hl-pt', 'circle-opacity', 0);
//	oMap.setPaintProperty('debug-p', 'circle-opacity', 0.5);
	$('#edit-save').on('click', addControl);
	$('#edit-cancel').on('click', cancelAdd);
	$('#dlgEdit').siblings('.ui-dialog-titlebar').children('button').on('click', cancelAdd);
}


function setType()
{
	if (nCtrlType === -1)
	{
		oMap.on('mousemove', carmaclSnapToLane);
		oMap.on('click', carmaclStartLanePoly);
	}
	for (let nIndex = 0; nIndex < aCtrlEnums.length; nIndex++)
	{
		if (this.id === aCtrlEnums[nIndex][0])
		{
			nCtrlType = nIndex;
			break
		}
	}
	oPopup.setHTML('<p>Select start point of control<br>Point will snap to existing lane geometry<br>Click "Layer Dialog" to exit mode</p>');
}


function carmaclPopupPos({target, lngLat, point})
{
	oPopup.setLngLat(lngLat);
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
		let nCount = 0;
		for (let nIndex = 2; nIndex < oLine.length; nIndex += 2)
		{
			oLine[nIndex] += oLine[nIndex - 2];
			oLine[nIndex + 1] += oLine[nIndex - 1];
			if (nCount++ % 2 === 0)
				aCoords.push([fromIntDeg(oLine[nIndex]), fromIntDeg(oLine[nIndex + 1])]);
		}
	}

	let nFirst = this.url.indexOf('/', 1) + 1;
	let sUrl = this.url.substring(this.url.indexOf('/', nFirst));
	oLanes[sUrl] = oCoords;
	updateCurrent(sUrl);
	createPolyStart(sUrl, this.startPoint);
	setCurCLane(sUrl, this.startPoint);
}

function updateCurrent(sUrl)
{
	if (sUrl !== sCurLane)
		return;

	oMap.setPaintProperty('hl-pt', 'circle-opacity', 0);
	let oLayer = oMap.getSource('hl-line-gjson');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.geometry.coordinates = createWholePoly(oLanes[sUrl]['a'], oLanes[sUrl]['b']);
		oLayer.setData(oData);
		oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 1);
	}
}


function createPolyStart(sUrl, oLngLat)
{
	setCursor('e-resize');
	oMap.on('click', carmaclEndLanePoly);
	oMap.on('mousemove', carmaclUpdateLanePoly);
	let oLane = oLanes[sUrl];
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
	oLane['aCLanes'] = [];
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

	let oSrc = oMap.getSource('hl-poly-gjson');
	if (oSrc !== undefined)
	{
		let oData = oSrc._data;
		oData.geometry.coordinates = createPoly(aA, aB, nStart, nEnd);
		oSrc.setData(oData);
		oMap.setPaintProperty('hl-poly', 'fill-opacity', 1);
	}
}


function carmaclStartLanePoly({target, lngLat, point})
{
	let sUrl = sCurLane;
	if (sUrl !== undefined)
	{
		oMap.off('click', carmaclStartLanePoly);
		oMap.off('mousemove', carmaclSnapToLane);

		if (oLanes[sUrl] === undefined)
		{
			oLanes[sUrl] = 'processing';
			setCursor('progress');
			$.ajax('/api/geolanes' + sUrl,
					{
						dataType: 'json',
						startPoint: lngLat
					}).done(geoSuccess).always(function() {if (getCursor() === 'progress') setCursor('');});
		}
		else if (oLanes[sUrl] !== 'processing')
		{
			updateCurrent(sUrl);
			createPolyStart(sUrl, lngLat);
			setCurCLane(sUrl, lngLat);
		}
		oPopup.setHTML('<p>Click to finish control geometry<br>Click "Layer Dialog" to exit mode</p>');
	}
}


function carmaclEndLanePoly({target, lngLat, point})
{
//	switchListener('mousemove', carmaclUpdateLanePoly, carmaclSnapToLane);
//	switchListener('click', carmaclEndLanePoly, carmaclStartLanePoly);
	oMap.off('mousemove', carmaclUpdateLanePoly);
	oMap.off('click', carmaclEndLanePoly);
	$('#delete-layers input[type="radio"]').prop('disabled', true);
	oPopup.remove();
	let sHtml = '<form id="edit-form"><table>';
	let sType = aCtrlEnums[nCtrlType][0];
	if (sType === 'yield' || sType === 'stop')
	{
		sHtml += `<p style="margin: 1% 3% 1% 3%;">No value to set for this control</p>`;
	}
	else if (aCtrlEnums[nCtrlType].length === 1) // not an enumerated type
	{
		sHtml += `<tr><td>value</td><td><input id="edit-input" name="value">${oCtrlUnits[nCtrlType] ? '&nbsp;' + oCtrlUnits[nCtrlType] : ''}</td></tr>`;
	}
	else
	{
		let nValues = nCtrlType === 9 ? 2 : 1; // lat perm has two selects
		for (let nSelectIndex = 0; nSelectIndex < nValues; nSelectIndex++)
		{
			let sOptions = '';
			for (let nIndex = 1; nIndex < aCtrlEnums[nCtrlType].length; nIndex++)
			{
				sOptions += `<option value="${nIndex}">${aCtrlEnums[nCtrlType][nIndex]}</option>`;
			}
			let sHeading = '';
			if (nValues ===  2)
			{
				if (nSelectIndex === 0)
					sHeading = 'outer edge';
				else
					sHeading = 'inner edge';
			}
			else
			{
				sHeading = 'Select control value';
			}
			sHtml += `<tr><td>${sHeading}</td><td><select id="${nSelectIndex === 0 ? 'edit-select1' : 'edit-select2'}" name="${nSelectIndex === 0 ? 'value1' : 'value2'}">${sOptions}</select></td></tr>`;
		}
	}

	sHtml += `<tr><td><label for="edit-regulatory">Regulatory</label></td><td><input id="edit-regulatory" type="checkbox" name="reg" checked></td></tr>`
	let sOptions = '';
	for (let nIndex = 0; nIndex < aLabelOpts.length; nIndex++)
		sOptions += `<option value="${nIndex}">${aLabelOpts[nIndex]}</option>`;
	sHtml += `<tr><td><label for="edit-label">Label</label></td><td><select id="edit-label">${sOptions}</select></td></tr>`;
	sHtml += '<tr><td></td><td><input style="display: none;" type="text" id="edit-label-text" name="label" maxlength="63"></td></tr>';
	sHtml += '</table></form>';
	$('#edit-content').html(sHtml);
	$('#edit-label').on('change', function() 
	{
		let sText = aLabelOpts[this.value];
		if (sText === 'other')
		{
			$('#edit-label-text').show().val('').on('input', checkOther).on('input', checkOtherLength);
			$('#edit-save').prop('disabled', true);
		}
		else
		{
			$('#edit-label-text').hide().val(sText).off('input', checkOther).off('input', checkOtherLength);
			$('#edit-save').prop('disabled', false);
		}
	});
	if (document.getElementById('edit-input'))
	{
		$('#edit-input').on('input', function() 
		{
			aValid[0] = $(this).val().length !== 0;
			$('#edit-save').prop('disabled', !checkValid());
		});
		$('#edit-save').prop('disabled', true);
	}
	else
	{
		$('#edit-save').prop('disabled', false);
		aValid[0] = true;
	}
	aValid[1] = true;
	$('#dlgEdit').dialog('option', 'title', `Confirm Adding ${aCtrlEnums[nCtrlType][0]} Control`);
	$('#dlgEdit').dialog('open');
	document.activeElement.blur();
	
//	oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
//	oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
//	let oLayer = oMap.getSource('created-lanes');
//	let sUrl = sCurLane;
//	let oLane = oLanes[sUrl];

//	if (oLayer !== undefined)
//	{
//		let oData = oLayer._data;
//		oData.features.push({'type': 'Feature', 'properties': {'url': sUrl}, 'geometry': {'type': 'Polygon', 'coordinates': createPoly(oLane['a'], oLane['b'], oLane['oCurCLane']['s'], oLane['oCurCLane']['e'])}});
//		let sDetail = '';
//		let aA = oLane['a'];
//		let aB = oLane['b'];
//		let nStart = oLane['oCurCLane']['s'];
//		let nEnd = oLane['oCurCLane']['e'];
//		for (let nIndex = nStart; nIndex <= nEnd; nIndex++)
//		{
//			sDetail += aA[nIndex][0].toFixed(7) + ',' + aA[nIndex][1].toFixed(7) + ',' + aB[nIndex][0].toFixed(7) + ',' + aB[nIndex][1].toFixed(7) + ',';
//		}
//		$('#geoDetail').html(sDetail);
//		oLayer.setData(oData);
//		oMap.setPaintProperty('lanes', 'fill-opacity', 1);
//	}
	setCursor('');
}


function checkOther()
{
	if ($('#edit-label-text').val() === 'other')
	{
		$('#edit-label-text').val('');
		aValid[1] = false;
	}
	
	$('#edit-save').prop('disabled', !checkValid());
}

function checkValid()
{
	for (let bBool of aValid.values())
	{
		if (!bBool)
			return false;
	}
	
	return true;
}


function checkOtherLength()
{
	aValid[1] = $('#edit-label-text').val().length !== 0;
	$('#edit-save').prop('disabled', !checkValid());
}

function addControl()
{
	$('#dlg-edit-overlay').show();
	$('#dlg-edit-overlay > p').html('Saving...');
	let oData = {'token': sessionStorage.token, 'type': nCtrlType, 'id': sCurLane, 's': oLanes[sCurLane].oCurCLane.s, 'e': oLanes[sCurLane].oCurCLane.e};
	for (let oKeyVal of $('#edit-form').serializeArray().values())
		oData[oKeyVal.name] = oKeyVal.value;
	$.ajax(
	{
		'url': 'api/ctrl/add',
		'method': 'POST',
		'data': oData
	}).done(doneAddControl).fail(failAddControl);
}

function cancelAdd()
{
	oMap.on('click', carmaclStartLanePoly);
	oMap.on('mousemove', carmaclSnapToLane);
	$('#delete-layers input[type="radio"]').prop('disabled', false);
	oPopup.setHTML('<p>Select start point of control<br>Point will snap to existing lane geometry<br>Click "Layer Dialog" to exit mode</p>').addTo(oMap);
	oMap.setPaintProperty('hl-pt', 'circle-opacity', 1);
	oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
	oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
	closeEditDialog();
}

function doneAddControl()
{
	resetTileFlag(true);
	refreshVectorTiles();
	oMap.on('click', carmaclStartLanePoly);
	oMap.on('mousemove', carmaclSnapToLane);
	$('#delete-layers input[type="radio"]').prop('disabled', false);
	oPopup.setHTML('<p>Select start point of control<br>Point will snap to existing lane geometry<br>Click "Layer Dialog" to exit mode</p>').addTo(oMap);
	oMap.setPaintProperty('debug-c', 'line-opacity', 1);
	oMap.setPaintProperty('hl-pt', 'circle-opacity', 1);
	oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
	oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
	$('#dlg-edit-overlay > p').html('Successfully added control.');
	setTimeout(function()
	{
		$('#dlg-edit-overlay').hide();
		closeEditDialog();
	}, 1500);
}

function failAddControl()
{
	$('#dlg-edit-overlay > p').html('Unsuccessfully added control. Try again later.');
	oMap.setPaintProperty('dummy-path-outline', 'line-opacity', 0);
	oMap.setPaintProperty('hl-poly', 'fill-opacity', 0);
	setTimeout(function()
	{
		$('#dlg-edit-overlay').hide();
		closeEditDialog();
	}, 1500);
}


function switchListener(sType, fnFrom, fnTo)
{
	oMap.off(sType, fnFrom);
	oMap.on(sType, fnTo);
}


function setCursor(sStyle)
{
	oMap.getCanvas().style.cursor = sStyle;
}


function getCursor()
{
	return oMap.getCanvas().style.cursor;
}


function carmaclSnapToLane({target, lngLat, point})
{
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 4);
	if (oFeature !== undefined)
	{
		if (getCursor() !== 'progress')
			setCursor('pointer');
		let oLayer = oMap.getSource('hl-pt-gjson');
		if (oLayer !== undefined)
		{
			let oData = oLayer._data;
			oData.geometry.coordinates = oFeature.oSnapInfo.aPt;
			oLayer.setData(oData);
			oMap.setPaintProperty('hl-pt', 'circle-opacity', 1);
//			if (bDebug && !Number.isNaN(oData.geometry.coordinates[0]))
//				oPopup.setLngLat(oData.geometry.coordinates).setHTML('<p>' + oFeature.oMBoxFeature.properties.sponge_id + '<p>').addTo(oMap);
		}
		sCurLane = oFeature.oMBoxFeature.properties.sponge_id;
	}
	else
	{
		sCurLane = undefined;
		oMap.setPaintProperty('hl-pt', 'circle-opacity', 0);
		if (getCursor() !== 'progress')
			setCursor('');
		if (bDebug)
			oPopup.remove();
	}
}


function setCurCLane(sUrl, lngLat)
{
	let oLane = oLanes[sUrl];
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
	let oLane = oLanes[sUrl];
	if (oLane['aCLanes'].length === 1)
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
	let sUrl = sCurLane;
	if (sUrl !== undefined)
	{
		if (target.queryRenderedFeatures(point, {layers: ['hl-line']}).length === 0)
		{
			setCursor('not-allowed');
			return;
		}
		else
		{
			if (getCursor() !== 'e-resize')
				setCursor('e-resize');
		}
		let oLane = oLanes[sUrl];
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

		let oLayer = oMap.getSource('hl-poly-gjson');
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
	let oFeatures = oMap.queryRenderedFeatures(pointToPaddedBounds(point, Math.round(dTol / METERS_PER_PIXEL[Math.round(oMap.getZoom())])), {layers: aLayers});
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
	oPopup.setHTML(lngLat.lng.toFixed(7) + ',' + lngLat.lat.toFixed(7));
}

function carmaclAddPoint({target, lngLat, point})
{
	$('#geoDetail').append(lngLat.lng.toFixed(7) + ',' + lngLat.lat.toFixed(7) + '<br/>');
}

function setMode(nVal)
{
	nMode = nVal;
}

$(document).on('initPage', initialize);

export {oMap, oPopup, switchListener, setCursor, resetMode, getClosestLineFeature,
	carmaclPopupPos, addCtrlSources, removeCtrlSources, nMode, setMode, aCtrlEnums,
	nCtrlZoom, refreshVectorTiles, switchMode, oCtrlUnits, aLabelOpts, nOtherIndex};
