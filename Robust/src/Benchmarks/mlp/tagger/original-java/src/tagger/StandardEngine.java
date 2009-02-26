/**
 * StandardEngine class
 * Standard registration of actions
 * Implemented as a subclass of Engine for no good reason
 *
 * @author  Daniel Jackson
 * @version 0, 07/08/01
 */

package tagger;
import java.io.*;
import java.util.*;

public class StandardEngine extends Engine {

	static Engine STANDARD;

	// reserved words for property files

	// character table
	static final String index_prop_name = "index";
	static final String font_prop_name = "font";

	static final String apostrophe_char_name = "quoteright";
	static final String prime_char_name = "prime";
	static final String opensinglequote_char_name = "quoteleft";
	static final String closesinglequote_char_name = "quoteright";
	static final String opendoublequote_char_name = "quotedblleft";
	static final String closedoublequote_char_name = "quotedblright";
	static final String hyphen_char_name = "hyphen";
	static final String endash_char_name = "endash";
	static final String emdash_char_name = "emdash";
	static final String period_char_name = "period";
	static final String twodotleader_char_name = "twodotleader";
	static final String ellipsis_char_name = "ellipsis";

	static final String ROMAN_COMMANDNAME = "roman";
	static final String BOLD_COMMANDNAME = "bold";
	static final String ITALICS_COMMANDNAME = "italic";
	static final String SUBSCRIPT_COMMANDNAME = "sub";
	static final String SUPERSCRIPT_COMMANDNAME = "sup";

	// style sheet
	static final String next_style_prop_name = "next";
	static final String default_style_name = "body";

	public StandardEngine (
			final Generator generator,
			final PropertyMap style_map,
			final PrintStream error_stream, final PrintStream index_stream
			) {

		final PropertyMap char_map = new PropertyMap ();
		final Numbering numbering = new Numbering (style_map, error_stream);

		// a hack to work around lack of proper closures in Java
		// can't assign to local variable within actions
		class StringBox {
			String string;
			StringBox (String s) {string = s;}
			void set (String s) {string = s;}
		}
		final StringBox current_para_style = new StringBox (default_style_name);

		// special action for start of paragraph
		// created once, but dynamically inserted and removed
		// so that it's performed once at the start of each paragraph
		final Action paragraph_action = new Action () {
			boolean first_para = true;
			public void perform (Token t, Iterator iter) {
				if (t.type != Token.PARASTYLECOMMAND) {
					if (!first_para) generator.linefeed ();
					generator.new_para (current_para_style.string);
					String numstr = numbering.get_numbering_string (current_para_style.string);
					if (numstr.length() != 0) {
						// display numbering as evidence of progress
						error_stream.println (numstr);
						/*
						// this doesn't work. not sure why.
						// because it becomes a recursive call!
						// need an impoverished engine specially for this, without paras?

						Reader numreader = new StringReader (numstr);
						try {
						Tagger.consume_source (StandardEngine.STANDARD, style_map, numreader);
						}
						catch (IOException e) {Assert.unreachable ();}
						*/
						generator.plaintext (numstr);
						}

					iter.remove ();
					first_para = false;
					}
				}};

		register_by_type (new Action () {
			public void perform (Token t) {
				generator.plaintext (t.arg);
				}},
			Token.ALPHABETIC);

		register_by_type (new Action () {
			public void perform (Token t) {
				generator.plaintext (t.arg);
				}},
			Token.NUMERIC);

		register_by_type (new Action () {
			public void perform (Token t) {
				generator.plaintext (t.arg);
				}},
			Token.WHITESPACE);

		register_by_type (new Action () {
			public void perform (Token t) {
				generator.new_line ();
				}},
			Token.LINEBREAK);

		register_by_type (new Action () {
			public void perform (Token t) {
				put_special_char (generator, char_map, apostrophe_char_name, error_stream, t.line);
				}},
			Token.APOSTROPHE);

		register_by_type (new Action () {
			public void perform (Token t) {
				put_special_char (generator, char_map, prime_char_name, error_stream, t.line);
				}},
			Token.PRIME);

		register_by_type (new Action () {
			public void perform (Token t) {
				put_special_char (generator, char_map, opensinglequote_char_name, error_stream, t.line);
				}},
			Token.OPENSINGLEQUOTE);

		register_by_type (new Action () {
			public void perform (Token t) {
				put_special_char (generator, char_map, closesinglequote_char_name, error_stream, t.line);
				}},
			Token.CLOSESINGLEQUOTE);

		register_by_type (new Action () {
			public void perform (Token t) {
				put_special_char (generator, char_map, opendoublequote_char_name, error_stream, t.line);
				}},
			Token.OPENDOUBLEQUOTE);

		register_by_type (new Action () {
			public void perform (Token t) {
				put_special_char (generator, char_map, closedoublequote_char_name, error_stream, t.line);
				}},
			Token.CLOSEDOUBLEQUOTE);

		register_by_type (new Action () {
			public void perform (Token t) {
				int len = t.arg.length ();
				if (len == 1)
					put_special_char (generator, char_map, hyphen_char_name, error_stream, t.line);
				else if (len == 2)
					put_special_char (generator, char_map, endash_char_name, error_stream, t.line);
				else if (len == 3)
					put_special_char (generator, char_map, emdash_char_name, error_stream, t.line);
				else
					error_stream.println (t.line + ": Too many hyphens: " + t.arg);
				}},
			Token.HYPHENS);

		register_by_type (new Action () {
			public void perform (Token t) {
				int len = t.arg.length ();
				if (len == 1)
					generator.plaintext (".");
				else if (len == 2)
					put_special_char (generator, char_map, twodotleader_char_name, error_stream, t.line);
				else if (len == 3)
					put_special_char (generator, char_map, ellipsis_char_name, error_stream, t.line);
				else
					error_stream.println (t.line + ": Too many dots: " + t.arg);
				}},
			Token.DOTS);

		register_by_type (new Action () {
			public void perform (Token t) {
				// open file with given name and load char map from it
				String file_name = t.arg;
				try {
					File f = new File (file_name);
					FileInputStream s = new FileInputStream (f);
					InputStreamReader r = new InputStreamReader (s);
					PropertyParser p = new PropertyParser (r, error_stream);
					char_map.incorporate (p);
					} catch (IOException e) {
					error_stream.println (t.line + ": Can't open char map file: " + file_name);
					}
				}},
			Token.LOADCHARMAPCOMMAND);

		register_by_type (new Action () {
			public void perform (Token t) {
				// open file with given name and load char map from it
				String file_name = t.arg;
				try {
					File f = new File (file_name);
					FileInputStream s = new FileInputStream (f);
					InputStreamReader r = new InputStreamReader (s);
					PropertyParser p = new PropertyParser (r, error_stream);
					style_map.incorporate (p);
					numbering.incorporate ();
					} catch (IOException e) {
					error_stream.println (t.line + ": Can't open style sheet file: " + file_name);
					}
				}},
			Token.LOADSTYLESHEETCOMMAND);

		final Action unsuppress_action = new Action () {
			public void perform (Token t, Iterator i) {
				generator.suppress_off ();
				i.remove ();
			}};

		// preamble command switches on output suppression
		// registers action to turn suppression off with paragraph break command
		register_by_type (new Action () {
			public void perform (Token t) {
				generator.suppress_on ();
				register_by_type (unsuppress_action, Token.PARABREAK);
				}},
			Token.PREAMBLECOMMAND);

		register_by_type (new Action () {
			public void perform (Token t) {
				String next_style = style_map.get_property (current_para_style.string, next_style_prop_name);
				if (next_style == null) {
					error_stream.println (t.line + ": No next style property given for style: " + current_para_style.string);
					return;
					}
				current_para_style.set (next_style);
				register_for_all (paragraph_action);
				}},
			Token.PARABREAK);

		register_by_type (new Action () {
			public void perform (Token t) {
				current_para_style.set (t.arg);
				}},
			Token.PARASTYLECOMMAND);

		register_by_type (new Action () {
			public void perform (Token t) {
				String index = char_map.get_property (t.arg, index_prop_name);
				if (index == null) {
					error_stream.println (t.line + ": No index property given for character: " + t.arg);
					return;
					}
				String font = char_map.get_property (t.arg, font_prop_name);
				// if no font is listed, generate special character in standard font
				if (font == null)
					generator.special_char (index);
				else
					generator.special_char (font, index);
				}},
			Token.CHARCOMMAND);

		register_by_type (new Action () {
			boolean italic_mode_on = false;
			public void perform (Token t) {
				if (italic_mode_on) {
					italic_mode_on = false;
					generator.pop_format ();
					}
				else {
					italic_mode_on = true;
					generator.push_format (Generator.ITALICS);
					}
				}},
			Token.UNDERSCORE);

		// used to italicize alphabetic tokens in math mode
		final Action push_italics_action = new Action () {
			public void perform (Token t, Iterator iter) {
				Assert.assert (t.type == Token.ALPHABETIC);
				generator.push_format (Generator.ITALICS);
				}};
		final Action pop_italics_action = new Action () {
			public void perform (Token t, Iterator iter) {
				Assert.assert (t.type == Token.ALPHABETIC);
				generator.pop_format ();
				}};

		register_by_type (new Action () {
			boolean math_mode_on = false;
			public void perform (Token t) {
				if (math_mode_on) {
					math_mode_on = false;
					unregister_by_type (push_italics_action, Token.ALPHABETIC);
					unregister_by_type (pop_italics_action, Token.ALPHABETIC);
					}
				else {
					math_mode_on = true;
					register_by_type_back (pop_italics_action, Token.ALPHABETIC);
					register_by_type_front (push_italics_action, Token.ALPHABETIC);
					}
				}},
			Token.DOLLAR);

		register_by_type (new Action () {
			public void perform (Token t) {
				if (t.arg.equals (ROMAN_COMMANDNAME))
					generator.push_format (Generator.ROMAN);
				else if (t.arg.equals (BOLD_COMMANDNAME))
					generator.push_format (Generator.BOLD);
				else if (t.arg.equals (ITALICS_COMMANDNAME))
					generator.push_format (Generator.ITALICS);
				else if (t.arg.equals (SUBSCRIPT_COMMANDNAME))
					generator.push_format (Generator.SUBSCRIPT);
				else if (t.arg.equals (SUPERSCRIPT_COMMANDNAME))
					generator.push_format (Generator.SUPERSCRIPT);
				}},
			Token.FORMATCOMMAND);

		register_by_type (new Action () {
			public void perform (Token t) {
				generator.pop_format ();
				}},
			Token.POPFORMATCOMMAND);

		register_by_type (new Action () {
			public void perform (Token t) {
				generator.plaintext (t.arg);
				}},
			Token.OTHER);

		register_by_type (new Action () {
			public void perform (Token t) {
				error_stream.println ("... done");
				}},
			Token.ENDOFSTREAM);

		STANDARD = this;
	}

	/* no actions for these token types:
	COMMENT
	SEPARATORCOMMAND
	*/

/*
	not yet coded:

	public static final int REFCOMMAND = 32;
	public static final int TAGCOMMAND = 33;
	public static final int CITECOMMAND = 34;
*/


	/* general form of action registration is this:
		register_by_type (new Action () {
			public void perform (Token t) {
				// put code to be executed for token type here
				}},
			Token.TYPENAME);
	*/

	void put_special_char (Generator generator, PropertyMap char_map,
		String char_name, PrintStream error_stream, int line) {
			String index = char_map.get_property (char_name, index_prop_name);
			if (index == null) {
				error_stream.println (line + ": Unresolved character: " + char_name);
				}
			else
				generator.special_char (index);
		}

}