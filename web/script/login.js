function error(oJqXHR, sStatus, sError)
{
	$('#connmsg').show(); // show message leave fields alone
}


function success(sData, sStatus, oJqXHR)
{
	var sToken = JSON.parse(sData).token;
	if (sToken === undefined || sToken.length === 0)
	{
		$('#usermsg').show();
		$('#pword').val("").focus(); // reset password and focus there
	}
	else
	{
		sessionStorage.token = sToken; // store session token
		sessionStorage.uname = $('#uname').val(); // store username
		document.location="map.html";
	}
}


function login()
{
	$('#usermsg').hide(); // reset status messages
	$('#connmsg').hide();

	$.ajax("api/auth/login", 
	{
		async: false, 
		method: "POST", 
		data: {"uname": $('#uname').val(), "pword": $('#pword').val()}, 
		error: error, 
		success: success, 
		timeout: 3000
	});
}


function enterKey(oEvent)
{
	if (oEvent.which === 13) // keycode for enter key
	{
		$('#btnLogin').click(); // implicitly click login button
		return false;
	}
	return true;
}


function init()
{
	var sToken = sessionStorage.token;
	if (sToken != undefined && sToken.length > 0)
	{
		$.post("api/auth/logout",
		{
			"token": sToken
		},
		function(sData, oStatus)
		{
			sessionStorage.clear();
		});
	}

	$('#uname').focus(); // set default focus to username field
	$('#btnLogin').click(login);
	$('#pword').on('keydown', enterKey);
}


$(document).ready(init);
