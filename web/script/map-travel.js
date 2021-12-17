import {oMap, oPopup, resetMode, setCursor, getClosestLineFeature, carmaclPopupPos, setMode, nMode, switchMode} from './map.js';
import {METERS_PER_PIXEL, headingA, length} from './geoutil.js';

let oVeh = {aCoord: [undefined, undefined], nSpd: 55, aPlan: [], nIndex: undefined, oCurFeature: undefined, oDestFeature: undefined, bPlanning: false, nPanCount: 0};
let oPts = {ori: [undefined, undefined], dest: [undefined, undefined], veh: [undefined, undefined]};
let nTimeoutId;

function carmaclStartTravel()
{
	if (nMode !== 1)
		return;
	if (nTimeoutId === undefined)
	{
		if (oMap.getPaintProperty('origin-pt', 'circle-opacity') === 0) //|| oMap.getPaintProperty('dest-pt', 'circle-opacity') == 0)
		{
			alert('Set origin before travel');
			return;
		}
		setPt(oVeh.aCoord, 'veh-gjson', oPts.ori[0], oPts.ori[1]);
		ptOn('veh-pt');
		nTimeoutId = setTimeout(travel, 100);
	}
	else
	{
		clearTimeout(nTimeoutId);
		ptOff('veh-pt');
		nTimeoutId = undefined;
	}
}


function travel()
{
	let aCoord = oVeh.aPlan.shift();
	if (aCoord === undefined)
	{
		console.log('no plan');
		if (nTimeoutId !== undefined)
		{
			clearTimeout(nTimeoutId);
			nTimeoutId = undefined;
		}
		return;
	}
	
	
	setPt(oVeh.aCoord, 'veh-gjson', aCoord[0], aCoord[1]);
	if (oVeh.nPanCount++ > 10)
	{
		oMap.panTo(aCoord);
		oVeh.nPanCount = 0;
	}
	
	if (oVeh.aPlan.length < 10 && !oVeh.bPlanning)
	{
		oVeh.bPlanning = true;
		console.log('planning');
		createPlan(oVeh.aPlan);
		console.log('done planning');
		oVeh.bPlanning = false;
	}
	nTimeoutId = setTimeout(travel, 100);
}


function carmaclStartOrigin()
{
	if ($('#dlgEdit').dialog('isOpen') || $('#dlgDelete').dialog('isOpen'))
		return;
//	ptOff('dest-pt');
	ptOff('veh-pt');
	ptOff('origin-pt');
	if (nMode !== 0 && nMode !== 1)
		switchMode();
	if (nMode === 1)
	{
		resetMode();
		if (nTimeoutId !== undefined)
		{
			clearTimeout(nTimeoutId);
			ptOff('veh-pt');
			nTimeoutId = undefined;
		}
		return;
	}
	resetMode();
	setMode(1);
	setCursor('crosshair');
	oMap.on('click', carmaclSetOrigin);
	oMap.on('mousemove', carmaclPopupPos);
	oMap.on('mousemove', carmaclUpdateOrigin);
	oPopup.setHTML('Left-click<br>Start Point').addTo(oMap);
}


function carmaclUpdateOrigin({target, lngLat, point})
{
	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
	if (oFeature !== undefined)
	{
		setCursor('pointer');
		setPt(oPts.ori, 'origin-gjson', oFeature.oSnapInfo.aPt[0], oFeature.oSnapInfo.aPt[1]);
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
		oMap.off('mousemove', carmaclUpdateOrigin);
		oMap.off('click', carmaclSetOrigin);
		let aPt = oFeature.oSnapInfo.aPt;
		oVeh.sCurLane = oFeature.oMBoxFeature.properties.sponge_id;
		oVeh.nIndex = oFeature.oSnapInfo.nIndex;
		oVeh.oCurFeature = oFeature.oMBoxFeature;
		setPt(oPts.ori, 'origin-gjson', aPt[0], aPt[1]);
		ptOn('origin-pt');
		createPlan([]);
		setCursor('');
		oPopup.remove();
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
	let dPixelWidth = dWidth / METERS_PER_PIXEL[nZoom];
	let dPixelLength = dLength / METERS_PER_PIXEL[nZoom];
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
	
	
//	let aCoords = [oMap.unproject(oSw), oMap.unproject([oSw[0], oNe[1]]), oMap.unproject(oNe), oMap.unproject([oNe[0], oSw[1]]), oMap.unproject(oSw)];
////	let aCoords = [oMap.unproject(aPt1), oMap.unproject(aPt2), oMap.unproject(aPt3), oMap.unproject(aPt4), oMap.unproject(aPt1)];
//	let aLngLats = [];
//	for (let oLngLat of aCoords.values())
//		aLngLats.push([oLngLat.lng, oLngLat.lat]);
//	let oPoly = oMap.getSource('weather-polygon');
//	let oData = oPoly._data;
//	oData.geometry.coordinates = [aLngLats];
//	oPoly.setData(oData);
//	oMap.setPaintProperty('w-poly', 'fill-opacity', 0.5);
	
	return [oSw, oNe];
}


function createPlan(aPlan)
{
	let aCoords = oVeh.oCurFeature.geometry.coordinates;

//	let nIncrease = Math.round(oVeh.nSpd * 0.745);
	let nIncrease = Math.round(oVeh.nSpd * 1.49);
	oVeh.nIndex += nIncrease;
	while (oVeh.nIndex >= aCoords.length)
	{
		let oRet = getNextFeature(aCoords, nIncrease, aPlan);
		if (!oRet[0])
		{
			if (oRet[1] !== undefined)
				aPlan = oRet[1];
			break;
		}
	}
	
	aCoords = oVeh.oCurFeature.geometry.coordinates;
	if (oVeh.nIndex >= aCoords.length)
		return;
	for (;oVeh.nIndex < aCoords.length && aPlan.length < 50; oVeh.nIndex += nIncrease)
	{
		let aCoord = aCoords[oVeh.nIndex];
		aPlan.push(aCoord);
		if (oMap.queryRenderedFeatures(oMap.project(aCoord), {layers: ['w-poly']}).length > 0)
		{
			oVeh.nSpd = 35;
			nIncrease = Math.round(oVeh.nSpd * 1.49);
		}
		else
		{
			oVeh.nSpd = 55;
			nIncrease = Math.round(oVeh.nSpd * 1.49);
		}
	}
	
	oVeh.nIndex -= nIncrease;
	
	if (aPlan.length < 50 || oVeh.nIndex >= aCoords.length)
	{
		let oRet = getNextFeature(aCoords, nIncrease, aPlan);
		if (oRet[0])
			createPlan(aPlan);
		else if (oRet[1] !== undefined)
			aPlan = oRet[1];
	}
	
	oVeh.aPlan = aPlan;
}

function getNextFeature(aCoords, nIncrease, aPlan)
{
	console.log('getting next');
	let nLast = aCoords.length - 1;
	let nStart = nLast - 20;
	if (nStart < 0)
		nStart = 0;

	let dHdg = headingA(aCoords[nStart], aCoords[nLast]);
	let aPlanBox = getPlanBox(oMap.project(aCoords[nLast]), dHdg, 3, 3);
	let oClosures = oMap.queryRenderedFeatures(aPlanBox, {layers: ['closed-outline']});
	if (oClosures.length > 0)
	{
		let nZoom = 19;
		let dPixelOffset = 3 / METERS_PER_PIXEL[nZoom];
		let nPlanSize = aPlan.length;
		let aCurPt = aPlan[0];
		let aLastPt = aPlan[nPlanSize - 1];
		let aPixelPt = oMap.project(aLastPt);
		let aShiftPoint = {x: aPixelPt.x + dPixelOffset * Math.cos(dHdg + Math.PI / 2), y: aPixelPt.y - dPixelOffset * Math.sin(dHdg + Math.PI / 2)};
		let oFeature = getClosestLineFeature(oMap.unproject(aShiftPoint), aShiftPoint, ['debug-c'], 6);
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
		oVeh.nIndex = oFeature.oSnapInfo.nIndex;
		oVeh.oCurFeature = oFeature.oMBoxFeature;
		return [false, aNewPlan];
		
	}
	let oFeatures = oMap.queryRenderedFeatures(aPlanBox, {layers: ['debug-c']});

	let aLastPt = aCoords[nLast];
	let oNextFeat = undefined;
	let nNextIndex = undefined;
	for (let oFeature of oFeatures.values())
	{
		if (oFeature.properties.sponge_id === oVeh.oCurFeature.properties.sponge_id && oFeature.geometry.coordinates[0][0] === aCoords[0][0]) // skip current feature
			continue;

		let aNewCoords = oFeature.geometry.coordinates;
		let nLimit = Math.min(500, aNewCoords.length);
		for (let i = 0; i < nLimit; i++)
		{
			let dDist = length(aNewCoords[i][0], aNewCoords[i][1], aLastPt[0], aLastPt[1]);
			if (dDist < 0.000001)
			{
				let nExtra = oVeh.nIndex + nIncrease - aCoords.length;
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
		oVeh.oCurFeature = oNextFeat;
		oVeh.nIndex = nNextIndex;
		return [true];
//		createPlan(aPlan);
	}
	
	else
		return [false, undefined];
}


//function carmaclUpdateDest({target, lngLat, point})
//{
//	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
//	if (oFeature !== undefined)
//	{
//		setCursor('pointer');
//		setPt(oPts.dest, 'dest-gjson', oFeature.oSnapInfo.aPt[0], oFeature.oSnapInfo.aPt[1]);
//		ptOn('dest-pt');
//	}
//	else
//	{
//		setCursor('crosshair');
//		ptOff('dest-pt');
//	}
//}
//
//
//function carmaclSetDest({target, lngLat, point})
//{
//	let oFeature = getClosestLineFeature(lngLat, point, ['debug-c'], 1.8);
//	if (oFeature !== undefined)
//	{
//		oMap.off('mousemove', carmaclUpdateDest);
//		oMap.off('mousemove', carmaclPopupPos);
//		oMap.off('click', carmaclSetDest);
//		oVeh.oDestFeature = oFeature.oMBoxFeature;
//		setPt(oPts.dest, 'dest-gjson', oFeature.oSnapInfo.aPt[0], oFeature.oSnapInfo.aPt[1]);
//		ptOn('dest-pt');
//		createPlan([]);
//		endMode();
//	}
//	else
//	{
//		setCursor('crosshair');
//		ptOff('dest-pt');
//	}
//}

function ptOn(sLayer)
{
	oMap.setPaintProperty(sLayer, 'circle-opacity', 1);
	oMap.setPaintProperty(sLayer, 'circle-stroke-opacity', 1);
}


function ptOff(sLayer)
{
	oMap.setPaintProperty(sLayer, 'circle-opacity', 0);
	oMap.setPaintProperty(sLayer, 'circle-stroke-opacity', 0);
}


function setPt(oPt, sSource, dX, dY)
{
	oPt[0] = dX;
	oPt[1] = dY;
	let oLayer = oMap.getSource(sSource);
	let oData = oLayer._data;
	oData.geometry.coordinates = [dX, dY];
	oLayer.setData(oData);
}

export {carmaclStartOrigin, carmaclStartTravel};
