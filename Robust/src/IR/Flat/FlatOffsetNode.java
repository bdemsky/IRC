package IR.Flat;
import IR.TypeDescriptor;
import IR.FieldDescriptor;

public class FlatOffsetNode extends FlatNode {
  TempDescriptor dst;
  FieldDescriptor field;
  TypeDescriptor baseclass;

  public FlatOffsetNode(TypeDescriptor classtype, FieldDescriptor field, TempDescriptor dst) {
    this.baseclass=classtype;
    this.field = field;
    this.dst = dst;
  }

  public TypeDescriptor getClassType() {
    return baseclass;
  }

  public FieldDescriptor getField() {
    return field;
  }

  public String toString() {
    return "FlatOffsetNode_"+ dst.toString()+"="+"{ "+ field.getSymbol()+" }";
  }

  public int kind() {
    return FKind.FlatOffsetNode;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {dst};
  }
}
