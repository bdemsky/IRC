/**
 * Property class
 * <p>
 *
 * @author  Daniel Jackson
 * @version 0, 07/02/01
 */

package tagger;
import java.io.*;
import java.util.*;

public class Property {
	public String property;
	public String value;

	public Property (String p, String v) {
		property = p;
		value = v;
		}

	public String toString () {
		return "<" + property + ":" + value + ">";
		}
	}