package IR.Tree;
import IR.Operation;
import IR.TypeDescriptor;

public class OpNode extends ExpressionNode {
  ExpressionNode left;
  ExpressionNode right;
  Operation op;
  TypeDescriptor td;
  TypeDescriptor lefttype;
  TypeDescriptor righttype;

  public OpNode(ExpressionNode l, ExpressionNode r, Operation o) {
    left=l;
    right=r;
    op=o;
  }

  public OpNode(ExpressionNode l, Operation o) {
    left=l;
    right=null;
    op=o;
  }

  public ExpressionNode getLeft() {
    return left;
  }

  public ExpressionNode getRight() {
    return right;
  }

  public Operation getOp() {
    return op;
  }

  public String printNode(int indent) {
    if (right==null)
      return op.toString()+"("+left.printNode(indent)+")";
    else
      return left.printNode(indent)+" "+op.toString()+" "+right.printNode(indent);
  }

  public void setLeftType(TypeDescriptor argtype) {
    this.lefttype=argtype;
  }

  public TypeDescriptor getLeftType() {
    return lefttype;
  }

  public void setRightType(TypeDescriptor argtype) {
    this.righttype=argtype;
  }

  public TypeDescriptor getRightType() {
    return righttype;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public void setType(TypeDescriptor td) {
    this.td=td;
  }

  public int kind() {
    return Kind.OpNode;
  }
}
