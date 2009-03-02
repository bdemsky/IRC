/**
 * Tagger class
 * Main class of Tagger application
 *
 * @author  Daniel Jackson
 * @version 0, 07/02/01
 */


//package tagger;
//import java.io.*;
//import java.util.*;

public class Tagger {

  // holds mapping of token types to actions
  //Engine engine;

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
    FileInputStream input_stream;
    FileOutputStream output_stream;
    FileOutputStream index_stream;
    
    input_stream = new FileInputStream(source_file_name);
    output_stream = new FileOutputStream(output_file_name);
    index_stream = new FileOutputStream(index_file_name);

    // for now, hardwire to Quark
    Generator generator = new QuarkGenerator (output_stream);

    PropertyMap style_map = new PropertyMap ();
    Engine engine = new StandardEngine (generator, style_map, index_stream);

    consume_source (engine, style_map, input_stream);

    output_stream.close ();
  }
  
  public static void consume_source (Engine engine, 
				     PropertyMap style_map,
				     FileInputStream source_reader) {
    HashSet para_styles = style_map.get_items ();
    SourceParser p = new SourceParser (source_reader, para_styles);
    Token token;
    while (p.has_more_tokens ()) {
      token = p.get_token ();
      String s = token.toString();
      if( s == null ) {
	System.out.println( "token: [null]" );
      } else {
	System.out.println( "token: ["+s+"]" );
      }
      engine.consume_token (token);
    }
    // consume end of stream token explicitly
    // depends on get_token returning ENDOFSTREAM token when no more tokens
    token = p.get_token ();
    engine.consume_token (token);
  }
  
  static void check_usage (String args []) {
    if (args.length == 0) {
      System.out.println ("one argument required, should be name of source file, excluding .txt extension");
    }
  }  
}
