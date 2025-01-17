package Analysis.Pointer;
import java.util.*;

public class MySet<T> extends AbstractSet<T> {
  HashMap<T,T> map;
  boolean locked;
  public MySet(boolean locked) {
    this.locked=locked;
    map=new HashMap<T,T>();
  }

  public MySet() {
    map=new HashMap<T,T>();
  }

  public MySet(T obj) {
    map=new HashMap<T,T>();
    add(obj);
  }

  public MySet(MySet base) {
    map=new HashMap<T,T>();
    if (base!=null)
      addAll(base);
  }

  public int size() {
    return map.size();
  }

  public void clear() {
    map.clear();
  }

  public boolean remove(Object obj) {
    if (locked)
      throw new Error();
    return map.remove(obj)!=null;
  }

  public boolean add(T obj) {
    if (locked)
      throw new Error();
    boolean retval=map.remove(obj)==null;
    map.put(obj, obj);
    return retval;
  }

  public boolean contains(Object obj) {
    return map.containsKey(obj);
  }

  public boolean removeAll(Collection c) {
    if (c!=null)
      return super.removeAll(c);
    else
      return false;
  }

  public boolean addAll(Collection c) {
    if (c!=null)
      return super.addAll(c);
    else
      return false;
  }

  public T get(T obj) {
    return map.get(obj);
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public Iterator<T> iterator() {
    return map.keySet().iterator();
  }

  public Object clone() {
    MySet<T> cl=new MySet<T>();
    cl.map.putAll(this.map);
    return cl;
  }
}