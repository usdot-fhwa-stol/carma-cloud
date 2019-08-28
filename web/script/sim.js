var g_oSims = {};
var g_oSimColors =
[
	"#ff0", "#f60", "#c00", "#f09", "#309", "#00c", "#09f",
	"#090", "#060", "#630", "#963", "#ccc", "#999", "#333"
];


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
						"data": {"type": "Feature", "geometry": {"type": "MultiPoint", "coordinates": [oSim]}}}, 
						"paint": {"circle-radius": 2, "circle-color": sColor}
					}
				);
			}
			else
				oLayer._data.geometry.coordinates.push(oSim);
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
