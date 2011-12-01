package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;

public class SharedLocMap {

  Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>> map;

  public SharedLocMap() {
    map = new Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>>();
  }

  public Hashtable<NTuple<Location>, Set<NTuple<Descriptor>>> getMap() {
    return map;
  }

  public boolean equals(Object obj) {

    if (obj instanceof SharedLocMap) {
      return map.equals(((SharedLocMap) obj).getMap());
    } else {
      return false;
    }

  }

  public int hashCode() {
    return map.hashCode();
  }

  public Set<NTuple<Descriptor>> get(NTuple<Location> locTuple) {
    return map.get(locTuple);
  }

  public void addWrite(NTuple<Location> locTuple, Set<NTuple<Descriptor>> hpSet) {

    Set<NTuple<Descriptor>> writeSet = map.get(locTuple);
    if (writeSet == null) {
      writeSet = new HashSet<NTuple<Descriptor>>();
      map.put(locTuple, writeSet);
    }
    writeSet.addAll(hpSet);
  }

  public void addWrite(NTuple<Location> locTuple, NTuple<Descriptor> hp) {

    Set<NTuple<Descriptor>> writeSet = map.get(locTuple);
    if (writeSet == null) {
      writeSet = new HashSet<NTuple<Descriptor>>();
      map.put(locTuple, writeSet);
    }
    writeSet.add(hp);
  }

  public void removeWrite(NTuple<Location> locTuple, NTuple<Descriptor> hp) {
    Set<NTuple<Descriptor>> writeSet = map.get(locTuple);
    if (writeSet != null) {
      writeSet.remove(hp);
    }
  }

  public String toString() {
    return map.toString();
  }

  public Set<NTuple<Location>> keySet() {
    return map.keySet();
  }

  public void kill(SharedLocMap kill) {
    Set<NTuple<Location>> killKeySet = kill.keySet();
    for (Iterator iterator = killKeySet.iterator(); iterator.hasNext();) {
      NTuple<Location> killKey = (NTuple<Location>) iterator.next();
      map.remove(killKey);
    }
  }

  public void gen(SharedLocMap gen) {
    Set<NTuple<Location>> genKeySet = gen.keySet();
    for (Iterator iterator = genKeySet.iterator(); iterator.hasNext();) {
      NTuple<Location> genKey = (NTuple<Location>) iterator.next();
      map.put(genKey, gen.get(genKey));
    }
  }

}
