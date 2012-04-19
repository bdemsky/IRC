package Analysis.SSJava;

import IR.Descriptor;

public class FlowEdge {

  private FlowNode src;
  private FlowNode dst;

  // indicates that which tuple in the graph initiates this edge
  private NTuple<Descriptor> initTuple;

  // indicates that which tuple in the graph is the end of this edge
  private NTuple<Descriptor> endTuple;

  public FlowEdge(FlowNode src, FlowNode dst, NTuple<Descriptor> initTuple,
      NTuple<Descriptor> endTuple) {
    this.src = src;
    this.dst = dst;
    this.initTuple = initTuple;
    this.endTuple = endTuple;
  }

  public String toString() {
    return "Edge(" + initTuple + "/" + endTuple + "):: " + src + " to " + dst;
  }

  public FlowNode getSrc() {
    return src;
  }

  public void setSrc(FlowNode src) {
    this.src = src;
  }

  public FlowNode getDst() {
    return dst;
  }

  public void setDst(FlowNode dst) {
    this.dst = dst;
  }

  public NTuple<Descriptor> getInitTuple() {
    return initTuple;
  }

  public void setInitTuple(NTuple<Descriptor> initTuple) {
    this.initTuple = initTuple;
  }

  public int hashCode() {
    return src.hashCode() + dst.hashCode() + initTuple.hashCode() + endTuple.hashCode();
  }

  public NTuple<Descriptor> getEndTuple() {
    return endTuple;
  }

  public boolean equals(Object obj) {

    if (obj instanceof FlowEdge) {
      FlowEdge in = (FlowEdge) obj;
      if (src.equals(in.getSrc()) && dst.equals(in.getDst()) && initTuple.equals(in.getInitTuple())
          && endTuple.equals(in.getEndTuple())) {
        return true;
      }
    }

    return false;
  }

}
