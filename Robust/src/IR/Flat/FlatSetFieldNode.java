package IR.Flat;
import IR.FieldDescriptor;

public class FlatSetFieldNode extends FlatNode {
  TempDescriptor src;
  TempDescriptor dst;
  FieldDescriptor field;

  public FlatSetFieldNode(TempDescriptor dst, FieldDescriptor field, TempDescriptor src) {
    this.field=field;
    this.src=src;
    this.dst=dst;
  }

  public FlatNode clone(TempMap t) {
    return new FlatSetFieldNode(t.tempMap(dst), field, t.tempMap(src));
  }
  public void rewriteUse(TempMap t) {
    dst=t.tempMap(dst);
    src=t.tempMap(src);
  }
  public void rewriteDef(TempMap t) {
  }
  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public FieldDescriptor getField() {
    return field;
  }

  public String toString() {
    return "FlatSetFieldNode_"+dst.toString()+"."+field.getSymbol()+"="+src.toString();
  }

  public int kind() {
    return FKind.FlatSetFieldNode;
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor [] {src,dst};
  }
}
