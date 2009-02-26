/**
 * Counter class
 *
 * @author  Daniel Jackson
 * @version 0, 07/03/01
 */

package tagger;
import java.io.*;

public class Counter {
	private int count;
	private int initial;
	private int type;

	final static int NO_SUCH_TYPE = -1;
	final static int ARABIC = 0;
	final static int ROMAN_UPPER = 1;
	final static int ROMAN_LOWER = 2;
	final static int ALPHA_UPPER = 3;
	final static int ALPHA_LOWER = 4;

	// eventually recognize counter_type and set initial count and output format
	// takes style and stream for error reporting
	/*
	* requires: count_prop and style are non null
	*
	*/
	public Counter (String count_prop, String style, PrintStream error_stream) {
		Assert.assert (count_prop != null);
		Assert.assert (style != null);
		type = get_type (count_prop);
		switch (type) {
			case NO_SUCH_TYPE:
				type = ARABIC;
				initial = 0;
				break;
			case ALPHA_LOWER:
			case ALPHA_UPPER:
				if (count_prop.length () != 1) {
					error_stream.println ("Bad counter type for style " + style + ": " + count_prop);
					initial = 0;
					break;
					}
				initial = count_prop.toLowerCase().charAt (0) - 'a';
				break;
			case ARABIC:
				try {
				initial = Integer.parseInt (count_prop) - 1;
				} catch (NumberFormatException e) {
					error_stream.println ("Bad counter type for style " + style + ": " + count_prop + "; " + e.getMessage());
					}
				break;
			case ROMAN_LOWER:
			case ROMAN_UPPER:
				// not yet implemented
				initial = 0;
				type = ARABIC;
				break;
			default:
				Assert.unreachable ();
			}
		count = initial;
		}

	/**
	* ensures: increments counter
	* returns true iff successful, false otherwise (eg, because alphabetic counter went past 'z')
	*/
	public boolean increment () {
		if ((type == ALPHA_UPPER || type == ALPHA_LOWER) && count == 26)
			return false;
		count++;
		return true;
		}

	public void reset () {
		count = initial;
		}

	public String unparse () {
		switch (type) {
			case ALPHA_LOWER: {
				char c = (char) ('a' + count - 1);
				return new Character (c).toString();
				}
			case ALPHA_UPPER: {
				char c = (char) ('A' + count - 1);
				return new Character (c).toString();
				}
			case ARABIC:
				return String.valueOf (count);
			case ROMAN_LOWER:
			case ROMAN_UPPER:
				// not yet implemented
				Assert.unreachable ();
				break;
			default:
				Assert.unreachable ();
			}
		return "DUMMY";
		}

	/**
	*
	* ensures: returns counter type of counter given in the string counter_type
	* as an int, being equal to one of the values of the constants declared in the Counter class.
	* returns Counter.NO_SUCH_TYPE if the string is not well formed.
	*/
	public static int get_type (String counter_type) {
		if (counter_type.length() == 0) return NO_SUCH_TYPE;
		char c = counter_type.charAt (0);
		if (c >= 'a' && c <= 'z')
			return ALPHA_LOWER;
		if (c >= 'A' && c <= 'Z')
			return ALPHA_UPPER;
		if (c == 'i' || c == 'v' || c == 'x' ||c == 'l' || c == 'c' || c == 'm')
			return ROMAN_LOWER;
		if (c == 'I' || c == 'V' || c == 'X' ||c == 'L' || c == 'C' || c == 'M')
			return ROMAN_LOWER;
		if (c >= '0' && c <= '9')
			return ARABIC;
		return NO_SUCH_TYPE;
		}
}