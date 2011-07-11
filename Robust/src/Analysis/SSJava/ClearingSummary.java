package Analysis.SSJava;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;

public class ClearingSummary {

  Hashtable<NTuple<Descriptor>, SharedStatus> summary;

  public ClearingSummary() {
    summary = new Hashtable<NTuple<Descriptor>, SharedStatus>();
  }

  public Iterator<NTuple<Descriptor>> heapPathIterator() {
    return summary.keySet().iterator();
  }

  public SharedStatus get(NTuple<Descriptor> hp) {
    return summary.get(hp);
  }

  public Set<NTuple<Descriptor>> keySet() {
    return summary.keySet();
  }

  public void put(NTuple<Descriptor> key, SharedStatus value) {
    summary.put(key, value);
  }

  public String toString() {
    return summary.toString();
  }

  public int hashCode() {
    return summary.hashCode();
  }

  public Hashtable<NTuple<Descriptor>, SharedStatus> getSummary() {
    return summary;
  }

  public boolean equals(Object o) {

    if (!(o instanceof ClearingSummary)) {
      return false;
    }

    ClearingSummary in = (ClearingSummary) o;

    if (getSummary().equals(in.getSummary())) {
      return true;
    }

    return false;
  }
}
