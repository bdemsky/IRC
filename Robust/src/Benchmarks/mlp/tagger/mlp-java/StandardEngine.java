/**
 * StandardEngine class
 * Standard registration of actions
 * Implemented as a subclass of Engine for no good reason
 *
 * @author  Daniel Jackson
 * @version 0, 07/08/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;


// a hack to work around lack of proper closures in Java
// can't assign to local variable within actions
class StringBox {
  String string;
  StringBox (String s) {string = s;}
  void set (String s) {string = s;}
}


public class StandardEngine extends Engine {

  //static Engine STANDARD;

  // reserved words for property files

  // character table
  static public String index_prop_name           () { return "index";         }
  static public String font_prop_name            () { return "font";	      }
									      
  static public String apostrophe_char_name      () { return "quoteright";    }
  static public String prime_char_name           () { return "prime";	      }
  static public String opensinglequote_char_name () { return "quoteleft";     }
  static public String closesinglequote_char_name() { return "quoteright";    }
  static public String opendoublequote_char_name () { return "quotedblleft";  }
  static public String closedoublequote_char_name() { return "quotedblright"; }
  static public String hyphen_char_name          () { return "hyphen";	      }
  static public String endash_char_name          () { return "endash";	      }
  static public String emdash_char_name          () { return "emdash";	      }
  static public String period_char_name          () { return "period";	      }
  static public String twodotleader_char_name    () { return "twodotleader";  }
  static public String ellipsis_char_name        () { return "ellipsis";      }
									      
  static public String ROMAN_COMMANDNAME         () { return "roman";	      }
  static public String BOLD_COMMANDNAME          () { return "bold";	      }
  static public String ITALICS_COMMANDNAME       () { return "italic";	      }
  static public String SUBSCRIPT_COMMANDNAME     () { return "sub";	      }
  static public String SUPERSCRIPT_COMMANDNAME   () { return "sup";	      }
									      
  // style sheet				 			      
  static public String next_style_prop_name      () { return "next";	      }
  static public String default_style_name        () { return "body";          }

  
  public StandardEngine (
			 final Generator generator,
			 final PropertyMap style_map,
			 final FileOutputStream index_stream
			 ) {
    Engine();
    
    final PropertyMap char_map = new PropertyMap ();
    final Numbering numbering = new Numbering (style_map);
    
    final StringBox current_para_style = new StringBox (default_style_name());

    // special action for start of paragraph
    // created once, but dynamically inserted and removed
    // so that it's performed once at the start of each paragraph
    final Action paragraph_action = new ParagraphAction ( generator,
							  current_para_style,
							  numbering );
    
    register_by_type (new PlaintextAction (generator),
		      Token.ALPHABETIC());
    
    register_by_type (new PlaintextAction (generator),
		      Token.NUMERIC());
    
    register_by_type (new PlaintextAction (generator),
		      Token.WHITESPACE());
    
    register_by_type (new NewlineAction (generator),
		      Token.LINEBREAK());
    
    register_by_type (new ApostropheAction (generator, char_map),
		      Token.APOSTROPHE());
    
    register_by_type (new PrimeAction (generator, char_map),
		      Token.PRIME());

    register_by_type (new OpenSingleQuoteAction (generator, char_map),
		      Token.OPENSINGLEQUOTE());
    
    register_by_type (new CloseSingleQuoteAction (generator, char_map),
		      Token.CLOSESINGLEQUOTE());
    
    register_by_type (new OpenDoubleQuoteAction (generator, char_map),
		      Token.OPENDOUBLEQUOTE());
    
    register_by_type (new CloseDoubleQuoteAction (generator, char_map),
		      Token.CLOSEDOUBLEQUOTE());
    
    register_by_type (new HyphenAction (generator, char_map),
		      Token.HYPHENS());
    
    register_by_type (new DotsAction (generator, char_map),
		      Token.DOTS());
    
    register_by_type (new LoadCharMapCommandAction (generator,
						    char_map,
						    numbering),
		      Token.LOADCHARMAPCOMMAND());
    
    register_by_type (new LoadStyleSheetCommandAction (generator,
						       style_map,
						       numbering),
		      Token.LOADSTYLESHEETCOMMAND());
    
    final Action unsuppress_action = new UnsuppressAction (generator);
    
    // preamble command switches on output suppression
    // registers action to turn suppression off with paragraph break command
    register_by_type (new PreambleCommandAction (generator,
						 unsuppress_action,
						 this ),
		      Token.PREAMBLECOMMAND());
    
    register_by_type (new ParaBreakAction (paragraph_action,
					   current_para_style,
					   style_map),
		      Token.PARABREAK());
    
    register_by_type (new ParaStyleCommandAction (current_para_style),
		      Token.PARASTYLECOMMAND());
    
    register_by_type (new CharCommandAction (generator,
					     char_map),
		      Token.CHARCOMMAND());

    register_by_type (new UnderscoreAction (generator) {},
		      Token.UNDERSCORE());
    
    // used to italicize alphabetic tokens in math mode
    final Action push_italics_action = new PushItalicsAction (generator);
    final Action pop_italics_action = new PopItalicsAction (generator);
    
    register_by_type (new DollarAction (push_italics_action,
					pop_italics_action),
		      Token.DOLLAR());
    
    register_by_type (new FormatCommandAction (generator),
		      Token.FORMATCOMMAND());
    
    register_by_type (new PopFormatCommandAction (generator),
		      Token.POPFORMATCOMMAND());
    
    register_by_type (new OtherAction (generator),
		      Token.OTHER());
    
    register_by_type (new EndOfStreamAction (generator),
		      Token.ENDOFSTREAM());
    
    //STANDARD = this;
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
      
  static public void put_special_char (Generator generator, 
				       PropertyMap char_map,
				       String char_name,		
				       int line) {
    String index = char_map.get_property (char_name, index_prop_name());
    if (index == null) {
      System.out.println (line + ": Unresolved character: " + char_name);
    }
    else
      generator.special_char (index);
  }
}
