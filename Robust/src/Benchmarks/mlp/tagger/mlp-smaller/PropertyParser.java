/**
 * PropertyParser class
 * Parses property files
 * <p>
 * <code>int</code>.
 *
 * @author  Daniel Jackson
 * @version 0, 07/02/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;

public class PropertyParser {
  private FileInputStream reader;
  private String token;
  private int next_char;

  public PropertyParser (FileInputStream r) {
    reader = r;
    next_char = reader.read ();
    consume_comments ();
  }
  
  private void consume_comments () {
    // consume lines that don't start with <
    while (next_char != '<' && !is_eos (next_char)) {
      if (!is_eol (next_char))
	reader.readLine ();
      consume_char ();
    }
  }
  
  private void consume_char () {
    token += (char) next_char;
    next_char = reader.read ();
    //while(next_char == 13 || next_char==10) {
    //  next_char = reader.read ();
    //}
    //System.out.println( "next_char: "+(char)next_char );
  }
  
  private void error (String msg) {
    // correct to number from 1, not zero
    //t line_number = reader.getLineNumber() + 1;
    System.out.println (msg);
    System.exit(-1);
  }
  
  public boolean has_more_properties () {
    return (!is_eos (next_char));
  }
  
  /**
   * requires: next_char contains next character in reader <p>
   * ensures: returns list of properties until end of line or stream <p>
   *	according to the following syntax:
   * 		property list is sequence of properties followed by eol of eos
   *		property is left-angle, property-name, colon, value, right-angle
   *		property-name is alphanumeric string, but value is any char sequence
   *	skips lines that do not start with <
   *	reports syntax errors on this.error_reporter
   *	Syntax
   * @return list of properties until end of line or stream.
   *	Notes: chose LinkedList because it provides removeFirst, to support common
   *	case in which first property is removed (eg, because it's a style name)
   */
  public LinkedList get_property_list () {
    LinkedList result = /*disjoint llPropList*/ new LinkedList ();
    while (!is_eol (next_char) && !is_eos(next_char))
      result.add (get_property ());
    consume_char ();
    consume_comments ();
    return result;
  }
  
  private Property get_property () {    
    if (next_char != '<')
      error ("Found " + next_char + " when expecting <");
    consume_char ();
    token = "";
    while (is_alphanumeric (next_char)) consume_char ();
    String property = token;
    if (next_char != ':')
      error ("Found " + next_char + " following " + token + " when expecting :");
    consume_char ();
    token = "";
    while (next_char != '>' && !is_eol(next_char) && !is_eos (next_char))
      consume_char ();
    String value = token;
    if (next_char != '>')
      error ("Found " + next_char + " following " + token + " when expecting >");
    consume_char ();
    return new Property (property, value);
  }
  
  static boolean is_eol (int c) {return c == '\n';}
  static boolean is_eos (int c) {return c == -1;}
  static boolean is_alphabetic (int c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
  }
  static boolean is_numeric (int c) {return c >= '0' && c <= '9';}
  static boolean is_alphanumeric (int c) {
    return is_numeric (c) || is_alphabetic (c);
  }
}
