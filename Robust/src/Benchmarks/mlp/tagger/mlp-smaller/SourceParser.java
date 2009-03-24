/**
 * SourceParser class
 * <p>
 * <code>int</code>.
 *
 * @author  Daniel Jackson
 * @version 0, 07/02/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;

public class SourceParser {
  public static String loadcharmapcommand_name   () { return  "loadchars"; }
  public static String loadstylesheetcommand_name() { return  "loadstyles";}
  public static String preamblecommand_name      () { return  "preamble";  }
  public static String refcommand_name           () { return  "ref";	   }
  public static String tagcommand_name           () { return  "tag";	   }
  public static String citecommand_name          () { return  "cite";	   }
  public static String separatorcommand_name     () { return  "sep";       }

  private FileInputStream reader;

  // holds set of strings recognized as paragraph styles
  private HashSet parastyles;

  // holds the previous value of next_char
  private int last_char;
  private int next_char;
  private boolean within_single_quotes;
  private boolean within_double_quotes;
  private boolean at_start_of_line;
  private String token;
  
  public SourceParser (FileInputStream reader, HashSet parastyles) {
    this.reader = reader;
    this.parastyles = parastyles;
    next_char = reader.read ();
    last_char = -1;
    at_start_of_line = true;
  }
  
  public boolean has_more_tokens () {
    return (next_char != -1);
  }
  
  private void consume_char () {
    token += (char) next_char;
    last_char = next_char;
    next_char = reader.read ();
  }

  // consume until next close curly and return string excluding curly
  private String consume_arg () {
    consume_char (); // consume open curly
    token = "";
    consume_char ();
    while (!is_close_curly (next_char) && !is_eol (next_char)) consume_char ();
    String arg = token;
    consume_char (); // consume close curly
    return arg;
  }
  
  /**
   * requires: next_char contains next character in reader <p>
   * ensures: returns next token according to one of these productions: <p>
   * <blockquote><pre>
   *    	char-sequence = alphanumeric+
   *		whitespace ::= (space | tab)+
   *		command ::= slash alphanum* [star]
   *		paragraph-break ::= <blank line>
   *		line-break ::= slash slash
   *		hyphen-sequence ::= hyphen+
   *		dot-sequence ::= dot+
   *		underscore ::= underscore
   * </pre></blockquote>
   *   	quote characters, disambiguated by context:
   *		open-single-quote: when not preceded by alphanumeric
   *		close-single-quote: when not followed by alphanumeric and preceded by
   *			open-single-quote
   *		open-double-quote: when not preceded by open-double-quote
   *		close-double-quote: when preceded by open-double-quote
   *		apostrophe: between alphanumerics, or when followed by numeric
   *		prime: after alphanumeric, when not followed by alphanumeric,
   *			and not preceded by open-single-quote
   * @return the next token.
   *	explicitly returns end of stream token.
   */
  public Token get_token () {
    token = new String ("");
    if (is_eos (next_char))
      return new Token (Token.ENDOFSTREAM(), 0);
    if (at_start_of_line) {
      if (is_eol (next_char)) {
	consume_char ();
	within_single_quotes = false;
	within_double_quotes = false;
	return new Token (Token.PARABREAK(), 0);
      }
      else if (is_hash (next_char)) {
	String line = reader.readLine ();
	consume_char ();
	return new Token (Token.COMMENT(), line, 0);
      }
      else
	at_start_of_line = false;
    }
    if (is_eol (next_char)) {
      consume_char ();
      at_start_of_line = true;
      if (is_eol (next_char)) {
	consume_char ();
	within_single_quotes = false;
	within_double_quotes = false;
	return new Token (Token.PARABREAK(), 0);
      }
      // check this
      return new Token (Token.WHITESPACE(), " ", 0);
    }
    if (is_slash (next_char)) {
      consume_char ();
      token = new String("");
      if (is_slash (next_char)) {
	consume_char ();
	return new Token (Token.LINEBREAK(), 0);
      }
      if (!is_alphabetic (next_char)) {
	// next character assumed prefixed with slash to avoid special treatment
	// eg, \< for <, \$ for $
	token = new Character ((char) next_char).toString ();
	return new Token (Token.OTHER(), token, 0);
      }
      while (is_alphanumeric (next_char)) consume_char ();
      String command_name = token;
      if (is_star (next_char)) consume_char ();
      if (command_name.equals (preamblecommand_name())) {
	return new Token (Token.PREAMBLECOMMAND(), 0);
      }
      if (command_name.equals (separatorcommand_name())) {
	// consume whitespace until next token
	while (is_whitespace (next_char)) consume_char ();
	return new Token (Token.SEPARATORCOMMAND(), 0);
      }
      if (is_less_than (next_char)) {
	consume_char ();
	return new Token (Token.FORMATCOMMAND(), command_name, 0);
      }
      if (is_open_curly (next_char)) {
	String arg = consume_arg ();
	if (command_name.equals (loadcharmapcommand_name())) {
	  return new Token (Token.LOADCHARMAPCOMMAND(), arg, 0);
	}
	if (command_name.equals (loadstylesheetcommand_name())) {
	  return new Token (Token.LOADSTYLESHEETCOMMAND(), arg, 0);
	}
	if (command_name.equals (refcommand_name())) {
	  return new Token (Token.REFCOMMAND(), arg, 0);
	}
	if (command_name.equals (tagcommand_name())) {
	  return new Token (Token.TAGCOMMAND(), arg, 0);
	}
	if (command_name.equals (citecommand_name())) {
	  return new Token (Token.CITECOMMAND(), arg, 0);
	}
      }
      if (parastyles.contains (command_name)) {
	while (is_whitespace (next_char)) consume_char ();
	// paragraph style command consumes the first linebreak following it also
	if (is_eol (next_char)) consume_char ();
	return new Token (Token.PARASTYLECOMMAND(), command_name, 0);
      }
      else
	// temporary
	return new Token (Token.CHARCOMMAND(), command_name, 0);
    }
    if (is_alphabetic (next_char)) {
      consume_char ();
      while (is_alphabetic (next_char)) consume_char ();
      return new Token (Token.ALPHABETIC(), token, 0);
    }
    if (is_numeric (next_char)) {
      consume_char ();
      while (is_numeric (next_char)) consume_char ();
      return new Token (Token.NUMERIC(), token, 0);
    }
    if (is_whitespace (next_char)) {
      consume_char ();
      while (is_whitespace (next_char)) consume_char ();
      if (is_eol (next_char)) {
	consume_char ();
	// check this
	return new Token (Token.WHITESPACE(), " ", 0);
      }
      return new Token (Token.WHITESPACE(), token, 0);
    }
    if (is_hyphen (next_char)) {
      consume_char ();
      while (is_hyphen (next_char)) consume_char ();
      return new Token (Token.HYPHENS(), token, 0);
    }
    if (is_dot (next_char)) {
      consume_char ();
      while (is_dot (next_char)) consume_char ();
      return new Token (Token.DOTS(), token, 0);
    }
    if (is_underscore (next_char)) {
      consume_char ();
      return new Token (Token.UNDERSCORE(), 0);
    }
    if (is_dollar (next_char)) {
      consume_char ();
      return new Token (Token.DOLLAR(), 0);
    }
    if (is_greater_than (next_char)) {
      consume_char ();
      return new Token (Token.POPFORMATCOMMAND(), 0);
    }
    if (is_single_quote (next_char)) {
      if (is_alphanumeric (last_char)) {
	if (is_alphanumeric (next_char)) {
	  consume_char ();
	  return new Token (Token.APOSTROPHE(), 0);
	}
	else if (within_single_quotes) {
	  within_single_quotes = false;
	  consume_char ();
	  return new Token (Token.CLOSESINGLEQUOTE(), 0);
	}
	else {
	  consume_char ();
	  return new Token (Token.PRIME(), 0);
	}
      }
      consume_char ();
      if (is_numeric (next_char)) {
	return new Token (Token.APOSTROPHE(), 0);
      }
      else {
	within_single_quotes = true;
	return new Token (Token.OPENSINGLEQUOTE(), 0);
      }
    }
    if (is_double_quote (next_char)) {
      consume_char ();
      if (within_double_quotes) {
	within_double_quotes = false;
	return new Token (Token.CLOSEDOUBLEQUOTE(), 0);
      }
      else {
	within_double_quotes = true;
	return new Token (Token.OPENDOUBLEQUOTE(), 0);
      }
    }
    consume_char ();
    return new Token (Token.OTHER(), token, 0);
  }

  static boolean is_eol (int c) {return c == '\n';}
  static boolean is_eos (int c) {return c == -1;}
  static boolean is_star (int c) {return c == '*';}
  static boolean is_hash (int c) {return c == '#';}
  static boolean is_dot (int c) {return c == '.';}
  static boolean is_slash (int c) {return c == '\\';}
  static boolean is_hyphen (int c) {return c == '-';}
  static boolean is_underscore (int c) {return c == '_';}
  static boolean is_dollar (int c) {return c == '$';}
  static boolean is_single_quote (int c) {return c == '\'';}
  static boolean is_double_quote (int c) {return c == '\"';}
  static boolean is_open_curly (int c) {return c == '{';}
  static boolean is_close_curly (int c) {return c == '}';}
  static boolean is_less_than (int c) {return c == '<';}
  static boolean is_greater_than (int c) {return c == '>';}
  
  // should perhaps use Character.isLetter? not sure, because that allows Unicode chars for
  // other languages that are outside the a-Z range.
  static boolean is_alphabetic (int c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
  }
  static boolean is_numeric (int c) {return c >= '0' && c <= '9';}
  static boolean is_alphanumeric (int c) {
    return is_numeric (c) || is_alphabetic (c);
  }
  static boolean is_whitespace (int c) {return c == ' ' || c == '\t';}
}
