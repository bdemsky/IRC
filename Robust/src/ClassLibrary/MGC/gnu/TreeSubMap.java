public final class TreeSubMap
{
  TreeMap map;
  /**
   * The lower range of this view, inclusive, or nil for unbounded.
   * Package visible for use by nested classes.
   */
  final Object minKey;

  /**
   * The upper range of this view, exclusive, or nil for unbounded.
   * Package visible for use by nested classes.
   */
  final Object maxKey;

  /**
   * Create a SubMap representing the elements between minKey (inclusive)
   * and maxKey (exclusive). If minKey is nil, SubMap has no lower bound
   * (headMap). If maxKey is nil, the SubMap has no upper bound (tailMap).
   *
   * @param minKey the lower bound
   * @param maxKey the upper bound
   * @throws IllegalArgumentException if minKey &gt; maxKey
   */
  TreeSubMap(TreeMap map, Object minKey, Object maxKey)
  {
    this.map = map;
    if (minKey != TreeMap.nil && maxKey != TreeMap.nil && map.compare(minKey, maxKey) > 0)
      throw new /*IllegalArgument*/Exception("IllegalArgumentException: fromKey > toKey");
    this.minKey = minKey;
    this.maxKey = maxKey;
    System.out.println("TreeSubMap() " + (String)this.minKey + " " + (String)this.maxKey);
  }

  public int size()
  {
    System.out.println("TreeSubMap.size() " + (String)this.minKey + " " + (String)this.maxKey);
    TreeNode node = map.lowestGreaterThan(minKey, true);
    TreeNode max = map.lowestGreaterThan(maxKey, false);
    System.out.println((String)node.key);
    System.out.println((String)max.key);
    int count = 0;
    while (node != max)
    {
      count++;
      node = map.successor(node);
    }
    return count;
  }
  
  /* 0=keys, 1=values, 2=entities */
  public Iterator iterator(int type) {
    TreeNode node = map.lowestGreaterThan(minKey, true);
    TreeNode max = map.lowestGreaterThan(maxKey, false);
    return (Iterator)(new TreeMapIterator(this.map, type, node, max));
  }
} // class SubMap  