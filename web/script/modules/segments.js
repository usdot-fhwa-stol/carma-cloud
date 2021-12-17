import {onlyUnique, latlngToPaddedPoints, hashCoordinates, boundsContainsOneOf,
  POINTER_PADDING,
  toMicros, calculateAngleB, buildRequestData,
  parseFlatLatLngArray} from './util.js';
import {getNearbyHashBoundaries, getHashBoundaries, fetchHashes} from './hashBoundaries.js';

const segCacheKey = segId => 'segments.' + segId;
const getCacheData = segId => sessionStorage.getItem(segCacheKey(segId));
const isSegmentAvailable = segId => isSegmentCached(segId);
const isSegmentCached = segId => getCacheData(segId) !== null;
const storeSegmentData = (segId, data) => sessionStorage.setItem(segCacheKey(segId), data);

const segmentArrayToMap = (map, seg) => {
  map[seg.id] = seg;
  return map;
};
const segIdsToSegmentoMap = (map, segId) => {
  map[segId] = getSegment(segId);
  return map;
};

const pendingSegPromises = new Map();

const latLngToPoint = latlng => new L.point(toMicros(latlng.lng), toMicros(latlng.lat));

const getSegment = segId => {
  const segment = JSON.parse(getCacheData(segId));
  segment.points = parseFlatLatLngArray(segment.points);
  segment.id = segId;
  return segment;
};


const loadSegmentsForPoints = points => {
  const hashes = points.map(latlng => hashCoordinates(latlng.lat, latlng.lng))
    .filter(onlyUnique);

  return fetchHashes(hashes)
    .then(hashBounds => Promise.all(hashBounds.map(hash => {

        const segIds = hash.getSegIdsIntersectingPoints(points);

        const localPendingPromises = segIds
          .map(segId => {
            if (pendingSegPromises.has(segId))
              return pendingSegPromises.get(segId);
            else if (isSegmentCached(segId))
              return new Promise(resolve => resolve(getSegment(segId)));
            else
              return null;
          })
          .filter(Boolean);

        const newSegIds = segIds.filter(segId => !isSegmentCached(segId) && !pendingSegPromises.has(segId));


        if (newSegIds.length === 0)
          return Promise.all(localPendingPromises).then(loadedSegs => loadedSegs.reduce(segmentArrayToMap, {}));
        else
        {
          const promises = [loadSegments(newSegIds, hash)];
          for (let pendingPromise of localPendingPromises)
            promises.push(pendingPromise);
          return Promise.all(promises)
            .then(([newSegs, ...pendingSegs]) => Object.assign(newSegs, pendingSegs.reduce(segmentArrayToMap, {})));
        }
      })).then(([firstHash, ...otherHashes]) => Object.assign(firstHash, ...otherHashes)));

};

const loadSegments = (fetchSegIds, hash) => {

  const loadSegPromise =
    $.post("api/geosvc/segpts", buildRequestData({
      hash: hash.getHash(),
      segid: fetchSegIds,
      zoom: 100
    }))
    .done(data => {
      Object.entries(JSON.parse(data)).forEach(([segId, segData]) => {
        const name = segData.name.trim();
        segData.name = name;
        segData.type = name.substring(name.length - 1);
        if(name.indexOf("_link") > 0)
          segData.type += '_l';
        storeSegmentData(segId, JSON.stringify(segData));
    });

      for (let fetchId of fetchSegIds)
        pendingSegPromises.delete(fetchId);
    })
    .promise()
    .then(() => fetchSegIds.reduce(segIdsToSegmentoMap, {}));

  for (let fetchId of fetchSegIds)
    pendingSegPromises.set(fetchId, loadSegPromise.then(() => getSegment(fetchId)));
  return loadSegPromise;
};


const findSegIdsToFetch = (latlng, hash, zoom) => {

  const fetchSegIds = [];
  hash.getZoomLevelBoundsMap(zoom).forEach((segBounds, segId) => {
    if (boundsContainsOneOf(segBounds, latlngToPaddedPoints(latlng))
      && !isSegmentCached(segId)
      && !pendingSegPromises.has(segId))
    {
      fetchSegIds.push(segId);
    }
  });
  return fetchSegIds;
};

const fetchSegmentsPromise = {};

const getSegIdsAroundPoint = (latlng, hash, zoom) => {
  const availableSegments = [];
  const segBoundsMap = zoom ? hash.getZoomLevelBoundsMap(zoom) : hash.getAllSegmentBoundsMap();

  segBoundsMap.forEach((segBounds, segId) => {
    if (segBounds.contains(latlng))
      availableSegments.push(segId);
  });
  return availableSegments;
};
  
async function getSegments(latlng, hash, zoom)
{
  const pendingSegments = [];
  const availableSegments = [];
  for(let segId of getSegIdsAroundPoint(latlng, hash, zoom))
  {
    if(isSegmentAvailable(segId))
      availableSegments.push(getSegment(segId));
    else if(pendingSegPromises.has(segId))
      pendingSegments.push(pendingSegPromises.get(segId));
  }
  
  if(pendingSegments.length > 0)
    return availableSegments.concat(await Promise.all(pendingSegments));
  else
    return availableSegments;
}

async function fetchSegments(latlng, hash, zoom) {

  const fetchSegIds = findSegIdsToFetch(latlng, hash, zoom);
  if (fetchSegIds.length > 0)
    await loadSegments(fetchSegIds, hash);

  return await getSegments(latlng, hash, zoom);
}

async function findSegmentSharingEndpoint({lastPoint, secondPoint, segmentType, oneway}) {

  const maxTurnAngle = 120;
  const minAngle = 180 - maxTurnAngle;
  const maxAngle = 180 + maxTurnAngle;

  for (let hashBounds of getNearbyHashBoundaries(lastPoint))
  {
    for (let segment of await getSegments(lastPoint, hashBounds))
    {
      if(segment.type !== segmentType)
        continue;
      if(segment.oneway !== oneway)
        continue;
      
      const {points} = segment;
      if (points[0].equals(lastPoint) && !points[1].equals(secondPoint))
      {
        const turnAngle = calculateAngleB(secondPoint, lastPoint, points[1]);
        if (turnAngle > minAngle && turnAngle < maxAngle)
          return segment;
      }
      if (points[points.length - 1].equals(lastPoint) && !points[points.length - 2].equals(secondPoint))
      {
        const turnAngle = calculateAngleB(secondPoint, lastPoint, points[points.length - 2]);
        if (turnAngle > minAngle && turnAngle < maxAngle)
          return segment;
      }
    }
  }
}


async function findClosestSegment(latlng, hashBounds, zoom) {
  if (!hashBounds)
    hashBounds = getHashBoundaries(hashCoordinates(latlng.lat, latlng.lng));

  const segments =  await fetchSegments(latlng, hashBounds, zoom);
  if (segments.length > 0)
  {
    const mousePoint = latLngToPoint(latlng);
    let closestSegment = null;
    let closestSegmentDistance = null;


    segments.forEach(segment => {
      const {points, name} = segment;
      const projectedPoints = points.map(latLngToPoint);

      for (let i = 0; i < projectedPoints.length - 1; ++i)
      {
        const distance = L.LineUtil.pointToSegmentDistance(mousePoint, projectedPoints[i], projectedPoints[i + 1]);
        if (closestSegment === null || distance < closestSegmentDistance)
        {
          closestSegment = segment;
          closestSegmentDistance = distance;
        }
      }
    });

    return closestSegmentDistance <= 2 * toMicros(POINTER_PADDING) ? closestSegment : null;
  }
  else
    return null;
}

export {isSegmentAvailable, findClosestSegment, findSegmentSharingEndpoint, loadSegmentsForPoints};
