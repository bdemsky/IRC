public class HashSet {
  HashMap map;
  HashSet() {
    map=new HashMap();
  }
  HashSet(int initialCapacity) {
    map=new HashMap(initialCapacity);
  }
  HashSet(int initialCapacity, float loadFactor) {
    map=new HashMap(initialCapacity, loadFactor);
  }
  public boolean add(Object o) {
    return (map.put(o, this)==null);
  }
  public boolean remove(Object o) {
    return (map.remove(o)!=null);
  }
  public boolean isEmpty() {
    return map.isEmpty();
  }
  public boolean contains(Object o) {
    return map.containsKey(o);
  }
  public int size() {
    return map.size();
  }
  public HashMapIterator iterator() {
    return map.iterator(0);
  }
}
