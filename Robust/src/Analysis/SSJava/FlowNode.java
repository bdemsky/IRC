package Analysis.SSJava;

import java.util.Set;

import IR.Descriptor;

public class FlowNode {

  // descriptor tuple is a unique identifier of the flow node
  private NTuple<Descriptor> descTuple;

  // if the infer node represents the base type of field access,
  // this set contains fields of the base type
  private Set<FlowNode> fieldNodeSet;

  public FlowNode(Descriptor desc) {
    this(null, desc);
  }

  public FlowNode(NTuple<Descriptor> base) {
    this(base, null);
  }

  public FlowNode(NTuple<Descriptor> base, Descriptor desc) {
    descTuple = new NTuple<Descriptor>();
    if (base != null) {
      descTuple.addAll(base);
    }
    if (desc != null) {
      descTuple.add(desc);
    }
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

}
