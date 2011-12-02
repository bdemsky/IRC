package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

public class MultiSourceMap<T, V> {

  Hashtable<T, Set<V>> map;

  public MultiSourceMap() {
    map = new Hashtable<T, Set<V>>();
  }

  public void put(T key, Set<V> set) {
    map.put(key, set);
  }

  public void put(T key, T setKey, Set<V> set) {

    if (!map.containsKey(setKey)) {
      map.put(setKey, set);
    }
    map.put(key, set);
  }

  public void put(T key, T setKey, V value) {

    if (setKey == null) {
      if (map.containsKey(key)) {
        Set<V> set = map.get(key);
        set.add(value);
      } else {
        // first insert
        Set<V> set = new HashSet<V>();
        set.add(value);
        map.put(key, set);
      }
    } else {
      assert map.containsKey(setKey);
      Set<V> set = map.get(setKey);
      set.add(value);
      map.put(key, set);
    }
  }

  public Set<V> get(T key) {
    return map.get(key);
  }

  public String toString() {
    return map.toString();
  }


  public Set<T> keySet() {
    return map.keySet();
  }

  public void union(T newKey, Set<V> writeSet) {

    if (map.containsKey(newKey)) {
      map.get(newKey).addAll(writeSet);
    } else {
      put(newKey, writeSet);
    }

  }
}
