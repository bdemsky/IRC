/**
 * Tagger class
 * Main class of Tagger application
 *
 * @author  Daniel Jackson
 * @version 0, 07/02/01
 */


package tagger;
import java.io.*;
import java.util.*;

public class Tagger {

	public static PrintStream error_stream = System.out;

	// holds mapping of token types to actions
	Engine engine;

	/**
	 * The main method of the Tagger application.
	 * @param args The command line arguments, described in usage method
	 */
	public static void main (String[] args) {
		check_usage (args);

		String base_name = args[0];
		String source_file_name = base_name + ".txt";
		String output_file_name = base_name + ".tag.txt";
		String index_file_name = base_name + ".index.txt";
		Reader source_reader;
		PrintStream output_stream;
		PrintStream index_stream;

		try {source_reader = get_reader_from_file_name (source_file_name);
		} catch (IOException e) {
			error_stream.println (
				"Unable to open source file " + source_file_name + ": " + e.getMessage ());
			return;
			};
		try {output_stream = get_stream_from_file_name (output_file_name);
		} catch (IOException e) {
			error_stream.println (
				"Unable to open output file " + output_file_name + ": " + e.getMessage ());
			return;
			};
		try {index_stream = get_stream_from_file_name (index_file_name);
		} catch (IOException e) {
			error_stream.println (
				"Unable to open index file " + index_file_name + ": " + e.getMessage ());
			return;
			};

		// for now, hardwire to Quark
		Generator generator = new QuarkGenerator (output_stream);

		PropertyMap style_map = new PropertyMap ();
		Engine engine = new StandardEngine (generator, style_map, error_stream, index_stream);
		try {
		consume_source (engine, style_map, source_reader);
		} catch (IOException e) {Assert.unreachable ();}
		output_stream.close ();
		}

	public static void consume_source (Engine engine, PropertyMap style_map, Reader source_reader)
		throws IOException {
		Set para_styles = style_map.get_items ();
		SourceParser p = new SourceParser (source_reader, para_styles);
		Token token;
		while (p.has_more_tokens ()) {
			token = p.get_token ();
			engine.consume_token (token);
			}
		// consume end of stream token explicitly
		// depends on get_token returning ENDOFSTREAM token when no more tokens
		token = p.get_token ();
		engine.consume_token (token);
		}

	static void check_usage (String args []) {
		if (args.length == 0) {
			error_stream.println (
				"one argument required, should be name of source file, excluding .txt extension"
				);
			}
		}

	static Reader get_reader_from_file_name(String file_name) throws IOException {
		File f = new File (file_name);
		FileInputStream s = new FileInputStream (f);
		InputStreamReader r = new InputStreamReader (s);
		return r;
		}

	static PrintStream get_stream_from_file_name (String file_name) throws IOException {
		File f = new File (file_name);
		FileOutputStream s = new FileOutputStream (f);
		PrintStream ps = new PrintStream (s);
		return ps;
		}

	}
