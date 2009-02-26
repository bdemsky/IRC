/**
 * PropertyMap class
 * Maps identifiers to property lists
 * Used for stylesheets, character maps, etc
 *
 * @author  Daniel Jackson
 * @version 0, 07/03/01
 */

package tagger;
import java.io.*;
import java.util.*;

public class PropertyMap {
	private HashMap map;	// String -> LinkedList [Property]

    /**
	* ensures: constructs an empty property map
	*/
	public PropertyMap () {
		map = new HashMap ();
		}

    /**
	* ensures: constructs a property map using the parser <code>p</code>.
	*/
	public PropertyMap (PropertyParser p) {
		map = new HashMap ();
		try {
			while (p.has_more_properties ()) {
				LinkedList props = p.get_property_list ();
				Property prop = (Property) props.removeFirst ();
				map.put (prop.value, props);
				}
			} catch (IOException e) {Assert.unreachable ();}
		}

    /**
	* ensures: incorporates properties using the parser <code>p</code>.
	*/
	public void incorporate (PropertyParser p) {
		try {
			while (p.has_more_properties ()) {
				LinkedList props = p.get_property_list ();
				Property prop = (Property) props.removeFirst ();
				map.put (prop.value, props);
				}
			} catch (IOException e) {Assert.unreachable ();}
		}

    /**
	* @return the property list for item <code>item</code>. Returns null if no such item.
	*/
	public List get_property_list (String item) {
		return (List) map.get (item);
		}

    /**
	* @return the value of property <code>prop</code> for item <code>item</code>
	* or null if it does not exist
	*/
	public String get_property (String item, String prop) {
		List props = (List) map.get (item);
		if (props == null) return null;
		ListIterator iter = props.listIterator ();
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
	public Set get_items () {
		return map.keySet ();
		}

	public String toString () {
		return map.toString ();
		}
}
