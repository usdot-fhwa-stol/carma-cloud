/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import cc.util.FileUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author aaron.cherney
 */
public class TcmReplyServlet extends HttpServlet
{
	String m_sReplyFile = "/dev/shm/cc/replies.txt";
	@Override
	public void init()
	{
		try
		{
			Path oReplies = Paths.get(m_sReplyFile);
			if (Files.exists(oReplies))
				Files.delete(oReplies);
			Files.createFile(oReplies);
		}
		catch (Exception oEx)
		{
			oEx.printStackTrace();
		}
		
		
	}
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws IOException, ServletException
	{
		synchronized (this)
		{
			try (ServletInputStream oIn = oReq.getInputStream();
				 BufferedWriter oOut = new BufferedWriter(Channels.newWriter(Files.newByteChannel(Paths.get(m_sReplyFile), FileUtil.APPENDTO), "UTF-8")))
			{
				int nByte;
				while ((nByte = oIn.read()) >= 0)
					oOut.append((char)nByte);
				oOut.append('\n').append('\n');
			}
		}
	}
}
