import {buildRequestData, parseFlatLatLngArray} from './util.js';
const laneTypes = {};
const eventTypes = {};

$.post("api/event/lanes", buildRequestData(), null, 'json').promise().then(lanes => Object.assign(laneTypes, lanes));

$.post("api/event/types", buildRequestData(), null, 'json').promise().then(types => Object.assign(eventTypes, types));

const eventsMap = new Map();

const parseEventPoints = events => {
  const eventIds = Object.keys(events);
  const arrayValues = Array.isArray(events[eventIds[0]]);
  for (let eventId of eventIds)
  {
    if (arrayValues)
      events[eventId] = parseFlatLatLngArray(events[eventId]);
    else
    {
      events[eventId].points = parseFlatLatLngArray(events[eventId].pts);
      events[eventId].pts = null;
    }
  }
  return events;
};

const cleanupEventDates = events => 
{
  Object.values(events).forEach( event => {
    event.start = 1*event.start;
    event.end = 1*event.end;
  });
  
  return events;
};

const loadEventsInBounds = latLngBounds => $.post("api/event/list", buildRequestData({
    lat1: latLngBounds.getNorth(),
    lon1: latLngBounds.getWest(),
    lat2: latLngBounds.getSouth(),
    lon2: latLngBounds.getEast()}), null, 'json').promise()
    .then(parseEventPoints)
    .then(cleanupEventDates);

const loadAllEvents = () => $.post("api/event", buildRequestData(),
    null, 'json').promise()
    .then(parseEventPoints);

const loadEventDetails = id => $.post("api/event/detail", buildRequestData({id}),
    null, 'json')
    .then(parseEventPoints)
    .then(cleanupEventDates)
    .then(events => events[id]);



export {loadAllEvents, laneTypes, eventTypes, loadEventsInBounds, loadEventDetails};