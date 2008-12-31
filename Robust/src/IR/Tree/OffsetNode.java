package IR.Tree;
import IR.FieldDescriptor;
import IR.TypeDescriptor;
import IR.ClassDescriptor;

public class OffsetNode extends ExpressionNode {
  TypeDescriptor td;
  TypeDescriptor type;
  ClassDescriptor cd;
  FieldDescriptor fd;
  String fieldname;

  public OffsetNode(TypeDescriptor td, String fieldname) {
    this.td = td;
    this.fieldname = fieldname;
    this.fd = null;
  }

  public OffsetNode(ClassDescriptor cd, String fieldname) {
    this.cd = cd;
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

  public void setType(TypeDescriptor argtype) {
    this.type=argtype;
  }

  public void setClassDesc(ClassDescriptor cd) {
    this.cd = cd;
  }

  public ClassDescriptor getClassDesc() {
    return cd;
  }

  public TypeDescriptor getType() {
    return type;
  }

  public String printNode(int indent) {
    return "getoffset {"+ td.toString() + " , " + fieldname + " } ";
  }

  public int kind() {
    return Kind.OffsetNode;
  }
}
