let PARAMETER_KEYS = ['space_factor', 'ratio_reduced_speed', 'consecutive_time_interval_trigger', 'time_interval', 'maximal_gap', 'baseline_flow'];
let ERROR = 0;
let INVALIDBOUNDS = 1;
let READY = 2;
let RUNNING = 3;
let nStatusId;
let oMap;
let oCurState = {'drawing': false};
let oPopup;
let nLastSegment = -1;
let sActiveMonth;
let sActiveDay;
let bStop = false;

async function initialize()
{
	pollStatus();
	$.ajax(
	{
		'url': 'api/ihp/getParameters',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}
	}).done(function(data)
	{
		for (let sKey of PARAMETER_KEYS.values())
			$('#' + sKey).val(data[sKey]);
	}).fail(function()
	{
		alert('Failed to retrieve default parameters');
	});
		
	
	oMap = new mapboxgl.Map({'container': 'map_container', 'style': 'mapbox/satellite-streets-v11.json', 'attributionControl': false,
		'minZoom': 4, 'maxZoom': 24, 'center': [-81.83067386, 28.1195796], 'zoom': 15, 'accessToken': 'pk.eyJ1Ijoia3J1ZWdlcmIiLCJhIjoiY2tuajlwYWZ5MGI0ZTJ1cGV1bTk5emtsaCJ9.En7O3cNsbmy7Gk555ZjmVQ'});
	window.mymap = oMap;
	oMap.doubleClickZoom.disable();
	oMap.dragRotate.disable(); // disable map rotation using right click + drag
	oMap.touchZoomRotate.disableRotation(); // disable map rotation using touch rotation gesture
	oMap.addControl(new mapboxgl.NavigationControl({showCompass: false}));
	oMap.showTileBoundaries = false;
	
	oMap.on('load', async function() 
	{
		oMap.addSource('center_line', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}, 'generateId': true});
		oMap.addLayer({'id': 'center_line', 'type': 'line', 'source': 'center_line', 'paint': {'line-width': 3, 'line-color': '#000000', 'line-opacity': 1}});
		oMap.addSource('all_points', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}, 'generateId': true});
		oMap.addLayer({'id': 'all_points', 'type': 'circle', 'source': 'all_points', 'paint': {'circle-radius': ['case', ['boolean', ['get', 'inner'], false], 4, ['boolean', ['feature-state', 'small'], false], 4, 6], 'circle-color': ['case', ['boolean', ['==', ['feature-state', 'color'], null], true], '#ff00ff', ['feature-state', 'color']], 'circle-opacity': ['case', ['boolean', ['==', ['feature-state', 'opacity'], null], true], 1, ['feature-state', 'opacity']]}});
		oMap.addSource('corridor', {'type': 'geojson', 'data': {'type': 'FeatureCollection', 'features': []}, 'generateId': true});
		oMap.addLayer({'id': 'corridor', 'type': 'line', 'source': 'corridor', 'layout':{'line-cap':'round', 'line-join':'round'}, 'paint': {'line-width': 3, 'line-color': '#000000', 'line-opacity': 1}});
		oMap.addSource('corridor_poly', {'type': 'geojson', 'data':{'type': 'FeatureCollection', 'features': []}, 'generateId': true});
		oMap.addLayer({'id': 'corridor_poly', 'type': 'fill', 'source': 'corridor_poly', 'paint': {'fill-color': ['case', ['boolean', ['==', ['feature-state', 'color'], null], true], '#808080', ['feature-state', 'color']], 'fill-opacity': ['case', ['boolean', ['==', ['feature-state', 'opacity'], null], true], 0.6, ['feature-state', 'opacity']]}});
		oPopup = new mapboxgl.Popup({closeButton: false, closeOnClick: false, anchor: 'bottom', offset: [0, -25]});
		
		oMap.on('mousemove', updatePopupPos);
		oMap.on('mousemove', labelSubsegment);
		getGeometry();
	});
	
	$('#save_parameters').on('click', saveParameters);
	$('#start_sim').on('click', startSim);
	$('#stop_sim').on('click', stopSim);
	$('#new_corridor').on('click', checkNewCorridor);
	$('#save_corridor').prop('disabled', true).on('click', saveCorridor);
	$('#undo_corridor').prop('disabled', true).on('click', undoEndPoint);
	$('#help_corridor').on('click', toggleHelp);
	$('#update_detectors').on('click', updateDetectors);
	$('#upload_detectors').on('click', uploadDetectors);
	
	buildInstructionDialog();
	buildConfirmationDialog();
	buildStartDialog();
	buildFilesDialog();
	$('#download_logs').on('click', function()
	{
		$('#dlgFiles').dialog('open');
	});
	
	let oCan = document.createElement('canvas');
	oCan.width = 24;
	oCan.height = 24;
	
	let ctx = oCan.getContext('2d');
	ctx.fillStyle = '#179b54';
	ctx.font = '24px "Font Awesome 5 Free"';
	ctx.textAlign = 'center';
	ctx.textBaseline = 'middle';
	ctx.fillText('\uf067', 12, 12);
	addStyleRule('.pluscursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
	
	ctx.clearRect(0, 0, oCan.width, oCan.height);
	ctx.fillStyle = '#b2000c';
	ctx.fillText('\uf068', 12, 12);
	addStyleRule('.minuscursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
	
	ctx.clearRect(0, 0, oCan.width, oCan.height);
	ctx.fillStyle = '#66cdaa';
	ctx.fillText('\uf058', 12, 12);
	addStyleRule('.checkcursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
	
	ctx.clearRect(0, 0, oCan.width, oCan.height);
	ctx.fillStyle = '#39ff14';
	ctx.fillText('\uf0b2', 12, 12);
	addStyleRule('.movecursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
	
	ctx.clearRect(0, 0, oCan.width, oCan.height);
	ctx.fillStyle = '#000000';
	ctx.fillText('\uf044', 12, 12);
	addStyleRule('.editcursor', `cursor: url('${oCan.toDataURL('image/png')}') 12 12, auto`);
	
	$('input').on('focusout', addLeadingZero);
}

function stopSim()
{
	clearTimeout(nStatusId);
	$.ajax(
	{
		'url': 'api/ihp/stop',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}
	}).always(function ()
	{
		pollStatus();
	});
}

function startSim()
{
	let aDetectors = $('#detectors_container  table  input').map(function() {return this.id;}).get();
	if (aDetectors.length === 0)
	{
		showPageoverlay('Click "New" to use the map interface to create a corridor');
		$('#start_sim').prop('disabled', true);
		timeoutPageoverlay();
		return;
	}
	clearTimeout(nStatusId);
	$.ajax(
	{
		'url': 'api/ihp/start',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}
	}).always(function ()
	{
		pollStatus();
	});;
}

function saveParameters()
{
	let oData = {'token': sessionStorage.token};
	if (checkDoubleInputs(PARAMETER_KEYS))
	{
		for (let sKey of PARAMETER_KEYS.values())
			oData[sKey] = $('#' + sKey).val();
	}
	else
		return;

	showPageoverlay('Saving parameters...');
	$.ajax(
	{
		'url': 'api/ihp/saveParameters',
		'method': 'POST',
		'dataType': 'json',
		'data': oData
	}).done(function()
	{
		showPageoverlay('Parameters saved');
	}).fail(function(jqXHR)
	{
		showPageoverlay(`Failed to save parameters<br>${jqXHR.responseJSON.error}`);
	}).always(function()
	{
		timeoutPageoverlay();
	});
}


function pollStatus()
{
	$.ajax(
	{
		'url': 'api/ihp/status',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}		
	}).done(function(oData)
	{
		if (oData.status === INVALIDBOUNDS)
		{
			showPageoverlay('Invalid subsegment definitions... upload a valid bounds file and refresh page');
			return;
		}
		if (oData.status === ERROR)
		{
			showPageoverlay('Service unavailable... Try again later');
			return;
		}
		
		disableEntities(oData.status);
		if (oData.users > 1)
		{
			showPageoverlay('Another user is using the system. Try again later.');
			bStop = true;
			return;
		}
		$('#sim_status').html(oData.desc);
		fillInFiles(oData.files);
		$('#sim_ctrls').html(oData.ctrls_made !== undefined ? oData.ctrls_made : 'N/A');
		$('#sim_last').html(oData.last_run !== undefined && oData.last_run > 0? moment(oData.last_run).format('h:mm:ss a') : 'N/A');
		$('#sim_next').html(oData.next_run !== undefined && oData.next_run > 0 ? moment(oData.next_run).format('h:mm:ss a') : 'N/A');
		nStatusId = setTimeout(pollStatus, 10000);
	}).fail(function()
	{
		
	});
	
}

function fillInFiles(oFiles)
{
//	`<div id="month_dirs">
//<h3>day 1</h3>
//<div id="days"><h4>1</h4><div><ul><li>hi</li><li>hi</li></ul></div><h4>2</h4><div><ul><li>hi</li><li>hi</li></ul></div><h4>3</h4><div><ul><li>hi</li><li>hi</li></ul></div></div></div>`;
	if ($('#dlgFiles').dialog('isOpen'))
		return;
	
	$('#month_dirs').empty();
	$('#month_dirs').accordion('destroy');
	for (let sMonth of Object.keys(oFiles).sort().reverse().values())
	{
		let oDays = oFiles[sMonth];
		let oMonth = $(`<h3>${sMonth}</h3>`);
		let oDaysDiv = $(`<div class="day"></div>`);
		for (let sDay of Object.keys(oDays).sort().reverse().values())
		{
			let aFiles = oDays[sDay];
			let oDay = $(`<h4>${sDay}</h4>`);
			let oFilesDiv = $('<div></div>');
			let oFilesUl = $('<ul class="w3-ul"></ul>');
			for (let sFile of aFiles.values())
			{
				oFilesUl.append($(`<li id=${sFile} class="w3-hover-blue">${sFile}</li>`).on('click', {'file': sFile}, getFile));
			}
			oFilesDiv.append(oFilesUl);
			oDaysDiv.append(oDay).append(oFilesDiv);
		}
		$('#month_dirs').append(oMonth).append(oDaysDiv);
	}
	$('#month_dirs').accordion({collapsible: true, heightStyle: 'content', active: false, 
		activate: function(event, ui)
	{
		if (ui.newHeader[0])
			sActiveMonth = ui.newHeader[0].innerText;
	}});
	$('.day').accordion({collapsible: true, heightStyle: 'content', active: false, 
		activate: function(event, ui)
	{
		if (ui.newHeader[0])
			sActiveDay = ui.newHeader[0].innerText;
	}});
	$('#month_dirs').accordion('refresh');
}


function getFile(oEvent)
{
	showPageoverlay(`Downloading ${oEvent.data.file}`);
	$.ajax(
	{
		'url': 'api/ihp/getFile',
		'method': 'POST',
		'dateType': 'json',
		'data': {'token': sessionStorage.token, 'file': `${sActiveMonth}/${sActiveDay}/${oEvent.data.file}`}
	}).done(function(oData)
	{
		timeoutPageoverlay(1);
		let oEl = document.createElement('a');
		oEl.setAttribute('href','data:text/plain;charset=utf-8,' + encodeURIComponent(oData.csv));
		oEl.setAttribute('download', oEvent.data.file);
		document.body.appendChild(oEl);
		oEl.click();
		document.body.removeChild(oEl);
		$('#month_dirs').accordion('option', 'active', false);
		$('.days').accordion('option', 'active', false);
		$('#dlgFiles').dialog('close');
	}).fail(function()
	{
		showPageoverlay(`Failed downloading ${oEvent.data.file}`);
		timeoutPageoverlay();
		
	}).always(function()
	{
		$('#month_dirs').accordion('option', 'active', false);
		$('.days').accordion('option', 'active', false);
		$('#dlgFiles').dialog('close');
	});
}


function disableEntities(nStatus)
{
	if (nStatus === READY)
	{
		$('#save_parameters').prop('disabled', false);
		$('#start_sim').prop('disabled', false);
		$('#stop_sim').prop('disabled', true);
		$('#new_corridor').prop('disabled', false);
		for (let sKey of PARAMETER_KEYS.values())
			$('#' + sKey).prop('disabled', false);
	}
	
	if (nStatus === RUNNING)
	{
		$('#save_parameters').prop('disabled', true);
		$('#start_sim').prop('disabled', true);
		$('#stop_sim').prop('disabled', false);
		$('#new_corridor').prop('disabled', true);
		for (let sKey of PARAMETER_KEYS.values())
			$('#' + sKey).prop('disabled', true);
	}
	
	if (oCurState.drawing)
	{
		$('#start_sim').prop('disabled', true);
		$('#new_corridor').prop('disabled', true);
	}
}


function getGeometry()
{
	showPageoverlay('Loading corridor geometry');
	$.ajax(
	{
		'url': 'api/ihp/getGeo',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}
	}).done(function(oResData)
	{
		showPageoverlay('Successfully loaded corridor geometry');
		let oSrc = oMap.getSource('corridor');
		let oData = oSrc._data;
		let oPolySrc = oMap.getSource('corridor_poly');
		let oPolyData = oPolySrc._data;
		oData.features = oResData.features;
		let nCount = 0;
		
		for (let oFeature of oData.features.values())
			oPolyData.features.push({'type': 'Feature', 'properties': {'sub': nCount++}, 'geometry': {'type': 'Polygon', 'coordinates': [oFeature.geometry.coordinates]}});

		oSrc.setData(oData);
		oPolySrc.setData(oPolyData);
		
		createDetectorList();
	}).fail(function()
	{
		showPageoverlay('Failed loading corridor geometry');
	}).always(function() 
	{
		timeoutPageoverlay();
	});
	
}


function newCorridor()
{
	oMap.off('mousemove', updateEndPoint);
	oMap.off('click', updateEndPointClick);
	oMap.off('mousemove', updateInnerPoint);
	oMap.off('click', updateInnerPointClick);
	let oDialog = $('#dlgInstructions');
	updateInstructions('Left-click the start of the center line of the corridor');
	if (!oDialog.dialog('isOpen'))
		oDialog.dialog('open');
	
	$(oMap.getCanvas()).addClass('pointercursor');
	
	$('#detectors_container table').remove();
	let oCorrSrc = oMap.getSource('corridor');
	let oCorrData = oCorrSrc._data;
	oCorrData.features = [];
	oCorrSrc.setData(oCorrData);
	let oPolySrc = oMap.getSource('corridor_poly');
	let oPolyData = oPolySrc._data;
	oPolyData.features = [];
	oPolySrc.setData(oPolyData);
	let oSrc = oMap.getSource('all_points');
	let oData = oSrc._data;
	oData.features = [];
	oData.features.push({'type': 'Feature', 'id': 0, 'geometry': {'type': 'Point', 'coordinates': [0, 0]}});
	oSrc.setData(oData);
	oCurState = {'endpoints': [[0, 0]], 'index': 0, 'allpoints': [], 'last': [0,0], 'add': false, 'continue': false, 'end': false, 'undo': [], 'drawing': true};
	oMap.on('mousemove', updateEndPoint);
	oMap.on('click', updateEndPointClick);
	$('#undo_corridor').prop('disabled', true);
	$('#save_corridor').prop('disabled', true);
	$('#new_corridor').prop('disabled', true);
	$('#start_sim').prop('disabled', true);
	$('#update_detectors').prop('disabled', true);
}


function checkNewCorridor()
{
	let oSrc = oMap.getSource('all_points');
	let oData = oSrc._data;
	if (oData.features.length === 0)
		$('#dlgStart').dialog('open');
	else
		$('#dlgConfirm').dialog('open');
}
	


function updateEndPoint(oEvent)
{
	let oSrc = oMap.getSource('all_points');
	let oData = oSrc._data;
	if (oCurState.index < 4 || oCurState.add)
	{
		oCurState.last = oEvent.lngLat;
		oData.features[oCurState.index].geometry.coordinates = [oEvent.lngLat.lng, oEvent.lngLat.lat];
		oSrc.setData(oData);
	}
	else 
	{
		if (distanceBetweenPoints(oMap.project(oCurState.last), oEvent.point) < 10)
		{
			$(oMap.getCanvas()).addClass('pluscursor');
			oCurState.continue = true;
		}
		else if (distanceBetweenPoints(oMap.project(oData.features[0].geometry.coordinates), oEvent.point) < 10)
		{
			$(oMap.getCanvas()).addClass('checkcursor');
			oCurState.end = true;
		}
		else
		{
			$(oMap.getCanvas()).removeClass('pluscursor checkcursor');
			oCurState.continue = false;
			oCurState.end = false;
		}
	}
}


function updateEndPointClick(oEvent)
{
	let oSrc = oMap.getSource('all_points');
	let oData = oSrc._data;
	if (oCurState.index < 4 || oCurState.add)
	{
		++oCurState.index;
		$('#undo_corridor').prop('disabled', false);
		if (oCurState.index < 4)
		{
			oData.features.push({'type': 'Feature', 'id': oCurState.index, 'geometry': {'type': 'Point', 'coordinates': oCurState.last}});
			updateInstructions('Left-click the end point of the center line of the current subsegment');
		}
		else
		{
			updateInstructions('Left-click the last point to add another subsegment<br>Left-click the first point to edit inner points of the subsegments');
			$(oMap.getCanvas()).removeClass('pointercursor pluscursor');
			oCurState.add = false;
			$(oMap.getCanvas()).addClass('pluscursor');
			oCurState.continue = true;
		}
	}
	else if (oCurState.continue)
	{
		oData.features.push({'type': 'Feature', 'id': oCurState.index, 'geometry': {'type': 'Point', 'coordinates': oCurState.last}});
		updateInstructions('Left-click the end point of the center line of the current subsegment');
		oCurState.continue = false;
		oCurState.add = true;
		$(oMap.getCanvas()).removeClass('pluscursor').addClass('pointercursor');
	}
	else if (oCurState.end)
	{
		updateInstructions('Left-click an inner point to adjust its geometry.');
		$(oMap.getCanvas()).removeClass('checkcursor');
		oCurState.allpoints = [];
		oCurState.endpoints = [];
		oCurState.addons = [];
		for (let nIndex = 0; nIndex < oData.features.length - 1; nIndex++)
		{
			let aP1 = oData.features[nIndex].geometry.coordinates;
			let aP2 = oData.features[nIndex + 1].geometry.coordinates;
			let aMid = [(aP1[0] + aP2[0]) / 2, (aP1[1] + aP2[1]) / 2];
			oCurState.endpoints.push([aP1, aP2]);
			oCurState.allpoints.push([aP1, aMid, aP2]);
			oCurState.addons.push([]);
		}
		
		let oLineSrc = oMap.getSource('center_line');
		let oLineData = oLineSrc._data;
		let nCount = 0;
		for (let aPts of oCurState.allpoints.values())
		{
			oLineData.features.push({'type': 'Feature', 'id': nCount, 'geometry': {'type': 'LineString', 'coordinates': aPts}});
			oData.features.push({'type': 'Feature', 'id': oCurState.index + nCount, 'properties': {'inner': true, 'index': 1}, 'geometry':{'type': 'Point', 'coordinates': aPts[1]}});
			++nCount;
		}
		oLineSrc.setData(oLineData);
		oSrc.setData(oData);
		
		for (let oFeature of oSrc._data.features.values())
		{
			if (oFeature.id >= oCurState.index)
				oMap.setFeatureState({'source': 'all_points', 'id': oFeature.id}, {'opacity': 0.7});
		}
		
		oMap.off('mousemove', updateEndPoint);
		oMap.off('click', updateEndPointClick);
		
		oMap.on('mousemove', updateInnerPoint);
		oMap.on('click', updateInnerPointClick);
		
		$('#save_corridor').prop('disabled', false);
		$('#undo_corridor').off('click', undoEndPoint).on('click', undoInnerPoint);
	}
}


function undoEndPoint()
{
	--oCurState.index;
	if (oCurState.index === 0)
	{
		newCorridor();
		return;
	}
	
	let oSrc = oMap.getSource('all_points');
	let oData = oSrc._data;
	oData.features.pop();
	oCurState.last = oData.features[oData.features.length - 1].geometry.coordinates;
	if (oCurState.index < 3)
		oData.features[oData.features.length - 1].geometry.coordinates = oMap.unproject([0,0]);
	
	if (oCurState.add)
	{
		updateInstructions('Left-click the last point to add another subsegment<br>Left-click the first point to edit inner points of the subsegments');
		$(oMap.getCanvas()).removeClass('pointercursor');
		oCurState.add = false;
		++oCurState.index;
	}
	else if (oCurState.index === 3)
	{
		oData.features.push({'type': 'Feature', 'id': oCurState.index, 'geometry': {'type': 'Point', 'coordinates': oCurState.last}});
		updateInstructions('Left-click the end point of the center line of the current subsegment');
		$(oMap.getCanvas()).addClass('pointercursor');
	}

	oSrc.setData(oData);
}


function updateInnerPoint(oEvent)
{
	if (oCurState.moving)
	{
		let oPointSrc = oMap.getSource('all_points');
		let oPointData = oPointSrc._data;
		
		let oLineSrc = oMap.getSource('center_line');
		let oLineData = oLineSrc._data;
		
		oPointData.features[oCurState.point.id].geometry.coordinates = [oEvent.lngLat.lng, oEvent.lngLat.lat];
		oLineData.features[oCurState.line.id].geometry.coordinates[oCurState.point.properties.index] = [oEvent.lngLat.lng, oEvent.lngLat.lat];
		
		oPointSrc.setData(oPointData);
		oLineSrc.setData(oLineData);
		
	}
	else
	{
		let oPoint = oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point), {'layers': ['all_points']})[0];
		if (!oPoint || !oPoint.properties.inner)
		{
			$(oMap.getCanvas()).removeClass('movecursor');
			oCurState.point = oCurState.line = undefined;
			return;
		}
		let oLine = oMap.queryRenderedFeatures(pointToPaddedBounds(oMap.project(oPoint.geometry.coordinates)), {'layers': ['center_line']})[0];
		if (!oLine)
		{
			$(oMap.getCanvas()).removeClass('movecursor');
			oCurState.point = oCurState.line = undefined;
			return;
		}
		
		oCurState.point = oPoint;
		oCurState.line = oLine;
		$(oMap.getCanvas()).addClass('movecursor');
	}
}


function updateInnerPointClick(oEvent)
{
	if (oCurState.moving)
	{
		$('#save_corridor').prop('disabled', false);
		oCurState.moving = false;
		let oLineSrc = oMap.getSource('center_line');
		let oLineData = oLineSrc._data;
		let oCoords = oLineData.features[oCurState.line.id].geometry.coordinates;
		let aCur = oCoords[oCurState.point.properties.index];
		let aBefore = oCoords[oCurState.point.properties.index - 1];
		let aAfter = oCoords[oCurState.point.properties.index + 1];
		let aNewAfter = [(aCur[0] + aAfter[0]) / 2, (aCur[1] + aAfter[1]) / 2];
		let aNewBefore = [(aCur[0] + aBefore[0]) / 2, (aCur[1] + aBefore[1]) / 2];
		oCoords.splice(oCurState.point.properties.index + 1, 0, aNewAfter);
		oCoords.splice(oCurState.point.properties.index, 0, aNewBefore);
		
		oLineSrc.setData(oLineData);
		
		let oPointSrc = oMap.getSource('all_points');
		let oPointData = oPointSrc._data;
		oPointData.features.push({'type': 'Feature', 'id': oPointData.features.length, 'properties': {'inner': true, 'index': oCurState.point.properties.index + 2}, 'geometry':{'type': 'Point', 'coordinates': aNewAfter}});
		oPointData.features.push({'type': 'Feature', 'id': oPointData.features.length, 'properties': {'inner': true, 'index': oCurState.point.properties.index}, 'geometry':{'type': 'Point', 'coordinates': aNewBefore}});
		oPointData.features[oCurState.point.id].properties.inner = false;
		
		for (let nId of oCurState.addons[oCurState.line.id].values())
		{
			let oPt = oPointData.features[nId];
			if (oPt.properties.index > oCurState.point.properties.index)
				oPt.properties.index += 2;
		}
		++oPointData.features[oCurState.point.id].properties.index;
		oPointSrc.setData(oPointData);
		
		oMap.setFeatureState({'source': 'all_points', 'id': oCurState.point.id}, {'opacity': 1, 'color': '#39ff14', 'small': true});
		oMap.setFeatureState({'source': 'all_points', 'id': oPointData.features.length - 2}, {'opactiry': 0.7});
		oMap.setFeatureState({'source': 'all_points', 'id': oPointData.features.length - 1}, {'opactiry': 0.7});
		oCurState.addons[oCurState.line.id].push(oPointData.features.length - 1);
		oCurState.addons[oCurState.line.id].push(oPointData.features.length - 2);
		
		oCurState.undo.push({'point': oCurState.point, 'line': oCurState.line});
		
		
		$(oMap.getCanvas()).removeClass('pointercursor');
		oCurState.point = oCurState.line = undefined;
	}
	else
	{
		if (oCurState.point && oCurState.line)
		{
			$('#save_corridor').prop('disabled', true);
			oCurState.moving = true;
			$(oMap.getCanvas()).removeClass('movecursor').addClass('pointercursor');;
		}
	}
}


function undoInnerPoint()
{
	if (oCurState.moving)
	{
		oCurState.moving = false;
		let oPointSrc = oMap.getSource('all_points');
		let oPointData = oPointSrc._data;
		let oLineSrc = oMap.getSource('center_line');
		let oLineData = oLineSrc._data;
		let aBefore = oLineData.features[oCurState.line.id].geometry.coordinates[oCurState.point.properties.index - 1];
		let aAfter = oLineData.features[oCurState.line.id].geometry.coordinates[oCurState.point.properties.index + 1];
		let aMid = [(aBefore[0] + aAfter[0]) / 2, (aBefore[1] + aAfter[1]) / 2];
		oLineData.features[oCurState.line.id].geometry.coordinates[oCurState.point.properties.index] = aMid;
		oPointData.features[oCurState.point.id].geometry.coordinates = aMid;
		oPointSrc.setData(oPointData);
		oLineSrc.setData(oLineData);
		$(oMap.getCanvas()).removeClass('pointercursor');
	}
	else
	{
		let oUndo = oCurState.undo.pop();
		let oLineSrc = oMap.getSource('center_line');
		let oLineData = oLineSrc._data;
		let oPointSrc = oMap.getSource('all_points');
		let oPointData = oPointSrc._data;
		if (oUndo === undefined)
		{
			oMap.off('mousemove', updateInnerPoint);
			oMap.off('click', updateInnerPointClick);
			
			oMap.on('mousemove', updateEndPoint);
			oMap.on('click', updateEndPointClick);

			$('#undo_corridor').on('click', undoEndPoint).off('click', undoInnerPoint);
			$('#save_corridor').prop('disabled', true);
			updateInstructions('Left-click the last point to add another subsegment<br>Left-click the first point to edit inner points of the subsegments');
			let nLimit = oPointData.features.length;
			for (let nIndex = oCurState.index; nIndex < nLimit; nIndex++)
				oPointData.features.pop();
			oPointSrc.setData(oPointData);
			oLineData.features = [];
			oLineSrc.setData(oLineData);
			return;
		}
		
		
		let aBefore = oLineData.features[oUndo.line.id].geometry.coordinates[oPointData.features[oUndo.point.id].properties.index - 2];
		let aAfter = oLineData.features[oUndo.line.id].geometry.coordinates[oPointData.features[oUndo.point.id].properties.index + 2];
		let aMid = [(aBefore[0] + aAfter[0]) / 2, (aBefore[1] + aAfter[1]) / 2];
		oLineData.features[oUndo.line.id].geometry.coordinates[oPointData.features[oUndo.point.id].properties.index] = aMid;
		oPointData.features[oUndo.point.id].geometry.coordinates = aMid;
		oPointData.features[oUndo.point.id].properties.inner = true;
		
		oMap.setFeatureState({'source': 'all_points', 'id': oUndo.point.id}, {'opacity': 0.7, 'color': null, 'small': null});
		for (let nRemoveId of [oCurState.addons[oUndo.line.id].pop(), oCurState.addons[oUndo.line.id].pop()].values())
		{
			let oRemove = oPointData.features[nRemoveId];
			for (let nId of oCurState.addons[oUndo.line.id].values())
			{
				let oPt = oPointData.features[nId];
				if (oPt.properties.index > oRemove.properties.index && nId !== oUndo.point.id)
					--oPt.properties.index;
			}
			
			oLineData.features[oUndo.line.id].geometry.coordinates.splice(oRemove.properties.index, 1);
		}
		
		--oPointData.features[oUndo.point.id].properties.index;
		oPointData.features.pop();
		oPointData.features.pop();
		oPointSrc.setData(oPointData);
		oLineSrc.setData(oLineData);
	}
}


function saveCorridor()
{
	showPageoverlay('Saving corridor');
	oMap.off('mousemove', updateEndPoint);
	oMap.off('click', updateEndPointClick);
	oMap.off('mousemove', updateInnerPoint);
	oMap.off('click', updateInnerPointClick);
	$('#undo_corridor').prop('disabled', true);
	$('#save_corridor').prop('disabled', true);
	$('#new_corridor').prop('disabled', false);
	$('#start_sim').prop('disabled', false);
	$('#update_detectors').prop('disabled', false);
	oCurState.drawing = false;
	$.ajax(
	{
		'url': 'api/ihp/saveGeo',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token, 'geo': JSON.stringify(oMap.getSource('center_line')._data.features), 'lanes': $('#lanes_txt').val(), 'width': $('#lanes_width_txt').val()}
	}).done(function()
	{
		showPageoverlay('Corridor saved');
		let oSrc = oMap.getSource('all_points');
		let oData = oSrc._data;
		oData.features = [];
		oSrc.setData(oData);
		window.setTimeout(function()
		{
			$('#pageoverlay').html('').hide();
			getGeometry();
		}, 1500);
	}).fail(function(jqXHR)
	{
		showPageoverlay(`Failed to save corridor<br>${jqXHR.responseJSON.error}`);
		timeoutPageoverlay();
	});
}


function toggleHelp()
{
	let oDialog = $('#dlgInstructions');
	if (oDialog.dialog('isOpen'))
		oDialog.dialog('close');
	else
	{
		oDialog.dialog('open');
		document.activeElement.blur();
	}
}

function buildInstructionDialog()
{
	let oDialog = $('#dlgInstructions');
	oDialog.dialog({autoOpen: false, position: {my: "left top", at: "left+8 top+8", of: "#map_container"}, draggable: true, resizable: false, width: 'auto'});
	
	oDialog.dialog('option', 'title', 'Instructions');
	let sHtml = '<p id="instructions">Left-click "New" to create a new corridor</p><p id="instructions-status" style="color: #108010"></p><p id="instructions-error" style="color: #d00010"></p>';
	
	oDialog.html(sHtml);
	oDialog.dialog('close');
	document.activeElement.blur();
}


function buildConfirmationDialog()
{
	let oDialog = $('#dlgConfirm');
	oDialog.dialog(
	{
		autoOpen: false, 
		position: {my: "middle", at: "middle", of: "#map_container"}, 
		draggable: false, 
		resizable: false, 
		width: 400,
		modal: true,
		buttons: 
		[
			{text: 'Go Back', click: function(){$(this).dialog('close');}},
			{text: 'Create New', click: function(){$(this).dialog('close'); $('#dlgStart').dialog('open');}}
		],
		title: 'Confirm New Corridor'
	});
	
	oDialog.html('Creating a new corridor removes the current corridor. Are you sure you want to create a new corrider?');
}


function buildStartDialog()
{
	let oDialog = $('#dlgStart');
	oDialog.dialog(
	{
		autoOpen: false, 
		position: {my: "middle top", at: "middle top", of: "#map_container"}, 
		draggable: false, 
		resizable: false, 
		width: 550,
		height: 450,
		modal: true,
		buttons: 
		[
			{text: 'Start Corridor', id: 'start_corridor', click: function()
				{
					if (checkDoubleInputs(['lanes', 'lane_width'])) 
					{
						$(this).dialog('close'); 
						$('#lanes_txt').html(`Lane count ${$('#lanes').val()}`).addClass('editcursor').on('click', editLanes);
						$('#lanes_width_txt').html(`Lane width ${$('#lane_width').val()}`).addClass('editcursor').on('click', editLanes);
						$('#lanes_width_txt').val($('#lanes').val());
						$('#lanes_txt').val($('#lanes').val());
						newCorridor();
					}
				}
			}
		],
		title: 'Create New Corridor'
	});
	
	
	let sHtml = '<p>A corridor for the Speed Harmonization Algorithm is created in multiple steps.';
	sHtml += ' First specify the lane count and lane width in this dialog and click "Start Corridor".';
	sHtml += ' Next left-click on the map to define the endpoints of each subsegment. These endpoints should be in the middle of the subsegment.';
	sHtml += ' After endpoints have been defined, inner points can be added in each subsegment to follow the road geometry.</p>';
	sHtml += '<table class="w3-table"><tr><td>Lane count</td><td><input id="lanes"></td><td>Lane width (m)</td><td><input id="lane_width"></td></tr></table>';
	oDialog.html(sHtml);
}


function buildFilesDialog()
{
	let oDialog = $('#dlgFiles');
	oDialog.dialog({autoOpen: false, position: {my: "center", at: "center", of: "body"}, draggable: false, resizable: false, width: 600, height: 800, modal: true});
	
	oDialog.dialog('option', 'title', 'Select File to Download');
	let sHtml = '<div id="month_dirs"></div>';
	oDialog.html(sHtml);
	$('#month_dirs').accordion({collapsible: true, heightStyle: 'content'});
}

function editLanes()
{
	let sParentId = $(this).prop('id');
	let sId =  sParentId + '_new';
	if ($('#' + sId).length > 0)
	{
		$('#' + sId).focus();
		return;
	}
	let oPopup = $(`<span class="popuptext show" style="width:80px; margin-left:30px;"><input id="${sId}">${sParentId === 'lanes_width_txt' ? '(m)' : ''}</span>`);
	oPopup.on('focusout keyup', function(oEvent)
	{
		if (oEvent.type === 'focusout' || oEvent.which === 13)
		{
			let dVal = $('#' + sId).val();
			if (!/^-?[0-9]+([.][0-9]+)?$/.test(dVal))
			{
				setTimeout(function() {$('#' + sId).focus().val('').addClass('red-focus');}, 10);
				return;
			}
			$('#' + sParentId).html(sParentId === 'lanes_width_txt' ? `Lane width ${dVal}` : `Lane count ${dVal}`).val(dVal).removeClass('popup');
			oPopup.remove();
			$('#' + sParentId).on('click', editLanes);
		}
	});
	$(this).addClass('popup').append(oPopup);
	$('#' + sId).focus();
	$(this).off('click', editLanes);
}


function checkDoubleInputs(sIds)
{
	for (let sKey of sIds.values())
	{
		let dVal = $('#' + sKey).val();
		if (!/^-?[0-9]+([.][0-9]+)?$/.test(dVal))
		{
			let oPopuptext = $('<span class="popuptext show" style="margin-left:-60px;bottom:100%;">Invalid parameter</span>');
			$('#' + sKey).parent().addClass('popup').append(oPopuptext);
			$('#' + sKey).focus();
			setTimeout(function() 
			{
				$('#' + sKey).parent().removeClass('popup');
				oPopuptext.remove();
			}, 1000);
			return false;
		}
	}
	
	return true;
}


function updateInstructions(sIns)
{
	$('#instructions').html(sIns);
	$('#dlgInstructions').dialog("option", "position", {my: "left top", at: "left+8 top+8", of: "#map_container"});
}


function distanceBetweenPoints(oP1, oP2)
{
	let dX = oP2.x - oP1.x;
	let dY = oP2.y - oP1.y;
	return Math.sqrt(dX * dX + dY * dY);
}


function addStyleRule(sSelector, sRule)
{
	let oSheet = document.styleSheets[document.styleSheets.length - 1];
	oSheet.insertRule(`${sSelector} {${sRule}}`, oSheet.cssRules.length);
}


function pointToPaddedBounds(oPoint, nTol = 4)
{
	return [{x: oPoint.x - nTol, y: oPoint.y - nTol}, {x: oPoint.x + nTol, y: oPoint.y + nTol}];
}


function updatePopupPos(oEvent)
{
	oPopup.setLngLat(oEvent.lngLat);
}


function labelSubsegment(oEvent)
{
	let oFeature = oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point), {'layers': ['corridor_poly']})[0];
	if (oFeature === undefined)
	{
		oPopup.remove();
		nLastSegment = -1;
		return;
	}
	
	if (!oPopup.isOpen())
		oPopup.addTo(oMap);
	
	if (nLastSegment !== oFeature.id)
	{
		oPopup.setHTML(`<p>Subsegment ${oFeature.id}</p>`).addTo(oMap);
		nLastSegment = oFeature.id;
	}
}

function createDetectorList()
{
	let oDetectorDiv = $('#detectors_container');
	let oTable = $('<table></table>');
	oTable.append($('<tr><th>subsegment</th><th>speed limit</th><th>15th %ile</th><th>85th %ile</th><th>Density</th><th>Volume</th></tr>'));
	oTable.append($('<tr><th></th><th>mph</th><th>mph</th><th>mph</th><th>% (0-100)</th><th>veh/h/lane</th></tr>'));
	oTable.addClass('w3-table');
	let oSrc = oMap.getSource('corridor_poly');
	for (let oFeature of oSrc._data.features.values())
	{
		let oRow = $(`<tr><td>${oFeature.properties.sub}</td><td><input id="limit_${oFeature.properties.sub}"></td><td><input id="15_${oFeature.properties.sub}"></td><td><input id="85_${oFeature.properties.sub}"></td><td><input id="den_${oFeature.properties.sub}"></td><td><input id="vol_${oFeature.properties.sub}"></td></tr>`);
		oRow.mouseenter({'id': oFeature.properties.sub}, highlightPoly).mouseleave({'id': oFeature.properties.sub}, unhighlightPoly);
		oTable.append(oRow);
	}
	
	oTable.find('td').addClass('w3-center');
	oTable.find('th').addClass('w3-center');
	
	oDetectorDiv.append(oTable);
	oDetectorDiv.find('input').on('focusout', addLeadingZero);
	getDetectors();
}


function getDetectors()
{
	showPageoverlay('Loading detectors...');
	$.ajax(
	{
		'url': 'api/ihp/getDetectors',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token}
	}).done(function(oData)
	{
		let aAllDetectors = oData.detectors;
		if (aAllDetectors.length === 0)
		{
			showPageoverlay('No saved detectors. Fill out table and click "Update" or click <i class="fas fa-cloud-upload-alt"></i> to upload a file');
			timeoutPageoverlay(2500);
			return;
		}
		for (let nCorridorIndex = 0; nCorridorIndex < aAllDetectors.length; nCorridorIndex++)
		{
			let aCorridor = aAllDetectors[nCorridorIndex];
			for (let nSegmentIndex = 0; nSegmentIndex < aCorridor.length; nSegmentIndex++)
			{
				let aSegment = aCorridor[nSegmentIndex];
				$('#limit_' + nSegmentIndex).val(aSegment[0]);
				$('#15_' + nSegmentIndex).val(aSegment[1]);
				$('#85_' + nSegmentIndex).val(aSegment[2]);
				$('#den_' + nSegmentIndex).val(aSegment[3]);
				$('#vol_' + nSegmentIndex).val(aSegment[4]);
			}
		}
		
		showPageoverlay('Successfully loaded detectors');
		timeoutPageoverlay();
	});
}


function highlightPoly(oEvent)
{
	oMap.setFeatureState({'source': 'corridor_poly', 'id': oEvent.data.id}, {'color': '#ffff00', 'opacity': 1});
}


function unhighlightPoly(oEvent)
{
	oMap.setFeatureState({'source': 'corridor_poly', 'id': oEvent.data.id}, {'color': null, 'opacity': null});
}


function updateDetectors()
{
	let aDetectors = $('#detectors_container  table  input').map(function() {return this.id;}).get();
	if (!checkDoubleInputs(aDetectors))
		return;
	
	let aSpeedData = [];
	for (let nIndex = 0; nIndex < aDetectors.length;)
	{
		aSpeedData.push([parseFloat($('#' + aDetectors[nIndex++]).val()), parseFloat($('#' + aDetectors[nIndex++]).val()), parseFloat($('#' + aDetectors[nIndex++]).val()), parseFloat($('#' + aDetectors[nIndex++]).val()), parseFloat($('#' + aDetectors[nIndex++]).val())]);
	}
	
	$.ajax(
	{
		'url': 'api/ihp/updateDetectors',
		'method': 'POST',
		'dataType': 'json',
		'data': {'token': sessionStorage.token, 'detectors': JSON.stringify([aSpeedData])}
	}).done(function() 
	{
		$('#start_sim').prop('disabled', false);
		showPageoverlay('Successfully updated detectors');
		timeoutPageoverlay();
	});	
}


function uploadDetectors()
{
	let oEl = document.createElement('input');
	oEl.setAttribute('type', 'file');
	document.body.appendChild(oEl);
	oEl.click();
	$(oEl).on('input', processDetectors);
	document.body.removeChild(oEl);
}


function processDetectors()
{
	let oEl = $(this);
	let oFile = oEl[0].files[0];
	let sContents = '';
	let nRecv = 0;
	let oReader = oFile.stream().getReader();
	oReader.read().then(function processText({done, value}) 
	{
		// Result objects contain two properties:
		// done  - true if the stream has already given you all its data.
		// value - some data. Always undefined when done is true.
		if (done) 
		{
			let aAllDetectors;
			try
			{
				aAllDetectors = JSON.parse(sContents);
			}
			catch (e)
			{
				showPageoverlay(`Error in file: ${e.message}`);
				timeoutPageoverlay(2000);
			}
			for (let nCorridorIndex = 0; nCorridorIndex < aAllDetectors.length; nCorridorIndex++)
			{
				let aCorridor = aAllDetectors[nCorridorIndex];
				for (let nSegmentIndex = 0; nSegmentIndex < aCorridor.length; nSegmentIndex++)
				{
					let aSegment = aCorridor[nSegmentIndex];
					$('#limit_' + nSegmentIndex).val(aSegment[0]);
					$('#15_' + nSegmentIndex).val(aSegment[1]);
					$('#85_' + nSegmentIndex).val(aSegment[2]);
					$('#den_' + nSegmentIndex).val(aSegment[3]);
					$('#vol_' + nSegmentIndex).val(aSegment[4]);
				}
			}
			oEl.remove();
			updateDetectors();
			return;
		}

		// value for fetch streams is a Uint8Array
		nRecv += value.length;
		let chunk = value;
		sContents += new TextDecoder("utf-8").decode(chunk);

		// Read some more, and call this function again
		return oReader.read().then(processText);
	});
}


function timeoutPageoverlay(nMillis = 1500)
{
	window.setTimeout(function()
	{
		if (!bStop)
			$('#pageoverlay').hide();
	}, nMillis);
}


function showPageoverlay(sContents)
{
	if (bStop)
		return;
	$('#pageoverlay p').html(sContents);
	$('#pageoverlay').show();
}

function addLeadingZero()
{
	let sVal = $(this).val();
	if (sVal[0] === '.')
		$(this).val('0' + sVal);
}
$(document).on('initPage', initialize);

