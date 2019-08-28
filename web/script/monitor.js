var g_oMap;
const g_oSourceLayers = $.getJSON('mapbox/sourcelayers.json').promise();


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
                type = layer.type
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

            const fullSource = {type, layers: fullLayerDefs, id: sourceId, mapboxSource: Object.assign({}, source, {tiles: source.tiles})};
            oSources.set(sourceId, fullSource);
        }
    });
    return oSources;
}


async function initialize()
{
	g_oMap = new mapboxgl.Map({"container": "mapid", "style": "mapbox/streets-v11.json", "attributionControl": false,
		"minZoom": 4, "maxZoom": 24 "center": [-77.149, 38.956], "zoom": 15});

	g_oMap.dragRotate.disable(); // disable map rotation using right click + drag
	g_oMap.touchZoomRotate.disableRotation(); // disable map rotation using touch rotation gesture
	g_oMap.addControl(new mapboxgl.NavigationControl({showCompass: false}));
	g_oMap.showTileBoundaries = true;

	g_oMap.on('load', async function()
	{
		const oSources = buildSourceMap(await g_oSourceLayers);
		for (let oSrc of oSources.values())
		{
			g_oMap.addSource(oSrc.id, oSrc.mapboxSource);
			for (let oLayer of oSrc.layers)
				g_oMap.addLayer(oLayer);
		}
	});
	

	setInterval(pollSim, 1000);
}


$(document).on("initPage", initialize);
