import {onlyUnique, latlngToPaddedPoints, hashCoordinates, buildRequestData, boundsContainsOneOf} from './util.js';
const hashCacheKey = hash => 'hashes.' + hash;
const isHashAvailable = hash => isHashCached(hash);
const isHashCached = hash => sessionStorage.getItem(hashCacheKey(hash)) !== null;
const getCachedHashData = hash => sessionStorage.getItem(hashCacheKey(hash));
const storeHashData = (hash, data) => sessionStorage.setItem(hashCacheKey(hash), data);

const pendingHashPromises = new Map();



const getHashesAroundPoint = latlng =>
  latlngToPaddedPoints(latlng)
    .map(coords => hashCoordinates(coords[0], coords[1]))
    .filter(onlyUnique);

const getNearbyHashBoundaries = latlng =>
  getHashesAroundPoint(latlng)
    .filter(isHashAvailable)
    .map(getHashBoundaries);

const getHashBoundaries = hash => new HashBoundaries(getCachedHashData(hash));

const fetchHashes = hashes => Promise.all(hashes.map(hash => fetchHash(hash, 18)));

const fetchHash = (hash, zoom) => {
  if (isHashCached(hash))
  {
    const cachedHash = getHashBoundaries(hash);
    if (cachedHash.getMaxZoom() >= zoom)
      return new Promise(resolve => resolve(getHashBoundaries(hash)));
  }

  if (pendingHashPromises.has(hash))
    return pendingHashPromises.get(hash);

  const pendingPromise = $.post("api/geosvc/bounds", buildRequestData({hash, zoom}))
    .done(data => storeHashData(hash, JSON.stringify(Object.assign(JSON.parse(data), {hash, maxZoom: zoom}))))
    .done(() => pendingHashPromises.delete(hash))
    .promise()
    .then(() => getHashBoundaries(hash));

  pendingHashPromises.set(hash, pendingPromise);
  return pendingPromise;
};

const fetchHashesForPoint = (latlng, zoom) => Promise.all(getHashesAroundPoint(latlng).map(hash => fetchHash(hash, zoom)));

class HashBoundaries {

  constructor(hashJson)
  {
    const allSegIds = this.allSegIds = [];
    const allSegmentsMap = this.allSegmentsMap = new Map();
    const hashBounds = JSON.parse(hashJson);
    this.hash = hashBounds.hash;
    this.maxZoom = hashBounds.maxZoom;
    
    delete hashBounds.hash;
    delete hashBounds.maxZoom;
    
    const zoomLevels = this.zoomLevelMap = new Map();
    Object.entries(hashBounds).forEach(([zoom, zoomSegments]) =>
    {
      const segmentMap = new Map();
      zoomLevels.set(1 * zoom, segmentMap);

      Object.entries(zoomSegments).forEach(([segId, segBounds]) => {
        const latLngBounds = L.latLngBounds([segBounds[1], segBounds[0]], [segBounds[3], segBounds[2]]);
        segmentMap.set(segId, latLngBounds);
        allSegIds.push(segId);
        allSegmentsMap.set(segId, latLngBounds);
      });
    });
  }

  getAllSegIds()
  {
    return this.allSegIds;
  }

  getAllSegmentBoundsMap()
  {
    return this.allSegmentsMap;
  }

  getZoomLevelBoundsMap(zoom)
  {
    const zoomMap = new Map();
    for (let [segmentZoom, zoomLevelMap] of this.zoomLevelMap)
    {
      if (zoom >= segmentZoom)
      {
        for (let [segId, segBounds] of zoomLevelMap)
          zoomMap.set(segId, segBounds);
      }
    }
    return zoomMap;
  }

  getSegIdsIntersectingPoints(points)
  {
    return [...this.allSegmentsMap.entries()]
      .filter(([segId, segBounds]) => boundsContainsOneOf(segBounds, points))
      .map(([segId]) => segId);
  }

  getHash()
  {
    return this.hash;
  }

  getMaxZoom()
  {
    return this.maxZoom;
  }
}

export {fetchHashesForPoint, isHashAvailable, getHashBoundaries, getNearbyHashBoundaries, fetchHashes};