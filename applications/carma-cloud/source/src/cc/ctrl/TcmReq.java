/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.ctrl;

import java.util.ArrayList;

/**
 *
 * @author aaron.cherney
 */
public class TcmReq
{
	public String m_sVersion;
	public String m_sReqId;
	public int m_nReqSeq;
	public int m_nScale;
	public ArrayList<TcBounds> m_oBounds;
	
	TcmReq()
	{
	}
}
