/**
 * Counter class
 *
 * @author  Daniel Jackson
 * @version 0, 07/03/01
 */

//package tagger;
//import java.io.*;

public class Counter {
  private int count;
  private int initial;
  private int type;

  static int NO_SUCH_TYPE() { return -1; }
  static int ARABIC      () { return  0; }
  static int ROMAN_UPPER () { return  1; }
  static int ROMAN_LOWER () { return  2; }
  static int ALPHA_UPPER () { return  3; }
  static int ALPHA_LOWER () { return  4; }

  // eventually recognize counter_type and set initial count and output format
  // takes style and stream for error reporting
  /*
   * requires: count_prop and style are non null
   *
   */
  public Counter (String count_prop, String style) {
    Assert.assert_ (count_prop != null);
    Assert.assert_ (style != null);
    type = get_type (count_prop);

    //switch (type) {
    if( type == NO_SUCH_TYPE() ) {
      type = ARABIC();
      initial = 0;

    } else if( type == ALPHA_LOWER() ||
	       type == ALPHA_UPPER() ) {
      if (count_prop.length () != 1) {
	System.out.println ("Bad counter type for style " + style + ": " + count_prop);
	initial = 0;	
      } else {
	initial = count_prop.toLowerCase().charAt (0) - 'a';
      }

    } else if( type == ARABIC() ) {
      initial = Integer.parseInt (count_prop) - 1;

    } else if( type == ROMAN_LOWER() ||
	       type == ROMAN_UPPER() ) {
      // not yet implemented
      initial = 0;
      type = ARABIC();
    
    } else {
      Assert.unreachable ();
    }

    count = initial;
  }
  
  /**
   * ensures: increments counter
   * returns true iff successful, false otherwise (eg, because alphabetic counter went past 'z')
   */
  public boolean increment () {
    if ((type == ALPHA_UPPER() || type == ALPHA_LOWER()) && count == 26)
      return false;
    count++;
    return true;
  }
  
  public void reset () {
    count = initial;
  }
  
  public String unparse () {

    //switch (type) {

    if( type == ALPHA_LOWER() ) {
      char c = (char) ('a' + count - 1);
      return new Character (c).toString();
    }

    if( type == ALPHA_UPPER() ) {
      char c = (char) ('A' + count - 1);
      return new Character (c).toString();
    }

    if( type == ARABIC() ) {
      return String.valueOf (count);
    }

    Assert.unreachable ();
    return "DUMMY";
  }
  
  /**
   *
   * ensures: returns counter type of counter given in the string counter_type
   * as an int, being equal to one of the values of the constants declared in the Counter class.
   * returns Counter.NO_SUCH_TYPE if the string is not well formed.
   */
  public static int get_type (String counter_type) {
    if (counter_type.length() == 0) return NO_SUCH_TYPE();
    char c = counter_type.charAt (0);
    if (c >= 'a' && c <= 'z')
      return ALPHA_LOWER();
    if (c >= 'A' && c <= 'Z')
      return ALPHA_UPPER();
    if (c == 'i' || c == 'v' || c == 'x' ||c == 'l' || c == 'c' || c == 'm')
      return ROMAN_LOWER();
    if (c == 'I' || c == 'V' || c == 'X' ||c == 'L' || c == 'C' || c == 'M')
      return ROMAN_LOWER();
    if (c >= '0' && c <= '9')
      return ARABIC();
    return NO_SUCH_TYPE();
  }
}
