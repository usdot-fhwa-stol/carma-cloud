package cc.ws;

import java.io.IOException;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;


@ServerEndpoint("/swarm")
public class SwarmSvc
{
	public SwarmSvc()
	{
	}


	@OnMessage
	public void onMessage(Session oSess, String sMsg, boolean bLast)
	{
		try
		{
			if (oSess.isOpen())
			{
				oSess.getBasicRemote().sendText(sMsg, bLast);
			}
		}
		catch (IOException e)
		{
			try 
			{
				oSess.close();
			}
			catch (IOException e1)
			{
			}
		}
	}
}
