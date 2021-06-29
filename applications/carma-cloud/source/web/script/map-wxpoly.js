import {oMap, oPopup, setCursor, switchListener, resetMode, switchMode, 
	carmaclPopupPos, addCtrlSources, removeCtrlSources, setMode, nMode} from './map.js';

function carmaclStartWx()
{
	if ($('#dlgEdit').dialog('isOpen') || $('#dlgDelete').dialog('isOpen'))
		return;
	if (nMode !== 0 && nMode !== 2)
		switchMode();
	if (nMode === 2)
	{
		resetMode();
		oMap.setPaintProperty('w-line', 'line-opacity', 0);
		oMap.setPaintProperty('w-poly', 'fill-opacity', 0);
		return;
	}
	resetMode();
	setMode(2);
	setCursor('crosshair');
	oMap.on('click', carmaclFirstWx);
	oMap.on('mousemove', carmaclPopupPos);
	oPopup.setHTML('Left-click<br>Top left corner').addTo(oMap);
}


function carmaclFirstWx({target, lngLat, point})
{
	let oLayer = oMap.getSource('weather-line');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		oData.geometry.coordinates = [[lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat], [lngLat.lng, lngLat.lat]];
		oLayer.setData(oData);
		oMap.setPaintProperty('w-poly', 'fill-opacity', 0);
		oMap.setPaintProperty('w-line', 'line-opacity', 1);
		setCursor('nwse-resize');
		oMap.on('mousemove', carmaclUpdateWx);
		switchListener('click', carmaclFirstWx, carmaclEndWx);
		oPopup.setHTML('Left-click<br>Bottom right corner');
	}
}


function carmaclUpdateWx({target, lngLat, point})
{
	let oLayer = oMap.getSource('weather-line');
	if (oLayer !== undefined)
	{
		let oData = oLayer._data;
		let oTL = oData.geometry.coordinates[0];
		oData.geometry.coordinates[1] = [lngLat.lng, oTL[1]];
		oData.geometry.coordinates[2] = [lngLat.lng, lngLat.lat];
		oData.geometry.coordinates[3] = [oTL[0], lngLat.lat];
		oLayer.setData(oData);
	}
}


function carmaclEndWx({target, lngLat, point})
{
	let oWeatherLine = oMap.getSource('weather-line');
	if (oWeatherLine !== undefined)
	{
		let oWeatherPoly = oMap.getSource('weather-polygon');
		{
			let aRing = [];
			for (let aPt of oWeatherLine._data.geometry.coordinates.values())
				aRing.push(aPt);

			let oData = oWeatherPoly._data;
			oData.geometry.coordinates = [aRing];
			oWeatherPoly.setData(oData);
			
			oMap.setPaintProperty('w-poly', 'fill-opacity', 0.5);
			oMap.setPaintProperty('w-line', 'line-opacity', 0);
			resetMode();
			$.ajax(
			{
				url: '/api/wxpoly',
				data: aRing[3][0].toFixed(7) + ',' + aRing[3][1].toFixed(7)+ ',' + aRing[1][0].toFixed(7) + ',' + aRing[1][1].toFixed(7),
				type: 'POST'
			}).done(weatherpolySuccess);
		}
	}
}


function weatherpolySuccess(oData, sStatus, oJqXHR)
{
	removeCtrlSources();
	addCtrlSources();
}

export {carmaclStartWx};
