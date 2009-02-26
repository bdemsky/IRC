/**
 * Generator interface
 * Generic backend tagged text generator
 *
 * @author  Daniel Jackson
 * @version 0, 07/08/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;

public /*interface*/ class Generator {
  
  // formats to pass to push_format
  public static int ROMAN      () { return  0; }
  public static int ITALICS    () { return  1; }
  public static int BOLD       () { return  2; }
  public static int SUBSCRIPT  () { return  3; }
  public static int SUPERSCRIPT() { return  4; }
  
  // prints new line to output
  void linefeed (){}
  
  void new_para (String style){}
  
  // inserts code for new line
  void new_line (){}
  
  void special_char (String font, String index){}
  
  // for dashes, ellipses, etc
  void special_char (String index){}
  
  void plaintext (String text){}
  void push_format (int format){}
  void pop_format (){}
  
  // turn output suppression on and off
  void suppress_on (){}
  void suppress_off (){}
  
}
