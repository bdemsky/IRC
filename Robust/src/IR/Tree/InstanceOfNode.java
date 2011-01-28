package IR.Tree;
import IR.TypeDescriptor;
import IR.TypeUtil;

public class InstanceOfNode extends ExpressionNode {
  ExpressionNode e;
  TypeDescriptor t;

  public InstanceOfNode(ExpressionNode e, TypeDescriptor t) {
    this.e=e;
    this.t=t;
  }

  public String printNode(int indent) {
    return (e.printNode(indent)+" instanceof "+t.toString());
  }

  public ExpressionNode getExpr() {
    return e;
  }

  public TypeDescriptor getExprType() {
    return t;
  }

  public TypeDescriptor getType() {
    return new TypeDescriptor(TypeDescriptor.BOOLEAN);
  }

  public int kind() {
    return Kind.InstanceOfNode;
  }
  
  public Long evaluate() {
    eval = null;
    return eval; //null;
  }
}
