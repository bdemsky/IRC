/**
 * Assert class
 * Provides assertion checking
 *
 * @author  Daniel Jackson
 * @version 0, 07/03/01
 */

package tagger;
import java.io.*;

public class Assert {
	static PrintStream error_stream = Tagger.error_stream;

	public static void assert (boolean cond) {
		if (!cond) {
			error_stream.println ("Assertion failure");
			// print stack trace
			}
		}

	public static void unreachable () {
		error_stream.println ("Assertion failure");
		}
	}
