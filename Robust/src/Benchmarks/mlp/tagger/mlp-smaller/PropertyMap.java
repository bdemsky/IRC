/**
 * PropertyMap class
 * Maps identifiers to property lists
 * Used for stylesheets, character maps, etc
 *
 * @author  Daniel Jackson
 * @version 0, 07/03/01
 */

//package tagger;
//import java.io.*;
//import java.util.*;

public class PropertyMap {
  private HashMap map;	// String -> LinkedList [Property]
  private HashSet keys;

  /**
   * ensures: constructs an empty property map
   */
  public PropertyMap () {
    map = new HashMap ();
    keys = new HashSet();
  }

  /**
   * ensures: constructs a property map using the parser <code>p</code>.
   */
  public PropertyMap (PropertyParser p) {
    map = new HashMap ();
    keys = new HashSet();
	
    while (p.has_more_properties ()) {
      LinkedList props = p.get_property_list ();
      Property prop = (Property) props.removeFirst ();
      map.put (prop.value, props);
      keys.add(prop.value);
    }
  }

  /**
   * ensures: incorporates properties using the parser <code>p</code>.
   */
  public void incorporate (PropertyParser p) {
    
    while (p.has_more_properties ()) {
      LinkedList props = p.get_property_list ();
      Property prop = (Property) props.removeFirst ();
      map.put (prop.value, props);
      keys.add(prop.value);
    }
	
  }

  /**
   * @return the property list for item <code>item</code>. Returns null if no such item.
   */
  public LinkedList get_property_list (String item) {
    return (LinkedList) map.get (item);
  }

  /**
   * @return the value of property <code>prop</code> for item <code>item</code>
   * or null if it does not exist
   */
  public String get_property (String item, String prop) {
    LinkedList props = (LinkedList) map.get (item);
    if (props == null) return null;
    Iterator iter = props.iterator ();
    while (iter.hasNext ()) {
      Property p = (Property) iter.next ();
      if (p.property.equals (prop))
	return p.value;
    }
    return null;
  }

  /**
   * @return the set of items with property lists in the map
   */
  public HashSet get_items () {
    return keys; //map.keySet ();
  }
  
  public String toString () {
    return map.toString ();
  }
}
