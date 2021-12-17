const POINTER_PADDING = 0.00005;
const REQUEST_JSON = 'json';
const REQUEST_FORM = 'form';
const toMicros = dbl => Math.round(dbl * 1000000);
const fromMicros = micros => micros / 1000000;

const truncateCoordinate = coord => Math.floor(coord * 100);
const hashCoordinates = (lat, lng) => truncateCoordinate(lng) * 65536 + truncateCoordinate(lat);
const onlyUnique = (v, i, a) => a.indexOf(v) === i;

const calculateAngleB = (latLngA, latLngB, latLngC) => {
  const p = L.Projection.SphericalMercator;

  const pointA = p.project(latLngA);
  const pointB = p.project(latLngB);
  const pointC = p.project(latLngC);

  const c = pointA.distanceTo(pointB);
  const b = pointA.distanceTo(pointC);
  const a = pointB.distanceTo(pointC);

  return Math.acos((a * a + c * c - b * b) / (2 * a * c)) * 180 / Math.PI;
};


const latlngToPaddedPoints = latlng => [
    [latlng.lat - POINTER_PADDING, latlng.lng - POINTER_PADDING],
    [latlng.lat + POINTER_PADDING, latlng.lng - POINTER_PADDING],
    [latlng.lat + POINTER_PADDING, latlng.lng + POINTER_PADDING],
    [latlng.lat - POINTER_PADDING, latlng.lng + POINTER_PADDING]
  ];



const getLatLngBoundsForDivMarker = (map, markerLatLng, markerDiv) => {
  const referencePoint = map.containerPointToLatLng([0, 0]);
  const brPoint = map.containerPointToLatLng([markerDiv.width(), markerDiv.height()]);
  const lngWidth = referencePoint.lng - brPoint.lng;
  const latHeight = referencePoint.lat - brPoint.lat;

  return L.latLngBounds([markerLatLng, [markerLatLng.lat - latHeight, markerLatLng.lng - lngWidth]]);
};


const boundsContainsOneOf = (bounds, latlngArray) => {
  for (let latlng of latlngArray)
  {
    if (bounds.contains(latlng))
      return true;
  }
  return false;
};


const buildRequestData = (requestObject = {}, options = {}) => {
  const {type = REQUEST_FORM} = options;


  switch (type)
  {
    case REQUEST_JSON:
      const paramData = {token: sessionStorage.token};
      if (requestObject.id)
        paramData.id = requestObject.id;
      
      paramData.data = JSON.stringify(requestObject);
      return $.param(paramData);
    case REQUEST_FORM:
      let requestString = '';
      requestObject.token = sessionStorage.token;

      Object.entries(requestObject).forEach(entry => {
        if (entry[1].constructor === Array)
        {
          const paramHolder = {};
          for (let val of entry[1])
          {
            paramHolder[entry[0]] = val;
            requestString += $.param(paramHolder) + '&';
          }
          delete requestObject[entry[0]];
        }
      });
      return requestString + $.param(requestObject);
}
};

const objectsToOptions = (valField, displayField) => (options, entry) => '<option value="' + entry[valField] + '">' + entry[displayField] + '</option>' + options;
const entriesToOptions = objectsToOptions(0, 1);
const entriesToAttributes = (attributes, entry) => entry[0] + '="' + entry[1] + '"' + attributes;

const objectToSelectElementHtml = (obj, attributes) => {
  const options = Object.entries(obj).reduce(entriesToOptions, '');
  const selectAttributes = attributes ? Object.entries(attributes).reduce(entriesToAttributes, '') : '';
  return '<select ' + selectAttributes + '>' + options + '</select>';
};

const parseFlatLatLngArray = flatArray => {
  const latLngArray = [];
  for (let i = 0; i < flatArray.length; i += 2)
  {
    const lat = flatArray[i];
    const lng = flatArray[i + 1];
    latLngArray[i / 2] = L.latLng(lat, lng);
  }
  return latLngArray;
};

const promiseData = data =>  new Promise(resolve => resolve(data));

const delay = (t, v) => new Promise(resolve => setTimeout(resolve.bind(null, v), t));

export {POINTER_PADDING, REQUEST_JSON, REQUEST_FORM,
  truncateCoordinate, hashCoordinates,
  onlyUnique, latlngToPaddedPoints, boundsContainsOneOf,
  toMicros, fromMicros,
  buildRequestData,
  calculateAngleB,
  parseFlatLatLngArray, delay, promiseData,
  entriesToOptions, objectsToOptions, objectToSelectElementHtml};
