//var StaticMode = {};
//StaticMode.onSetup = function() 
//{
//	return {};
//};
//
//StaticMode.toDisplayFeatures = function(oState, oGeojson, fnDisplay)
//{
//	fnDisplay(oGeojson);
//};
//
//var EditPathMode = {};
//
//EditPathMode.getCurrentPart = function(oState)
//{
//	if (oState.oPathStack && oState.oPathStack.length > 0)
//		return oState.oPathStack[oState.oPathStack.length - 1];
//
//	return undefined;
//}
//EditPathMode.onSetup = function() 
//{
//	let oState = {debug: false}
//	oState.oPathStack = [new PathPart(undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined)];
//	return oState;
//};
//
//EditPathMode.onClick = function(oState, oEvent) 
//{
//	
//	let oPathPart = this.getCurrentPart(oState);
//	if (oPathPart == undefined)
//		return;
//
//	let oBBox = [[oEvent.point.x - 5, oEvent.point.y - 5], [oEvent.point.x + 5, oEvent.point.y + 5]];
//	let oFeatures = this.featuresAt(oEvent, oBBox);
//	if (oPathPart.sMode === 'free')
//	{
//		if (oPathPart.oStart === undefined)
//		{
//			oPathPart.oStart = this.createAndAddFeature({sPt: 'start', aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 1)}, 'Point', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 1), true);
//			oPathPart.oEnd = this.createAndAddFeature({sPt: 'end', aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 1)}, 'Point', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 1), true);			
//			oPathPart.oCurve = this.createAndAddFeature({sPt: 'curve', aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 1)}, 'Point', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 1), true);
//			oPathPart.oMdpt = this.createAndAddFeature({sPt: 'mdpt', aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 1)}, 'Point', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 1), true);
//			oPathPart.oLine = this.createAndAddFeature({aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 2)}, 'LineString', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 2), false);
//			oPathPart.oPath = this.createAndAddFeature({aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 2)}, 'LineString', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 2), false);
//			oPathPart.oRight = this.createAndAddFeature({aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 5)}, 'LineString', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 2), true);
//			oPathPart.oLeft = this.createAndAddFeature({aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 5)}, 'LineString', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 2), true);
//			oPathPart.oWidth1 = this.createAndAddFeature({sPt: 'center', aPixels: newCoordinates(oEvent.point.x, oEvent.point.y, 1)}, 'Point', newCoordinates(oEvent.lngLat.lng, oEvent.lngLat.lat, 1), false);
//		}
//		else if (oPathPart.oStart.properties.sDrawState === 'draw')
//		{
//			oPathPart.setDrawState('edit');	
//			oPathPart.oCurve.updateCoordinate(oPathPart.oMdpt.coordinates[0], oPathPart.oMdpt.coordinates[1]);
//			oPathPart.oCurve.properties.aMercs[0].x = oPathPart.oMdpt.properties.aMercs[0].x;
//			oPathPart.oCurve.properties.aMercs[0].y = oPathPart.oMdpt.properties.aMercs[0].y;
//			oPathPart.sMode = 'edit';
//		}
//		else
//		{
//			
//		}
//	}
//	else if (oPathPart.sMode === 'edit')
//	{
//		for (let oFeature of oFeatures.values())
//		{
//			if (oPathPart.oEnd && oFeature.properties.id === oPathPart.oEnd.id)
//			{
//				let dX = oPathPart.oEnd.coordinates[0];
//				let dY = oPathPart.oEnd.coordinates[1];
//				oPathPart.setDrawState('set');
//				let oNextPart = new PathPart(
//						this.createAndAddFeature({}, 'LineString', newCoordinates(dX, dY, 2), true),
//						this.createAndAddFeature({}, 'LineString', newCoordinates(dX, dY, 2), true),
//						this.createAndAddFeature({sPt: 'start'}, 'Point', newCoordinates(dX, dY, 1), true), 
//						this.createAndAddFeature({sPt: 'end'}, 'Point', newCoordinates(dX, dY, 1), true), 
//						this.createAndAddFeature({sPt: 'curve'}, 'Point', newCoordinates(dX, dY, 1), true),
//						this.createAndAddFeature({sPt: 'mdpt'}, 'Point', newCoordinates(dX, dY, 1), true), 
//						this.createAndAddFeature({}, 'LineString', newCoordinates(dX, dY, 2), true),
//						this.createAndAddFeature({}, 'LineString', newCoordinates(dX, dY, 2), true), 
//						this.createAndAddFeature({sPt: 'center'}, 'Point', newCoordinates(dX, dY , 1), true),
//						undefined);
//				oNextPart.dLastHdg = heading(oPathPart.oLine.properties.aMercs[0], oPathPart.oLine.properties.aMercs[1], true);
//				oNextPart.sMode = 'free';
//				oState.oPathStack.push(oNextPart);
//				
//			}
//			else if (oFeature.properties.id === oPathPart.oStart.id)
//			{
//				oState.oPathStack.pop();
//				this.removePath(oPathPart);
//			}
//			else if (oFeature.properties.id === oPathPart.oCurve.id)
//			{
//				oPathPart.sMode = 'curve';
//			}
//		}
//	}
//	else if (oPathPart.sMode === 'curve')
//	{
//		oPathPart.sMode = 'edit';
//	}
//};
//
//EditPathMode.onKeyDown = function(oState, oEvent)
//{
//	if (oEvent.keyCode === 17)
//		oState.debug = true;
//}
//
//EditPathMode.onKeyUp = function(oState, oEvent) 
//{
//	if (oEvent.keyCode === 27)
//		return this.changeMode('static');
//	
//	if (oEvent.keyCode === 17)
//		oState.debug = false;
//	
//	if (oEvent.keyCode === 67) // 'c'
//	{
//		let oPathPart = this.getCurrentPart(oState);
//		if (oPathPart == undefined)
//			return;
//		
//		if (oPathPart.sMode === 'edit')
//		{
//			let oCircle = getCircle(oPathPart.oStart.properties.aMercs[0], oPathPart.oEnd.properties.aMercs[0], oPathPart.dLastHdg, 100, 0.001, oPathPart, oState, oEvent, this);
//			oPathPart.oCircle = oCircle;
//			let aLngLats = [];
//			let aMercs = [];
//			for (let nIndex = 0; nIndex <= oCircle.nSteps; nIndex++)
//			{
//				let dAngle = oCircle.dStartAngle + oCircle.dStepSize * nIndex;
//				let dX = oCircle.dH + oCircle.dR * Math.cos(dAngle);
//				let dY = oCircle.dK - oCircle.dR * Math.sin(dAngle);
//				let oMerc = new mapboxgl.MercatorCoordinate(dX, dY);
//				aMercs.push(oMerc);
//				let oLngLat = oMerc.toLngLat();
//				aLngLats.push([oLngLat.lng, oLngLat.lat]);
//			}
//			oPathPart.oPath.properties.aMercs = aMercs;
//			oPathPart.oPath.coordinates = aLngLats;
//			oPathPart.oPath.changed();
//			oPathPart.updateDraw(oState, oEvent, 'curve');
//		}
//	}
//	
//	if (oEvent.keyCode === 80)
//	{
//		$.post('/api/print/', g_sCsv, function() {alert("success");}).fail(function(jqXHR, textStatus, errorThrown) {alert(jqXHR.responseText);});
//	}
//};
//
//EditPathMode.onMouseMove = function(oState, oEvent)
//{
//	let oPathPart = this.getCurrentPart(oState);
//	if (oPathPart == undefined)
//		return;
//
//	if (oPathPart.sMode === 'free')
//	{
//		if (oPathPart.oLine && oPathPart.oLine.properties.sDrawState === 'draw')
//		{
//			oPathPart.updateDraw(oState, oEvent, 'free');
//		}
//	}
//	else if (oPathPart.sMode === 'curve')
//	{
//		let dHdg = heading(oPathPart.oLine.properties.aMercs[0], oPathPart.oLine.properties.aMercs[1], true) + Math.PI / 2;
//		let oMouseMerc = mapboxgl.MercatorCoordinate.fromLngLat(oEvent.lngLat);
//		let aSnapPt = snap(oMouseMerc.x, oMouseMerc.y, oPathPart.oMdpt.properties.aMercs[0].x, oPathPart.oMdpt.properties.aMercs[0].y, dHdg, false, true);
//		let oSnapMerc = new mapboxgl.MercatorCoordinate(aSnapPt[0], aSnapPt[1]);		
//		let oP1 = oPathPart.oStart.properties.aMercs[0];
//		let oP3 = oPathPart.oEnd.properties.aMercs[0];
//		let oMdpt = oPathPart.oMdpt.properties.aMercs[0];
//		let dMaxRadius = length(oP1.x, oP1.y, oMdpt.x, oMdpt.y) * 0.90;
//		let dCurveDistance = length(oSnapMerc.x, oSnapMerc.y, oMdpt.x, oMdpt.y);
//		if (dCurveDistance > dMaxRadius)
//		{
//			let dCurveHdg = heading(oMdpt, oSnapMerc, true);
//			oSnapMerc.x = oMdpt.x + Math.cos(dCurveHdg) * dMaxRadius;
//			oSnapMerc.y = oMdpt.y - Math.sin(dCurveHdg) * dMaxRadius;
//		}
//		
//		oPathPart.oCurve.properties.aMercs[0] = oSnapMerc;
//		let oSnapLngLat = oSnapMerc.toLngLat();
//		oPathPart.oCurve.updateCoordinate(oSnapLngLat.lng, oSnapLngLat.lat);
//		
//		let oP2 = oPathPart.oCurve.properties.aMercs[0];
//		let oCenter = center(oP1, oP2, oP3);
//		let dCenterAngle = angle(oP1.x, -oP1.y, oCenter.x, -oCenter.y, oP3.x, -oP3.y);
//		let dR = length(oP1.x, oP1.y, oCenter.x, oCenter.y);
//		let oCenterLngLat = new mapboxgl.MercatorCoordinate(oCenter.x, oCenter.y).toLngLat();
//		oPathPart.oWidth1.updateCoordinate(oCenterLngLat.lng, oCenterLngLat.lat);
//		let dStartAngle = heading(oCenter, oP1, true);
//		let dEndAngle = heading(oCenter, oP3, true);
//		let dAngleDiff = dEndAngle - dStartAngle;
//		let sDetail = 'S Angle:&emsp;' + dStartAngle + '</br>';
//		sDetail += 'E Angle:&emsp;' + dEndAngle + '</br>';
//		sDetail += 'D Angle:&emsp;' + dAngleDiff + '</br>';
//		sDetail += 'C Angle:&emsp;' + dCenterAngle + '</br>';
//		if (dAngleDiff < dCenterAngle)
//		{
//			if (dStartAngle < dEndAngle)
//				dStartAngle += Math.PI * 2;
//			else
//				dEndAngle += Math.PI * 2;
//		}
//
//
//		let dMaxStep = 0.1 * Math.PI / 180;
//		if (dMaxStep > dCenterAngle)
//			dMaxStep = dCenterAngle;
//		
//		let nSteps = (dCenterAngle / dMaxStep + 1) | 0;
//		let dStepSize = dCenterAngle / nSteps;
//		if (dAngleDiff > -dCenterAngle - 0.000000000001 && dAngleDiff < -dCenterAngle + 0.000000000001 || dAngleDiff > dCenterAngle + 0.000000000001)
//			dStepSize *= -1;
//		let aLngLats = [];
//		let aMercs = [];
//		
//		oPathPart.oCircle.dH = oCenter.x;
//		oPathPart.oCircle.dK = oCenter.y;
//		oPathPart.oCircle.dR = dR;
//		oPathPart.oCircle.nSteps = nSteps;
//		oPathPart.oCircle.dStepSize = dStepSize;
//		oPathPart.oCircle.dStartAngle = dStartAngle;
//		oPathPart.oCircle.dHdg = dHdg;
//		oPathPart.oCircle.dRightHand = rightHand(oP1.x, oP1.y, oP2.x, oP2.y, oP3.x, oP3.y, true);
//		sDetail += 'Righthand:&emsp;' + oPathPart.oCircle.dRightHand + '</br>';
//		for (let nIndex = 0; nIndex <= nSteps; nIndex++)
//		{
//			let dAngle = dStartAngle + dStepSize * nIndex;
//			let dX = oCenter.x + dR * Math.cos(dAngle);
//			let dY = oCenter.y - dR * Math.sin(dAngle);
//			let oMerc = new mapboxgl.MercatorCoordinate(dX, dY);
//			aMercs.push(oMerc);
//			let oLngLat = oMerc.toLngLat();
//			aLngLats.push([oLngLat.lng, oLngLat.lat]);
//		}
//		oPathPart.oPath.properties.aMercs = aMercs;
//		oPathPart.oPath.coordinates = aLngLats;
//		oPathPart.oPath.changed();
//		oPathPart.updateDraw(oState, oEvent, 'curve');
////		$("#detail").empty().html(sDetail).show();
//	}
//};
//
//
//EditPathMode.toDisplayFeatures = function(oState, oGeojson, fnDisplay) 
//{
//	fnDisplay(oGeojson);
//};
//
//
//EditPathMode.onStop = function(oState, oEvent)
//{
//	for (let oPath of oState.oPathStack.values())
//		oPath.setDrawState('set');
//};
//
//
//EditPathMode.createAndAddFeature = function(oProps, sType, aCoordinates, bAdd)
//{
//	let oAllProps = Object.assign({sDrawState: 'draw', aMercs: []}, oProps)
//	if (aCoordinates.length == 2 && !Array.isArray(aCoordinates[1]))
//		oAllProps.aMercs.push(mapboxgl.MercatorCoordinate.fromLngLat(aCoordinates));
//	else
//	{
//		for (let nIndex = 0; nIndex < aCoordinates.length; nIndex++)
//			oAllProps.aMercs.push(mapboxgl.MercatorCoordinate.fromLngLat(aCoordinates[nIndex]));
//	}
//	let oFeature = this.newFeature({type: 'Feature', properties: oAllProps, geometry: {type: sType, coordinates: aCoordinates}});
//	if (bAdd == true)
//		this.addFeature(oFeature);
//	return oFeature;
//};
//
//
//EditPathMode.removePath = function(oPathPart)
//{
//	this.deleteFeature(oPathPart.oStart.id);
//	this.deleteFeature(oPathPart.oEnd.id);
//	this.deleteFeature(oPathPart.oCurve.id);
//	this.deleteFeature(oPathPart.oMdpt.id);
//	this.deleteFeature(oPathPart.oLine.id);
//	this.deleteFeature(oPathPart.oPath.id);
//	this.deleteFeature(oPathPart.oRight.id);
//	this.deleteFeature(oPathPart.oLeft.id);
//	this.deleteFeature(oPathPart.oWidth1.id);
//}

//function updateFeaturePoint(nIndex, aMercs, oFeature)
//{
//	let oLngLat = aMercs[nIndex].toLngLat();
//	oFeature.updateCoordinate(nIndex, oLngLat.lng, oLngLat.lat);
//}
//
//function PathPart(oLine, oPath, oStart, oEnd, oCurve, oMdpt, oRight, oLeft, oWidth1, oWidth2)
//{
//	this.oLine = oLine;
//	this.oPath = oPath;
//	this.oStart = oStart;
//	this.oEnd = oEnd;
//	this.oCurve = oCurve;
//	this.oMdpt = oMdpt
//	this.oRight = oRight;
//	this.oLeft = oLeft;
//	this.oWidth1 = oWidth1;
//	this.oWidth2 = oWidth2;
//	this.sMode = 'free';
//	this.oCircle = {dR: 0};
//	this.dPrevHdg = 0.0;
//}
//
//PathPart.prototype.setDrawState = function(sDrawState)
//{
//	if (this.oLine)
//	{
//		this.oLine.properties.sDrawState = sDrawState;
//		this.oLine.changed();
//	}
//	if (this.oPath)
//	{
//		this.oPath.properties.sDrawState = sDrawState;
//		this.oPath
//	}
//	if (this.oStart)
//	{
//		this.oStart.properties.sDrawState = sDrawState;
//		this.oStart.changed();
//	}
//	if (this.oEnd)
//	{
//		this.oEnd.properties.sDrawState = sDrawState;
//		this.oEnd.changed();
//	}
//	if (this.oCurve)
//	{
//		this.oCurve.properties.sDrawState = sDrawState;
//		this.oCurve.changed();
//	}
//	if (this.oRight)
//	{
//		this.oRight.properties.sDrawState = sDrawState;
//		this.oRight.changed();
//	}
//	if (this.oLeft)
//	{
//		this.oLeft.properties.sDrawState = sDrawState;
//		this.oLeft.changed();
//	}
//	if (this.oWidth1)
//	{
//		this.oWidth1.properties.sDrawState = sDrawState;
//		this.oWidth1.changed();
//	}
//	if (this.oWidth2)
//	{
//		this.oWidth2.properties.sDrawState = sDrawState;
//		this.oWidth2.changed();
//	}
//};
//
//PathPart.prototype.updateDraw = function(oState, oEvent, sMode)
//{
//	let aLineMercs = this.oLine.properties.aMercs
//	let aPathMercs = this.oPath.properties.aMercs;
//	if (sMode == 'free')
//	{
//		aPathMercs[aPathMercs.length - 1] = mapboxgl.MercatorCoordinate.fromLngLat(oEvent.lngLat);
//		aLineMercs[1] = mapboxgl.MercatorCoordinate.fromLngLat(oEvent.lngLat);
//		this.oLine.updateCoordinate(1, oEvent.lngLat.lng, oEvent.lngLat.lat);
//		this.oEnd.properties.aMercs[0] = mapboxgl.MercatorCoordinate.fromLngLat(oEvent.lngLat);
//		this.oEnd.updateCoordinate(oEvent.lngLat.lng, oEvent.lngLat.lat);
//		let aMdpt = midpoint(this.oStart.properties.aMercs[0], this.oEnd.properties.aMercs[0])
//		this.oMdpt.properties.aMercs[0].x = aMdpt.x;
//		this.oMdpt.properties.aMercs[0].y = aMdpt.y;
//		let oMdptLngLat = this.oMdpt.properties.aMercs[0].toLngLat();
//		this.oMdpt.updateCoordinate(oMdptLngLat.lng, oMdptLngLat.lat);
//	}
//	
//	let aLeftMercs = [];
//	let aRightMercs = [];
//	
//	for (let nIndex = 0; nIndex < aPathMercs.length; nIndex++)
//	{
//		aLeftMercs.push(new mapboxgl.MercatorCoordinate());
//		aRightMercs.push(new mapboxgl.MercatorCoordinate());
//	}
//	
//	let oCircle = this.oCircle;
//	let dWidth = meterInMercatorCoordinateUnits(this.oStart.coordinates[1]) * 3.2;
//	if (oCircle.dR == 0)
//	{
//		let dHdg = heading(this.oStart.properties.aMercs[0], this.oEnd.properties.aMercs[0], true);
//		for (let nIndex = 0; nIndex < aPathMercs.length; nIndex++)
//		{
//			aRightMercs[nIndex].x = aPathMercs[nIndex].x - Math.sin(dHdg) * dWidth;
//			aRightMercs[nIndex].y = aPathMercs[nIndex].y - Math.cos(dHdg) * dWidth;
//
//			aLeftMercs[nIndex].x = aPathMercs[nIndex].x - Math.sin(dHdg - Math.PI) * dWidth;
//			aLeftMercs[nIndex].y = aPathMercs[nIndex].y - Math.cos(dHdg - Math.PI) * dWidth;
//		}
//	}
//	else
//	{
//		let dRightR;
//		let oLeftR;
//		if (oCircle.dRightHand > 0)
//		{
//			dRightR = oCircle.dR + dWidth;
//			dLeftR = oCircle.dR - dWidth;
//		}
//		else
//		{
//			dRightR = oCircle.dR - dWidth;
//			dLeftR = oCircle.dR + dWidth;
//		}
//		
//		for (let nIndex = 0; nIndex <= oCircle.nSteps; nIndex++)
//		{
//			let dAngle = oCircle.dStartAngle + oCircle.dStepSize * nIndex;
//			aRightMercs[nIndex].x = oCircle.dH + dRightR * Math.cos(dAngle);
//			aRightMercs[nIndex].y = oCircle.dK - dRightR * Math.sin(dAngle);
//
//			aLeftMercs[nIndex].x = oCircle.dH + dLeftR * Math.cos(dAngle);
//			aLeftMercs[nIndex].y = oCircle.dK - dLeftR * Math.sin(dAngle);
//		}
//	}
//	
//	this.oRight.properties.aMercs = aRightMercs;
//	this.oLeft.properties.aMercs = aLeftMercs;
//	let aLeftLngLats = [];
//	let aRightLngLats = [];
//	for (let nIndex = 0; nIndex < aRightMercs.length; nIndex++)
//	{
//		oRightLngLat = aRightMercs[nIndex].toLngLat();
//		aRightLngLats.push([oRightLngLat.lng, oRightLngLat.lat]);
//		oLeftLngLat = aLeftMercs[nIndex].toLngLat();
//		aLeftLngLats.push([oLeftLngLat.lng, oLeftLngLat.lat]);
//		
//	}
//	this.oRight.coordinates = aRightLngLats;
//	this.oLeft.coordinates = aLeftLngLats;
//	this.oRight.changed();
//	this.oLeft.changed();
//};

