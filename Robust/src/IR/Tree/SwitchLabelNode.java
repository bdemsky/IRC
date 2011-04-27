package IR.Tree;

public class SwitchLabelNode extends BlockStatementNode {
  ExpressionNode cond;
  boolean isdefault;

  public SwitchLabelNode(ExpressionNode cond, boolean isdefault) {
    this.cond = cond;
    this.isdefault = isdefault;
  }

  public ExpressionNode getCondition() {
    return cond;
  }

  public boolean isDefault() {
    return this.isdefault;
  }

  public String printNode(int indent) {
    if(this.isdefault) {
      return "case default: ";
    }
    return "case " + cond.printNode(indent) + ": ";
  }

  public int kind() {
    return Kind.SwitchLabelNode;
  }
}