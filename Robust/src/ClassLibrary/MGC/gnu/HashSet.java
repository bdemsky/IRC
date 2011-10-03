public class HashSet implements Set {
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
  public boolean addAll(Collection c) {
    Iterator it = c.iterator();
    while(it.hasNext()) {
      if(!this.add(it.next())) {
        return false;
      }
    }
    return true;
  }
  public boolean remove(Object o) {
    return (map.remove(o)!=null);
  }
  boolean removeAll(Collection c) {
    Iterator it = c.iterator();
    while(it.hasNext()) {
      if(!this.remove(it.next())) {
        return false;
      }
    }
    return true;
  }
  public boolean isEmpty() {
    return map.isEmpty();
  }
  public void clear() {
    map.clear();
  }
  public boolean contains(Object o) {
    return map.containsKey(o);
  }
  public boolean containsAll(Collection c) {
    Iterator it = c.iterator();
    while(it.hasNext()) {
      if(!this.contains(it.next())) {
        return false;
      }
    }
    return true;
  }
  public int size() {
    return map.size();
  }
  public Iterator iterator() {
    return map.iterator(0);
  }
}
