package IR.Tree;

public class BlockExpressionNode extends BlockStatementNode {
  ExpressionNode en;
  public BlockExpressionNode(ExpressionNode e) {
    this.en=e;
  }

  public String printNode(int indent) {
    return en.printNode(indent);
  }

  public ExpressionNode getExpression() {
    return en;
  }

  public int kind() {
    return Kind.BlockExpressionNode;
  }
}
