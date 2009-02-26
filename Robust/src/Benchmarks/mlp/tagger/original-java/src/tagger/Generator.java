/**
 * Generator interface
 * Generic backend tagged text generator
 *
 * @author  Daniel Jackson
 * @version 0, 07/08/01
 */

package tagger;
import java.io.*;
import java.util.*;

public interface Generator {
	// formats to pass to push_format
	int ROMAN = 0;
	int ITALICS = 1;
	int BOLD = 2;
	int SUBSCRIPT = 3;
	int SUPERSCRIPT = 4;

	// prints new line to output
	void linefeed ();

	void new_para (String style);

	// inserts code for new line
	void new_line ();

	void special_char (String font, String index);

	// for dashes, ellipses, etc
	void special_char (String index);

	void plaintext (String text);
	void push_format (int format);
	void pop_format ();

	// turn output suppression on and off
	void suppress_on ();
	void suppress_off ();

	}
