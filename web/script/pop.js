function init()
{
	$("#rsupop").dataTable
	(
		{
			searching: false,
			columnDefs : [{className : "dt-center", targets : "_all"}]
//			ajax :
//			{
//				url : "api/pop",
//				dataSrc : "",
//				type : "POST",
//				data : {token : sessionStorage.token}
//			}
		}
  	);
}


$(document).on("initPage", init);