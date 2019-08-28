function init()
{
	$("#wxlib").dataTable
	(
		{
			searching: false,
			columnDefs : [{className : "dt-center", targets : "_all"}],
			ajax :
			{
				url : "api/replay",
				dataSrc : "",
				type : "POST",
				data : {token : sessionStorage.token}
			}
		}
  	);
}


$(document).on("initPage", init);
