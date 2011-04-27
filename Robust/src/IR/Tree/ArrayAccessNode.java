package IR.Tree;
import IR.FieldDescriptor;
import IR.TypeDescriptor;

public class ArrayAccessNode extends ExpressionNode {
  ExpressionNode left;
  ExpressionNode index;
  TypeDescriptor wrappertype;

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

  public boolean iswrapper() {
    return wrappertype!=null;
  }

  public TypeDescriptor getType() {
    if (wrappertype!=null)
      return wrappertype;
    else
      return left.getType().dereference();
  }

  public Long evaluate() {
    eval = null;
    return eval; //null;
  }
}
