/* TreeMap.java -- a class providing a basic Red-Black Tree data structure,
   mapping Object --> Object
   Copyright (C) 1998, 1999, 2000, 2001, 2002, 2004, 2005, 2006  Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */


package java.util;

import java.util.TreeMap.Node;


/**
 * This class provides a red-black tree implementation of the SortedMap
 * interface.  Elements in the Map will be sorted by either a user-provided
 * Comparator object, or by the natural ordering of the keys.
 *
 * The algorithms are adopted from Corman, Leiserson, and Rivest's
 * <i>Introduction to Algorithms.</i>  TreeMap guarantees O(log n)
 * insertion and deletion of elements.  That being said, there is a large
 * enough constant coefficient in front of that "log n" (overhead involved
 * in keeping the tree balanced), that TreeMap may not be the best choice
 * for small collections. If something is already sorted, you may want to
 * just use a LinkedHashMap to maintain the order while providing O(1) access.
 *
 * TreeMap is a part of the JDK1.2 Collections API.  Null keys are allowed
 * only if a Comparator is used which can deal with them; natural ordering
 * cannot cope with null.  Null values are always allowed. Note that the
 * ordering must be <i>consistent with equals</i> to correctly implement
 * the Map interface. If this condition is violated, the map is still
 * well-behaved, but you may have suprising results when comparing it to
 * other maps.<p>
 *
 * This implementation is not synchronized. If you need to share this between
 * multiple threads, do something like:<br>
 * <code>SortedMap m
 *       = Collections.synchronizedSortedMap(new TreeMap(...));</code><p>
 *
 * The iterators are <i>fail-fast</i>, meaning that any structural
 * modification, except for <code>remove()</code> called on the iterator
 * itself, cause the iterator to throw a
 * <code>ConcurrentModificationException</code> rather than exhibit
 * non-deterministic behavior.
 *
 * @author Jon Zeppieri
 * @author Bryce McKinlay
 * @author Eric Blake (ebb9@email.byu.edu)
 * @author Andrew John Hughes (gnu_andrew@member.fsf.org)
 * @see Map
 * @see HashMap
 * @see Hashtable
 * @see LinkedHashMap
 * @see Comparable
 * @see Comparator
 * @see Collection
 * @see Collections#synchronizedSortedMap(SortedMap)
 * @since 1.2
 * @status updated to 1.6
 */
public class TreeMap//<K, V> extends AbstractMap<K, V>
  implements Map, SortedMap //NavigableMap<K, V>, Cloneable, Serializable
{
  // Implementation note:
  // A red-black tree is a binary search tree with the additional properties
  // that all paths to a leaf node visit the same number of black nodes,
  // and no red node has red children. To avoid some null-pointer checks,
  // we use the special node nil which is always black, has no relatives,
  // and has key and value of null (but is not equal to a mapping of null).

  /**
   * Compatible with JDK 1.2.
   */
  private static final long serialVersionUID = 919286545866124006L;

  /**
   * Color status of a node. Package visible for use by nested classes.
   */
  static final int RED = -1,
                   BLACK = 1;

  /**
   * Sentinal node, used to avoid null checks for corner cases and make the
   * delete rebalance code simpler. The rebalance code must never assign
   * the parent, left, or right of nil, but may safely reassign the color
   * to be black. This object must never be used as a key in a TreeMap, or
   * it will break bounds checking of a SubMap.
   */
  static final TreeNode nil = new TreeNode(null, null, BLACK);
  static
    {
      // Nil is self-referential, so we must initialize it after creation.
      nil.parent = nil;
      nil.left = nil;
      nil.right = nil;
    }

  /**
   * The root node of this TreeMap.
   */
  private transient TreeNode root;

  /**
   * The size of this TreeMap. Package visible for use by nested classes.
   */
  transient int size;

  /**
   * The cache for {@link #entrySet()}.
   */
  //private transient Set<Map.Entry<K,V>> entries;

  /**
   * The cache for {@link #descendingMap()}.
   */
  //private transient NavigableMap<K,V> descendingMap;

  /**
   * The cache for {@link #navigableKeySet()}.
   */
  //private transient NavigableSet<K> nKeys;

  /**
   * Counts the number of modifications this TreeMap has undergone, used
   * by Iterators to know when to throw ConcurrentModificationExceptions.
   * Package visible for use by nested classes.
   */
  transient int modCount;

  /**
   * This TreeMap's comparator, or null for natural ordering.
   * Package visible for use by nested classes.
   * @serial the comparator ordering this tree, or null
   */
  //final Comparator<? super K> comparator;

  /**
   * Instantiate a new TreeMap with no elements, using the keys' natural
   * ordering to sort. All entries in the map must have a key which implements
   * Comparable, and which are <i>mutually comparable</i>, otherwise map
   * operations may throw a {@link ClassCastException}. Attempts to use
   * a null key will throw a {@link NullPointerException}.
   *
   * @see Comparable
   */
  public TreeMap()
  {
    /*this((Comparator) null);
  }

  /**
   * Instantiate a new TreeMap with no elements, using the provided comparator
   * to sort. All entries in the map must have keys which are mutually
   * comparable by the Comparator, otherwise map operations may throw a
   * {@link ClassCastException}.
   *
   * @param c the sort order for the keys of this map, or null
   *        for the natural order
   */
  /*public TreeMap(Comparator<? super K> c)
  {
    comparator = c;*/
    fabricateTree(0);
  }

  /**
   * Instantiate a new TreeMap, initializing it with all of the elements in
   * the provided Map.  The elements will be sorted using the natural
   * ordering of the keys. This algorithm runs in n*log(n) time. All entries
   * in the map must have keys which implement Comparable and are mutually
   * comparable, otherwise map operations may throw a
   * {@link ClassCastException}.
   *
   * @param map a Map, whose entries will be put into this TreeMap
   * @throws ClassCastException if the keys in the provided Map are not
   *         comparable
   * @throws NullPointerException if map is null
   * @see Comparable
   */
  /*public TreeMap(Map<? extends K, ? extends V> map)
  {
    this((Comparator) null);
    putAll(map);
  }*/

  /**
   * Instantiate a new TreeMap, initializing it with all of the elements in
   * the provided SortedMap.  The elements will be sorted using the same
   * comparator as in the provided SortedMap. This runs in linear time.
   *
   * @param sm a SortedMap, whose entries will be put into this TreeMap
   * @throws NullPointerException if sm is null
   */
  /*public TreeMap(SortedMap<K, ? extends V> sm)
  {
    this(sm.comparator());
    int pos = sm.size();
    Iterator itr = sm.entrySet().iterator();

    fabricateTree(pos);
    Node node = firstNode();

    while (--pos >= 0)
      {
        Map.Entry me = (Map.Entry) itr.next();
        node.key = me.getKey();
        node.value = me.getValue();
        node = successor(node);
      }
  }*/

  /**
   * Clears the Map so it has no keys. This is O(1).
   */
  public void clear()
  {
    if (size > 0)
      {
        modCount++;
        root = nil;
        size = 0;
      }
  }

  /**
   * Returns a shallow clone of this TreeMap. The Map itself is cloned,
   * but its contents are not.
   *
   * @return the clone
   */
  /*public Object clone()
  {
    TreeMap copy = null;
    try
      {
        copy = (TreeMap) super.clone();
      }
    catch (CloneNotSupportedException x)
      {
      }
    copy.entries = null;
    copy.fabricateTree(size);

    Node node = firstNode();
    Node cnode = copy.firstNode();

    while (node != nil)
      {
        cnode.key = node.key;
        cnode.value = node.value;
        node = successor(node);
        cnode = copy.successor(cnode);
      }
    return copy;
  }*/

  /**
   * Return the comparator used to sort this map, or null if it is by
   * natural order.
   *
   * @return the map's comparator
   */
  /*public Comparator<? super K> comparator()
  {
    return comparator;
  }*/

  /**
   * Returns true if the map contains a mapping for the given key.
   *
   * @param key the key to look for
   * @return true if the key has a mapping
   * @throws ClassCastException if key is not comparable to map elements
   * @throws NullPointerException if key is null and the comparator is not
   *         tolerant of nulls
   */
  public boolean containsKey(Object key)
  {
    return getNode(key) != nil;
  }

  /**
   * Returns true if the map contains at least one mapping to the given value.
   * This requires linear time.
   *
   * @param value the value to look for
   * @return true if the value appears in a mapping
   */
  /*public boolean containsValue(Object value)
  {
    Node node = firstNode();
    while (node != nil)
      {
        if (equals(value, node.value))
          return true;
        node = successor(node);
      }
    return false;
  }*/

  /**
   * Returns a "set view" of this TreeMap's entries. The set is backed by
   * the TreeMap, so changes in one show up in the other.  The set supports
   * element removal, but not element addition.<p>
   *
   * Note that the iterators for all three views, from keySet(), entrySet(),
   * and values(), traverse the TreeMap in sorted sequence.
   *
   * @return a set view of the entries
   * @see #keySet()
   * @see #values()
   * @see Map.Entry
   */
  /*public Set<Map.Entry<K,V>> entrySet()
  {
    if (entries == null)
      // Create an AbstractSet with custom implementations of those methods
      // that can be overriden easily and efficiently.
      entries = new NavigableEntrySet();
    return entries;
  }*/

  /**
   * Returns the first (lowest) key in the map.
   *
   * @return the first key
   * @throws NoSuchElementException if the map is empty
   */
  public Object firstKey()
  {
    if (root == nil)
      throw new /*NoSuchElement*/Exception("NoSuchElementException");
    return firstNode().key;
  }

  /**
   * Return the value in this TreeMap associated with the supplied key,
   * or <code>null</code> if the key maps to nothing.  NOTE: Since the value
   * could also be null, you must use containsKey to see if this key
   * actually maps to something.
   *
   * @param key the key for which to fetch an associated value
   * @return what the key maps to, if present
   * @throws ClassCastException if key is not comparable to elements in the map
   * @throws NullPointerException if key is null but the comparator does not
   *         tolerate nulls
   * @see #put(Object, Object)
   * @see #containsKey(Object)
   */
  public Object get(Object key)
  {
    // Exploit fact that nil.value == null.
    return getNode(key).value;
  }

  /**
   * Returns the last (highest) key in the map.
   *
   * @return the last key
   * @throws NoSuchElementException if the map is empty
   */
  public Object lastKey()
  {
    if (root == nil)
      throw new /*NoSuchElement*/Exception("NoSuchElementException empty");
    return lastNode().key;
  }

  /**
   * Puts the supplied value into the Map, mapped by the supplied key.
   * The value may be retrieved by any object which <code>equals()</code>
   * this key. NOTE: Since the prior value could also be null, you must
   * first use containsKey if you want to see if you are replacing the
   * key's mapping.
   *
   * @param key the key used to locate the value
   * @param value the value to be stored in the Map
   * @return the prior mapping of the key, or null if there was none
   * @throws ClassCastException if key is not comparable to current map keys
   * @throws NullPointerException if key is null, but the comparator does
   *         not tolerate nulls
   * @see #get(Object)
   * @see Object#equals(Object)
   */
  public Object put(Object key, Object value)
  {
    TreeNode current = root;
    TreeNode parent = nil;
    int comparison = 0;

    // Find new node's parent.
    while (current != nil)
      {
        parent = current;
        comparison = compare(key, current.key);
        if (comparison > 0)
          current = current.right;
        else if (comparison < 0)
          current = current.left;
        else // Key already in tree.
          return current.setValue(value);
      }

    // Set up new node.
    TreeNode n = new TreeNode(key, value, RED);
    n.parent = parent;

    // Insert node in tree.
    modCount++;
    size++;
    if (parent == nil)
      {
        // Special case inserting into an empty tree.
        root = n;
        return null;
      }
    if (comparison > 0)
      parent.right = n;
    else
      parent.left = n;

    // Rebalance after insert.
    insertFixup(n);
    return null;
  }

  /**
   * Copies all elements of the given map into this TreeMap.  If this map
   * already has a mapping for a key, the new mapping replaces the current
   * one.
   *
   * @param m the map to be added
   * @throws ClassCastException if a key in m is not comparable with keys
   *         in the map
   * @throws NullPointerException if a key in m is null, and the comparator
   *         does not tolerate nulls
   */
  /*public void putAll(Map<? extends K, ? extends V> m)
  {
    Iterator itr = m.entrySet().iterator();
    int pos = m.size();
    while (--pos >= 0)
      {
        Map.Entry<K,V> e = (Map.Entry<K,V>) itr.next();
        put(e.getKey(), e.getValue());
      }
  }*/

  /**
   * Removes from the TreeMap and returns the value which is mapped by the
   * supplied key. If the key maps to nothing, then the TreeMap remains
   * unchanged, and <code>null</code> is returned. NOTE: Since the value
   * could also be null, you must use containsKey to see if you are
   * actually removing a mapping.
   *
   * @param key the key used to locate the value to remove
   * @return whatever the key mapped to, if present
   * @throws ClassCastException if key is not comparable to current map keys
   * @throws NullPointerException if key is null, but the comparator does
   *         not tolerate nulls
   */
  public Object remove(Object key)
  {
    TreeNode n = getNode(key);
    if (n == nil)
      return null;
    // Note: removeNode can alter the contents of n, so save value now.
    Object result = n.value;
    removeNode(n);
    return result;
  }

  /**
   * Returns the number of key-value mappings currently in this Map.
   *
   * @return the size
   */
  public int size()
  {
    return size;
  }

  /**
   * Maintain red-black balance after deleting a node.
   *
   * @param node the child of the node just deleted, possibly nil
   * @param parent the parent of the node just deleted, never nil
   */
  private void deleteFixup(TreeNode node, TreeNode parent)
  {
    // if (parent == nil)
    //   throw new InternalError();
    // If a black node has been removed, we need to rebalance to avoid
    // violating the "same number of black nodes on any path" rule. If
    // node is red, we can simply recolor it black and all is well.
    while (node != root && node.color == BLACK)
      {
        if (node == parent.left)
          {
            // Rebalance left side.
            TreeNode sibling = parent.right;
            // if (sibling == nil)
            //   throw new InternalError();
            if (sibling.color == RED)
              {
                // Case 1: Sibling is red.
                // Recolor sibling and parent, and rotate parent left.
                sibling.color = BLACK;
                parent.color = RED;
                rotateLeft(parent);
                sibling = parent.right;
              }

            if (sibling.left.color == BLACK && sibling.right.color == BLACK)
              {
                // Case 2: Sibling has no red children.
                // Recolor sibling, and move to parent.
                sibling.color = RED;
                node = parent;
                parent = parent.parent;
              }
            else
              {
                if (sibling.right.color == BLACK)
                  {
                    // Case 3: Sibling has red left child.
                    // Recolor sibling and left child, rotate sibling right.
                    sibling.left.color = BLACK;
                    sibling.color = RED;
                    rotateRight(sibling);
                    sibling = parent.right;
                  }
                // Case 4: Sibling has red right child. Recolor sibling,
                // right child, and parent, and rotate parent left.
                sibling.color = parent.color;
                parent.color = BLACK;
                sibling.right.color = BLACK;
                rotateLeft(parent);
                node = root; // Finished.
              }
          }
        else
          {
            // Symmetric "mirror" of left-side case.
            TreeNode sibling = parent.left;
            // if (sibling == nil)
            //   throw new InternalError();
            if (sibling.color == RED)
              {
                // Case 1: Sibling is red.
                // Recolor sibling and parent, and rotate parent right.
                sibling.color = BLACK;
                parent.color = RED;
                rotateRight(parent);
                sibling = parent.left;
              }

            if (sibling.right.color == BLACK && sibling.left.color == BLACK)
              {
                // Case 2: Sibling has no red children.
                // Recolor sibling, and move to parent.
                sibling.color = RED;
                node = parent;
                parent = parent.parent;
              }
            else
              {
                if (sibling.left.color == BLACK)
                  {
                    // Case 3: Sibling has red right child.
                    // Recolor sibling and right child, rotate sibling left.
                    sibling.right.color = BLACK;
                    sibling.color = RED;
                    rotateLeft(sibling);
                    sibling = parent.left;
                  }
                // Case 4: Sibling has red left child. Recolor sibling,
                // left child, and parent, and rotate parent right.
                sibling.color = parent.color;
                parent.color = BLACK;
                sibling.left.color = BLACK;
                rotateRight(parent);
                node = root; // Finished.
              }
          }
      }
    node.color = BLACK;
  }

  /**
   * Construct a perfectly balanced tree consisting of n "blank" nodes. This
   * permits a tree to be generated from pre-sorted input in linear time.
   *
   * @param count the number of blank nodes, non-negative
   */
  private void fabricateTree(final int count)
  {
    if (count == 0)
      {
	root = nil;
	size = 0;
	return;
      }

    // We color every row of nodes black, except for the overflow nodes.
    // I believe that this is the optimal arrangement. We construct the tree
    // in place by temporarily linking each node to the next node in the row,
    // then updating those links to the children when working on the next row.

    // Make the root node.
    root = new TreeNode(null, null, BLACK);
    size = count;
    TreeNode row = root;
    int rowsize;

    // Fill each row that is completely full of nodes.
    for (rowsize = 2; rowsize + rowsize <= count; rowsize <<= 1)
      {
        TreeNode parent = row;
        TreeNode last = null;
        for (int i = 0; i < rowsize; i += 2)
          {
            TreeNode left = new TreeNode(null, null, BLACK);
            TreeNode right = new TreeNode(null, null, BLACK);
            left.parent = parent;
            left.right = right;
            right.parent = parent;
            parent.left = left;
            TreeNode next = parent.right;
            parent.right = right;
            parent = next;
            if (last != null)
              last.right = left;
            last = right;
          }
        row = row.left;
      }

    // Now do the partial final row in red.
    int overflow = count - rowsize;
    TreeNode parent = row;
    int i;
    for (i = 0; i < overflow; i += 2)
      {
        TreeNode left = new TreeNode(null, null, RED);
        TreeNode right = new TreeNode(null, null, RED);
        left.parent = parent;
        right.parent = parent;
        parent.left = left;
        TreeNode next = parent.right;
        parent.right = right;
        parent = next;
      }
    // Add a lone left node if necessary.
    if (i - overflow == 0)
      {
        TreeNode left = new TreeNode(null, null, RED);
        left.parent = parent;
        parent.left = left;
        parent = parent.right;
        left.parent.right = nil;
      }
    // Unlink the remaining nodes of the previous row.
    while (parent != nil)
      {
        TreeNode next = parent.right;
        parent.right = nil;
        parent = next;
      }
  }

  /**
   * Returns the first sorted node in the map, or nil if empty. Package
   * visible for use by nested classes.
   *
   * @return the first node
   */
  final TreeNode firstNode()
  {
    // Exploit fact that nil.left == nil.
    TreeNode node = root;
    while (node.left != nil)
      node = node.left;
    return node;
  }
  
  /**
   * Return the node following the given one, or nil if there isn't one.
   * Package visible for use by nested classes.
   *
   * @param node the current node, not nil
   * @return the next node in sorted order
   */
  final TreeNode successor(TreeNode node)
  {
    if (node.right != nil)
      {
        node = node.right;
        while (node.left != nil)
          node = node.left;
        return node;
      }

    TreeNode parent = node.parent;
    // Exploit fact that nil.right == nil and node is non-nil.
    while (node == parent.right)
      {
        node = parent;
        parent = parent.parent;
      }
    return parent;
  }


  /**
   * Return the TreeMap.Node associated with key, or the nil node if no such
   * node exists in the tree. Package visible for use by nested classes.
   *
   * @param key the key to search for
   * @return the node where the key is found, or nil
   */
  final TreeNode getNode(Object key)
  {
    TreeNode current = root;
    while (current != nil)
      {
        int comparison = compare(key, current.key);
        if (comparison > 0)
          current = current.right;
        else if (comparison < 0)
          current = current.left;
        else
          return current;
      }
    return current;
  }
  
  final int compare(Object o1, Object o2)
  {
    if((o1 instanceof Integer) && (o2 instanceof Integer)) {
      if(((Integer)o1).intValue() > ((Integer)o2).intValue()) {
        return 1;
      } else if(((Integer)o1).intValue() > ((Integer)o2).intValue()) {
        return 0;
      } else {
        return -1;
      }
    }
    System.println("Compare non-int values in TreeMap.compare(Object, Object)");
    return 0;
  }
  
  /**
   * Maintain red-black balance after inserting a new node.
   *
   * @param n the newly inserted node
   */
  private void insertFixup(TreeNode n)
  {
    // Only need to rebalance when parent is a RED node, and while at least
    // 2 levels deep into the tree (ie: node has a grandparent). Remember
    // that nil.color == BLACK.
    while (n.parent.color == RED && n.parent.parent != nil)
      {
        if (n.parent == n.parent.parent.left)
          {
            TreeNode uncle = n.parent.parent.right;
            // Uncle may be nil, in which case it is BLACK.
            if (uncle.color == RED)
              {
                // Case 1. Uncle is RED: Change colors of parent, uncle,
                // and grandparent, and move n to grandparent.
                n.parent.color = BLACK;
                uncle.color = BLACK;
                uncle.parent.color = RED;
                n = uncle.parent;
              }
            else
              {
                if (n == n.parent.right)
                  {
                    // Case 2. Uncle is BLACK and x is right child.
                    // Move n to parent, and rotate n left.
                    n = n.parent;
                    rotateLeft(n);
                  }
                // Case 3. Uncle is BLACK and x is left child.
                // Recolor parent, grandparent, and rotate grandparent right.
                n.parent.color = BLACK;
                n.parent.parent.color = RED;
                rotateRight(n.parent.parent);
              }
          }
        else
          {
            // Mirror image of above code.
            TreeNode uncle = n.parent.parent.left;
            // Uncle may be nil, in which case it is BLACK.
            if (uncle.color == RED)
              {
                // Case 1. Uncle is RED: Change colors of parent, uncle,
                // and grandparent, and move n to grandparent.
                n.parent.color = BLACK;
                uncle.color = BLACK;
                uncle.parent.color = RED;
                n = uncle.parent;
              }
            else
              {
                if (n == n.parent.left)
                {
                    // Case 2. Uncle is BLACK and x is left child.
                    // Move n to parent, and rotate n right.
                    n = n.parent;
                    rotateRight(n);
                  }
                // Case 3. Uncle is BLACK and x is right child.
                // Recolor parent, grandparent, and rotate grandparent left.
                n.parent.color = BLACK;
                n.parent.parent.color = RED;
                rotateLeft(n.parent.parent);
              }
          }
      }
    root.color = BLACK;
  }

  /**
   * Returns the last sorted node in the map, or nil if empty.
   *
   * @return the last node
   */
  public TreeNode lastNode()
  {
    // Exploit fact that nil.right == nil.
    TreeNode node = root;
    while (node.right != nil)
      node = node.right;
    return node;
  }
  
  /**
   * Remove node from tree. This will increment modCount and decrement size.
   * Node must exist in the tree. Package visible for use by nested classes.
   *
   * @param node the node to remove
   */
  final void removeNode(TreeNode node)
  {
    TreeNode splice;
    TreeNode child;

    modCount++;
    size--;

    // Find splice, the node at the position to actually remove from the tree.
    if (node.left == nil)
      {
        // Node to be deleted has 0 or 1 children.
        splice = node;
        child = node.right;
      }
    else if (node.right == nil)
      {
        // Node to be deleted has 1 child.
        splice = node;
        child = node.left;
      }
    else
      {
        // Node has 2 children. Splice is node's predecessor, and we swap
        // its contents into node.
        splice = node.left;
        while (splice.right != nil)
          splice = splice.right;
        child = splice.left;
        node.key = splice.key;
        node.value = splice.value;
      }

    // Unlink splice from the tree.
    TreeNode parent = splice.parent;
    if (child != nil)
      child.parent = parent;
    if (parent == nil)
      {
        // Special case for 0 or 1 node remaining.
        root = child;
        return;
      }
    if (splice == parent.left)
      parent.left = child;
    else
      parent.right = child;

    if (splice.color == BLACK)
      deleteFixup(child, parent);
  }

  /**
   * Rotate node n to the left.
   *
   * @param node the node to rotate
   */
  private void rotateLeft(TreeNode node)
  {
    TreeNode child = node.right;
    // if (node == nil || child == nil)
    //   throw new InternalError();

    // Establish node.right link.
    node.right = child.left;
    if (child.left != nil)
      child.left.parent = node;

    // Establish child->parent link.
    child.parent = node.parent;
    if (node.parent != nil)
      {
        if (node == node.parent.left)
          node.parent.left = child;
        else
          node.parent.right = child;
      }
    else
      root = child;

    // Link n and child.
    child.left = node;
    node.parent = child;
  }

  /**
   * Rotate node n to the right.
   *
   * @param node the node to rotate
   */
  private void rotateRight(TreeNode node)
  {
    TreeNode child = node.left;
    // if (node == nil || child == nil)
    //   throw new InternalError();

    // Establish node.left link.
    node.left = child.right;
    if (child.right != nil)
      child.right.parent = node;

    // Establish child->parent link.
    child.parent = node.parent;
    if (node.parent != nil)
      {
        if (node == node.parent.right)
          node.parent.right = child;
        else
          node.parent.left = child;
      }
    else
      root = child;

    // Link n and child.
    child.right = node;
    node.parent = child;
  }
  
  public TreeMap subMap(Object fromKey, Object toKey)
  {
    new SubMap(fromKey, toKey)
  }
  
  /**
   * Find the "lowest" node which is &gt;= key. If key is nil, return either
   * nil or the first node, depending on the parameter first.  Package visible
   * for use by nested classes.
   *
   * @param key the lower bound, inclusive
   * @param first true to return the first element instead of nil for nil key
   * @return the next node
   */
  final TreeNode lowestGreaterThan(Object key, boolean first)
  {
    return lowestGreaterThan(key, first, true);
  }

  /**
   * Find the "lowest" node which is &gt; (or equal to, if <code>equal</code>
   * is true) key. If key is nil, return either nil or the first node, depending
   * on the parameter first.  Package visible for use by nested classes.
   *
   * @param key the lower bound, inclusive
   * @param first true to return the first element instead of nil for nil key
   * @param equal true if the key should be returned if found.
   * @return the next node
   */
  final TreeNode lowestGreaterThan(Object key, boolean first, boolean equal)
  {
    if (key == nil)
      return first ? firstNode() : nil;

    TreeNode last = nil;
    TreeNode current = root;
    int comparison = 0;

    while (current != nil)
      {
        last = current;
        comparison = compare(key, current.key);
        if (comparison > 0)
          current = current.right;
        else if (comparison < 0)
          current = current.left;
        else
          return (equal ? current : successor(current));
      }
    return comparison > 0 ? successor(last) : last;
  }
  
  /* 0=keys, 1=values, 2=entities */
  public Iterator iterator(int type) {
    return (Iterator)(new TreeMapIterator(this, type));
  }
} // class TreeMap
