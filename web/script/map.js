import {hashCoordinates, onlyUnique, latlngToPaddedPoints,
  buildRequestData, objectToSelectElementHtml,
  parseFlatLatLngArray} from './modules/util.js';
import  { findClosestSegment} from './modules/segments.js';
import { fetchHashesForPoint, isHashAvailable, getHashBoundaries} from './modules/hashBoundaries.js';
import {MapEventsDialog} from './modules/MapEventsDialog.js';
import {loadAllEvents} from './modules/api-events.js';

const orientElementToMouseEvent = (element, padding) => e => element.css({left: e.pageX + padding, top: e.pageY + padding});


const initPage = () => {

  L.Icon.Default.imagePath = "images/leaflet/";
  const roadLabel = $('<div></div>')
    .addClass("tooltip-content over-map")
    .hide()
    .css({position: "absolute"})
    .appendTo(document.body);

  const orientTooltipOnMouse = orientElementToMouseEvent(roadLabel, 10);

  roadLabel.mousemove(orientTooltipOnMouse);

  $("#mapid").mousemove(orientTooltipOnMouse);

  let currentHashBounds = null;


  const map = L.map("mapid", {
    center: [38.956, -77.149],
    zoom: 15,
    zoomControl: false
  });

  async function getCurrentHashBounds(latlng)
  {
    const hashResponse = await fetchHashesForPoint(latlng, map.getZoom());

    //see if I have entered a new hash, and load the new boundary boxes if I did
    const hash = hashCoordinates(latlng.lat, latlng.lng);
    if (isHashAvailable(hash))
    {
      if (currentHashBounds === null || currentHashBounds.hash !== hash)
        currentHashBounds = getHashBoundaries(hash);
    }
    return currentHashBounds;
  }


  let mappedLine = null;

  const hoverRoadLine = L.polyline([], {color: 'green'});


  const closeDialogHandler = event => {
    mappedLine.removeFrom(map);
    mappedLine = null;
    if (event)
      reloadEvents();
  };

  const mapEventsLayerGroup = L.layerGroup().addTo(map);

  const reloadEvents = () => loadAllEvents().then(events => {
      mapEventsLayerGroup.clearLayers();
      Object.values(events).forEach(eventPts => mapEventsLayerGroup.addLayer(L.polyline(eventPts, {color: 'black'})));
    });

  reloadEvents();


  async function mapClick(e)
  {
    if (mappedLine !== null)
      return;

    const {latlng, originalEvent} = e;

    const hashBounds = await getCurrentHashBounds(latlng);

    const closestSegment = await findClosestSegment(latlng, hashBounds);
    if (closestSegment !== null)
      mappedLine = new MapEventsDialog(map, closestSegment, {closeCallback: closeDialogHandler});
  }


  // track request initiation order so that if a later request finishes first
  // an earlier request won't change the highlght line
  let reqCounter = 0;
  let lastFinishedRequest = 0;
  async function mapMove(e)
  {
    const reqCount = ++reqCounter;
    const {latlng} = e;

    const hashBounds = await getCurrentHashBounds(latlng);

    if (hashBounds !== null)
    {
      const closestSegment = await findClosestSegment(latlng, hashBounds, map.getZoom());

      if (reqCounter < lastFinishedRequest)
        console.log('Req ' + lastFinishedRequest + ' already ran, so stop processing');
      else
      {
        lastFinishedRequest = reqCounter;
        if (closestSegment !== null)
        {
          roadLabel.html(closestSegment.name).show();
          if (mappedLine === null)
          {
            hoverRoadLine.setLatLngs(closestSegment.points);
            if (!map.hasLayer(hoverRoadLine))
              map.addLayer(hoverRoadLine);
          }
        }
        else
        {
          if (map.hasLayer(hoverRoadLine))
            map.removeLayer(hoverRoadLine);

          roadLabel.hide();
        }
      }
    }

  }



  L.tileLayer("https://api.tiles.mapbox.com/v4/{id}/{z}/{x}/{y}.png?access_token={accessToken}", {
    attribution: "Map data &copy; <a href='https://www.openstreetmap.org/'>OpenStreetMap</a> contributors, <a href='https://creativecommons.org/licenses/by-sa/2.0/'>CC-BY-SA</a>, Imagery &copy; <a href='https://www.mapbox.com/'>Mapbox</a>",
    maxZoom: 18,
    id: "mapbox.streets",
    accessToken: "pk.eyJ1Ijoia3J1ZWdlcmIiLCJhIjoiY2l6ZDl4dTlwMjJvaDJ3bW44bXFkd2NrOSJ9.KXqbeWgASgEUYQu0oi7Hbg"
  }).addTo(map);

  new L.Control.Zoom({position: "topright"}).addTo(map);


  map.on('mousemove', mapMove);
  map.on('click', mapClick);

};

$(document).on("initPage", initPage);