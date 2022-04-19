import {oMap, oPopup, carmaclPopupPos, aCtrlEnums, nCtrlZoom, setMode, resetMode, nMode, setCursor, refreshVectorTiles, switchMode, oCtrlUnits, aLabelOpts, nOtherIndex, oVTypes, pointToPaddedBounds, MODES} from './map.js';
import {fromIntDeg, createWholePoly, lon2tile, lat2tile, getPolygonBoundingBox} from './geoutil.js';

let nHoverId;
let nSelectedId;
let oExistingCtrls = {};
let oTilesLoaded = {};
let aLoadQueue = [];
let nCtrlType;
let nFeatureCount = 0;
let aCheckedState = [];
let aOriginalValues;
let sOriginalLabel;
let bOriginalReg;
let aOriginalVTypes;
let aDirty = [false, false, false, false, false, false];
let aValid = [false, false];
let bReload = false;

function resetAllLayers()
{
	$('#dlgLayers').dialog('option', 'title', 'Show/Hide');
	$('#all-layers :checkbox').each(function(index, element)
	{
		$(this).prop('disabled', false);
		if (element.checked !== aCheckedState[index])
			$(this).trigger('click');
	});
	nHoverId = undefined;
	nSelectedId = undefined;
	for (let [nKey, aIds] of Object.entries(oExistingCtrls))
	{
		for (let nId of aIds.values())
		{
			oMap.setFeatureState({'source': 'existing-ctrls-fill', 'id': nId}, {'hover': false, 'on': false, 'delete': false});
			oMap.setFeatureState({'source': 'existing-ctrls-outline', 'id': nId}, {'on': false});
		}	
	}
}


function captureLayerState()
{
	aCheckedState = [];
	$('#all-layers :checkbox').each(function(index, element)
	{
		aCheckedState.push(element.checked);
		if (element.checked && element.name !== 'pavement')
			$(this).trigger('click');
		if (element.name === 'pavement')
		{
			if (!element.checked)
			{
				$(this).trigger('click');
			}
		}
	});
}

function carmaclStartEdit()
{
	if ($('#dlgEdit').dialog('isOpen') || $('#dlgDelete').dialog('isOpen'))
		return;
	if (nMode !== MODES.nohandlers && nMode !== MODES.edit)
		switchMode();
	if (nMode === MODES.edit)
	{
		resetMode();
		oMap.off('mousemove', carmaclHover);
		oMap.off('click', 'existing-ctrls-fill', carmaclClickEdit);
		$('#edit-layers input[type="radio"]').off('input', carmaclStartLoadCtrls);
		resetAllLayers();
		$('#edit-layers').hide();
		$('#all-layers').show();
		$('#dlgLayers').dialog('option', 'position', {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'});
		$('#edit-cancel').off('click', closeEditDialog);
		$('#edit-save').off('click', saveEdit);
		$('#dlgEdit').siblings('.ui-dialog-titlebar').children('button').off('click', closeEditDialog);
		return;
	}
	resetMode();
	setMode(MODES.edit);
	captureLayerState();
	
	$('#all-layers').hide();
	$('#edit-layers input[type="radio"]').prop('checked', false).prop('disabled', false).on('input', carmaclStartLoadCtrls);
	$('#edit-layers').show();
	$('#dlgLayers').dialog('option', 'position', {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'});
	oPopup.setHTML('<p>Select control type to edit<br>Click "Layer Dialog" to exit mode</p>').addTo(oMap);
	$('#dlgLayers').dialog('option', 'title', 'Control Types');
	$('#dlgLayers').dialog('open');
	oPopup.addTo(oMap);
	oMap.on('mousemove', carmaclPopupPos);
	oMap.on('mousemove', carmaclHover);
	oMap.on('click', 'existing-ctrls-fill', carmaclClickEdit);
	$('#edit-cancel').on('click', closeEditDialog);
	$('#edit-save').on('click', saveEdit);
	$('#dlgEdit').siblings('.ui-dialog-titlebar').children('button').on('click', closeEditDialog);

}


function carmaclStartDelete()
{	
	if ($('#dlgEdit').dialog('isOpen') || $('#dlgDelete').dialog('isOpen'))
		return;
	if (nMode !== MODES.nohandlers && nMode !== MODES.delete)
		switchMode();
	if (nMode === MODES.delete)
	{
		resetMode();
		oMap.off('mousemove', carmaclHover);
		oMap.off('click', 'existing-ctrls-fill', carmaclClickDelete);
		$('#delete-layers input[type="radio"]').off('input', carmaclStartLoadCtrls);
		resetAllLayers();
		$('#delete-layers').hide();
		$('#all-layers').show();
		$('#dlgLayers').dialog('option', 'position', {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'});
		return;
	}
	resetMode();
	setMode(MODES.delete);
	
	captureLayerState();
	$('#all-layers').hide();
	$('#delete-layers input[type="radio"]').prop('checked', false).prop('disabled', false).on('input', carmaclStartLoadCtrls);
	$('#delete-layers').show();
	$('#dlgLayers').dialog('option', 'position', {my: 'right bottom', at: 'right-8 bottom-8', of: '#mapid'});
	oPopup.setHTML('<p>Select control type to delete from<br>Click "Layer Dialog" to exit mode</p>').addTo(oMap);
	$('#dlgLayers').dialog('option', 'title', 'Control Types');
	$('#dlgLayers').dialog('open');
	oPopup.addTo(oMap);
	oMap.on('mousemove', carmaclPopupPos);
	oMap.on('mousemove', carmaclHover);
	oMap.on('click', 'existing-ctrls-fill', carmaclClickDelete);
}

function carmaclClickDelete()
{
	if (nHoverId === undefined)
		return;
	if (!oMap.getFeatureState({'source': 'existing-ctrls-fill', 'id': nHoverId}).on)
		return;
	oMap.off('mousemove', carmaclHover);
	oMap.off('click', 'existing-ctrls-fill', carmaclClickDelete);
	$('#delete-layers input[type="radio"]').prop('disabled', false);
	oPopup.remove();
	nSelectedId = nHoverId;
	nHoverId = undefined;
	$('#dlgDelete').dialog('open');
	document.activeElement.blur();
}


function carmaclOneCheck(oEvent)
{
	if (!this.checked)
	{
		$(this).off('input', carmaclOneCheck);
		$(this).trigger('click');
		$(this).on('input', carmaclOneCheck);
		carmaclStartLoadCtrls();
		return;
	}
	
	$('#dlgLayers :checkbox').each(function(index, element) 
	{
		if (element.checked && element.name !== 'pavement' && element.name !== oEvent.target.name)
			$(element).trigger('click');
	});
}

function carmaclStartLoadCtrls()
{
	oPopup.setHTML('<p>Loading controls</p>');
	setCursor('progress');
	let sType = $((nMode === MODES.edit ? '#edit-layers ' : '#delete-layers ') + ':checked').prop('id');
	for (let nIndex = 0; nIndex < aCtrlEnums.length; nIndex++)
	{
		if (sType === aCtrlEnums[nIndex][0])
		{
			nCtrlType = nIndex;
			break
		}
	}
	if (oExistingCtrls[nCtrlType] === undefined)
		oExistingCtrls[nCtrlType] = [];
	if (oTilesLoaded[nCtrlType] === undefined)
		oTilesLoaded[nCtrlType] = {};
	for (let [nKey, aIds] of Object.entries(oExistingCtrls))
	{
		let bOn = nKey == nCtrlType;
		for (let nId of aIds.values())
		{
			oMap.setFeatureState({'source': 'existing-ctrls-fill', 'id': nId}, {'hover': false, 'on': bOn, 'delete': false});
			oMap.setFeatureState({'source': 'existing-ctrls-outline', 'id': nId}, {'on': bOn});
		}	
	}
	carmaclLoadCtrls();
	oMap.on('dragend', carmaclLoadCtrls);
}


function resetTileFlag(bBoolean)
{
	bReload = bBoolean;
}

function carmaclLoadCtrls()
{
	$((nMode === MODES.edit ? '#edit-layers ' : '#delete-layers ') + 'input[type="radio"]').prop('disabled', true);
	let oBounds = oMap.getBounds();
	let nMinX = lon2tile(oBounds._sw.lng, nCtrlZoom);
	let nMaxX = lon2tile(oBounds._ne.lng, nCtrlZoom);
	let nMinY = lat2tile(oBounds._ne.lat, nCtrlZoom);
	let nMaxY = lat2tile(oBounds._sw.lat, nCtrlZoom);
	
	for (let nX = nMinX; nX <= nMaxX; nX++)
	{
		for (let nY = nMinY; nY <= nMaxY; nY++)
		{
			if (!bReload && oTilesLoaded[nCtrlType][nX] && oTilesLoaded[nCtrlType][nX][nY])
				continue;
			else
			{
				if (oTilesLoaded[nCtrlType][nX] === undefined)
					oTilesLoaded[nCtrlType][nX] = {};
				oTilesLoaded[nCtrlType][nX][nY] = true;
				aLoadQueue.push(true);
				oPopup.setHTML('<p>Loading controls</p>');
				setCursor('progress');
			}
			$.ajax(
			{
				'url': 'api/geolanes',
				'method': 'POST',
				'dataType': 'json',
				'ctrlType': nCtrlType,
				'data': {'x': nX, 'y': nY, 'type': nCtrlType, 'token': sessionStorage.token}
			}).done(doneLoadCtrls).fail(failLoadCtrls);;
		}
	}
	if (aLoadQueue.length === 0)
	{
		resetTileFlag(false);
		oPopup.setHTML(`<p>Select control to ${nMode === MODES.edit ? 'edit' : 'delete'}<br>Click "Layer Dialog" to exit mode</p>`);
		$((nMode === MODES.edit ? '#edit-layers ' : '#delete-layers ') + 'input[type="radio"]').each(function(index, element) 
		{
			$(this).prop('disabled', false);
		});
		setCursor('');
	}
}


function doneLoadCtrls(oData)
{
	let oFillSrc = oMap.getSource('existing-ctrls-fill');
	let oFillData = oFillSrc._data;
	
	let oOutlineSrc = oMap.getSource('existing-ctrls-outline');
	let oOutlineData = oOutlineSrc._data;
	
	for (let [sId, oVals] of Object.entries(oData))
	{
		let oCoords = {};
		for (let sLine of ['a', 'b'].values())
		{
			let oLine = oVals[sLine];
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
		let oPoly = createWholePoly(oCoords['a'], oCoords['b']);
		oExistingCtrls[this.ctrlType].push(nFeatureCount);
		oFillData.features.push({'type': 'Feature', 'id': nFeatureCount, 'properties': {'ccid': sId, 'vals': oVals.vals, 'reg': oVals.reg, 'label': oVals.label, 'display': oVals.display, 'vtypes': oVals.vtypes}, 'geometry': {'type': 'Polygon', 'coordinates': oPoly}});
		oOutlineData.features.push({'type': 'Feature', 'id': nFeatureCount++, 'geometry': {'type': 'LineString', 'coordinates': oPoly[0]}});
	}
	oFillSrc.setData(oFillData);
	oOutlineSrc.setData(oOutlineData);
	for (let nId of oExistingCtrls[this.ctrlType].values())
	{
		oMap.setFeatureState({'source': 'existing-ctrls-fill', 'id': nId}, {'on': true, 'hover': false, 'delete': false});
		oMap.setFeatureState({'source': 'existing-ctrls-outline', 'id': nId}, {'on': true});
	}
	aLoadQueue.pop();
	if (aLoadQueue.length === 0)
	{
		resetTileFlag(false);
		oPopup.setHTML(`<p>Select control to ${nMode === MODES.edit ? 'edit' : 'delete'}<br>Click "Layer Dialog" to exit mode</p>`);
		$((nMode === MODES.edit ? '#edit-layers ' : '#delete-layers ') + 'input[type="radio"]').each(function(index, element) 
		{
			$(this).prop('disabled', false);
		});
		setCursor('');
	}
}


function failLoadCtrls()
{
	
}


function carmaclHover(oEvent)
{
	let oFeatures = oMap.queryRenderedFeatures(pointToPaddedBounds(oEvent.point, 2), {'layers': ['existing-ctrls-fill']});
	let nFeatureId = getFeatureId({features: oFeatures});
	if (!oMap.getFeatureState({source: 'existing-ctrls-fill', id: nFeatureId}).on)
	{
		let nHover = nHoverId;
		nHoverId = undefined;
		setCursor('');
		if (nHover >= 0)
			oEvent.target.setFeatureState({source: 'existing-ctrls-fill', id: nHover}, nMode === MODES.delete ? {delete: false} : {hover: false});
		return;
	}
	setCursor('pointer');
	let nHover = nHoverId;
	if (nFeatureId !== nHover)
	{
		if (nHover >= 0)
			oMap.setFeatureState({source: 'existing-ctrls-fill', id: nHover}, nMode === MODES.delete ? {delete: false} : {hover: false});
		if (nFeatureId >= 0)
		{
			nHoverId = nFeatureId;
			oMap.setFeatureState({source: 'existing-ctrls-fill', id: nFeatureId}, nMode === MODES.delete ? {delete: true} : {hover: true});
		}
	}
}


function carmaclClickEdit(oEvent)
{
	if (nHoverId === undefined || nHoverId < 0)
        return;
	oMap.off('mousemove', carmaclHover);
	oMap.off('click', 'existing-ctrls-fill', carmaclClickEdit);
	$('#dlgVTypes input[type="checkbox"]').on('click', carmaclCheckDirtyVTypes);
	oPopup.remove();
	$('#edit-layers input[type="radio"]').prop('disabled', true);
	nSelectedId = nHoverId;
	nHoverId = undefined;
	oMap.setFeatureState({source: 'existing-ctrls-fill', id: nSelectedId}, {selected: true, hover: false});
	let oData = oMap.getSource('existing-ctrls-fill')._data;
	let aCtrlVals = oData.features[nSelectedId].properties.vals;
	let nDisplayVal = oData.features[nSelectedId].properties.display;
	let sHtml = '<form id="edit-form"><table>';
	sHtml += `<tr><td>Vehicle types</td><td id="vtype-des">All<td><i class="fa fa-edit clickable"></tr>`;
	let nType;
	aOriginalValues = [];
	sOriginalLabel = oData.features[nSelectedId].properties.label;
	bOriginalReg = oData.features[nSelectedId].properties.reg;
	aOriginalVTypes = oData.features[nSelectedId].properties.vtypes;
	
	let nOriginalIndex;
	aDirty = [false, false, false, false, false, false];
	aValid = [true, true];
	
	if (aCtrlEnums[nCtrlType].length === 1) // not an enumerated type
	{
		sHtml += `<tr><td>Value</td><td><input id="edit-input" name="value" value="${nDisplayVal}"></td><td>${oCtrlUnits[nCtrlType][1] ? '&nbsp;' + oCtrlUnits[nCtrlType][1] : ''}</td></tr>`;
		nType = 0;
		aOriginalValues.push(nDisplayVal);
	}
	else
	{
		for (let nValIndex = 1; nValIndex <= aCtrlVals.length; nValIndex += 2)
		{
			let sOptions = '';
			for (let nIndex = 1; nIndex < aCtrlEnums[nCtrlType].length; nIndex++)
			{
				let sSelected = '"';
				if (aCtrlVals[nValIndex] === aCtrlEnums[nCtrlType][nIndex])
				{
					sSelected = '" selected';
					aOriginalValues.push(nIndex);
				}
				sOptions += `<option value="${nIndex}${sSelected}>${aCtrlEnums[nCtrlType][nIndex]}</option>`;
			}
			let sHeading = '';
			if (aCtrlVals.length > 2)
			{
				nType = 2;
				if (nValIndex === 1)
					sHeading = 'outer edge';
				else
					sHeading = 'inner edge';
			}
			else
			{
				nType = 1;
				sHeading = 'Select control value';
			}
			sHtml += `<tr><td>${sHeading}</td><td><select id="${nValIndex === 1 ? 'edit-select1' : 'edit-select2'}" name="${nValIndex === 1 ? 'value1' : 'value2'}">${sOptions}</select>`;
		}
	}
	sHtml += `<tr><td><label for="edit-regulatory">Regulatory</label></td><td><input id="edit-regulatory" type="checkbox" name="reg"${bOriginalReg ? ' checked' : ''}></td></tr>`;
	let sOptions = '';
	let bHasOtherLabel = true;
	for (let nIndex = 0; nIndex < aLabelOpts.length; nIndex++)
	{
		let sSelected = '"';
		if (aLabelOpts[nIndex] === sOriginalLabel)
		{
			sSelected = '" selected';
			bHasOtherLabel = false;
			nOriginalIndex = nIndex;
		}
		sOptions += `<option value="${nIndex}${sSelected}>${aLabelOpts[nIndex]}</option>`;
	}
		
	sHtml += `<tr><td><label for="edit-label">Label</label></td><td><select id="edit-label">${sOptions}</select></td></tr>`;
	sHtml += `<tr><td></td><td><input style="display: none;" type="text" id="edit-label-text" name="label" maxlength="63"></td></tr>`;
	
	sHtml += '</table></form>';
	$('#edit-content').html(sHtml);
	$('#dlgVTypes input[type="checkbox"]').prop('checked', false);
	for (let nType of aOriginalVTypes.values())
		$('#dlgVTypes .vtypeitem > input[value=' + nType + ']').click();
	$('#edit-form td i.fa-edit').click(function()
	{
		let oVTypes = $('#dlgVTypes');
		if (oVTypes.dialog('isOpen'))
			oVTypes.dialog('close');
		else
			$('#dlgVTypes').dialog('open');
	});
	$('#edit-label-text').val(sOriginalLabel);
	if (bHasOtherLabel)
	{
		$('#edit-label-text').show();
		$('#edit-label').val(nOtherIndex);
		nOriginalIndex = nOtherIndex;
		$('#edit-label-text').on('input', {'val': sOriginalLabel, 'index': 4}, checkOther);
	}
	if (nType === 0)
	{
		$('#edit-input').on('input', {'val': aOriginalValues[0], 'index': 0}, carmaclCheckDirty);
		$('#edit-input').on('input', function() 
		{
			aValid[0] = $(this).val().length !== 0;
			$('#edit-save').prop('disabled', !checkValid());
		});
	}
	else if (nType === 1)
	{
		$('#edit-select1').on('input', {'val': aOriginalValues[0], 'index': 1}, carmaclCheckDirty);
	}
	else if (nType === 2)
	{
		$('#edit-select1').on('change', {'val': aOriginalValues[0], 'index': 1}, carmaclCheckDirty);
		$('#edit-select2').on('change', {'val': aOriginalValues[1], 'index': 2}, carmaclCheckDirty);
	}
	
	$('#edit-label').on('change', {'val': nOriginalIndex, 'index': 4}, carmaclCheckDirty);

	$('#edit-label').on('change', function() 
	{
		let sText = aLabelOpts[this.value];
		if (sText === 'other')
		{
			$('#edit-label-text').show().val('').on('input', {'val': sOriginalLabel, 'index': 4}, checkOther);
			aValid[1] = false;
			$('#edit-save').prop('disabled', !checkDirty() ||!checkValid());
		}
		else
		{
			aValid[1] = true;
			$('#edit-label-text').hide().val(sText).off('input', checkOther);
			$('#edit-save').prop('disabled', !checkDirty() || !checkValid());
		}
	});
	
	$('#edit-regulatory').on('change', carmaclCheckDirtyReg);
	$('#edit-save').prop('disabled', true);
	let sCtrl = aCtrlEnums[nCtrlType][0];
	$('#dlgEdit').dialog('option', 'title', `Edit ${sCtrl[0].toUpperCase() + sCtrl.slice(1)} Control`);
	$('#dlgEdit').dialog('open');
	document.activeElement.blur();
}

function checkOther(oEvent)
{
	aValid[1] = $('#edit-label-text').val().length !== 0;
	aDirty[oEvent.data.index] = $(this).val() != oEvent.data.val;

	$('#edit-save').prop('disabled', !checkValid() || !checkDirty());
}

function checkOtherLength()
{
	aValid[1] = $('#edit-label-text').val().length !== 0;
	$('#edit-save').prop('disabled', !checkValid());
}

function carmaclCheckDirty(oEvent)
{
	aDirty[oEvent.data.index] = $(this).val() != oEvent.data.val;
	$('#edit-save').prop('disabled', !checkDirty());
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


function checkDirty()
{
	for (let bBool of aDirty.values())
	{
		if (bBool)
			return true;
	}
	
	return false;
}


function carmaclCheckDirtyReg()
{
	aDirty[3] = $('#edit-regulatory').prop('checked') !== bOriginalReg;
	$('#edit-save').prop('disabled', !checkDirty());
}


function carmaclCheckDirtyVTypes()
{
	let aVtypes = [];
	for (let oKeyVal of $('#vtype-form').serializeArray().values())
		aVtypes.push(oKeyVal.value);
	if (aVtypes.length !== aOriginalVTypes.length)
		aDirty[5] = true;
	else
	{
		aVtypes.sort();
		let bSame = true;
		let nIndex = aVtypes.length;
		while (bSame && nIndex-- > 0)
		{
			bSame = aVtypes[nIndex] == aOriginalVTypes[nIndex];
		}
		
		aDirty[5] = !bSame;
	}
	
	$('#edit-save').prop('disabled', !checkDirty());
}


function saveEdit()
{
	$('#dlg-edit-overlay').show();
	$('#dlg-edit-overlay > p').html('Saving...');
	let oMapData = oMap.getSource('existing-ctrls-fill')._data;
	let sCcId = oMapData.features[nSelectedId].properties.ccid;
	console.log(sCcId);
	let oData = {'token': sessionStorage.token, 'type': nCtrlType, 'id': sCcId};
	for (let oKeyVal of $('#edit-form').serializeArray().values())
		oData[oKeyVal.name] = oKeyVal.value;
	oData.vtypes = [];
	for (let oKeyVal of $('#vtype-form').serializeArray().values())
		oData.vtypes.push(oKeyVal.value);
	$.ajax(
	{
		'url': 'api/ctrl/saveEdit',
		'method': 'POST',
		'dataType': 'json',
		'data': oData
	}).done(doneSaveEdit).fail(failSaveEdit);
}

function doneSaveEdit(oData)
{
	let oSrc = oMap.getSource('existing-ctrls-fill');
	let oMapData = oSrc._data;
	oMapData.features[nSelectedId].properties.ccid = oData.id;
	oMapData.features[nSelectedId].properties.vals = oData.vals;
	oMapData.features[nSelectedId].properties.reg = oData.reg;
	oMapData.features[nSelectedId].properties.label = oData.label;
	oMapData.features[nSelectedId].properties.vtypes = oData.vtypes;
	oSrc.setData(oMapData);
	console.log(oData.id);;
	refreshVectorTiles();
	$('#dlg-edit-overlay > p').html('Successfully saved edit.');
	setTimeout(function()
	{
		$('#dlg-edit-overlay').hide();
		closeEditDialog();
	}, 1500);
}


function failSaveEdit()
{
	$('#dlg-edit-overlay > p').html('Unsuccessfully saved edit. Try again later.');
	setTimeout(function()
	{
		$('#dlg-edit-overlay').hide();
		closeEditDialog();
	}, 1500);
}


function deleteControl()
{
	$('#dlg-delete-overlay').show();
	$('#dlg-delete-overlay > p').html('Deleting...');
	let oMapData = oMap.getSource('existing-ctrls-fill')._data;
	let sCcId = oMapData.features[nSelectedId].properties.ccid;
	$.ajax(
	{
		'url': 'api/ctrl/delete',
		'method': 'POST',
		'data': {'token': sessionStorage.token, 'id': sCcId}
	}).done(doneDeleteControl).fail(failDeleteControl);
}

function doneDeleteControl()
{
	let oSrc = oMap.getSource('existing-ctrls-fill');
	let oMapData = oSrc._data;
	oMapData.features[nSelectedId].geometry.coordinates = [];
	oSrc.setData(oMapData);
	
	oSrc = oMap.getSource('existing-ctrls-outline');
	oMapData = oSrc._data;
	oMapData.features[nSelectedId].geometry.coordinates = [];
	oSrc.setData(oMapData);
	refreshVectorTiles();
	$('#dlg-delete-overlay > p').html('Successfully deleted control.');
	oMap.on('mousemove', carmaclHover);
	oMap.on('click', 'existing-ctrls-fill', carmaclClickDelete);
	setTimeout(function()
	{
		$('#dlg-delete-overlay').hide();
		closeDeleteDialog();
	}, 1500);
}


function failDeleteControl()
{
	$('#dlg-delete-overlay > p').html('Unsuccessfully deleted control. Try again later.');
	oMap.on('mousemove', carmaclHover);
	oMap.on('click', 'existing-ctrls-fill', carmaclClickDelete);
	setTimeout(function()
	{
		$('#dlg-delete-overlay').hide();
		closeDeleteDialog();
	}, 1500);
}

function closeEditDialog()
{
	if (nSelectedId !== undefined)
	{
		oMap.setFeatureState({source: 'existing-ctrls-fill', id: nSelectedId}, {selected: false, hover: false});
		nSelectedId = undefined;
	}
	if (nMode === MODES.edit)
	{
		oMap.on('mousemove', carmaclHover);
		oMap.on('click', 'existing-ctrls-fill', carmaclClickEdit);
		$('#dlgVTypes input[type="checkbox"]').off('click', carmaclCheckDirtyVTypes);
		oPopup.addTo(oMap);
		$('#edit-layers :input[type="radio"]').prop('disabled', false);
	}
	$('#dlgEdit').dialog('close');
}


function closeDeleteDialog()
{
	if (nSelectedId !== undefined)
	{
		oMap.setFeatureState({source: 'existing-ctrls-fill', id: nSelectedId}, {selected: false, delete: false});
		nSelectedId = undefined;
	}
	oPopup.addTo(oMap);
	$('#delete-layers :input[type="radio"]').prop('disabled', false);
	$('#dlgDelete').dialog('close');
}


function getFeatureId(oEvent)
{
	let nFeatureId = -1;
	if (oEvent.features.length > 0)
	{
		if (oEvent.features.length === 1)
		{
			nFeatureId = oEvent.features[0].id;
		}
		else
		{
			let nMinArea = Number.MAX_VALUE;
			let oData = oMap.getSource('existing-ctrls-fill')._data;
			
			for (let nIndex = 0; nIndex < oEvent.features.length; nIndex++)
			{
				let oFeature = oEvent.features[nIndex];
				let aBbox = getPolygonBoundingBox(oData.features[oFeature.id]);
				
				let nArea = (aBbox[1][0] - aBbox[0][0]) * (aBbox[1][1] - aBbox[0][1]);
				if (nArea < nMinArea && oMap.getFeatureState({'source': 'existing-ctrls-fill', 'id': oFeature.id}).on)
				{
					nMinArea = nArea;
					nFeatureId = oFeature.id;
				}
			}
			oMap.getSource('existing-ctrls-fill').setData(oData);
		}
	}
	
	return nFeatureId;
}

export {carmaclStartEdit, closeEditDialog, carmaclStartDelete, saveEdit,
		deleteControl, closeDeleteDialog, captureLayerState, resetAllLayers, resetTileFlag};
