package IR.Tree;

import java.util.*;

public class ChainHashMap<K,V> extends AbstractMap<K,V> {
  private ChainHashMap<K,V> parent;
  private HashMap<K,V> table;

  public ChainHashMap() {
    table=new HashMap<K,V>();
  }

  public ChainHashMap<K,V> makeChild() {
    ChainHashMap<K,V> chm=new ChainHashMap<K,V>();
    chm.parent=this;
    return chm;
  }

  public ChainHashMap<K,V> getParent() {
    return parent;
  }

  public V put(K key, V value) {
    return table.put(key, value);
  }

  public V get(Object o) {
    K key=(K) o;
    if (table.containsKey(key))
      return table.get(key);
    else if (parent!=null)
      return parent.get(key);
    else
      return null;
  }

  public boolean containsKey(Object o) {
    K key=(K)o;
    if (table.containsKey(key))
      return true;
    else if (parent!=null)
      return parent.containsKey(key);
    else
      return false;
  }

  public Set<Map.Entry<K,V>> entrySet() {
    throw new Error("ChainHashMap does not support entrySet");
  }
}