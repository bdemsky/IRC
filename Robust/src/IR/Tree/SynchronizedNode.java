package IR.Tree;

public class SynchronizedNode extends BlockStatementNode {
  BlockNode bn;
  ExpressionNode en;
  public SynchronizedNode(ExpressionNode en, BlockNode bn) {
    this.en=en;
    this.bn=bn;
  }

  public BlockNode getBlockNode() {
    return bn;
  }

  public ExpressionNode getExpr() {
    return en;
  }

  public String printNode(int indent) {
    return "synchronized("+en.printSpace(indent)+") {"+bn.printSpace(indent)+"}";
  }

  public int kind() {
    return Kind.SynchronizedNode;
  }
}
