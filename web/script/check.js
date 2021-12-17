function check()
{
	var sToken = sessionStorage.token;
	if (sToken === undefined || sToken.length === 0)
	{
		document.location="/"; // redirect to login
		return;
	}
	else
	{
		$.ajax(
		{
			'url': 'api/auth/check',
			'dataType': 'text',
			'method': 'POST',
			'data': {'token': sToken}
		}).done(function(sData, oStatus) 
		{
			sToken = JSON.parse(sData).token;
			if (sToken === undefined || sToken.length === 0)
			{
				document.location="/"; // redirect to login
				return;
			}
		});
	}

	$("#uname").text(sessionStorage.uname); // set menu user name
  $(document).trigger("initPage");
}


$(document).ready(check);
