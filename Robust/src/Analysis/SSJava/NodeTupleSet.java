package Analysis.SSJava;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import IR.Descriptor;

public class NodeTupleSet {

  private List<NTuple<Descriptor>> list;

  public NodeTupleSet() {
    list = new ArrayList<NTuple<Descriptor>>();
  }

  public void addTuple(NTuple<Descriptor> tuple) {

    for (Iterator iterator = list.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> t = (NTuple<Descriptor>) iterator.next();
      if (t.equals(tuple)) {
        return;
      }
    }

    list.add(tuple);
  }

  public void removeTuple(NTuple<Descriptor> tuple) {
    list.remove(tuple);
  }

  public Iterator<NTuple<Descriptor>> iterator() {
    return list.iterator();
  }

  public String toString() {
    return list.toString();
  }

  public Set<NTuple<Descriptor>> getSet() {
    Set<NTuple<Descriptor>> set = new HashSet<NTuple<Descriptor>>();
    set.addAll(list);
    return set;
  }

  public void addTupleSet(NodeTupleSet in) {
    if (in != null) {
      for (Iterator iterator = in.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> inTuple = (NTuple<Descriptor>) iterator.next();
        addTuple(inTuple);
      }
    }
  }

  public int size() {
    return list.size();
  }
}
