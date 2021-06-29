/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ws;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Federal Highway Administration
 */

@WebServlet(loadOnStartup = 1, urlPatterns =
{
	"/api/print/*"
})
public class TestServlet extends HttpServlet
{
	@Override
	public void doPost(HttpServletRequest oReq, HttpServletResponse oRes)
	   throws IOException
	{
		System.out.println("hi aaron");
        BufferedReader oIn = oReq.getReader();
        BufferedWriter oOut = new BufferedWriter(new FileWriter("/dev/shm/postprint.csv"));
        StringBuilder sBuffer = new StringBuilder();
        int nByte;
        while ((nByte = oIn.read()) >= 0)
                sBuffer.append((char)nByte);
        oOut.write(sBuffer.toString());
        oOut.close();
        oIn.close();
	}
}
