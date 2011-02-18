public final class TreeMapIterator extends Iterator
{
  /**
   * The type of this Iterator: {@link #KEYS}, {@link #VALUES},
   * or {@link #ENTRIES}.
   */
  private final int type;
  /** The number of modifications to the backing Map that we know about. */
  private int knownMod;
  /** The last Entry returned by a next() call. */
  private TreeNode last;
  /** The next entry that should be returned by next(). */
  private TreeNode next;
  /**
   * The last node visible to this iterator. This is used when iterating
   * on a SubMap.
   */
  private final TreeNode max;
  
  TreeMap map;

  /**
   * Construct a new TreeIterator with the supplied type.
   * @param type {@link #KEYS}, {@link #VALUES}, or {@link #ENTRIES}
   */
  TreeMapIterator(TreeMap map, int type)
  {
    this(map, type, map.firstNode(), TreeMap.nil);
  }

  /**
   * Construct a new TreeIterator with the supplied type. Iteration will
   * be from "first" (inclusive) to "max" (exclusive).
   *
   * @param type {@link #KEYS}, {@link #VALUES}, or {@link #ENTRIES}
   * @param first where to start iteration, nil for empty iterator
   * @param max the cutoff for iteration, nil for all remaining nodes
   */
  TreeMapIterator(TreeMap map, int type, TreeNode first, TreeNode max)
  {
    this.map = map;
    this.type = type;
    this.next = first;
    this.max = max;
    this.knownMod = this.map.modCount;
  }

  /**
   * Returns true if the Iterator has more elements.
   * @return true if there are more elements
   */
  public boolean hasNext()
  {
    return next != max;
  }

  /**
   * Returns the next element in the Iterator's sequential view.
   * @return the next element
   * @throws ConcurrentModificationException if the TreeMap was modified
   * @throws NoSuchElementException if there is none
   */
  public Object next()
  {
    if (knownMod != this.map.modCount)
      throw new /*ConcurrentModification*/Exception("ConcurrentModificationException");
    if (next == max)
      throw new /*NoSuchElement*/Exception("NoSuchElementException");
    last = next;
    next = map.successor(last);

    if (type == 1/*VALUES*/)
      return last.value;
    else if (type == 0/*KEYS*/)
      return last.key;
    return last;
  }

  /**
   * Removes from the backing TreeMap the last element which was fetched
   * with the <code>next()</code> method.
   * @throws ConcurrentModificationException if the TreeMap was modified
   * @throws IllegalStateException if called when there is no last element
   */
  public void remove()
  {
    if (last == null)
      throw new /*IllegalState*/Exception("IllegalStateException");
    if (knownMod != this.map.modCount)
      throw new /*ConcurrentModification*/Exception("ConcurrentModificationException");

    map.removeNode(last);
    last = null;
    knownMod++;
  }
} // class TreeMapIterator