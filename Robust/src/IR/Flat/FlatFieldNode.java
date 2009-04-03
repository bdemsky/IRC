package IR.Flat;
import IR.FieldDescriptor;

public class FlatFieldNode extends FlatNode {
  TempDescriptor src;
  TempDescriptor dst;
  FieldDescriptor field;

  public FlatFieldNode(FieldDescriptor field, TempDescriptor src, TempDescriptor dst) {
    this.field=field;
    this.src=src;
    this.dst=dst;
  }

  public FlatNode clone(TempMap t) {
    return new FlatFieldNode(field, t.tempMap(src), t.tempMap(dst));
  }
  public void rewriteUse(TempMap t) {
    src=t.tempMap(src);
  }
  public void rewriteDef(TempMap t) {
    dst=t.tempMap(dst);
  }

  public FieldDescriptor getField() {
    return field;
  }

  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public String toString() {
    return "FlatFieldNode_"+dst.toString()+"="+src.toString()+"."+field.getSymbol();
  }

  public int kind() {
    return FKind.FlatFieldNode;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {dst};
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor[] {src};
  }
}
