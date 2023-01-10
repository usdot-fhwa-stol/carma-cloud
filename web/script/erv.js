import { MapControlIcons } from './MapControlIcons.js';

//Global Variables
var oMap;
var trackAllMarkers = [];

async function initialize() {
	await initMap();
	refreshRSUs();
}

/***
*Load OpenStreetsMap (OSM)
*/
async function initMap() {
	//Load Map
	let pJumpTo = $.getJSON('mapbox/jumpto.json').promise();
	oMap = new mapboxgl.Map({
		'container': 'mapid', 'style': 'mapbox/satellite-streets-v11.json', 'attributionControl': false,
		'minZoom': 4, 'maxZoom': 24, 'center': [-77.149, 38.956], 'zoom': 17, 'accessToken': 'pk.eyJ1IjoiZGR1MjAyMCIsImEiOiJjbDJyeTF2Y20wMDc5M2pwZGdxbXVqMzUxIn0.OGvU1S1C-r97rdh7QycMuQ'
	});
	window.mymap = oMap;
	oMap.dragRotate.disable(); // disable map rotation using right click + drag
	oMap.touchZoomRotate.disableRotation(); // disable map rotation using touch rotation gesture
	oMap.addControl(new mapboxgl.NavigationControl({ showCompass: false }));
	oMap.showTileBoundaries = false;
	oMap.on('load', async function() {
		oMap.addControl(new MapControlIcons([{ t: 'Jump To', i: 'jumpto' }]), 'top-right');
		buildJumpToDialog(await pJumpTo);
		$('button[title|="Jump To"]').click(toggleJumpTo);
	});
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
			let rsu_table = $('#rsu_table');
			rsu_table.empty();
			let row_header = $(`<tr><th>RSU Name</th><th>RSU latitude</th><th>RSU longitude</th><th>V2X Hub Port</th><th>Last Registered At</th></tr>`);
			rsu_table.append(row_header);

			//clear all current markers on the OSM 
			clearAllMarkers();

			for (let rsu of rsu_list) {
				let latitude = rsu.latitude.toString().split("");
				if (latitude.includes("-")) {
					latitude.splice(3, 0, '.')
				} else {
					latitude.splice(2, 0, '.')
				}
				latitude = latitude.join("");
				let longitude = rsu.longitude.toString().split("");
				if (longitude.includes("-")) {
					longitude.splice(3, 0, '.')
				} else {
					longitude.splice(2, 0, '.')
				}
				longitude = longitude.join("");

				//Populate RSU table
				let row = $(`<tr><td>${rsu.id}</td><td>${latitude}</td><td>${longitude}</td><td>${rsu.v2xhub_port}</td><td>${new Date(rsu.last_update_at).toLocaleString()}</td></tr>`);
				rsu_table.append(row);

				//Populate Map with list of RSU Geo location markers and add to map
				const marker = new mapboxgl.Marker({ color: "red" }).setLngLat([longitude, latitude]).addTo(oMap);
				trackAllMarkers.push(marker);
			}
		}).fail(function() {
			alert('Failed to retrieve registered RSUs!');
		});
}

function clearAllMarkers() {
	if (trackAllMarkers.length > 0) {
		for (var i = trackAllMarkers.length - 1; i >= 0; i--) {
			trackAllMarkers[i].remove();
		}
	}
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

