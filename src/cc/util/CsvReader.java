package cc.util;

import java.io.InputStream;
import java.io.IOException;


public class CsvReader extends BufferedInStream
{
	protected static final int DEFAULT_COLS = 80;

	protected int m_nCol;
	protected int[] m_nColEnds;
	protected StringBuilder m_sBuf = new StringBuilder(BUFFER_SIZE);
	protected char m_cDelim = ',';

	public CsvReader(InputStream oInputStream, int nCols)
	{
		super(oInputStream);
		m_nColEnds = new int[nCols];
	}


	public CsvReader(InputStream oInputStream)
	{
		this(oInputStream, DEFAULT_COLS);
	}
	
	
	public CsvReader(InputStream oInputStream, char cDelim)
	{
		this(oInputStream, DEFAULT_COLS);
		m_cDelim = cDelim;
	}


	public int readLine()
		throws IOException
	{
		m_nCol = 0; // reset column index
		m_sBuf.setLength(0); // reset line buffer

		boolean bGo = true;
		int nChar;
		while (bGo && (nChar = read()) >= 0) // don't advance on line complete
		{
			if (nChar == m_cDelim || nChar < ' ')
			{
				bGo = (nChar != '\n');
				if (nChar != '\r') // ignore carriage return
					addCol(); // column found
			}
			else
				m_sBuf.append((char)nChar);
		}

		if (bGo && m_nCol > 0) // check for missing final newline
			addCol();

		return m_nCol; // discovered column count
	}


	private void addCol()
	{
		if (m_nCol == m_nColEnds.length) // extend column end array
		{
			int[] nColEnds = new int[m_nCol * 2];
			System.arraycopy(m_nColEnds, 0, nColEnds, 0, m_nCol);
			m_nColEnds = nColEnds;
		}
		m_nColEnds[m_nCol++] = m_sBuf.length();
	}


	private int getStart(int nCol)
	{
		if (nCol < m_nCol)
		{
			if (nCol == 0)
				return 0;

			return m_nColEnds[nCol - 1];
		}
		return -1; // force index out of bounds
	}


	private int getEnd(int nCol)
	{
		if (nCol < m_nCol)
			return m_nColEnds[nCol];

		return -1; // force index out of bounds
	}


	public boolean isNull(int nCol)
		throws IndexOutOfBoundsException
	{
		return (getEnd(nCol) - getStart(nCol) == 0);
	}


	public double parseDouble(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return Text.parseDouble(m_sBuf, getStart(nCol), getEnd(nCol));
	}


	public long parseLong(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return Text.parseLong(m_sBuf, getStart(nCol), getEnd(nCol));
	}


	public float parseFloat(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return (float)parseDouble(nCol);
	}


	public int parseInt(int nCol)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		return Text.parseInt(m_sBuf, getStart(nCol), getEnd(nCol));
	}


	public String parseString(int nCol)
		throws IndexOutOfBoundsException
	{
		return m_sBuf.substring(getStart(nCol), getEnd(nCol));
	}


	public int parseString(StringBuilder sBuf, int nCol)
		throws IndexOutOfBoundsException, NullPointerException
	{
		sBuf.setLength(0); // clear provided buffer
		sBuf.append(m_sBuf, getStart(nCol), getEnd(nCol));
		return sBuf.length();
	}
}
