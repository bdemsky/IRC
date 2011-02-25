package Analysis.Pointer;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

public class Util {
  public static <T> MySet<T> setSubtract(Set <T> orig, Set<T> sub) {
    MySet<T> newset=new MySet<T>();
    for(T e: orig) {
      if (!sub.contains(e))
	newset.add(e);
    }
    return newset;
  }

  public static <K,V> void relationUpdate(HashMap<K,MySet<V>> map, K key, MySet<V> toremove, MySet<V> toadd) {
    if (map.containsKey(key)) {
      if (toremove!=null)
	map.get(key).removeAll(toremove);
      map.get(key).addAll(toadd);
    } else {
      map.put(key, (MySet<V>) toadd.clone());
    }
  }

}