import { MapControlIcons } from './MapControlIcons.js';

//Global Variables
var oMap;
const geojson = {
	'type': 'FeatureCollection',
	'features': []
};

const geojsonPoly = {
	'type': 'FeatureCollection',
	'features': []
};

/***
 * whether to stop overlay
 */
let bStop = false;

async function f() {
    console.log('Async function.');
    return Promise.resolve(1);
}

async function initialize() {
	await initMap().then((response)=>{
		//load map
		oMap.on('load', async function() {
			let pJumpTo = $.getJSON('mapbox/jumpto.json').promise();
			oMap.addControl(new MapControlIcons([{ t: 'Jump To', i: 'jumpto' }]), 'top-right');
			buildJumpToDialog(await pJumpTo);
			$('button[title|="Jump To"]').click(toggleJumpTo);	
			refreshRSUs();					
		});
	}) 
}

/***
*Load OpenStreetsMap (OSM)
*/
async function initMap() {
	//Init Map
	oMap = new mapboxgl.Map({
		'container': 'mapid', 'style': 'mapbox/satellite-streets-v11.json', 'attributionControl': false,
		'minZoom': 4, 'maxZoom': 24, 'center': [-77.149, 38.956], 'zoom': 17, 'accessToken': 'pk.eyJ1IjoiZGR1MjAyMCIsImEiOiJjbDJyeHJob2YwYnhwM2xtaG9zaDdnYTR4In0.Rh2bSS44c99BoDj2W7jjfw'
	});
	window.mymap = oMap;
	oMap.dragRotate.disable(); // disable map rotation using right click + drag
	oMap.touchZoomRotate.disableRotation(); // disable map rotation using touch rotation gesture
	oMap.addControl(new mapboxgl.NavigationControl({ showCompass: false }));
	oMap.showTileBoundaries = false;	
	return Promise.resolve("Successfully init map!");
}

/***
**POST: Request to get a list of RSUs that are registered. Populate the table with RSUs information and add markers on OSM for each RSU from the list.
 */
function refreshRSUs() {
	$.ajax(
		{
			'url': 'carmacloud/rsu/list',
			'method': 'POST',
			'dataType': 'json',
			'data': { 'token': sessionStorage.token }
		}).done(function(data) {
			let rsu_list = data["RSUList"];
			timeoutPageoverlay(500);
			displayRSUList(rsu_list);
		}).fail(function() {
			alert('Failed to retrieve registered RSUs!');
		});

}

/***
DELETE: Delete RSU */
function delRSU(v2xhub_port) {
	let isDel = confirm("Are you sure you would like to delete RSU with v2xhub port:" + v2xhub_port);
	if (isDel) {
		$.ajax({
			url: 'carmacloud/rsu' + '?' + $.param({ "v2xhub_port": v2xhub_port, "token": sessionStorage.token }),
			type: 'DELETE'
		}).done(function(data) {
			data = JSON.parse(data);
			let rsu_list =  data["RSUList"];
			console.log(rsu_list)
			timeoutPageoverlay(100);
			displayRSUList(rsu_list);
		})
			.fail(function() {
				alert('Failed to delete an RSUs!');
			});
	}
}

/***
Render RSUs on the table and MAP
*/
function displayRSUList(rsu_list) {
	showPageoverlay(`Loading RSUs...`);	
	let rsu_table = $('#rsu_table');
	rsu_table.empty();
	let row_header = $(`<tr><th>RSU Name</th><th>RSU latitude</th><th>RSU longitude</th><th>V2X Hub Port</th><th>Last Registered At</th><th>Controls</th></tr>`);
	rsu_table.append(row_header);

	//clear all current markers on the OSM 
	$("div.marker").remove();
	geojson.features = [];
	geojsonPoly.features=[];
	//clear polygons
	if (oMap.getLayer('polyLayer')) {
  		oMap.removeLayer('polyLayer');
	}
	if(oMap.getSource("polySrc")){
		oMap.removeSource('polySrc');
	}
	if (rsu_list != undefined) {
		for (let rsu of rsu_list) {
			let latitude = rsu.latitude;
			let longitude = rsu.longitude;
			let rsu_name = rsu.id;
			if (rsu_name.lastIndexOf("_") != -1) {
				rsu_name = rsu_name.substr(0, rsu_name.lastIndexOf("_"));
			}

			//Populate RSU table
			var btn = $('<button/>',
				{
					text: 'DELETE',
					id: "${rsu.v2xhub_port}",
					class: "w3-btn w3-red w3-round",
					click: function() { delRSU(rsu.v2xhub_port) }
				});
			let row = $(`<tr><td>${rsu_name}</td><td>${latitude}</td><td>${longitude}</td><td>${rsu.v2xhub_port}</td><td>${new Date(rsu.last_update_at).toLocaleString()}</td></tr>`);
			row.append(btn).end();
			row.mouseenter({ 'id': rsu.id + rsu.v2xhub_port }, highlightMarker).mouseleave({ 'id': rsu.id + rsu.v2xhub_port }, unHighlightMarker);
			rsu_table.append(row);

			//Populate geojson with features
			let feature = createFeature(rsu.id + rsu.v2xhub_port, longitude, latitude, rsu_name, latitude + ',' + longitude + '<br> V2x Hub Port: ' + rsu.v2xhub_port);
			geojson.features.push(feature);
			
			//populate godejsonPoly with features
			const southWest = [rsu.bounding_box_coordinates[1], rsu.bounding_box_coordinates[0]];
			const northEast = [rsu.bounding_box_coordinates[3], rsu.bounding_box_coordinates[2]];
			const southEast = [ rsu.bounding_box_coordinates[3],rsu.bounding_box_coordinates[0]];
			const northWest = [ rsu.bounding_box_coordinates[1],rsu.bounding_box_coordinates[2]];
			const polyCoordinates = [northEast, southEast, southWest, northWest,northEast];
			const polyFeature = createPolyFeature(rsu.id + rsu.v2xhub_port, polyCoordinates, rsu_name, latitude + ',' + longitude + '<br> V2x Hub Port: ' + rsu.v2xhub_port)
			geojsonPoly.features.push(polyFeature);
		}		
	}

	//Populate Map with list of RSU Geo location markers and add to map
	for (const feature of geojson.features) {
		// create a HTML element for each feature
		const el = document.createElement('div');
		el.className = 'marker';
		el.id = feature.properties.id;

		// make a marker for each feature and add it to the map
		new mapboxgl.Marker(el).setLngLat(feature.geometry.coordinates)
			.setPopup(new mapboxgl.Popup({ offset: 25 })
				.setHTML(`<p><b>${feature.properties.title}</b> <br>${feature.properties.description}</p>`))
			.addTo(oMap);
	}
	if(geojsonPoly.features.length>0){
		//Add source for polygon
		oMap.addSource('polySrc',
			{
				'type':'geojson',
				'data': geojsonPoly
			}
		);
		
		// Add a line around the polygon.
		oMap.addLayer( {
			'id': 'polyLayer',
			'type': 'line',
			'source': 'polySrc',
			'layout': {},
			'paint': {
				'line-color': '#008080',
				'line-width': 4
			}
		});
	}	
	showPageoverlay(`Successfully Loaded RSUs...`);	
}


function timeoutPageoverlay(nMillis = 1500)
{
	window.setTimeout(function()
	{
		if (!bStop)
			$('#pageoverlay').hide();
	}, nMillis);
}


function createFeature(id, longitude, latitude, title, description) {
	let feature = {
		'type': 'Feature',
		'geometry': {
			'type': 'Point',
			'coordinates': [longitude, latitude]
		},
		'properties': {
			'id': id,
			'title': title,
			'description': description
		}
	};
	return feature;
}

function createPolyFeature(id, coordinates, title, description){
	let feature = {
		'type': 'Feature',
		'geometry': {
			'type': 'Polygon',
			'coordinates': [coordinates]
		},
		'properties': {
			'id': id,
			'title': title,
			'description': description
		}
	};
	return feature;
}

function highlightMarker(oEvent) {
	if(document.getElementById(oEvent.data.id) !==null){
		document.getElementById(oEvent.data.id).classList.add('active');
	}	
}

function unHighlightMarker(oEvent) {
	if(document.getElementById(oEvent.data.id)!==null){
		document.getElementById(oEvent.data.id).classList.remove('active');
	}
}


function showPageoverlay(sContents)
{
	if (bStop)
		return;
	$('#pageoverlay p').html(sContents);
	$('#pageoverlay').show();
}

function toggleJumpTo() {
	let oDialog = $('#dlgJumpTo');
	if (oDialog.dialog('isOpen'))
		oDialog.dialog('close');
	else
		oDialog.dialog('open');
}

function buildJumpToDialog(oJumpToJson) {
	let oDialog = $('#dlgJumpTo');
	oDialog.dialog({ autoOpen: false, position: { my: 'center', at: 'center', of: '#mapid' }, resizable: true, draggable: true, width: 300 });
	let sHtml = '<label for="selJumpTo">Select location</label><select id="selJumpTo">';
	let nCount = 0;
	sHtml += `<option value=${nCount++}>`;
	let aOptions = [];
	let aJumpToLocs = [''];
	for (let [sName, aLngLat] of Object.entries(oJumpToJson))
		aOptions.push([sName, aLngLat]);
	aOptions.sort(function(o1, o2) { return o1[0].localeCompare(o2[0]); });

	for (let aOption of aOptions.values()) {
		sHtml += `<option value=${nCount++}>${aOption[0]}</option>`;
		aJumpToLocs.push(aOption[1]);
	}
	sHtml += '</select>';
	oDialog.html(sHtml);
	$('#selJumpTo').on('change', function() {
		let nVal = $(this).val();
		if (nVal != 0) {
			oMap.jumpTo({ center: aJumpToLocs[nVal], zoom: 17 });
			oDialog.dialog('close');
			$(this).val(0);
		}
	});
}

$("#refresh_rsu").on('click', refreshRSUs);

$(document).on('initPage', initialize);

