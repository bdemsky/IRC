package IR.Flat;
import IR.TypeDescriptor;

public class FlatCastNode extends FlatNode {
  TempDescriptor src;
  TempDescriptor dst;
  TypeDescriptor type;

  public FlatCastNode(TypeDescriptor type, TempDescriptor src, TempDescriptor dst) {
    this.type=type;
    this.src=src;
    this.dst=dst;
  }

  public FlatNode clone(TempMap t) {
    return new FlatCastNode(type, t.tempMap(src), t.tempMap(dst));
  }
  public void rewriteUse(TempMap t) {
    src=t.tempMap(src);
  }
  public void rewriteDef(TempMap t) {
    dst=t.tempMap(dst);
  }

  public String toString() {
    return "FlatCastNode_"+dst.toString()+"=("+type.toString()+")"+src.toString();
  }

  public int kind() {
    return FKind.FlatCastNode;
  }

  public TypeDescriptor getType() {
    return type;
  }

  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {dst};
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor[] {src};
  }

}
