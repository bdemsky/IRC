package Main;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;

/* Test skeleton for java parser/lexer.
 * Copyright (C) 1998 C. Scott Ananian <cananian@alumni.princeton.edu>
 * This is released under the terms of the GPL with NO WARRANTY.
 * See the file COPYING for more details.
 */

public class Main {
  public static void main(String args[]) throws Exception {
      if (args.length<1) {
	System.out.println("Must input source file");
	System.exit(-1);
      }
    Reader fr = new BufferedReader(new FileReader(args[0]));
    Lex.Lexer l = new Lex.Lexer(fr);
    java_cup.runtime.lr_parser g;
    g = new Parse.Parser(l);
    g./*debug_*/parse();
    System.exit(l.numErrors());
  }
}
