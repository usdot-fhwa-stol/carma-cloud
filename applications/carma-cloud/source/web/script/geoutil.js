let sCsv;
let POW_OF_2 = [];
let METERS_PER_PIXEL = [];

for (let nIndex = 0; nIndex < 25; nIndex++)
{
	POW_OF_2.push(Math.pow(2, nIndex));
	METERS_PER_PIXEL.push(2 * Math.PI * 6378137 / (POW_OF_2[nIndex] * 256));
}


function midpoint(oStart, oEnd)
{
	let dX = (oStart.x + oEnd.x) / 2;
	let dY = (oStart.y + oEnd.y) / 2;
	return {x: dX, y: dY};
};


function headingFlipY(oP1, oP2)
{
	let dRads = angle(oP1.x, -oP1.y, oP2.x, -oP2.y);
	if (oP2.y > oP1.y)
		dRads = 2 * Math.PI - dRads;
	
	return dRads;
}

function headingA(aP1, aP2)
{
	let dRads = angle(aP1[0], aP1[1], aP2[0], aP2[1]);
	if (aP1[1] > aP2[1])
		dRads = 2 * Math.PI - dRads;
	
	return dRads;
}
function heading(oP1, oP2)
{
	let dRads = angle(oP1.x, oP1.y, oP2.x, oP2.y);
	if (oP1.y > oP2.y)
		dRads = 2 * Math.PI - dRads;
	
	return dRads;
}


function angle(dX1, dY1, dX2, dY2, dX3, dY3)
{
	if (dX3 === undefined && dY3 === undefined)
	{
		dX3 = dX2;
		dY3 = dY2;
		dX2 = dX1;
		dY2 = dY1;
		dX1 = dX2 + 1;
		dY1 = dY2;
	}
	let dUi = dX1 - dX2;
	let dUj = dY1 - dY2;
	let dVi = dX3 - dX2;
	let dVj = dY3 - dY2;
	let dot = dUi * dVi + dUj * dVj;
	let dLenU = Math.sqrt(dUi * dUi + dUj * dUj);
	let dLenV = Math.sqrt(dVi * dVi + dVj * dVj);
	if (dLenU === 0 || dLenV === 0)
		return Number.NaN;
	let dVal = dot / (dLenU * dLenV);
	dVal = (dVal * 1000000000000) / 1000000000000;
	if (dVal > 1 || dVal < -1)
		return Number.NaN;
	
	return Math.acos(dVal);
};


function snapFlipY(dX, dY, dX0, dY0, dHdg, bOnlyPositive)
{
	let dXp = dX - dX0;
	let dYp = -dY + dY0;
	let dXd = Math.cos(dHdg);
	let dYd = Math.sin(dHdg);
	
	let dU = dXp * dXd + dYp * dYd;
	let dV = dXd * dXd + dYd * dYd;
	
	if (bOnlyPositive && dU <= 0)
		return [dX0, dY0];
	
	return [dX0 + (dU * dXd / dV), dY0 - (dU * dYd / dV)];
}


function snap(dX, dY, dX0, dY0, dHdg, bOnlyPositive)
{
	let dXp = dX - dX0;
	let dYp = dY - dY0;
	let dXd = Math.cos(dHdg);
	let dYd = Math.sin(dHdg);
	
	let dU = dXp * dXd + dYp * dYd;
	let dV = dXd * dXd + dYd * dYd;
	
	if (bOnlyPositive && dU <= 0)
		return [dX0, dY0];
	
	return [dX0 + (dU * dXd / dV), dY0 + (dU * dYd / dV)];
}


function snapToLine(dX, dY, dX1, dY1, dX2, dY2)
{
	let aReturn = [Number.NaN, Number.NaN];
	let dXd = dX2 - dX1;
	let dYd = dY2 - dY1;
	let dXp = dX - dX1;
	let dYp = dY - dY1;
	
	if (dXd === 0 && dYd === 0) // line segment is a point
		return aReturn;

	let dU = dXp * dXd + dYp * dYd;
	let dV = dXd * dXd + dYd * dYd;

	if (dU < 0 || dU > dV) // nearest point is not on the line
	{
		return aReturn;
	}

	// find the perpendicular intersection of the point on the line
	aReturn[0] = dX1 + (dU * dXd / dV);
	aReturn[1] = dY1 + (dU * dYd / dV);
	
	return aReturn;
}


function rightHandFlipY(dX, dY, dX1, dY1, dX2, dY2)
{
	let dXp = dX - dX1;
	let dXd = dX2 - dX1;
	
	let dYp = -(dY - dY1);
	let dYd = -(dY2 - dY1);
	
	return (dXd * dYp) - (dYd * dXp);
}


function rightHand(dX, dY, dX1, dY1, dX2, dY2)
{
	let dXp = dX - dX1;
	let dXd = dX2 - dX1;

	let dYp = dY - dY1;
	let dYd = dY2 - dY1;
	
	return (dXd * dYp) - (dYd * dXp);
}


function newCoordinates(dX, dY, nDefaultPoints)
{
	if (nDefaultPoints === 1)
		return [dX, dY];
	
	let aReturn = [];
	for (let nIndex = 0; nIndex < nDefaultPoints; nIndex++)
		aReturn.push([dX, dY]);
	
	return aReturn;
}


function length(dX1, dY1, dX2, dY2)
{
	return Math.sqrt(sqDist(dX1, dY1, dX2, dY2));
}


function sqDist(dX1, dY1, dX2, dY2)
{
	let dDeltaX = dX2 - dX1;
	let dDeltaY = dY2 - dY1;
	return dDeltaX * dDeltaX + dDeltaY * dDeltaY;
}


function doubleCmpWithTol(dVal, dCompareTo, dTol)
{
	return dVal <= dCompareTo + dTol && dVal >= dCompareTo - dTol;
}

function center(oP1, oP2, oP3)
{
	let dDeltaX1 = oP2.x - oP1.x;
	let dDeltaY1 = oP2.y - oP1.y;
	let dDeltaX2 = oP3.x - oP2.x;
	let dDeltaY2 = oP3.y -oP2.y;
	let dM1 = dDeltaY1 / dDeltaX1;
	let dM2 = dDeltaY2 / dDeltaX2;
	
	if (doubleCmpWithTol(dM1, dM2, 0.000001))
		return {x: Number.NaN, y: Number.NaN};
	let dX = (dM1 * dM2 * (oP1.y - oP3.y) + dM2 * (oP1.x + oP2.x) - dM1 * (oP2.x + oP3.x)) / (2 * (dM2 - dM1));
	let dY = -1 * (dX - (oP1.x + oP2.x) / 2) / dM1 + (oP1.y + oP2.y) / 2;
	
	return {x: dX, y: -dY};
}

function centerFlipY(oP1, oP2, oP3)
{
	let dDeltaX1 = oP2.x - oP1.x;
	let dDeltaY1 = -oP2.y - (-oP1.y);
	let dDeltaX2 = oP3.x - oP2.x;
	let dDeltaY2 = -oP3.y - (-oP2.y);
	let dM1 = dDeltaY1 / dDeltaX1;
	let dM2 = dDeltaY2 / dDeltaX2;
	
	if (doubleCmpWithTol(dM1, dM2, 0.000001))
		return {x: Number.NaN, y: Number.NaN};
	let dX = (dM1 * dM2 * (-oP1.y - (-oP3.y)) + dM2 * (oP1.x + oP2.x) - dM1 * (oP2.x + oP3.x)) / (2 * (dM2 - dM1));
	let dY = -1 * (dX - (oP1.x + oP2.x) / 2) / dM1 + (-oP1.y + (-oP2.y)) / 2;
	
	return {x: dX, y: -dY};
}


function getSnapInfoForFeature(oFeature, oLngLat)
{
	return getSnapInfo(oFeature.geometry.coordinates, oLngLat);
}


function getSnapInfo(aCoords, oLngLat)
{
	let dClosest = Number.MAX_VALUE;
	let aSnapPoint;
	let aReturnSeg;
	let dX = oLngLat.lng;
	let dY = oLngLat.lat;
	let nReturnIndex = -1;
	for (let nIndex = 0, nLimit = aCoords.length - 1, dDist; nIndex < nLimit; nIndex++)
	{
		let aSeg = aCoords.slice(nIndex, nIndex + 2);
		let aInterPt = snapToLine(dX, dY, aSeg[0][0], aSeg[0][1], aSeg[1][0], aSeg[1][1]);
		if (Number.isNaN(aInterPt[0]))
			continue;
		dDist = sqDist(dX, dY, aInterPt[0], aInterPt[1]);
		if (dDist < dClosest)
		{
			dClosest = dDist;
			let dDistToOne = sqDist(aSeg[0][0], aSeg[0][1], aInterPt[0], aInterPt[1]);
			let dDistToTwo = sqDist(aSeg[1][0], aSeg[1][1], aInterPt[0], aInterPt[1]);
			if (dDistToOne <= dDistToTwo)
			{
				aSnapPoint = aSeg[0];
				nReturnIndex = nIndex;
			}
			else
			{
				aSnapPoint = aSeg[1];
				nReturnIndex = nIndex + 1;
			}
			aReturnSeg = aSeg;
		}
	}
	
	if (dClosest === Number.MAX_VALUE)
		return {aPt: [Number.NaN, Number.NaN], aSeg: [[Number.NaN, Number.NaN], [Number.NaN, Number.NaN]], dDist: Math.sqrt(dClosest), nIndex: nReturnIndex};
	
	return {aPt: aSnapPoint, aSeg: aReturnSeg, dDist: Math.sqrt(dClosest), nIndex: nReturnIndex};
}


//function getCircle(oP1, oP2, dInitHdg, nLimit, dTol, oPathPart, oState, oEvent, oMode)
//{
//	let dHdg = heading(oP1, oP2, true);
//	let dLineHdg = dHdg;
//	let nCount = 0;
//	let dPerpHdg = 0.0;
//	let oP3 = midpoint(oP1, oP2);
//	let oP4 = {x: 0, y: 0};
//	let dSkip = length(oP1.x, oP1.y, oP3.x, oP3.y) / 2;
//	let dMaxStep = 0.1 * Math.PI / 180;
//	let oCenter, dR, nSteps, dStepSize, dStartAngle, dEndAngle, dAngleDiff, dAngle, dCenterAngle;
//	let dLastHdg = dHdg > dInitHdg ? dLineHdg - (Math.PI / 2) : dLineHdg + (Math.PI / 2);
//	oCenter = {x: 0, y: 0};
//	g_sCsv = dInitHdg.toFixed(15);
//	while (Math.abs(dInitHdg - dHdg) > dTol && nCount++ < nLimit)
//	{
//		console.log(dInitHdg + " " + dHdg + " " + oCenter.x + " " + oCenter.y);
//		let dPerpHdg = dHdg > dInitHdg ? dLineHdg - (Math.PI / 2) : dLineHdg + (Math.PI / 2);
//		g_sCsv += '\n' + dHdg.toFixed(15) + ',' + oCenter.x.toFixed(15) + ',' + oCenter.y.toFixed(15) + ',' + dPerpHdg.toFixed(15) + ',' + dLastHdg.toFixed(15);
//		if (dLastHdg != dPerpHdg)
//		{
//			dSkip /= 2;
//			dLastHdg = dPerpHdg;
//		}
//		let dSkipX = dSkip * Math.cos(dPerpHdg);
//		let dSkipY = dSkip * Math.sin(dPerpHdg);
//		oP3.x = oP3.x + dSkipX;
//		oP3.y = oP3.y - dSkipY
//		g_sCsv += ',' + dSkipX.toFixed(15) + ',' + dSkipY.toFixed(15);
//		let oNewCenter = center(oP1, oP3, oP2);
//		if (isNaN(oNewCenter.x))
//			continue;
//
//		oCenter.x = oNewCenter.x;
//		oCenter.y = oNewCenter.y;
//		dR = length(oP1.x, oP1.y, oCenter.x, oCenter.y);
//		dCenterAngle = angle(oP1.x, -oP1.y, oCenter.x, -oCenter.y, oP2.x, -oP2.y);
//
//		dStartAngle = heading(oCenter, oP1, true);
//		dEndAngle = heading(oCenter, oP2, true);
//		dAngleDiff = dEndAngle - dStartAngle;
//		if (dAngleDiff < dCenterAngle)
//		{
//			if (dStartAngle < dEndAngle)
//				dStartAngle += Math.PI * 2;
//			else
//				dEndAngle += Math.PI * 2;
//		}
//		
//		if (dMaxStep > dCenterAngle)
//			dMaxStep = dCenterAngle;
//		
//		nSteps = (dCenterAngle / dMaxStep + 1) | 0;
//		dStepSize = dCenterAngle / nSteps;
//		if (dAngleDiff > -dCenterAngle - 0.000000000001 && dAngleDiff < -dCenterAngle + 0.000000000001 || dAngleDiff > dCenterAngle + 0.000000000001)
//			dStepSize *= -1;
//		
//		dAngle = dStartAngle + dStepSize;
//		oP4.x = oCenter.x + dR * Math.cos(dAngle);
//		oP4.y = oCenter.y - dR * Math.sin(dAngle)
//		dHdg = heading (oP1, oP4);
//	}
//	
//
//
//	console.log(nCount);
//	
//	let oCircle = {};
//	oCircle.dH = oCenter.x;
//	oCircle.dK = oCenter.y;
//	oCircle.dR = dR;
//	oCircle.nSteps = nSteps;
//	oCircle.dStepSize = dStepSize;
//	oCircle.dStartAngle = dStartAngle;
//	oCircle.dRightHand = rightHand(oP1.x, oP1.y, oP2.x, oP2.y, oP3.x, oP3.y, true);
//	
//	return oCircle;
//}


function getPolyFromSeg(aSeg, oMap, dPixelDelta)
{
	let oP1 = oMap.project(aSeg[0]);
	let oP2 = oMap.project(aSeg[1]);
	let dHdg = headingFlipY(oP1, oP2);
	let dHdgPrime = dHdg + Math.PI;
	let aReturn = [];
	let oLngLat = oMap.unproject([oP1.x - Math.sin(dHdg) * dPixelDelta, oP1.y - Math.cos(dHdg) * dPixelDelta]);
	aReturn.push([oLngLat.lng, oLngLat.lat]);
	oLngLat = oMap.unproject([oP1.x - Math.sin(dHdgPrime) * dPixelDelta, oP1.y - Math.cos(dHdgPrime) * dPixelDelta]);
	aReturn.push([oLngLat.lng, oLngLat.lat]);
	oLngLat = oMap.unproject([oP2.x - Math.sin(dHdgPrime) * dPixelDelta, oP2.y - Math.cos(dHdgPrime) * dPixelDelta]);
	aReturn.push([oLngLat.lng, oLngLat.lat]);
	oLngLat = oMap.unproject([oP2.x - Math.sin(dHdg) * dPixelDelta, oP2.y - Math.cos(dHdg) * dPixelDelta]);
	aReturn.push([oLngLat.lng, oLngLat.lat]);
	aReturn.push([aReturn[0][0], aReturn[0][1]]);
	
	let aNew = [];
	let len = aReturn.length;
	while (len-- > 0)
		aNew.push(aReturn[len]);
	
	return aNew;
}

function binarySearch(aArr, oKey, fnComp) 
{
	let nLow = 0;
	let nHigh = aArr.length - 1;

	while (nLow <= nHigh) 
	{
		let nMid = (nLow + nHigh) >> 1;
		let oMidVal = aArr[nMid];
		let nCmp = fnComp(oMidVal, oKey);

		if (nCmp < 0)
			nLow = nMid + 1;
		else if (nCmp > 0)
			nHigh = nMid - 1;
		else
			return nMid; // key found
	}
	return -(nLow + 1);  // key not found
}


function strCmp(s1, s2)
{
	let len1 = s1.length;
	let len2 = s2.length;
	let lim = Math.min(len1, len2);

	let k = 0;
	while (k < lim) 
	{
		let c1 = s1[k];
		let c2 = s2[k++];
		if (c1 !== c2)
			return c1 - c2;
	}
	return len1 - len2;
}


function isClockwise(aPoly)
{
	let dWinding = 0;
	for (let nIndex = 0; nIndex < aPoly.length - 1; nIndex++)
		dWinding += (aPoly[nIndex + 1][0] - aPoly[nIndex][0]) * (aPoly[nIndex + 1][1] + aPoly[nIndex][1]);
	
	return dWinding > 0;
}


function getPoly(aCenter, aInner, aOuter, dMaxDistance, nStartIndex, oInnerInfo, oOuterInfo)
{
	let nPos = nStartIndex;
	let nLim = Math.min(aCenter.length, aInner.length);
	nLim = Math.min(nLim, aOuter.length) - 1;
	let dTotalDist = 0;
	let nStart = nStartIndex;
	let nEnd = nStartIndex;
	while (nPos < nLim && dTotalDist < dMaxDistance)
	{
		dTotalDist += length(aCenter[nPos][0], aCenter[nPos++][1], aCenter[nPos][0], aCenter[nPos][1]);
		nEnd = nPos;
	}
	
	if (dTotalDist < dMaxDistance)
	{
		nLim = 0;
		nPos = nStartIndex;
		while (nPos > 0 && dTotalDist < dMaxDistance)
		{
			dTotalDist += length(aCenter[nPos][0], aCenter[nPos--][1], aCenter[nPos][0], aCenter[nPos][1]);
			nStart = nPos;
		}
	}
	
	let aPoly = [];
	let nInnerLen = nEnd - nStart;
	if (oInnerInfo.nIndex + nInnerLen >= aInner.length)
		nInnerLen = aInner.length - oInnerInfo.nIndex;
	let nOuterLen = nEnd - nStart;
	if (oOuterInfo.nIndex - nOuterLen < 0)
		nOuterLen = oOuterInfo.nIndex;
	
	let nLen = Math.min(nInnerLen, nOuterLen);
	
	for (let nIndex = oInnerInfo.nIndex, nCount = 0; nCount < nLen; nCount++)
		aPoly.push(aInner[nIndex++]);
	
	for (let nIndex = oOuterInfo.nIndex, nCount = 0; nCount < nLen; nCount++)
		aPoly.push(aOuter[nIndex--]);
	
	aPoly.push(aInner[oInnerInfo.nIndex]);
	
	if (isClockwise(aPoly))
		aPoly.reverse();
	
	return aPoly;
}


function fromIntDeg(nOrd)
{
	return nOrd / 10000000.0;
}


function createWholePoly(aInner, aOuter)
{
	return createPoly(aInner, aOuter, 0, Math.min(aInner.length - 1, aOuter.length - 1));
}


function createPoly(aInner, aOuter, nStart, nEnd)
{
	let aPoly = [];
	for (let nIndex = nStart; nIndex <= nEnd; nIndex++)
		aPoly.push([aInner[nIndex][0], aInner[nIndex][1]]);
	
	for (let nIndex = nEnd; nIndex >= nStart; nIndex--)
		aPoly.push([aOuter[nIndex][0], aOuter[nIndex][1]]);
	
	aPoly.push([aInner[nStart][0], aInner[nStart][1]]);
	return [aPoly];
}


function createMultiPolygon()
{
	
}


function linesConnect(aL1, aL2, dTol)
{
	let aS1 = aL1[0];
	let aE1 = aL1[aL1.length - 1];
	let aS2 = aL2[0];
	let aE2 = aL2[aL2.length -1];
	
	return (doubleCmpWithTol(aE1[0], aS2[0], dTol) && doubleCmpWithTol(aE1[1], aS2[1], dTol)) ||
		   (doubleCmpWithTol(aE1[0], aE2[0], dTol) && doubleCmpWithTol(aE1[1], aE2[1], dTol)) ||
		   (doubleCmpWithTol(aS1[0], aS2[0], dTol) && doubleCmpWithTol(aS1[1], aS2[1], dTol)) ||
		   (doubleCmpWithTol(aS1[0], aE2[0], dTol) && doubleCmpWithTol(aS1[1], aE2[1], dTol));
}
function lon2tile(dLon, nZoom)
{
	return (Math.floor((dLon + 180) / 360 * POW_OF_2[nZoom])); 
}

function lat2tile(dLat, nZoom)
{
	return (Math.floor((1 - Math.log(Math.tan(dLat * Math.PI / 180) + 1 / Math.cos(dLat  *Math.PI / 180)) / Math.PI) / 2 * POW_OF_2[nZoom]));
}

function getPolygonBoundingBox(oFeature)
{
	if (oFeature.bbox === undefined)
	{
		let nMinX = Number.MAX_VALUE;
		let nMinY = Number.MAX_VALUE;
		let nMaxX = -Number.MAX_VALUE;
		let nMaxY = -Number.MAX_VALUE;
		
		for (let aCoord of oFeature.geometry.coordinates[0].values())
		{
			if (aCoord[0] < nMinX)
				nMinX = aCoord[0];

			if (aCoord[0] > nMaxX)
				nMaxX = aCoord[0];

			if (aCoord[1] < nMinY)
				nMinY = aCoord[1];

			if (aCoord[1] > nMaxY)
				nMaxY = aCoord[1];
		}
		
		oFeature.bbox = [[nMinX, nMinY], [nMaxX, nMaxY]];
	}
	
	return oFeature.bbox;
}

export {METERS_PER_PIXEL, headingA, length, getSnapInfoForFeature, fromIntDeg, createWholePoly,
	getSnapInfo, createPoly, lon2tile, lat2tile, getPolygonBoundingBox};