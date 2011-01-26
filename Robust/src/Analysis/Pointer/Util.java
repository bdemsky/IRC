package Analysis.Pointer;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

public class Util {
  public static <T> HashSet<T> setSubtract(Set <T> orig, Set<T> sub) {
    HashSet<T> newset=new HashSet<T>();
    for(T e: orig) {
      if (!sub.contains(e))
	newset.add(e);
    }
    return newset;
  }

  public static <K,V> void relationUpdate(HashMap<K,HashSet<V>> map, K key, HashSet<V> toremove, HashSet<V> toadd) {
    if (map.containsKey(key)) {
      if (toremove!=null)
	map.get(key).removeAll(toremove);
      map.get(key).addAll(toadd);
    } else {
      map.put(key, (HashSet<V>) toadd.clone());
    }
  }

}