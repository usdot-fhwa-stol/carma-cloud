import {findSegmentSharingEndpoint, findClosestSegment} from './segments.js';


const markerIcons = [
  L.icon(Object.assign({}, L.Icon.Default.prototype.options, {shadowUrl: 'images/leaflet/marker-shadow.png', iconUrl: 'images/leaflet/marker-icon-B.png'})),
  L.icon(Object.assign({}, L.Icon.Default.prototype.options, {shadowUrl: 'images/leaflet/marker-shadow.png', iconUrl: 'images/leaflet/marker-icon-E.png'}))
];

class SelectedSegmentManager
{
  constructor(map, initialSegment, options)
  {
    this.segmentType = initialSegment.type;
    this.segmentOneway = initialSegment.oneway;

    this.options = options;
    this.map = map;
    this.segIds = [initialSegment.id];
    this.segBreakPoints = [];
    this.segBreakPointMarkers = [];
    const mappedLine = this.layerGroup = L.layerGroup().addTo(map);

    const {points} = initialSegment;

    const segmentLine = L.polyline(points, {color: 'red'});
    this.trackLine = segmentLine;
    this.layerGroup.addLayer(segmentLine);

    const markers = this.markers = [points[0], points[points.length - 1]]
      .map((initialPoint, index) => {
        const marker = L.marker(initialPoint, {icon: markerIcons[index]});
        mappedLine.addLayer(marker);
        const handler = new L.Handler.MarkerSnap(map, marker, {snapDistance: 1000, snapVertices: false});
        marker.snapediting = handler;
        handler.addGuideLayer(segmentLine);
        handler.enable();
        return marker;
      });

    const highlightLine = this.highlightLine = L.polyline(points, {color: 'yellow', weight: 5});
    mappedLine.addLayer(highlightLine);

    //console.log(segmentLine.getLatLngs());
    const updateHighlightLine = e => this._updateHighlightLine(e.target.getLatLng());

    markers.forEach(marker => {
      marker.on("dragend", updateHighlightLine);
      marker.on("dragend", () => {

        const {highlightLatLng1, highlightLatLng2,
          marker1, marker2} = this._getMarkerLocations();

        //if the B/E markers are out of order, switch them
        if (markers[0].getLatLng() !== highlightLatLng1)
        {
          markers[0].setLatLng(highlightLatLng1);
          markers[1].setLatLng(highlightLatLng2);
        }
      });
      marker.on("drag", updateHighlightLine);

      //marker.on("dragend", () => this.logSegments());
    });

  }

  _updateHighlightLine(draggingLatLng)
  {
    const markerLocations = this._getMarkerLocations();
    this._setHighlightLinePoints(markerLocations);
    this._adjustTrackLine(draggingLatLng, markerLocations);
    //console.log(markerLocations);g
  }

  _segmentChange(adjustedPoints)
  {
    if (this.options.onSegmentChange)
    {
      this.options.onSegmentChange({linePoints: this.trackLine.getLatLngs(), adjustedPoints, segIds: this.segIds});
    }
  }

  removeFrom(map)
  {
    this.layerGroup.removeFrom(map);
  }

  async _adjustTrackLine(draggingMarkerLatLng, markerLocations)
  {
    const {trackLine, segBreakPointMarkers, layerGroup} = this;
    const endpointTol = 75;
    const trackPoints = trackLine.getLatLngs();
    let adjacentLine;
    if (draggingMarkerLatLng.distanceTo(trackPoints[0]) < endpointTol)
      adjacentLine = await findSegmentSharingEndpoint({lastPoint: trackPoints[0], secondPoint: trackPoints[1], oneway: this.segmentOneway, segmentType: this.segmentType});
    else if (draggingMarkerLatLng.distanceTo(trackPoints[trackPoints.length - 1]) < endpointTol)
      adjacentLine = await findSegmentSharingEndpoint({lastPoint: trackPoints[trackPoints.length - 1], secondPoint: trackPoints[trackPoints.length - 2], oneway: this.segmentOneway, segmentType: this.segmentType});

    const {segIds, segBreakPoints} = this;
    if (adjacentLine) // should we extend?
    {
      const adjacentPoints = adjacentLine.points;
      if (adjacentPoints[0].equals(trackPoints[trackPoints.length - 1]))
      {
        segIds.push(adjacentLine.id);
        segBreakPoints.push(trackPoints.length - 1);
        const breakPointMarker = L.circleMarker(trackPoints[trackPoints.length - 1], {color: 'blue', radius: 1});
        layerGroup.addLayer(breakPointMarker);
        segBreakPointMarkers.push(breakPointMarker);
        trackLine.setLatLngs(trackPoints.slice(0, trackPoints.length - 1).concat(adjacentPoints));
      }
      else
      {
        const breakPointMarker = L.circleMarker(trackPoints[0], {color: 'blue', radius: 1});
        layerGroup.addLayer(breakPointMarker);

        segBreakPointMarkers.splice(0, 0, breakPointMarker);
        segIds.splice(0, 0, adjacentLine.id);
        const newPointCount = adjacentPoints.length - 1;
        for (let i = 0; i < segBreakPoints.length; ++i)
          segBreakPoints[i] += newPointCount;

        segBreakPoints.splice(0, 0, newPointCount);
        trackLine.setLatLngs(adjacentPoints.slice(0, adjacentPoints.length - 1).concat(trackPoints));
      }
      this._segmentChange(adjacentPoints);
    }
    else //should we contract?
    {
      const {subSegmentIndex2, subSegmentIndex1, highlightLatLng1, highlightLatLng2} = markerLocations;

      if (draggingMarkerLatLng.equals(highlightLatLng1))
      {
        let i;
        for (i = 0; i < segBreakPoints.length; ++i)
        {
          if (segBreakPoints[i] > subSegmentIndex1 - 1)
            break;
        }

        if (i > 0)
        {
          const newPointIndex1 = segBreakPoints[i - 1];
          segIds.splice(0, i);
          segBreakPoints.splice(0, i);
          segBreakPointMarkers.splice(0, i).forEach(marker => layerGroup.removeLayer(marker));
          trackLine.setLatLngs(trackPoints.slice(newPointIndex1));
          for (let i = 0; i < segBreakPoints.length; ++i)
            segBreakPoints[i] -= newPointIndex1;

          this._segmentChange(segBreakPoints.length > 0 ? trackLine.getLatLngs().slice(0, segBreakPoints[1]) : trackLine.getLatLngs());
        }
      }
      else
      {
        let i;
        for (i = segBreakPoints.length - 1; i > 0; --i)
        {
          if (segBreakPoints[i] <= subSegmentIndex2)
            break;
        }

        if (i < segBreakPoints.length - 1)
        {
          //   ++i;
          const newPointIndex2 = segBreakPoints[i + 1] + 1;
          segIds.splice(i + 2);
          segBreakPoints.splice(i + 1);
          segBreakPointMarkers.splice(i + 1).forEach(marker => layerGroup.removeLayer(marker));
          trackLine.setLatLngs(trackPoints.slice(0, newPointIndex2));

          this._segmentChange(segBreakPoints.length > 0 ? trackLine.getLatLngs().slice(segBreakPoints[segBreakPoints.length - 1]) : trackLine.getLatLngs());
        }
      }
    }
  }

  _getMarkerLocations()
  {
    const {markers, trackLine} = this;
    const markerLatLngs = markers.map(marker => marker.getLatLng());
    let subSegmentIndex1, subSegmentIndex2;
    let highlightLatLng1, highlightLatLng2;

    const points = trackLine.getLatLngs();

    for (let i = points.length - 1; i > 0; --i)
    {
      const point2 = points[i];
      const point1 = points[i - 1];

      for (let j = markerLatLngs.length - 1; j >= 0; --j)
      {
        const latlng = markerLatLngs[j];
        if (L.GeometryUtil.belongsSegment(latlng, point1, point2))
        {
          if (markerLatLngs.length === 2)//if this is the first point found
          {
            subSegmentIndex2 = latlng.equals(point2) ? i : i - 1;
            markerLatLngs.splice(j, 1);
            highlightLatLng2 = latlng;
          }
          else // if this is the second point found
          {
            subSegmentIndex1 = latlng.equals(point1) ? i - 1 : i;

            highlightLatLng1 = latlng;
            markerLatLngs.splice(j, 1);
          }
        }

      }
      if (markerLatLngs.length === 0)
        break;
    }
    
    //if we found both markers between the same two points, then make sure
    //that the right latLng is identified as being closer to the end
    if(Math.abs(subSegmentIndex2 - subSegmentIndex1 === -1))
    {
      const ep = points[subSegmentIndex1];
      if(ep.distanceTo(highlightLatLng2) > ep.distanceTo(highlightLatLng1) )
      {
        const temp = highlightLatLng2;
        highlightLatLng2 = highlightLatLng1;
        highlightLatLng1 = temp;
      }
    }
      

    return {subSegmentIndex1, subSegmentIndex2, highlightLatLng1, highlightLatLng2};
  }

  _setHighlightLinePoints(markerLocations)
  {
    const{highlightLine, trackLine} = this;
    const points = trackLine.getLatLngs();

    const {subSegmentIndex2, subSegmentIndex1, highlightLatLng1, highlightLatLng2} = markerLocations;

    const highlightPoints = subSegmentIndex2 < subSegmentIndex1 ?
      [highlightLatLng1, highlightLatLng2]
      : [highlightLatLng1, ...points.slice(subSegmentIndex1, subSegmentIndex2), highlightLatLng2];

    highlightLine.setLatLngs(highlightPoints);
  }

  async setPoints(points)
  {
    const {markers, layerGroup} = this;
    const initialSegment = await findClosestSegment(points[1]);

    this.segIds = [initialSegment.id];
    this.segBreakPoints = [];
    this.segBreakPointMarkers = [];


    layerGroup.clearLayers();
    this.trackLine.setLatLngs(initialSegment.points);
    layerGroup.addLayer(this.trackLine);
    layerGroup.addLayer(this.highlightLine);
    this.markers.forEach(marker => layerGroup.addLayer(marker));

    markers[0].setLatLng(points[0]);
    markers[1].setLatLng(points[1]);
    this._updateHighlightLine(points[0]);
    for (let i = 1; i < points.length; ++i)
    {
      markers[1].setLatLng(points[i]);
      this._updateHighlightLine(points[i]);
    }
  }

  getSelection()
  {
    return {
      points: this.highlightLine.getLatLngs(),
      segIds: this.segIds
    };
  }

  _logSegments()
  {
    const {segBreakPoints, segIds, trackLine} = this;
    const points = trackLine.getLatLngs();

    console.log(segBreakPoints);

//    if (segIds.length === 1)
//      console.log(segIds[0] + ': [' + points[0] + ',' + points[points.length - 1] + ']');
//    else
//    {
//      for (let i = 0; i < segIds.length; ++i)
//      {
//        const i1 = i === 0 ? 0 : segBreakPoints[i - 1];
//        const i2 = i === segIds.length - 1 ? points.length - 1 : segBreakPoints[i];
//
//        console.log(segIds[i] + ': [' + points[i1] + ',' + points[i2] + ']');
//      }
//    }
  }

}

export {SelectedSegmentManager};