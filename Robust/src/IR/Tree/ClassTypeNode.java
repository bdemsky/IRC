package IR.Tree;

import IR.TypeDescriptor;

public class ClassTypeNode extends ExpressionNode {
  TypeDescriptor td;

  public ClassTypeNode(TypeDescriptor td) {
    this.td=td;
  }

  public TypeDescriptor getTypeDesc() {
    return this.td;
  }

  public void setTypeDesc(TypeDescriptor td) {
    this.td = td;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public String printNode(int indent) {
    return td.toString();
  }

  public int kind() {
    return Kind.ClassTypeNode;
  }

  public Long evaluate() {
    eval = null;
    return eval; //null;
  }
}
