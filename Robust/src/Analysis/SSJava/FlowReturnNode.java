package Analysis.SSJava;

import java.util.HashSet;
import java.util.Set;

import IR.Descriptor;
import IR.NameDescriptor;
import IR.Tree.MethodInvokeNode;

public class FlowReturnNode extends FlowNode {

  Set<NTuple<Descriptor>> returnTupleSet;
  MethodInvokeNode min;

  public FlowReturnNode(NTuple<Descriptor> t, MethodInvokeNode min) {
    super(t);
    this.returnTupleSet = new HashSet<NTuple<Descriptor>>();
    this.min = min;
  }

  public void setNewTuple(NTuple<Descriptor> in) {
    returnTupleSet.clear();
    returnTupleSet.add(in);
  }

  public void addTupleSet(NodeTupleSet in) {
    returnTupleSet.addAll(in.getSet());
  }

  public void addTupleSet(Set<NTuple<Descriptor>> in) {
    returnTupleSet.addAll(in);
  }

  public void addTuple(NTuple<Descriptor> in) {
    returnTupleSet.add(in);
  }

  public Set<NTuple<Descriptor>> getReturnTupleSet() {
    return returnTupleSet;
  }

  public String toString() {
    String rtr = "[RNODE]:" + descTuple + ":" + min.getMethodName();
    rtr += ":" + returnTupleSet;
    return rtr;
  }

  public String getPrettyID() {
    String id = min.getMethodName() + "<";
    String property = "";
    for (int i = 0; i < descTuple.size(); i++) {
      if (i != 0) {
        id += ",";
      }
      id += descTuple.get(i).getSymbol();
    }
    id += ">";

    id += " " + returnTupleSet;

    return id;
  }
}
