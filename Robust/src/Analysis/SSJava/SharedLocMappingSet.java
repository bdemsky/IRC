package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;

public class SharedLocMappingSet {

  Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>> map;

  public SharedLocMappingSet() {
    map = new Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>>();
  }

  public Set<Descriptor> getWriteSet(NTuple<Descriptor> hp, Location loc) {

    Hashtable<Location, Set<Descriptor>> loc2Set = map.get(hp);

    if (loc2Set == null) {
      loc2Set = new Hashtable<Location, Set<Descriptor>>();
      map.put(hp, loc2Set);
    }

    Set<Descriptor> writeSet = loc2Set.get(loc);
    if (writeSet == null) {
      writeSet = new HashSet<Descriptor>();
      loc2Set.put(loc, writeSet);
    }

    return writeSet;

  }

  public void addWrite(NTuple<Descriptor> hp, Location loc, Descriptor desc) {
    getWriteSet(hp, loc).add(desc);
  }

  public void addWriteSet(NTuple<Descriptor> hp, Location loc, Set<Descriptor> descSet) {
    getWriteSet(hp, loc).addAll(descSet);
  }

  public void removeWriteSet(NTuple<Descriptor> hp, Location loc, Set<Descriptor> descSet) {
    getWriteSet(hp, loc).removeAll(descSet);
  }

  public Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>> getMap() {
    return map;
  }

  public boolean equals(Object obj) {

    if (!(obj instanceof SharedLocMappingSet)) {
      return false;
    }

    SharedLocMappingSet in = (SharedLocMappingSet) obj;
    return getMap().equals(in.getMap());

  }

  public Set<NTuple<Descriptor>> getHeapPathKeySet() {
    return map.keySet();
  }

  public Set<Location> getLocationKeySet(NTuple<Descriptor> hp) {
    Hashtable<Location, Set<Descriptor>> loc2Set = map.get(hp);
    return loc2Set.keySet();
  }

  public void intersectWriteSet(NTuple<Descriptor> hp, Location loc, Set<Descriptor> inSet) {

    boolean isFirst = false;
    Hashtable<Location, Set<Descriptor>> loc2Set = map.get(hp);
    if (loc2Set != null) {
      Set<Descriptor> set = loc2Set.get(loc);
      if (set == null) {
        isFirst = true;
      }
    } else {
      isFirst = true;
    }

    Set<Descriptor> writeSet = getWriteSet(hp, loc);
    if (isFirst) {
      writeSet.addAll(inSet);
    } else {
      writeSet.retainAll(inSet);
    }

  }

  public String toString() {
    return map.toString();
  }

  public void clear() {
    map.clear();
  }

  public void remove(NTuple<Descriptor> hp, Location loc) {
    Hashtable<Location, Set<Descriptor>> loc2Set = map.get(hp);
    if (loc2Set != null) {
      loc2Set.remove(loc);
      if (loc2Set.isEmpty()) {
        map.remove(hp);
      }
    }
  }

  public void kill(SharedLocMappingSet kill) {
    Set<NTuple<Descriptor>> hpKeySet = kill.getHeapPathKeySet();
    for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      Hashtable<Location, Set<Descriptor>> loc2Set = kill.getMap().get(hpKey);
      Set<Location> locKeySet = loc2Set.keySet();
      for (Iterator iterator2 = locKeySet.iterator(); iterator2.hasNext();) {
        Location locKey = (Location) iterator2.next();
        remove(hpKey, locKey);
      }
    }
  }

  public void add(SharedLocMappingSet gen) {
    Set<NTuple<Descriptor>> hpKeySet = gen.getMap().keySet();
    for (Iterator iterator = hpKeySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> hpKey = (NTuple<Descriptor>) iterator.next();
      Hashtable<Location, Set<Descriptor>> loc2Set = gen.getMap().get(hpKey);
      Set<Location> locKeySet = loc2Set.keySet();
      for (Iterator iterator2 = locKeySet.iterator(); iterator2.hasNext();) {
        Location locKey = (Location) iterator2.next();
        addWriteSet(hpKey, locKey, gen.getWriteSet(hpKey, locKey));
      }
    }
  }

}
