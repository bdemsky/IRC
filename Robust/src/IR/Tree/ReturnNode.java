package IR.Tree;

public class ReturnNode extends BlockStatementNode {
  ExpressionNode en;

  public ReturnNode() {
    en=null;
  }

  public ReturnNode(ExpressionNode en) {
    this.en=en;
  }

  public ExpressionNode getReturnExpression() {
    return en;
  }

  public String printNode(int indent) {
    if (en==null)
      return "return";
    else
      return "return "+en.printNode(indent);
  }
  public int kind() {
    return Kind.ReturnNode;
  }
}
