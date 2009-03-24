/**
 * Numbering class
 * Provides special navigations for numbering
 *
 * @author  Daniel Jackson
 * @version 0, 07/03/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;

public class Numbering {
  private PropertyMap style_map;

  public static String PARENT_PROPNAME   () { return  "parent";   }
  public static String CHILD_PROPNAME    () { return  "child";    }
  public static String ROOT_PROPNAME     () { return  "root";     }
  public static String COUNTER_PROPNAME  () { return  "counter";  }
  public static String SEPARATOR_PROPNAME() { return  "separator";}
  public static String LEADER_PROPNAME   () { return  "leader";   }
  public static String TRAILER_PROPNAME  () { return  "trailer";  }

  /*
   * The graph structure of the numbering relations is represented using
   * properties in the paragraph style property map.
   * Each style is mapped to its root -- the ancestor with no parent in the
   * numbering relationship -- and to its parent and child.
   * The child and root properties are added; the parent property is given
   * in the style sheet file.
   *
   * If a style is numbered, its ancestors must be also.
   * This property is not currently checked.
   */
  
  /*
   * Representation invariant
   *
   * Definition: A style is numbered if it has a counter property.
   * A numbered style has a root property.
   * A root style has itself as root and has no parent.
   * There is a bidirectional parent/child chain from a style to its root
   *
   * Checking that style sheet is well formed?
   */
  

  // maps paragraph style names to counters
  // styles that are not numbered are not mapped
  private HashMap counter_map; // String -> Counter

  /**
   * ensures: constructs a Numbering
   * not well formed until incorporate called
   */
  public Numbering (PropertyMap style_map) {
    this.style_map = style_map;
    counter_map = new HashMap ();
  }
  
  /**
   * ensures: constructs a Numbering
   * modifies: property lists in style_map
   */
  /*
    public Numbering (PropertyMap style_map) {
    this.style_map = style_map;
    add_extra_properties (style_map);
    initialize_counters (style_map);
    }
  */
  
  /**
   * ensures: constructs a Numbering using current entries in style_map
   * modifies: property lists in style_map
   */
  public void incorporate () {
    add_extra_properties ();
    initialize_counters ();
  }
  
  /*
   * requires: all ancestor and descendant styles of style are numbered iff style is numbered
   * ensures: returns the numbering string for a new paragraph whose style name is _style_
   *
   * format of numbering string is:
   * <mytrailer><rootcounter><rootseparator>...<counter><separator>...<mycounter><mytrailer>
   */
  public String get_numbering_string (String style) {
    // return empty string if style is not numbered
    if (!style_has_property (style, COUNTER_PROPNAME())) return "";
    
    // initialize numbering string to leader
    String leader = style_map.get_property (style, LEADER_PROPNAME());
    String numbering = leader == null ? "" : leader;
    
    // append numbering for each style from root to this style
    // each followed by its separator
    String s = style_map.get_property (style, ROOT_PROPNAME());
    Assert.assert_ (s != null);
    while (! s.equals (style)) {
      numbering += ((Counter) counter_map.get(s)).unparse ();
      String separator = style_map.get_property (s, SEPARATOR_PROPNAME());
      numbering += separator == null ? "" : separator;
      s = style_map.get_property (s, CHILD_PROPNAME());
    }
    
    // increment numbering for this style and append its string
    Counter c = (Counter) counter_map.get (s);
    boolean success = c.increment ();
    if (!success)
      System.out.println ("Counter overrun for style: " + style);
    numbering += c.unparse ();
    
    // append trailer
    String trailer = style_map.get_property (s, TRAILER_PROPNAME());
    numbering += trailer == null ? "" : trailer;
    
    // reset counters for all descendant styles
    s = style_map.get_property (s, CHILD_PROPNAME());
    while (s != null) {
      c = (Counter) counter_map.get (s);
      c.reset ();
      s = style_map.get_property (s, CHILD_PROPNAME());
    }
    return numbering;
  }
  
  private void add_extra_properties () {
    add_child_property ();
    add_root_property ();
  }
  
  // for each style with a counter property, insert into counter_map
  private void initialize_counters () {
    HashSet styles = style_map.get_items ();
    Iterator iter = (Iterator) styles.iterator ();
    while (iter.hasNext ()) {
      String style = (String) iter.next ();
      if (style_has_property (style, COUNTER_PROPNAME())) {
	// get counter type (arabic, roman, etc)
	String count_prop = style_map.get_property (style, COUNTER_PROPNAME());
	int count_type = Counter.get_type (count_prop);
	if (count_type == Counter.NO_SUCH_TYPE()) {
	  System.out.println ("Bad counter type for style " + style + ": " + count_prop);
	  // and insert into counter_map anyway to preserve rep invariant
	  // so must check counter type when counter is created and default if bad
	}
	counter_map.put (style, new Counter (count_prop, style));
      }
    }
  }
  
  // add to each style that is a parent of another style a child property to it
  private void add_child_property () {
    HashSet styles = style_map.get_items ();
    Iterator iter = (Iterator) styles.iterator ();
    while (iter.hasNext ()) {
      String style = (String) iter.next ();
      String pstyle = (String) style_map.get_property (style, PARENT_PROPNAME());
      // if parent exists, add child property to it
      if (pstyle != null) {
	LinkedList props = style_map.get_property_list (pstyle);
	props.add (new Property (CHILD_PROPNAME(), style));
      }
    }
  }
  
  // add root property to each numbered style
  private void add_root_property () {
    HashSet styles = style_map.get_items ();
    Iterator iter = (Iterator) styles.iterator ();
    while (iter.hasNext ()) {
      String style = (String) iter.next ();
      if (!style_has_property (style, PARENT_PROPNAME())) {
	// if no parent, then it's a root, so add root property for it and all descendants
	String root = style;
	while (style != null) {
	  LinkedList props = style_map.get_property_list (style);
	  props.add (new Property (ROOT_PROPNAME(), root));
	  style = style_map.get_property (style, CHILD_PROPNAME());
	}
      }
    }
  }
  
  // ensures: returns true iff style has property prop_name
  private boolean style_has_property (String style, String prop_name) {
    String p = (String) style_map.get_property (style, prop_name);
    return p != null;
  }
  
  public String toString () {
    return "UNIMPLEMENTED";
  }
}
