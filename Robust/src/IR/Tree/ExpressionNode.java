package IR.Tree;
import IR.TypeDescriptor;

public class ExpressionNode extends TreeNode {
  Long eval = null;

  public TypeDescriptor getType() {
    throw new Error();
  }

  public String printNode(int indentlevel) {
    return null;
  }

  public Long evaluate() {
    throw new Error();
  }

  public Long getEval() {
    return this.eval;
  }
}
