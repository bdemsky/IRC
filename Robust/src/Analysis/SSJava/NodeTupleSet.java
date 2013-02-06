package Analysis.SSJava;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import IR.Descriptor;

public class NodeTupleSet {

  private ArrayList<NTuple<Descriptor>> list;

  private ArrayList<NTuple<Location>> globalLocTupleList;

  private NTuple<Descriptor> baseDescTuple;

  public NodeTupleSet() {
    list = new ArrayList<NTuple<Descriptor>>();
    globalLocTupleList = new ArrayList<NTuple<Location>>();
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

  public void setMethodInvokeBaseDescTuple(NTuple<Descriptor> in) {
    baseDescTuple = in;
  }

  public NTuple<Descriptor> getBaseDesc() {
    return baseDescTuple;
  }

  public void addGlobalFlowTuple(NTuple<Location> tuple) {
    globalLocTupleList.add(tuple);
  }

  public void addGlobalFlowTupleSet(ArrayList<NTuple<Location>> in) {
    globalLocTupleList.addAll(in);
  }

  public Iterator<NTuple<Location>> globalIterator() {
    return globalLocTupleList.iterator();
  }

  public void removeTuple(NTuple<Descriptor> tuple) {
    list.remove(tuple);
  }

  public Iterator<NTuple<Descriptor>> iterator() {
    return list.iterator();
  }

  public String toString() {
    String str = list.toString();

    if (globalLocTupleList.size() > 0) {
      str += " GlobalFlow=" + globalLocTupleList.toString();
    }

    return str;
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

  public void clear() {
    list.clear();
  }

  public int globalLocTupleSize() {
    return globalLocTupleList.size();
  }

  private void setGlobalLocTupleList(ArrayList<NTuple<Location>> in) {
    globalLocTupleList = in;
  }

  public ArrayList<NTuple<Location>> getGlobalLocTupleSet() {
    return globalLocTupleList;
  }

  private void setDescTupleList(ArrayList<NTuple<Descriptor>> in) {
    list = in;
  }

  public NodeTupleSet clone() {
    NodeTupleSet set = new NodeTupleSet();
    set.setDescTupleList((ArrayList<NTuple<Descriptor>>) list.clone());
    set.setGlobalLocTupleList((ArrayList<NTuple<Location>>) globalLocTupleList.clone());
    return set;
  }
}
