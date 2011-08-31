package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;

public class ReadSummary {

  Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>> summary;

  public ReadSummary() {
    summary = new Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>>();
  }

  public Hashtable<NTuple<Descriptor>, Hashtable<Location, Set<Descriptor>>> getSummary() {
    return summary;
  }

  public Set<NTuple<Descriptor>> keySet() {
    return summary.keySet();
  }

  public Hashtable<Location, Set<Descriptor>> get(NTuple<Descriptor> hp) {
    return summary.get(hp);
  }

  private Set<Descriptor> getReadSet(NTuple<Descriptor> key, Location loc) {
    Hashtable<Location, Set<Descriptor>> map = summary.get(key);
    if (map == null) {
      map = new Hashtable<Location, Set<Descriptor>>();
      summary.put(key, map);
    }
    Set<Descriptor> descSet = map.get(loc);
    if (descSet == null) {
      descSet = new HashSet<Descriptor>();
      map.put(loc, descSet);
    }
    return descSet;
  }

  public void addRead(NTuple<Descriptor> key, Location loc, Descriptor in) {
    if (loc != null) {
      // if location is null, we do not need to care about it!
      Set<Descriptor> readSet = getReadSet(key, loc);
      readSet.add(in);
    }
  }

  public void addReadSet(NTuple<Descriptor> key, Location loc, Set<Descriptor> inSet) {
    Set<Descriptor> readSet = getReadSet(key, loc);
    readSet.addAll(inSet);
  }

  public int hashCode() {
    return summary.hashCode();
  }

  public boolean equals(Object o) {

    if (!(o instanceof ReadSummary)) {
      return false;
    }

    ReadSummary in = (ReadSummary) o;

    if (getSummary().equals(in.getSummary())) {
      return true;
    }

    return false;
  }

  public void put(NTuple<Descriptor> boundHeapPath, Hashtable<Location, Set<Descriptor>> inTable) {

    Set<Location> keySet = inTable.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Location locKey = (Location) iterator.next();
      Set<Descriptor> readSet = inTable.get(locKey);
      addReadSet(boundHeapPath, locKey, readSet);
    }

  }

  public void merge(ReadSummary in) {

    Set<NTuple<Descriptor>> keySet = in.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> heapPathKey = (NTuple<Descriptor>) iterator.next();
      put(heapPathKey, in.get(heapPathKey));
    }
  }

  public String toString() {
    return summary.toString();
  }
}
