package Analysis.SSJava;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;

public class NodeTupleSet {

  private Set<NTuple<Descriptor>> set;

  public NodeTupleSet() {
    set = new HashSet<NTuple<Descriptor>>();
  }

  public void addTuple(NTuple<Descriptor> tuple) {

    // need to add additional elements because we need to create edges even from
    // the base
    // for example, if we have input <a,b,c>, we need to add additional element
    // <a,b> and <a> to the set

    // NTuple<Descriptor> cur = new NTuple<Descriptor>();
    // for (int i = 0; i < tuple.size(); i++) {
    // Descriptor d = tuple.get(i);
    // cur.add(d);
    // set.add(new NTuple<Descriptor>(cur));
    // }

    set.add(tuple);
  }

  public Iterator<NTuple<Descriptor>> iterator() {
    return set.iterator();
  }

  public String toString() {
    return set.toString();
  }

  public Set<NTuple<Descriptor>> getSet() {
    return set;
  }

  public void addTupleSet(NodeTupleSet in) {
    set.addAll(in.getSet());
  }
}
