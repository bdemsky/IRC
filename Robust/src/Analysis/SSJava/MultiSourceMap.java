package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class MultiSourceMap<T, V> {

  Hashtable<NTuple<T>, Set<V>> map;

  public MultiSourceMap() {
    map = new Hashtable<NTuple<T>, Set<V>>();
  }

  public void put(NTuple<T> key, NTuple<T> setKey, Set<V> set) {

    if (!map.containsKey(setKey)) {
      map.put(setKey, set);
    }
    map.put(key, set);
  }

  public void put(NTuple<T> key, NTuple<T> setKey, V value) {

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

  public Set<V> get(NTuple<T> key) {
    return map.get(key);
  }

  public String toString() {
    return map.toString();
  }

  public Hashtable<NTuple<T>, Set<V>> getMappingByStartedWith(NTuple<T> in) {

    Hashtable<NTuple<T>, Set<V>> rtrMapping = new Hashtable<NTuple<T>, Set<V>>();

    Set<NTuple<T>> keySet = map.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      NTuple<T> key = (NTuple<T>) iterator.next();
      if (key.startsWith(in)) {
        rtrMapping.put(key, map.get(key));
      }
    }

    return rtrMapping;

  }
}
