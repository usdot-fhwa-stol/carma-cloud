var g_oSims = {};
var g_oSimColors =
[
	"#ff0", "#f60", "#c00", "#f09", "#309", "#00c", "#09f",
	"#090", "#060", "#630", "#963", "#ccc", "#999", "#333"
];
var g_sFollowing = null;

const funcPointToPaddedBounds = point => [{x: point.x - 4, y: point.y - 4}, {x: point.x + 4, y:point.y + 4}];
const funcIsOnColorLayer = oFeature => 
{
	let sId = oFeature.layer.id;
	for (let sColor of g_oSimColors)
		if (sId === sColor)
			return true;
	
	return false;
};

function mapClickHandler({target, point, lngLat})
{
	const oFeatures = target.queryRenderedFeatures(funcPointToPaddedBounds(point))
		.filter(funcIsOnColorLayer);
	if (oFeatures.length > 0)
		g_sFollowing = {"id": oFeatures[0].properties.id, "events": {}};
	else
		g_sFollowing = null;
}

function simglSuccess(sData, sStatus, oJqXHR)
{
	let oSims = JSON.parse(sData);
	for (let sId in oSims)
	{
		let oSim = g_oSims[sId]; // get sim vehicle from list
		if (oSim === undefined)
		{
			oSim = [0.0, 0.0]; // default geo-point
			g_oSims[sId] = oSim; // save sim coordinate array

			let sColor = g_oSimColors[Math.floor(Math.random() * g_oSimColors.length)];
			let oLayer = g_oMap.getSource(sColor);
			if (oLayer === undefined) // create layer on first point
			{
				g_oMap.addLayer
				(
					{
						"id": sColor, "type": "circle", "source": {"type": "geojson", 
						"data": {"type": "FeatureCollection", "features": [{"type": "Feature", "geometry": {"type": "Point", "coordinates": oSim}, "properties" : {"id": sId}}]}}, 
						"paint": {"circle-radius": 2, "circle-color": sColor}
					}
				);
			}
			else
				oLayer._data.features.push({"type": "Feature", "geometry": {"type": "Point", "coordinates": oSim}, "properties": {"id": sId}});
		}
		let oLatLng = oSims[sId];
		oSim[0] = oLatLng[1]; // switch to lon lat order
		oSim[1] = oLatLng[0];
	}

	for (let nIndex in g_oSimColors)
	{
		let oLayer = g_oMap.getSource(g_oSimColors[nIndex]);
		if (oLayer !== undefined)
			oLayer.setData(oLayer._data);
	}
	if (g_sFollowing !== null && g_sFollowing !== undefined)
	{
		let oLngLat = g_oSims[g_sFollowing.id];
		$.ajax("api/event/msgs",
		{
			method: "POST",
			data: {"token": sessionStorage.token,
					 "lat": oLngLat[1], "lon": oLngLat[0]},
			success: msgsSuccess,
			timeout: 500
		});
	}
	else
		$("#simDetail").hide();
}


function msgsSuccess(sData, sStatus, oJqXHR)
{
	let oLngLat = g_oSims[g_sFollowing.id];
	let sDetail = "Lon: " + oLngLat[0] + " Lat: " + oLngLat[1];
	let oEvents = JSON.parse(sData);
	for (let [sEvent, oEvent] of Object.entries(oEvents))
	{
		let oPrevEvent = g_sFollowing.events[sEvent];
		if (oPrevEvent === undefined)
		{
			g_sFollowing.events[sEvent] = {"dist": oEvent.dist, "lanes": undefined};
			continue;
		}
		else
		{
			if (oPrevEvent.lanes === undefined)
			{
				if (oPrevEvent.dist < oEvent.dist) // current distance is greater
					oPrevEvent.lanes = oEvent["lanes-be"]
				else
					oPrevEvent.lanes = oEvent["lanes-eb"];
			}
		}
		sDetail += "<br/><br/>";
		sDetail += "Type: " + g_oTypes[oEvent.type] + "&emsp;Lanes Affected: " + g_oLanes[oPrevEvent.lanes];
		for (let [sRop, oRop] of Object.entries(oEvent.ropdetails))
		{
			for (let oControl of oRop.controls)
			{
				sDetail += "<br/>&emsp;";
				oLookup = g_oControls[oControl.type];
				sDetail += oLookup.label;
				for (let [sId, sValue] of Object.entries(oControl.inputs))
				{
					if (oLookup.vals === undefined)
						sDetail += " " + sValue;
					else
						sDetail += " " + oLookup.vals[sValue];
					if (oLookup.uom !== undefined)
						sDetail += " " + oLookup.uom
				}
			}
		}
	}
	$("#simDetail").empty().html(sDetail).show();
	g_oMap.flyTo({"center": oLngLat, "zoom": 16});
}


function simSuccess(sData, sStatus, oJqXHR)
{
	var oSims = JSON.parse(sData);
	for (var sId in oSims)
	{
		var oSim = g_oSims[sId];
		if (oSim === undefined)
		{
			var sColor = g_oSimColors[Math.floor(Math.random() * g_oSimColors.length)];
			oSim = L.circleMarker(oSims[sId], {radius: 2, stroke: false, fill: true, fillColor: sColor, fillOpacity: 1.0});
			g_oSims[sId] = oSim;
			g_oMap.addLayer(oSim);
		}
		oSim.setLatLng(oSims[sId]);
	}
}


function pollSim()
{
	$.ajax("api/geosvc/sim",
	{
		method: "POST",
		data: {"token": sessionStorage.token},
		success: simglSuccess,
		timeout: 900
	});
}


function radarStyles(oFeat)
{
	var oStyle = {};
	if (oFeat.layer.name === 'RDR05') oStyle.color = 'rgba(  0, 239, 231, 0.4)';
	if (oFeat.layer.name === 'RDR010') oStyle.color = 'rgba(  0, 156, 247, 0.4)';
	if (oFeat.layer.name === 'RDR015') oStyle.color = 'rgba(  0,   0, 247, 0.4)';
	if (oFeat.layer.name === 'RDR020') oStyle.color = 'rgba(  0, 255,   0, 0.4)';
	if (oFeat.layer.name === 'RDR025') oStyle.color = 'rgba(  0, 198,   0, 0.4)';
	if (oFeat.layer.name === 'RDR030') oStyle.color = 'rgba(  0, 140,   0, 0.4)';
	if (oFeat.layer.name === 'RDR035') oStyle.color = 'rgba(255, 255,   0, 0.4)';
	if (oFeat.layer.name === 'RDR040') oStyle.color = 'rgba(231, 189,   0, 0.4)';
	if (oFeat.layer.name === 'RDR045') oStyle.color = 'rgba(254, 147,   0, 0.4)';
	if (oFeat.layer.name === 'RDR050') oStyle.color = 'rgba(255,   0,   0, 0.4)';
	if (oFeat.layer.name === 'RDR055') oStyle.color = 'rgba(214,   0,   0, 0.4)';
	if (oFeat.layer.name === 'RDR060') oStyle.color = 'rgba(189,   0,   0, 0.4)';
	if (oFeat.layer.name === 'RDR065') oStyle.color = 'rgba(254,   0, 254, 0.4)';
	if (oFeat.layer.name === 'RDR070') oStyle.color = 'rgba(156,  82, 198, 0.4)';
	return oStyle;
}

//var sNow = "time=" + new Date().now + "; domain=.data-env.com; path=/";
//document.cookie = "r" + sNow;
//document.cookie = "t" + sNow;
//map.addLayer(new L.TileLayer.MVTSource({url: "https://testimrcp.data-env.com/mvt/rdr0/{z}/{x}/{y}", style: radarStyles}));
	
