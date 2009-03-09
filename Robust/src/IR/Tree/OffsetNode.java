package IR.Tree;
import IR.FieldDescriptor;
import IR.TypeDescriptor;
import IR.ClassDescriptor;

public class OffsetNode extends ExpressionNode {
  TypeDescriptor td;
  FieldDescriptor fd;
  String fieldname;

  public OffsetNode(TypeDescriptor td, String fieldname) {
    this.td = td;
    this.fieldname = fieldname;
    this.fd = null;
  }

  public void setField(FieldDescriptor fd) {
    this.fd = fd;
  }

  public String getFieldName() {
    return fieldname;
  }

  public FieldDescriptor getField() {
    return fd;
  }

  public ClassDescriptor getClassDesc() {
    return td.getClassDesc();
  }

  public TypeDescriptor getType() {
    return new TypeDescriptor(TypeDescriptor.SHORT);
  }

  public String printNode(int indent) {
    return "getoffset {"+ td.toString() + " , " + fieldname + " } ";
  }

  public int kind() {
    return Kind.OffsetNode;
  }
}
