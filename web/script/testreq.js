function init()
{
	$("#fileInput").on('input', uploadFile);
}

var sContents = '';
var nRecv = 0;
function uploadFile()
{
	let oFile = document.getElementById('fileInput').files[0]
	sContents = '';
	nRecv = 0;
	var oReader = oFile.stream().getReader();
	oReader.read().then(function processText({done, value}) {
		// Result objects contain two properties:
		// done  - true if the stream has already given you all its data.
		// value - some data. Always undefined when done is true.
		if (done) {
		  console.log("Stream complete");
		  $("#demo").val(sContents);
		  $.ajax('/api/tcmreq/', 
		  {
				type: 'POST',
				contentType: 'text/xml',
				data: sContents
				}).done(function(){alert('success');}).fail(function() {alert('fail');});
		  return;
		}

		// value for fetch streams is a Uint8Array
		nRecv += value.length;
		let chunk = value;

		sContents += new TextDecoder("utf-8").decode(chunk);

		// Read some more, and call this function again
		return oReader.read().then(processText);
	  });
}

$(document).ready(	init);

