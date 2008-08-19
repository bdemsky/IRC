package IR.Tree;
import IR.AssignOperation;
import IR.TypeDescriptor;

public class AssignmentNode extends ExpressionNode {
  ExpressionNode left;
  ExpressionNode right;
  AssignOperation op;

  public AssignmentNode(ExpressionNode l, ExpressionNode r, AssignOperation op) {
    left=l;
    right=r;
    this.op=op;
  }

  public ExpressionNode getDest() {
    return left;
  }

  public ExpressionNode getSrc() {
    return right;
  }

  public AssignOperation getOperation() {
    return op;
  }

  public String printNode(int indent) {
    return left.printNode(indent)+" "+op.toString()+" "+right.printNode(indent);
  }

  public TypeDescriptor getType() {
    return left.getType();
  }

  public int kind() {
    return Kind.AssignmentNode;
  }
}
