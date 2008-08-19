package IR.Tree;

public class IfStatementNode extends BlockStatementNode {
  ExpressionNode cond;
  BlockNode true_st;
  BlockNode else_st;

  public IfStatementNode(ExpressionNode cond, BlockNode true_st, BlockNode else_st) {
    this.cond=cond;
    this.true_st=true_st;
    this.else_st=else_st;
  }

  public ExpressionNode getCondition() {
    return cond;
  }

  public BlockNode getTrueBlock() {
    return true_st;
  }

  public BlockNode getFalseBlock() {
    return else_st;
  }

  public String printNode(int indent) {
    if (else_st==null)
      return "if("+cond.printNode(indent)+") "+true_st.printNode(indent);
    else
      return "if("+cond.printNode(indent)+") "+true_st.printNode(indent)+" else "+   else_st.printNode(indent);
  }
  public int kind() {
    return Kind.IfStatementNode;
  }
}
