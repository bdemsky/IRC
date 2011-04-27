package IR.Tree;

public class ContinueBreakNode extends BlockStatementNode {
  LoopNode ln;
  boolean isbreak;

  public ContinueBreakNode(boolean isbreak) {
    this.isbreak=isbreak;
  }

  public boolean isBreak() {
    return isbreak;
  }

  public void setLoop(LoopNode l) {
    this.ln=l;
  }

  public String printNode(int indent) {
    if( isbreak )
      return "break;";
    else
      return "continue;";
  }

  public int kind() {
    return Kind.ContinueBreakNode;
  }
}
