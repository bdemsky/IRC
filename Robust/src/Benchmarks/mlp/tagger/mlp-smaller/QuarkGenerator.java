/**
 * QuarkGenerator interface
 * Backend tagged text generator for QuarkXpress
 *
 * @author  Daniel Jackson
 * @version 0, 07/08/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;

public class QuarkGenerator extends Generator {
  FileOutputStream output_stream;
  LinkedList format_stack;
  private boolean on;

  public QuarkGenerator (FileOutputStream s) {
    on = true;
    output_stream = s;
    
    // stack holds strings used to terminate formats
    format_stack = /*disjoint llQuarkGen*/ new LinkedList ();
  }
  
  public void suppress_on () {
    on = false;
  }
  
  public void suppress_off () {
    on = true;
  }

  private void print (String s) {
    if (on) output_stream.write (s.getBytes());
  }

  public void linefeed () {
    if (on) output_stream.write ('\n');
  }

  // print "@style:"
  public void new_para (String style) {
    print ("@" + style + ":");
  }

  // print "<\n>"
  public void new_line () {
    print ("<\\n>");
  }

  public void special_char (String font, String index) {
    print ("<f\"" + font + "\"><\\#" + index + "><f$>");
  }

  public void special_char (String index) {
    print ("<\\#" + index + ">");
  }

  public void plaintext (String text) {
    print (text);
  }

  public void push_format (int format) {
    //switch (format) {

    if( format == Generator.ROMAN() ||
	format == Generator.ITALICS() ) {
      print ("<I>");
      format_stack.push ("<I>");
      return;
    }

    if( format == Generator.BOLD() ) {
      print ("<B>");
      format_stack.push ("<B>");
      return;
    }

    if( format == Generator.SUBSCRIPT() ) {
      print ("<->");
      format_stack.push ("<->");
      return;
    }

    if( format == Generator.SUPERSCRIPT() ) {
      print ("<+>");
      format_stack.push ("<+>");
      return;
    }
    
    Assert.unreachable ();
  }
  
  public void pop_format () {
    // for now, handle too many pops without report
    if (format_stack.isEmpty ()) return;
    print ((String) format_stack.pop ());
  }
}
