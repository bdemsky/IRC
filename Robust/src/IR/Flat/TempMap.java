package IR.Flat;
import java.util.Hashtable;

public class TempMap {
  Hashtable<TempDescriptor, TempDescriptor> map;
  public TempMap() {
    map=new Hashtable<TempDescriptor, TempDescriptor>();
  }

  public boolean maps(TempDescriptor t) {
    return map.containsKey(t);
  }
  public TempDescriptor tempMap(TempDescriptor t) {
    if (t==null)
      return null;
    else if (map.containsKey(t))
      return map.get(t);
    else
      return t;
  }
  public void addPair(TempDescriptor t1, TempDescriptor t2) {
    map.put(t1,t2);
  }
}