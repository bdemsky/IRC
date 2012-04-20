package Analysis.SSJava;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;

public class FlowNode {

  // descriptor tuple is a unique identifier of the flow node
  private NTuple<Descriptor> descTuple;

  // if the infer node represents the base type of field access,
  // this set contains fields of the base type
  private Set<FlowNode> fieldNodeSet;

  public Set<FlowNode> getFieldNodeSet() {
    return fieldNodeSet;
  }

  private Set<FlowEdge> outEdgeSet;

  public FlowNode(NTuple<Descriptor> tuple) {

    NTuple<Descriptor> base = null;
    Descriptor desc = null;
    if (tuple.size() > 1) {
      base = tuple.subList(0, tuple.size() - 1);
      desc = tuple.get(tuple.size() - 1);
    } else {
      base = tuple;
    }
    fieldNodeSet = new HashSet<FlowNode>();
    descTuple = new NTuple<Descriptor>();
    if (base != null) {
      descTuple.addAll(base);
    }
    if (desc != null) {
      descTuple.add(desc);
    }
    outEdgeSet = new HashSet<FlowEdge>();
  }

  public void addFieldNode(FlowNode node) {
    fieldNodeSet.add(node);
  }

  public NTuple<Descriptor> getDescTuple() {
    return descTuple;
  }

  public Descriptor getOwnDescriptor() {
    return descTuple.get(descTuple.size() - 1);
  }

  public String toString() {
    return "[FlowNode]::" + descTuple;
  }

  public Iterator<FlowEdge> iteratorOfOutEdges() {
    return outEdgeSet.iterator();
  }

  public void addOutEdge(FlowEdge out) {
    outEdgeSet.add(out);
  }

  public Set<FlowEdge> getOutEdgeSet() {
    return outEdgeSet;
  }

  public int hashCode() {
    return 7 + descTuple.hashCode();
  }

  public boolean equals(Object obj) {

    if (obj instanceof FlowNode) {
      FlowNode in = (FlowNode) obj;
      if (descTuple.equals(in.getDescTuple())) {
        return true;
      }
    }

    return false;

  }

  public String getID() {
    String id = "";
    for (int i = 0; i < descTuple.size(); i++) {
      id += descTuple.get(i).getSymbol();
    }
    return id;
  }

  public String getPrettyID() {
    String id = "<";
    for (int i = 0; i < descTuple.size(); i++) {
      if (i != 0) {
        id += ",";
      }
      id += descTuple.get(i).getSymbol();
    }
    id += ">";
    return id;
  }
}
