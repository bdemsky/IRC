/**
 * Class to represent an entry in the tree. Holds a single key-value pair,
 * plus pointers to parent and child nodes.
 *
 * @author Eric Blake (ebb9@email.byu.edu)
 */
public static final class TreeNode//<K, V> extends AbstractMap.SimpleEntry<K, V>
{
  // All fields package visible for use by nested classes.
  /** The color of this node. */
  int color;
  Object key;
  Object value;

  /** The left child node. */
  TreeNode left;// = TreeMap.nil;
  /** The right child node. */
  TreeNode right;// = TreeMap.nil;
  /** The parent node. */
  TreeNode parent;// = TreeMap.nil;

  /**
   * Simple constructor.
   * @param key the key
   * @param value the value
   */
  TreeNode(Object key, Object value, int color)
  {
    key = key;
    value = value;
    this.color = color;
    left = TreeMap.nil;
    right = TreeMap.nil;
    parent = TreeMap.nil;
  }
  
  TreeNode(int color)
  {
    key = null;
    value = null;
    this.color = color;
  }

  public boolean equals(Object o)
  {
    if (! (o instanceof TreeNode))
      return false;
    // Optimize for our own entries.
    TreeNode e = (TreeNode) o;
    return (key.equals(e.key) && value.equals(e.value));
  }

  /**
   * Get the key corresponding to this entry.
   *
   * @return the key
   */
  public Object getKey()
  {
    return key;
  }

  /**
   * Get the value corresponding to this entry. If you already called
   * Iterator.remove(), the behavior undefined, but in this case it works.
   *
   * @return the value
   */
  public Object getValue()
  {
    return value;
  }

  /**
   * Returns the hash code of the entry.  This is defined as the exclusive-or
   * of the hashcodes of the key and value (using 0 for null). In other
   * words, this must be:<br>
   * <pre>(getKey() == null ? 0 : getKey().hashCode())
   *       ^ (getValue() == null ? 0 : getValue().hashCode())</pre>
   *
   * @return the hash code
   */
  public int hashCode()
  {
    return (key.hashCode() ^ value.hashCode());
  }

  /**
   * Replaces the value with the specified object. This writes through
   * to the map, unless you have already called Iterator.remove(). It
   * may be overridden to restrict a null value.
   *
   * @param newVal the new value to store
   * @return the old value
   * @throws NullPointerException if the map forbids null values.
   * @throws UnsupportedOperationException if the map doesn't support
   *          <code>put()</code>.
   * @throws ClassCastException if the value is of a type unsupported
   *         by the map.
   * @throws IllegalArgumentException if something else about this
   *         value prevents it being stored in the map.
   */
  public Object setValue(Object newVal)
  {
    Object r = value;
    value = newVal;
    return r;
  }

  /**
   * This provides a string representation of the entry. It is of the form
   * "key=value", where string concatenation is used on key and value.
   *
   * @return the string representation
   */
  public String toString()
  {
    return key + "=" + value;
  }
}