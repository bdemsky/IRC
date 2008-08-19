package IR.Tree;
import IR.FieldDescriptor;
import IR.TypeDescriptor;

public class ArrayAccessNode extends ExpressionNode {
  ExpressionNode left;
  ExpressionNode index;

  public ArrayAccessNode(ExpressionNode l, ExpressionNode index) {
    this.index=index;
    left=l;
  }

  public ExpressionNode getIndex() {
    return index;
  }

  public ExpressionNode getExpression() {
    return left;
  }

  public String printNode(int indent) {
    return left.printNode(indent)+"["+index.printNode(0)+"]";
  }

  public int kind() {
    return Kind.ArrayAccessNode;
  }

  public TypeDescriptor getType() {
    return left.getType().dereference();
  }
}
