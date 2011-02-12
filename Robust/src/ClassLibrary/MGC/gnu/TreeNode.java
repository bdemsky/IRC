import TreeMap.Node;

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
    TreeNode left = nil;
    /** The right child node. */
    TreeNode right = nil;
    /** The parent node. */
    TreeNode parent = nil;

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
    }
  }