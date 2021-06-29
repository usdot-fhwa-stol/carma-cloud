RangeBar = function (sRowId, oRanges, sBorder, sHover, sSelected, callback) // constructor
{
  var oTr = $(sRowId);
  if (oTr === undefined || oRanges === undefined || oRanges.length < 2)
    return; // verify table row exists and ranges are specified


  this.formatMinute = function (nMinute)
  {
    if (nMinute < 10) // always 2-digit hour
      return ("0" + nMinute);

    return ("" + nMinute); // convert to string
  };


  this.formatHour = function (nHour)
  {
    if (nHour > 12) // 12-hour time
      nHour -= 12;

    if (nHour === 0) // adjust midnight
      nHour += 12;

    return ("" + nHour); // convert to string
  };


  this.m_sHover = sHover; // save hover style
  this.m_sSelected = sSelected; // save selected style
  this.m_oCallback = callback; // save callback function, if any
  this.m_oTds = oRanges; // save ranges array
  var nStep = oRanges[oRanges.length - 1] - oRanges[0]; // init maximum range
  var dWidthFactor = 96.0 / nStep; // multiplier for column width
  for (var nIndex = 0; nIndex < oRanges.length - 1; nIndex++)
  {
    var nMinStep = oRanges[nIndex + 1] - oRanges[nIndex];
    if (nMinStep < nStep) // determine smallest range
      nStep = nMinStep;
  }

  var oNow = new Date(); // round time down to nearest step interval
  oNow.setMilliseconds(0);
  oNow.setSeconds(0);
  oNow.setMinutes(Math.floor(oNow.getMinutes() / nStep) * nStep);

  var oBegin = new Date(); // date objects used to calculate time offsets
  var oEnd = new Date();

  oBegin.setTime(oNow.getTime() + oRanges[0] * 60000); // convert minutes to milliseconds
  var oTd = $("<td style=\"width: 2%;\"><div style=\"float: right;\">" + this.formatHour(oBegin.getHours()) + ":</div></td>");
  oTr.append(oTd); // set initial cell label

  var dBeginPct = 2.0; // accumulate percentage ranges for search
  for (var nIndex = 0; nIndex < oRanges.length - 1; nIndex++)
  {
    var nBegin = oRanges[nIndex];
    var nEnd = oRanges[nIndex + 1];
    oBegin.setTime(oNow.getTime() + nBegin * 60000); // calculate start and end times
    oEnd.setTime(oNow.getTime() + nEnd * 60000); // in milliseconds

    var dWidth = (nEnd - nBegin) * dWidthFactor; // adjust col width proportinally to time
    oTd = $("<td style=\"width: " + dWidth + "%;\"><div style=\"float: left;\">" + this.formatMinute(oBegin.getMinutes()) +
            "</div><div style=\"float: right;\">" + this.formatHour(oEnd.getHours()) + ":</div></td>").addClass(sHover);
    var oJq = oTd[0];// use [] operator since jQuery wraps object
    oJq.m_dBeginPct = dBeginPct;
    dBeginPct += dWidth; // increment to end percent
    oJq.m_dEndPct = dBeginPct;

    oJq.m_lBegin = oBegin.getTime(); // preserve time range in table data object
    oJq.m_lEnd = oEnd.getTime();

    if (nBegin === 0) // default range is selected
    {

      this.m_oSelectedRange = oJq;
      oTd.removeClass(sHover); // no hover for default selection
      oTd.addClass(sBorder);
      oTd.addClass(sSelected);
      this.m_nSetIndex = nIndex; // save selected index
    }

    oRanges[nIndex] = oTd; // replace original integer minute value with jQuery object
    oTr.append(oTd); // append table data object to row
  }

  oEnd.setTime(oNow.getTime() + oRanges[nIndex] * 60000); // final time value skipped in loop
  oTd = $("<td style=\"width: 2%;\"><div style=\"float: left;\">" + this.formatMinute(oEnd.getMinutes()) + "</div></td>");
  oTr.append(oTd); // set final cell label


  oTr[0].rangebar = this; // callback to the owning range bar object
  oTr.click(function (oEvent)
  {
    this.rangebar.getRange(100 * oEvent.clientX / this.clientWidth);
  });
};


RangeBar.prototype.getRange = function (dPct)
{
  var oTds = this.m_oTds; // improve speed

  var oJq = oTds[0][0];
  if (dPct < oJq.m_dBeginPct)
    return; // early out if before first cell

  var nEnd = oTds.length - 2; // watch extra offset
  oJq = oTds[nEnd][0];
  if (dPct >= oJq.m_dEndPct)
    return; // early out if after last cell

  var nBegin = this.m_nSetIndex;
  var oTd = oTds[nBegin];
  oJq = oTd[0];
  if (dPct >= oJq.m_dBeginPct && dPct < oJq.m_dEndPct)
    return; // early out if same selection made

  oTd.removeClass(this.m_sSelected);
  oTd.addClass(this.m_sHover); // reset hover style

  if (dPct >= oJq.m_dEndPct)
    ++nBegin; // new selection is after previous selection

  if (dPct < oJq.m_dBeginPct)
  {
    nEnd = --nBegin;
    nBegin = 0; // new selection is before previous selection
  }

  var oFound = undefined; // find td that matches clicked position
  while (oFound === undefined && nBegin <= nEnd)
  {
    oTd = oTds[nBegin];
    oJq = oTd[0]; // unwrap jQuery object
    if (dPct >= oJq.m_dBeginPct && dPct < oJq.m_dEndPct)
    {
      oTd.removeClass(this.m_sHover);
      oTd.addClass(this.m_sSelected); // set new selection
      oFound = oJq;
      this.m_nSetIndex = nBegin;
    }
    ++nBegin;
  }

  if (this.m_oCallback !== undefined)
    this.m_oCallback(oFound.m_lBegin, oFound.m_lEnd);

  this.m_oSelectedRange = oFound;
//	else
//		alert(oFound.m_lBegin + " " + oFound.m_lEnd);
};

RangeBar.prototype.getSelectedRange = function ()
{
  return this.m_oSelectedRange;
};