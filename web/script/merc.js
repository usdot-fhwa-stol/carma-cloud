/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
var R_MAJOR = 6378137.0;
var PI_OVER_TWO = Math.PI / 2.0;
var ORIGIN_SHIFT = Math.PI * R_MAJOR;
var ORIGIN_SHIFT_DIVIDED_BY_180 = ORIGIN_SHIFT / 180.0;
var PI_OVER_180 = Math.PI / 180.0;
var PI_OVER_360 = PI_OVER_180 / 2.0;

function lonToMeters(dLon)
{
	return dLon * ORIGIN_SHIFT_DIVIDED_BY_180;
}


function latToMeters(dLat)
{
	return Math.log(Math.tan((90.0 + dLat) * PI_OVER_360)) * R_MAJOR;
}

function xToLon(double dX)
{
	return dX / ORIGIN_SHIFT * 180.0;
}


function yToLat(double dY)
{
	let dLat = dY / ORIGIN_SHIFT * 180.0;
	return 180.0 / Math.PI * (2 * Math.atan(Math.exp(dLat * PI_OVER_180)) - PI_OVER_TWO);
}

