/**
 * Action class
 * Represents an action performed in response to a token
 * Instance of command pattern
 *
 * @author  Daniel Jackson
 * @version 0, 07/06/01
 */

//package tagger;
//import java.util.*;

public /*abstract*/ class Action {
  /**
   * requires: iter is an iterator that just yielded this
   * ensures: performs action for token, and may remove itself from iter
   * default behaviour is equivalent to perform
   */
  public void perform (Token token, Iterator iter) {
    perform (token);
  }

  public void perform (Token token) {
    ;
  }
}

public class ParagraphAction extends Action {
  boolean first_para;

  Generator generator;
  StringBox current_para_style;
  Numbering numbering;
  public ParagraphAction( Generator g, 
			  StringBox cps,
			  Numbering n) {
    generator = g;
    current_para_style = cps;
    numbering = n;
    first_para = true;
  }  

  public void perform (Token t, Iterator iter) {
    if (t.type != Token.PARASTYLECOMMAND()) {
      if (!first_para) generator.linefeed ();
      generator.new_para (current_para_style.string);
      String numstr = numbering.get_numbering_string (current_para_style.string);
      if (numstr.length() != 0) {
	// display numbering as evidence of progress
	System.out.println (numstr);
	generator.plaintext (numstr);
      }
      
      iter.remove ();
      first_para = false;
    }
  }
}

public class PlaintextAction extends Action {
  Generator generator;
  public PlaintextAction( Generator g ) {
    generator = g;
  }

  public void perform(Token t) {
    generator.plaintext (t.arg);
  }
}

public class NewlineAction extends Action {
  Generator generator;
  public NewlineAction( Generator g ) {
    generator = g;
  }

  public void perform(Token t) {
    generator.new_line ();
  }
}

public class ApostropheAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public ApostropheAction( Generator g,
			   PropertyMap cm) {
    generator = g;
    char_map = cm;
  }

  public void perform (Token t) {
    StandardEngine.put_special_char (generator, 
				     char_map, 
				     StandardEngine.apostrophe_char_name(), 
				     t.line);
  }
}

public class PrimeAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public PrimeAction( Generator g,
		      PropertyMap cm) {
    generator = g;
    char_map = cm;
  }

  public void perform (Token t) {
    StandardEngine.put_special_char (generator, 
				     char_map, 
				     StandardEngine.prime_char_name(), 
				     t.line);
  }  
}

public class OpenSingleQuoteAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public OpenSingleQuoteAction( Generator g,
				PropertyMap cm) {
    generator = g;
    char_map = cm;
  }
  
  public void perform (Token t) {
    StandardEngine.put_special_char (generator, 
				     char_map, 
				     StandardEngine.opensinglequote_char_name(), 
				     t.line);
  }  
}

public class CloseSingleQuoteAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public CloseSingleQuoteAction( Generator g,
				 PropertyMap cm) {
    generator = g;
    char_map = cm;
  }
  
  public void perform (Token t) {
    StandardEngine.put_special_char (generator, 
				     char_map, 
				     StandardEngine.closesinglequote_char_name(), 
				     t.line);
  }  
}

public class OpenDoubleQuoteAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public OpenDoubleQuoteAction( Generator g,
				PropertyMap cm) {
    generator = g;
    char_map = cm;
  }
  
  public void perform (Token t) {
    StandardEngine.put_special_char (generator, 
				     char_map, 
				     StandardEngine.opendoublequote_char_name(), 
				     t.line);
  }  
}

public class CloseDoubleQuoteAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public CloseDoubleQuoteAction( Generator g,
				 PropertyMap cm) {
    generator = g;
    char_map = cm;
  }
  
  public void perform (Token t) {
    StandardEngine.put_special_char (generator, 
				     char_map, 
				     StandardEngine.closedoublequote_char_name(), 
				     t.line);
  }  
}

public class HyphenAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public HyphenAction( Generator g,
		       PropertyMap cm ) {
    generator = g;
    char_map = cm;
  }

  public void perform (Token t) {
    int len = t.arg.length ();
    if (len == 1)
      StandardEngine.put_special_char (generator, 
				       char_map, 
				       StandardEngine.hyphen_char_name(), 
				       t.line);
    else if (len == 2)
      StandardEngine.put_special_char (generator, 
				       char_map, 
				       StandardEngine.endash_char_name(), 
				       t.line);
    else if (len == 3)
      StandardEngine.put_special_char (generator, 
				       char_map, 
				       StandardEngine.emdash_char_name(),
				       t.line);
    else
      System.out.println (t.line + ": Too many hyphens: " + t.arg);
  }
}

public class DotsAction extends Action {
  Generator generator;
  PropertyMap char_map;
  public DotsAction( Generator g,
		     PropertyMap cm ) {
    generator = g;
    char_map = cm;
  }

  public void perform (Token t) {
    int len = t.arg.length ();
    if (len == 1)
      generator.plaintext (".");
    else if (len == 2)
      StandardEngine.put_special_char (generator, 
				       char_map, 
				       StandardEngine.twodotleader_char_name(), 
				       t.line);
    else if (len == 3)
      StandardEngine.put_special_char (generator, 
				       char_map, 
				       StandardEngine.ellipsis_char_name(), 
				       t.line);
    else
      System.out.println (t.line + ": Too many dots: " + t.arg);
  }
}

public class LoadCharMapCommandAction extends Action {
  Generator generator;
  PropertyMap char_map;
  Numbering numbering;

  public LoadCharMapCommandAction( Generator g,
				   PropertyMap cm,
				   Numbering n ) {
    generator = g;
    char_map = cm;
    numbering = n;
  }

  public void perform (Token t) {
    // open file with given name and load char map from it
    String file_name = t.arg;
    FileInputStream s = new FileInputStream (file_name);
    PropertyParser p = new PropertyParser (s);
    char_map.incorporate (p);
  }
}

public class LoadStyleSheetCommandAction extends Action {
  Generator generator;
  PropertyMap style_map;
  Numbering numbering;

  public LoadStyleSheetCommandAction( Generator g,
				      PropertyMap sm,
				      Numbering n ) {
    generator = g;
    style_map = sm;
    numbering = n;
  }

  public void perform (Token t) {
    // open file with given name and load char map from it
    String file_name = t.arg;
    FileInputStream s = new FileInputStream (file_name);
    PropertyParser p = new PropertyParser (s);
    style_map.incorporate (p);
    numbering.incorporate ();
  }
}

public class UnsuppressAction extends Action {
  Generator generator;
 
  public UnsuppressAction( Generator g ) {
    generator = g;
  }

  public void perform (Token t, Iterator i) {
    generator.suppress_off ();
    i.remove ();
  }
}

public class PreambleCommandAction extends Action {
  Generator generator;
  Action unsuppress_action;
  StandardEngine engine;

  public PreambleCommandAction( Generator g,
				Action ua,
				StandardEngine se ) {
    generator = g;
    unsuppress_action = ua;
    engine = se;
  }

  public void perform (Token t) {
    generator.suppress_on ();
    engine.register_by_type (unsuppress_action, Token.PARABREAK());
  }
}

public class ParaBreakAction extends Action {
  Action paragraph_action;
  StringBox current_para_style;
  PropertyMap style_map;

  public ParaBreakAction( Action pa,
			  StringBox cps,
			  PropertyMap sm ) {
    paragraph_action = pa;
    current_para_style = cps;
    style_map = sm;
  }

  public void perform (Token t) {
    String next_style = style_map.get_property (current_para_style.string, 
						StandardEngine.next_style_prop_name());
    if (next_style == null) {
      System.out.println (t.line + ": No next style property given for style: " + current_para_style.string);
      return;
    }
    current_para_style.set (next_style);
    StandardEngine.register_for_all (paragraph_action);
  }
}

public class ParaStyleCommandAction extends Action {
  StringBox current_para_style;

  public ParaStyleCommandAction( StringBox cps ) {
    current_para_style = cps;
  }

  public void perform (Token t) {
    current_para_style.set (t.arg);
  }
}

public class CharCommandAction extends Action {
  Generator generator;
  PropertyMap char_map;
  
  public CharCommandAction( Generator g,
			    PropertyMap cm ) {
    generator = g;
    char_map = cm;
  }
  
  public void perform (Token t) {
    String index = char_map.get_property (t.arg, 
					  StandardEngine.index_prop_name());
    if (index == null) {
      System.out.println (t.line + ": No index property given for character: " + t.arg);
      return;
    }
    String font = char_map.get_property (t.arg, 
					 StandardEngine.font_prop_name());
    // if no font is listed, generate special character in standard font
    if (font == null)
      generator.special_char (index);
    else
      generator.special_char (font, index);
  }  
}

public class UnderscoreAction extends Action {
  Generator generator;
  boolean italic_mode_on;
  
  public UnderscoreAction( Generator g ) {
    generator = g;
    italic_mode_on = false;
  }

  public void perform (Token t) {
    if (italic_mode_on) {
      italic_mode_on = false;
      generator.pop_format ();
    }
    else {
      italic_mode_on = true;
      generator.push_format (Generator.ITALICS());
    }
  }  
}

public class PushItalicsAction extends Action {
  Generator generator;
  
  public PushItalicsAction( Generator g ) {
    generator = g;
  }

  public void perform (Token t, Iterator iter) {
    Assert.assert_ (t.type == Token.ALPHABETIC());
    generator.push_format (Generator.ITALICS());
  }
}

public class PopItalicsAction extends Action {
  Generator generator;
  
  public PopItalicsAction( Generator g ) {
    generator = g;
  }

  public void perform (Token t, Iterator iter) {
    Assert.assert_ (t.type == Token.ALPHABETIC());
    generator.pop_format ();
  }
}

public class DollarAction extends Action {
  Action push_italics_action;
  Action pop_italics_action;
  boolean math_mode_on;

  public DollarAction( Action pushia, Action popia ) {
    push_italics_action = pushia;
    pop_italics_action = popia;
    math_mode_on = false;
  }

  public void perform (Token t) {
    if (math_mode_on) {
      math_mode_on = false;
      StandardEngine.unregister_by_type (push_italics_action, Token.ALPHABETIC());
      StandardEngine.unregister_by_type (pop_italics_action, Token.ALPHABETIC());
    }
    else {
      math_mode_on = true;
      StandardEngine.register_by_type_back (pop_italics_action, Token.ALPHABETIC());
      StandardEngine.register_by_type_front (push_italics_action, Token.ALPHABETIC());
    }
  }  
}

public class FormatCommandAction extends Action {
  Generator generator;

  public FormatCommandAction( Generator g ) {
    generator = g;
  }

  public void perform (Token t) {
    if (t.arg.equals (StandardEngine.ROMAN_COMMANDNAME()))
      generator.push_format (Generator.ROMAN());
    else if (t.arg.equals (StandardEngine.BOLD_COMMANDNAME()))
      generator.push_format (Generator.BOLD());
    else if (t.arg.equals (StandardEngine.ITALICS_COMMANDNAME()))
      generator.push_format (Generator.ITALICS());
    else if (t.arg.equals (StandardEngine.SUBSCRIPT_COMMANDNAME()))
      generator.push_format (Generator.SUBSCRIPT());
    else if (t.arg.equals (StandardEngine.SUPERSCRIPT_COMMANDNAME()))
      generator.push_format (Generator.SUPERSCRIPT());
  }
}

public class PopFormatCommandAction extends Action {
  Generator generator;

  public PopFormatCommandAction( Generator g ) {
    generator = g;
  }

  public void perform (Token t) {
    generator.pop_format ();
  }
}

public class OtherAction extends Action {
  Generator generator;

  public OtherAction( Generator g ) {
    generator = g;
  }

  public void perform (Token t) {
    generator.plaintext (t.arg);
  }
}

public class EndOfStreamAction extends Action {
  Generator generator;

  public EndOfStreamAction( Generator g ) {
    generator = g;
  }
  
  public void perform (Token t) {
    System.out.println ("... done");
  }
}
