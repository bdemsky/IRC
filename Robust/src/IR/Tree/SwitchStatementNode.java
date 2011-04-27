package IR.Tree;

public class SwitchStatementNode extends BlockStatementNode {
  ExpressionNode cond;
  BlockNode switch_st;

  public SwitchStatementNode(ExpressionNode cond, BlockNode switch_st) {
    this.cond = cond;
    this.switch_st = switch_st;
  }

  public ExpressionNode getCondition() {
    return cond;
  }

  public BlockNode getSwitchBody() {
    return this.switch_st;
  }

  public String printNode(int indent) {
    return "switch(" + cond.printNode(indent) + ") " + switch_st.printNode(indent);
  }

  public int kind() {
    return Kind.SwitchStatementNode;
  }
}
