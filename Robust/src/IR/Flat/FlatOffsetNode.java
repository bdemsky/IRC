package IR.Flat;
import IR.ClassDescriptor;
import IR.FieldDescriptor;

public class FlatOffsetNode extends FlatNode {
  TempDescriptor dest;
  ClassDescriptor cd;
  FieldDescriptor field;

  public FlatOffsetNode(FieldDescriptor field, ClassDescriptor cd, TempDescriptor dest) {
    this.cd = cd;
    this.field = field;
    this.dest = dest;
  }

  public FieldDescriptor getField() {
    return field;
  }

  public ClassDescriptor getClassDesc() {
    return cd;
  }

  public String toString() {
    return "FlatOffsetNode_"+ dest.toString()+"="+"{ "+ cd.toString()+", "+field.getSymbol()+" }";
  }

  public int kind() {
    return FKind.FlatOffsetNode;
  }

  public TempDescriptor getDst() {
    return dest;
  }
}
