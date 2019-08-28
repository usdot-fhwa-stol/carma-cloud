package cc.util;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;


/**
 * Provides methods to parse strings to extract numerical values. Also contains
 * methods to format and compare character sequences.
 */
public abstract class Text
{
    /**
     * Lower-case character set used to encode a byte array as a hex string.
     */
    private static final char[] HEX_CHARS =
            {
                    '0', '1', '2', '3', '4', '5', '6', '7',
                    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
            };
    /**
     * Digit offset.
     */
    private static final int DIGIT_OFFSET = 48;
    /**
     * Minimum exponent limit.
     */
    private static final int MIN_EXPONENT = -323;
    /**
     * Maximum exponent limit.
     */
    private static final int MAX_EXPONENT = 308;

    /**
     * State Constant marking initial character.
     */
    private static final int INIT_CHAR = 0;
    /**
     * State Constant marking decimal.
     */
    private static final int DECIMAL = 1;
    /**
     * State Constant marking fraction.
     */
    private static final int FRACTION = 2;
    /**
     * State Constant marking exponents.
     */
    private static final int EXPONENT = 3;
    /**
     * State Constant marking end of string.
     */
    private static final int PARSE_END = 4;

    /**
     * Positive infinity - unicode constant used to convert strings to doubles
     */
    private static final String POS_INF = "\u221E";
    /**
     * Negative infinity - unicode constant used to convert strings to doubles
     */
    private static final String NEG_INF = "-\u221E";
    /**
     * Positive infinity - string name constant used to convert strings to
     * doubles
     */
    private static final String POS_INFINITY = "Infinity";
    /**
     * Negative infinity - string name constant used to convert strings to
     * doubles
     */
    private static final String NEG_INFINITY = "-Infinity";
    /**
     * Not a number - string name constant used to convert strings to
     * doubles
     */
    private static final String NAN = "NaN";
    /**
     * Convenience Base64 URL encoder without padding
     */
	public static final Base64.Encoder B64ENC = Base64.getUrlEncoder().withoutPadding();
	
	private static final ByteBuffer UUID_BUFFER = ByteBuffer.allocate(16);


    /**
     * <b> Default Constructor </b>
     * <p>
     * Creates new instances of {@code Text}
     * </p>
     */
    private Text()
		{
    }


    /**
     * Removes whitespace from the provided string builder.
     *
     * @param sBuffer the string to remove whitespace from.
     */
    public static void removeWhitespace(StringBuilder sBuffer) {
        // only remove whitespace if there is something to scan
        if (sBuffer.length() > 0) {
            // reverse iterate to the first non-whitespace character
            int nIndex = sBuffer.length();
            while (nIndex-- > 0 &&
                    Character.isWhitespace(sBuffer.charAt(nIndex))) ;

            // remove the trailing whitespace segment
            sBuffer.delete(++nIndex, sBuffer.length());

            // forward iterate to the first non-whitespace character
            nIndex = 0;
            while (nIndex < sBuffer.length() &&
                    Character.isWhitespace(sBuffer.charAt(nIndex++))) ;

            // remove the leading whitespace segment
            if (nIndex > 0)
                sBuffer.delete(0, --nIndex);
        }
    }


    /**
     * Wraps {@link Text#parseInt(java.lang.CharSequence, int, int)} to convert
     * the sequence from beginning to end.
     *
     * @param iCharSeq a set of characters to be converted into a long value
     * @return the decimal long value represented by the character sequence.
     */
    public static long parseLong(CharSequence iCharSeq) {
        return parseLong(iCharSeq, 0, iCharSeq.length());
    }


    /**
     * Parses the character sequence argument as a signed decimal long.
     * The characters in the sequence must all be decimal digits, except that
     * the first character may be an ASCII minus sign '-' ('\u002D') to
     * indicate a negative value.
     *
     * @param iCharSeq a set of characters to be converted into an integer value
     * @param nPos     the position in the sequence where conversion begins
     * @param nEndPos  the sequence position where conversion stops (exclusive)
     * @return the decimal long value represented by the character sequence
     * @throws NumberFormatException if the character sequence does not contain
     *                               characters that can be converted to a decimal long
     */
    public static long parseLong(CharSequence iCharSeq, int nPos, int nEndPos)
            throws NumberFormatException {
        // test for the sign character
        boolean bNegative = (iCharSeq.charAt(nPos) == '-');
        if (bNegative)
            ++nPos;

        int nDigit = 0;
        long lValue = 0L;
        while (nPos < nEndPos) {
            // map the character value to the numeric value
            nDigit = iCharSeq.charAt(nPos++) - DIGIT_OFFSET;
            // test for characters that are not numbers
            if (nDigit < 0 || nDigit > 9)
                throw new NumberFormatException();

            // shift the existing value and add the new digit value
            lValue *= 10L;
            lValue += nDigit;
        }

        if (bNegative)
            return -lValue;

        return lValue;
    }


    /**
     * Wraps {@link Text#parseInt(java.lang.CharSequence, int, int)} to convert
     * the sequence from beginning to end.
     *
     * @param iCharSeq a set of characters to be converted into an integer value
     * @return the decimal integer value represented by the character sequence.
     */
    public static int parseInt(CharSequence iCharSeq) {
        return parseInt(iCharSeq, 0, iCharSeq.length());
    }


    /**
     * Parses the character sequence argument as a signed decimal integer.
     * The characters in the sequence must all be decimal digits, except that
     * the first character may be an ASCII minus sign '-' ('\u002D') to
     * indicate a negative value.
     *
     * @param iCharSeq a set of characters to be converted into an integer value
     * @param nPos     the position in the sequence where conversion begins
     * @param nEndPos  the sequence position where conversion stops (exclusive)
     * @return the decimal integer value represented by the character sequence
     * @throws NumberFormatException if the character sequence does not contain
     *                               characters that can be converted to a decimal integer
     */
    public static int parseInt(CharSequence iCharSeq, int nPos, int nEndPos)
            throws NumberFormatException {
        int nSign = 1;
        int nValue = 0;

        // valid characters for an integer are -, +, and digits
        int nState = INIT_CHAR;
        while (nPos < nEndPos && nState != PARSE_END) {
            char cDigit = iCharSeq.charAt(nPos++);
            switch (nState) {
                // the digits test is first since it is the most likely
                case DECIMAL: {
                    if (Character.isDigit(cDigit)) {
                        // shift any existing value
                        nValue *= 10;
                        nValue += (cDigit - DIGIT_OFFSET);
                    } else
                        throw new NumberFormatException("Illegal character '" +
                                iCharSeq.charAt(--nPos) + "' for integer " +
                                "expression at position " + nPos + ".");
                }
                break;

                // the initial character test is only performed once
                case INIT_CHAR: {
                    nState = DECIMAL;
                    if (cDigit == '-')
                        nSign = -1;
                    else
                        // back up one character to test for digits
                        --nPos;
                }
            }
        }

        return (nSign * nValue);
    }


	public static double parseDouble(CharSequence iCharSeq, int nPos, int nEndPos)
		throws IndexOutOfBoundsException, NumberFormatException
	{
		boolean bNeg = (iCharSeq.charAt(nPos) == '-');
		if (bNeg) // test for sign character
			++nPos;

		double dVal = 0.0;
		while (nPos < nEndPos && iCharSeq.charAt(nPos) != '.')
		{
			int nDigit = iCharSeq.charAt(nPos++) - DIGIT_OFFSET; // map char value
			if (nDigit < 0 || nDigit > 9) // check for non-numeric chars
				throw new NumberFormatException();

			dVal *= 10.0; // shift existing value
			dVal += nDigit; // add new digit value
		}

		if (iCharSeq.charAt(nPos++) == '.') // fractional part check
		{
			double dDiv = 1.0;
			double dFrac = 0.0;
			while (nPos < nEndPos)
			{
				int nDigit = iCharSeq.charAt(nPos++) - DIGIT_OFFSET; // map char value
				if (nDigit < 0 || nDigit > 9) // check for non-numeric chars
					throw new NumberFormatException();

				dDiv *= 10.0; // track decimal places
				dFrac *= 10.0; // shift existing value
				dFrac += nDigit; // add new digit value
			}
			dVal += dFrac / dDiv; // expensive division operator
		}

		if (bNeg)
			return -dVal;

		return dVal;
	}


    /**
     * Converts the character sequence into a double value.
     *
     * @param iCharSeq a set of characters to be converted into a double value
     * @return the converted double value.
     */
    public static double parseDouble(CharSequence iCharSeq) {
        // first test for expected string names
        if (compare(iCharSeq, POS_INF) == 0 ||
                compare(iCharSeq, POS_INFINITY) == 0)
            return Double.POSITIVE_INFINITY;

        if (compare(iCharSeq, NEG_INF) == 0 ||
                compare(iCharSeq, NEG_INFINITY) == 0)
            return Double.NEGATIVE_INFINITY;

        if (compare(iCharSeq, NAN) == 0)
            return Double.NaN;

        int nExponent = 0;
        double dSign = 1.0;
        double dValue = 0.0;
        double dMultiplier = 0.1;
        double dFraction = 0.0;

        // valid characters for a double are -, +, ., digits, E, and e
        int nIndex = 0;
        int nState = INIT_CHAR;
        while (nIndex < iCharSeq.length() && nState != PARSE_END) {
            char cDigit = iCharSeq.charAt(nIndex++);
            switch (nState) {
                // the digits test is first since it is the most likely
                // parseInt cannot be used due to potential leading zeros
                // in the decimal and fractional parts, i.e. -0.00763
                case DECIMAL: {
                    if (Character.isDigit(cDigit)) {
                        // shift any existing value
                        dValue *= 10.0;
                        dValue += (cDigit - DIGIT_OFFSET);
                    } else {
                        // switch to other states for the double interpretation
                        switch (cDigit) {
                            case '.':
                                nState = FRACTION;
                                break;

                            case 'e':
                            case 'E':
                                nState = EXPONENT;
                                break;

                            default:
                                nState = PARSE_END;
                                break;
                        }
                    }
                }
                break;

                case FRACTION: {
                    if (Character.isDigit(cDigit)) {
                        dFraction += (cDigit - DIGIT_OFFSET) * dMultiplier;
                        dMultiplier *= 0.1;
                    } else if (cDigit == 'e' || cDigit == 'E')
                        nState = EXPONENT;
                    else
                        nState = PARSE_END;
                }
                break;

                // the exponent state is able to use the parseInt method
                // as any fractional exponent will be ignored
                case EXPONENT: {
                    nExponent = parseInt(iCharSeq, --nIndex, iCharSeq.length());
                    nState = PARSE_END;
                }
                break;

                // the initial character test is only performed once
                case INIT_CHAR: {
                    switch (cDigit) {
                        case '-': {
                            dSign = -1.0;
                            nState = DECIMAL;
                        }
                        break;

                        case '+': {
                            dSign = 1.0;
                            nState = DECIMAL;
                        }
                        break;

                        case '.':
                            nState = FRACTION;
                            break;

                        default: {
                            if (Character.isDigit(cDigit)) {
                                // back up one character to test for digits
                                --nIndex;
                                nState = DECIMAL;
                            } else
                                nState = PARSE_END;
                        }
                    }
                }
            }
        }

        // only generate an exponent when necessary
        double dExponent = 1.0;
        if (nExponent != 0) {
            // check the limits of the exponent
            if (nExponent < MIN_EXPONENT)
                nExponent = MIN_EXPONENT;

            if (nExponent > MAX_EXPONENT)
                nExponent = MAX_EXPONENT;

            dExponent = Math.pow(10.0, (double) nExponent);
        }

        return (dSign * (dValue + dFraction) * dExponent);
    }


    /**
     * Lexicographically compare two character sequences. Using the character
     * sequence interface enables the mixing of comparisons between
     * <tt>String</tt>, <tt>StringBuffer</tt>, and <tt>StringBuilder</tt>
     * objects. The character values at each index of the sequences is compared
     * up to the minimum number of available characters. The sequence lengths
     * determine the comparison when the contents otherwise appear to be equal.
     *
     * @param iSeqL the first character sequence to be compared
     * @param iSeqR the second character sequence to be compared
     * @return a negative integer, zero, or a positive integer as the first
     * argument is less than, equal to, or greater than the second
     */
    public static int compare(CharSequence iSeqL, CharSequence iSeqR) {
        int nCompare = 0;
        int nIndex = -1;
        int nLimit = Math.min(iSeqL.length(), iSeqR.length());

        while (nCompare == 0 && ++nIndex < nLimit)
            nCompare = (iSeqL.charAt(nIndex) - iSeqR.charAt(nIndex));

        if (nCompare == 0)
            nCompare = (iSeqL.length() - iSeqR.length());

        return nCompare;
    }


    /**
     * Lexicographically compare two character sequences. Comparison is
     * performed without regard to differing character case.
     *
     * @param iSeqL the first character sequence to be compared
     * @param iSeqR the second character sequence to be compared
     * @return a negative integer, zero, or a positive integer as the first
     * argument is less than, equal to, or greater than the second
     */
    public static int compareIgnoreCase(CharSequence iSeqL, CharSequence iSeqR) {
        int nCompare = 0;
        int nIndex = -1;
        int nLimit = Math.min(iSeqL.length(), iSeqR.length());

        while (nCompare == 0 && ++nIndex < nLimit)
            nCompare =
                    (
                            Character.toLowerCase(iSeqL.charAt(nIndex)) -
                                    Character.toLowerCase(iSeqR.charAt(nIndex))
                    );

        if (nCompare == 0)
            nCompare = (iSeqL.length() - iSeqR.length());

        return nCompare;
    }


    /**
     * Tests if the source character sequence begins with the
     * prefix character sequence.
     *
     * @param iSource the character sequence to check
     * @param iPrefix the search character sequence
     * @return <tt>true</tt> if the initial source characters match the
     * characters in the prefix. <tt>false</tt> otherwise.
     */
    public static boolean startsWith(CharSequence iSource, CharSequence iPrefix) {
        int nIndex = iPrefix.length();

        // the source cannot start with a pattern with more characters
        if (nIndex > iSource.length())
            return false;

        // compare each characters starting at the end of the sequence
        boolean bMatch = true;
        while (bMatch && nIndex-- > 0)
            bMatch = (iSource.charAt(nIndex) == iPrefix.charAt(nIndex));

        return bMatch;
    }


    /**
     * Tests if the source character sequence ends with the
     * suffix character sequence.
     *
     * @param iSource the character sequence to check
     * @param iSuffix the search character sequence
     * @return <tt>true</tt> if the characters at the end of the source
     * match the characters in the suffix. <tt>false</tt> otherwise.
     */
    public static boolean endsWith(CharSequence iSource, CharSequence iSuffix) {
        int nIndex = iSuffix.length();
        int nSrcIndex = iSource.length();

        // the source cannot end with a pattern with more characters
        if (nIndex > nSrcIndex)
            return false;

        // compare each characters starting at the end of the sequence
        boolean bMatch = true;
        while (bMatch && nIndex-- > 0)
            bMatch = (iSource.charAt(--nSrcIndex) == iSuffix.charAt(nIndex));

        return bMatch;
    }


    /**
     * Converts a byte array into a hexadecimal string
     *
     * @param yBytes Byte array containing data to be converted.
     * @return Hexadecimal string that represents the supplied byte data.
     */
    public static String toHexString(byte[] yBytes)
		{
			return toHexString(yBytes, 0, yBytes.length);
    }


    /**
     * Converts a byte array into a hexadecimal string
     *
     * @param yBytes Byte array containing data to be converted.
     * @param sBuf String buffer that holds the converted characters.
     */
    public static void toHexString(byte[] yBytes, StringBuilder sBuf)
		{
			toHexString(yBytes, 0, yBytes.length, sBuf);
    }


    /**
     * Converts a byte array into a hexadecimal string
     *
     * @param yBytes   Byte array containing data to be converted.
     * @param nOffset The position within the array to begin converting.
     * @param nLength The number of bytes to be converted.
     * @return Hexadecimal string that represents the supplied byte data.
     */
    public static String toHexString(byte[] yBytes, int nOffset, int nLength)
		{
			StringBuilder sBuf = new StringBuilder();
			toHexString(yBytes, nOffset, nLength, sBuf);
			return sBuf.toString();
		}


    /**
     * Converts a byte array into a hexadecimal string
     *
     * @param yBytes   Byte array containing data to be converted.
     * @param nOffset The position within the array to begin converting.
     * @param nLength The number of bytes to be converted.
     * @param sBuf String buffer that holds the converted characters.
     */
    public static void toHexString(byte[] yBytes, int nOffset, 
			int nLength, StringBuilder sBuf)
		{
			for (; nOffset < nLength; nOffset++)
			{
				sBuf.append(HEX_CHARS[((yBytes[nOffset] & 0xf0) >> 4)]);
				sBuf.append(HEX_CHARS[(yBytes[nOffset] & 0x0f)]);
			}
    }


    /**
     * Converts a hexadecimal sequence into a byte array
     *
     * @param sBuf String buffer holding hexadecimal characters.
		 * @return A byte array containing the interpreted bytes.
     */
    public static byte[] fromHexString(StringBuilder sBuf)
		{
			if (sBuf == null || sBuf.length() == 0)
				return null;

			if (sBuf.length() % 2 != 0)
				sBuf.append("0");

			byte[] yBytes = new byte[sBuf.length() / 2];
			for (int nIndex = 0; nIndex < yBytes.length; nIndex++)
			{
				int nPos = nIndex * 2;
				yBytes[nIndex] = (byte)((Character.digit(sBuf.charAt(nPos), 16) << 4) + 
					Character.digit(sBuf.charAt(nPos + 1), 16));
			}
			return yBytes;
    }


    /**
     * Replaces all occurrences of the search string within the supplied
     * buffer with the replacement string.
     *
     * @param sBuffer  StringBuilder buffer containing text to be searched.
     * @param sSearch  Search string to find in the buffer.
     * @param sReplace Replacement string to substitute for the search string.
     */
    public static void replaceAll(StringBuilder sBuffer,
                                  String sSearch, String sReplace) {
        int nIndex = 0;
        while ((nIndex = sBuffer.indexOf(sSearch, nIndex)) >= 0) {
            sBuffer.replace(nIndex, nIndex + sSearch.length(), sReplace);
            // move start point after the replacement to enable recursion
            nIndex += sReplace.length();
        }
    }


    /**
     * Copies the contents of a character sequence into the provided
     * byte buffer. No locale translation is performed. This is mostly useful
     * for UTF-8 and ASCII encoded strings.
     *
     * @param yBuffer  The byte buffer where characters are copied.
     * @param iCharSeq The sequence of characters to convert to bytes.
     * @return The number of bytes copied into the buffer.
     */
    public static int getBytes(byte[] yBuffer, CharSequence iCharSeq) {
        // determine the maximum the available capacity
        int nLength = iCharSeq.length();
        if (nLength > yBuffer.length)
            nLength = yBuffer.length;

        for (int nIndex = 0; nIndex < nLength; nIndex++)
            yBuffer[nIndex] = (byte) iCharSeq.charAt(nIndex);

        return nLength;
    }


    /**
     * Truncates the passed string value if it is longer than the
     * passed length, or returns the original value if it isn't.
     *
   * @param sValue The value to truncate
   * @param nLength The maximum length of the truncated string.
     * @return The truncated string
     */
    public static String truncate(String sValue, int nLength) {
        if(sValue == null || sValue.length() <= nLength)
          return sValue;
        else
          return sValue.substring(0, nLength);
    }
	
	
	public static String getUUID()
	{
		UUID oUuid = UUID.randomUUID();
		synchronized(UUID_BUFFER) // shared byte buffer
		{
			UUID_BUFFER.clear();
			UUID_BUFFER.putLong(oUuid.getMostSignificantBits());
			UUID_BUFFER.putLong(oUuid.getLeastSignificantBits());
			return B64ENC.encodeToString(UUID_BUFFER.array());
		}
	}
}
