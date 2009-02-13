package IR.Flat;
import IR.TypeDescriptor;

public class FlatInstanceOfNode extends FlatNode {
  TempDescriptor src;
  TempDescriptor dst;
  TypeDescriptor t;

  public FlatInstanceOfNode(TypeDescriptor t, TempDescriptor src, TempDescriptor dst) {
    this.t=t;
    this.src=src;
    this.dst=dst;
  }

  public TypeDescriptor getType() {
    return t;
  }

  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public String toString() {
    return "FlatInstanceNode_"+dst.toString()+"="+src.toString()+".instanceof "+t.getSymbol();
  }

  public int kind() {
    return FKind.FlatInstanceOfNode;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {dst};
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor[] {src};
  }
}
