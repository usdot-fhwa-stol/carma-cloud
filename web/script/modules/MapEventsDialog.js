import {objectToSelectElementHtml, objectsToOptions, buildRequestData, delay, REQUEST_JSON} from './util.js';
import {laneTypes, eventTypes, loadEventsInBounds, loadEventDetails} from './api-events.js';
import {loadRules} from './api-rop.js';
import {loadSegmentsForPoints} from './segments.js';
import {SelectedSegmentManager} from './SelectedSegmentManager.js';

const enableMapDragZoom = map => {
  map.dragging.enable();
  map.scrollWheelZoom.enable();
  map.doubleClickZoom.enable();
};

const findMarkerPoint = points => {
  const firstPoint = points[0];
  const lastPoint = points[points.length - 1];

  if (Math.abs(firstPoint.lat - lastPoint.lat) > Math.abs(firstPoint.lng - lastPoint.lng))
  {
    //primarily vertical line, place it to the right
    return [Math.max(firstPoint.lat, lastPoint.lat), Math.max(firstPoint.lng, lastPoint.lng) + .0025];
  }
  else
  {
    //primarily horizontal line, place it underneath
    return [Math.min(firstPoint.lat, lastPoint.lat) - .001, Math.min(firstPoint.lng, lastPoint.lng)];
  }
};

class MapEventsDialog
{
  constructor(map, initialSegment, options)
  {
    this.map = map;
    Promise.all([loadEventsInBounds(L.latLngBounds(initialSegment.points)), delay(750)]).then(([events]) => this._setEvents(events));

    this.segmentManager = new SelectedSegmentManager(map, initialSegment, {
      onSegmentChange: e => this.reposition(e)});

    const point = findMarkerPoint(initialSegment.points);


    const roadMenuMarker = this.divMarker = L.marker(point, {icon: L.divIcon({className: 'road-menu', iconSize: null,
        html: '\
          <div class="dialog-title w3-dark-gray">Event RoPs<i class="fa w3-right far fa-window-close dialog-close" style="margin:0 5px 0 5px"></i><i class="fa w3-right fa-caret-square-left dialog-back" style="display:none"></i></div>\n\
          <div class="dialog-content w3-light-gray"><div style="text-align:center;padding:20px;"><i class="fas fa-spinner fa-5x fa-spin"></i></div></div>'
      })})
      .addTo(map);

    const roadMenu = this.roadMenu = $('.road-menu');

    const initialContent = this.currentEventsDiv = roadMenu.find(".dialog-content");

    roadMenu.mouseenter(e => {
      map.dragging.disable();
      map.scrollWheelZoom.disable();
      map.doubleClickZoom.disable();
    });
    roadMenu.mouseleave(e => enableMapDragZoom(map));


    if (options.closeCallback)
      roadMenu.find(".dialog-close").click(() => {
        enableMapDragZoom(map);
        options.closeCallback(null);
      });

    const newContent = this.newContentDiv = $(`
      <div class="dialog-content w3-light-gray">
        <form>
          <table>
            <tr><td>enable edit</td><td><input class="dialog-edit" checked="checked" type="checkbox" /></td></tr>
            <tr><td>type</td><td>${objectToSelectElementHtml(eventTypes, {name: 'type'})}</td></tr>
            <tr><td>start</td><td><input name="start-picker" class="datetime-input start" /><input type="hidden" name="start" /></td></tr>
            <tr><td>end</td><td><input name="end-picker" class="datetime-input end" /><input type="hidden" name="end" /></td></tr>
            <tr><td colspan="2">
              <table class="rop w3-table w3-border w3-bordered">
                <thead><tr><th colspan="2">applied rules of practice</th></tr></thead>
                <tbody>
                  <tr><td><select class="new-rop" name="new-rop"></select></td><td><button type="button" class="add-rop">add</button></td></tr>
                </tbody>
              </table>
              </td></tr>
            <tr><td colspan="2">
              <table class="w3-table w3-border w3-bordered lanes">
                <thead><tr><th>from</th><th>to</th><th>lanes affected</th></tr></thead>
                <tbody>
                  <tr><td>B</td><td>E</td><td>${objectToSelectElementHtml(laneTypes, {name: 'lanes-be'})}</td></tr>
                  <tr><td>E</td><td>B</td><td>${objectToSelectElementHtml(laneTypes, {name: 'lanes-eb'})}</td></tr>
                </tbody>
              </table>
              </td></tr>
          </table>

          <div style="padding:1em 1em 3em 1em;"><div class="w3-left"><label>simulated event<input style="margin-left:3px" type="checkbox" value="true" name="test" /></label></div><button class="edit-dialog-save w3-right" type="button">save</button><button class="edit-dialog-cancel w3-right" type="button" style="margin-right: 10px;">cancel</button></div>
        </form>
      </div>`);


    const rops = loadRules()
      .then(r => newContent.find('.new-rop').append(r.reduce(objectsToOptions('id', 'name'), '')));


    newContent.find("button.add-rop").click(() => {
      const selectedRop = newContent.find('select.new-rop option:selected');
      const ropTable = newContent.find('table.rop');
      $(
        ` <tr>
            <td><input type="hidden" name='rop' value="${selectedRop.val()}" />${selectedRop.text()}</td>
            <td><button type="button">del</button></td>
          </tr>`)
        .appendTo(ropTable.find('tbody'))
        .find('button')
        .click(({target}) => $(target).parentsUntil('tbody', 'tr').remove());
    });

    newContent.find("button.edit-dialog-save").click(() => {

      newContent.find('.datetime-input').each((idx, el) => {
        const jqEl = $(el);
        const pickerName = jqEl.prop('name');
        const hiddenElName = pickerName.substring(0, pickerName.indexOf('-'));
        const hiddenEl = $('input[name="' + hiddenElName + '"]');
        hiddenEl.val(jqEl.datetimepicker("getValue").getTime());
      });

      const requestObject = {pts: this.segmentManager.getSelection().points.flatMap(latLng => [latLng.lat, latLng.lng])};

      const saveForm = newContent.find("form");
      saveForm.find('input[name="start"], input[name="test"], input[name="end"], select[name="type"], select[name="lanes-be"], select[name="lanes-eb"]')
        .each((idx, el) => requestObject[$(el).prop('name')] = $(el).val());

      requestObject.rops = saveForm.find('input[name="rop"], select[name="new-rop"]').map(function ()
      {
        return $(this).val();
      }).get();

      const savePost = $.post("api/event/save", buildRequestData(requestObject, {type: REQUEST_JSON}), null, 'json');


      const icon = $('<i class="fas fa-spinner fa-5x fa-spin"></i>');
      const iconContainer = $('<div style="text-align:center;padding:20px;"></div>')
        .append(icon);

      newContent
        .empty()
        .append(iconContainer);

      if (options.closeCallback)
      {
        const showEndSpinner = (success) => icon.removeClass('fa-spin fa-spinner').addClass(success ? 'fa-check-circle' : 'fa-exclamation-circle');
        const delayAndClose = event => delay(1000).then(() => {
            enableMapDragZoom(map);
            options.closeCallback(event);
          });

        delay(750).then(() => savePost.then((event) => {
            showEndSpinner(true);
            delayAndClose(event);
          })
            .catch(event => {
              showEndSpinner(false);
              delayAndClose(null);
            }));
      }
    });


    var minuteInterval = 60;
    var startDate = new Date();

    startDate.setSeconds(0);
    startDate.setMilliseconds(0);
    startDate.setMinutes(0);

//set max time to the next interval + 6 hours
    var maxTime = new Date(startDate.getTime() + (1000 * 60 * 60 * 6) + (1000 * 60 * minuteInterval));
    maxTime.setMinutes(maxTime.getMinutes() - maxTime.getMinutes() % minuteInterval);

    newContent.find('.datetime-input.start').datetimepicker({
      step: 360,
      value: new Date(),
      formatTime: 'h:i a',
      format: 'Y/m/d h:i a'
    });
    newContent.find('.datetime-input.end').datetimepicker({
      step: 360,
      value: new Date(),
      formatTime: 'h:i a',
      format: 'Y/m/d h:i a'
    });


    const enableInput = newContent.find('.dialog-edit');
    const dialogInputs = newContent.find("input, select,button.edit-dialog-save, button.add-rop").not(enableInput);
    enableInput.change(e => dialogInputs.prop('disabled', !$(e.target).prop('checked')));

    newContent.hide();
    initialContent.after(newContent);

    $(".dialog-back, button.edit-dialog-cancel").click(() => {
      initialContent.show();
      newContent.hide();
      roadMenu.find(".dialog-back").hide();
    });
  }

  _clearRopRows()
  {
    const ropTbody = this.newContentDiv.find("table.rop tbody");
    const first = ropTbody.find('tr').first().detach();
    ropTbody.empty().append(first);

  }

  _showEventDetailsPane()
  {
    const {roadMenu, currentEventsDiv, newContentDiv} = this;
    currentEventsDiv.hide();
    newContentDiv.show();
    roadMenu.find(".dialog-back").show();

  }

  reposition(e)
  {
    this.divMarker.setLatLng(findMarkerPoint(e.adjustedPoints));
  }

  removeFrom(map)
  {
    this.segmentManager.removeFrom(map);
    this.divMarker.removeFrom(map);
  }

  _setEvents(events)
  {
    const {currentEventsDiv, newContentDiv} = this;
    currentEventsDiv.empty();
    const currentEventsTable = $(' \n\
            <table class="w3-table w3-hoverable w3-centered w3-border w3-bordered">\n\
                <thead>\n\
                <tr>\n\
                  <th>type</th><th>start</th><th>end</th><th>lanes be</th><th>lanes eb</th>\n\
                </tr>\n\
                </thead>\n\
                <tbody>\n\
                </tbody>\n\
                <tfoot />\n\
            </table>\n\
            <div style="padding:1em 1em 3em 1em;"><button class="edit-dialog-new w3-right" type="button">New</button></div>\n\
').appendTo(currentEventsDiv);
    const tbody = currentEventsTable.find('tbody');
    tbody.empty();


    currentEventsDiv.find(".edit-dialog-new").click(() => {
      this._showEventDetailsPane();
      this._clearRopRows();
      newContentDiv.find('input[name="id"]').remove();
    });

    const dateFormat = "YYYY/MM/DD h:mm a";
    Object.entries(events).forEach(([eventId, event]) => $('\
                <tr>\
                  <td>' + eventTypes[event.type] + '</td>\
                  <td>' + moment(event.start).format(dateFormat) + '</td>\
                  <td>' + moment(event.end).format(dateFormat) + '</td>\
                  <td>' + laneTypes[event['lanes-be']] + '</td>\
                  <td>' + laneTypes[event['lanes-eb']] + '</td>\
                </tr>')
        .appendTo(tbody)
        .mouseenter(({target}) => {
          const {map} = this;
          const eventLine = L.polyline(event.points, {color: 'orange'}).addTo(map);
          $(target).mouseleave(e => {
            eventLine.removeFrom(map);
            $(target).off(e);
          });
        })
        .click(({target}) => loadEventDetails(eventId).then((eventDetails) => {
            this._clearRopRows();
            loadSegmentsForPoints(eventDetails.points)
              .then((loaded) => {
                //     console.log(loaded);
                this.segmentManager.setPoints(eventDetails.points);

                newContentDiv.find('form').append('<input type="hidden" value="' + eventId + '" name="id" />');
                newContentDiv.find('[name="type"]').val(event.type);
                newContentDiv.find('[name="lanes-eb"]').val(event['lanes-eb']);
                newContentDiv.find('[name="lanes-be"]').val(event['lanes-be']);
                newContentDiv.find('[name="start-picker"]').datetimepicker("setOptions", {value: new Date(event.start)});
                newContentDiv.find('[name="end-picker"]').datetimepicker("setOptions", {value: new Date(event.end)});
                newContentDiv.find('[name="test"][value="false"]').click();
                newContentDiv.find('.dialog-edit').prop("checked", false).change();
                this._showEventDetailsPane();
              });
          })));
  }
}

export {MapEventsDialog};