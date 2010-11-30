package IR.Tree;

import java.util.Vector;

public class SwitchBlockNode extends BlockStatementNode {
  Vector<SwitchLabelNode> switch_conds;
  BlockNode switch_st;

  public SwitchBlockNode(Vector<SwitchLabelNode> switch_conds, BlockNode switch_st) {
    this.switch_conds = switch_conds;
    this.switch_st = switch_st;
  }

  public Vector<SwitchLabelNode> getSwitchConditions() {
    return this.switch_conds;
  }
  
  public BlockNode getSwitchBlockStatement() {
    return this.switch_st;
  }

  public String printNode(int indent) {
    String result = "";
    for(int i = 0; i < this.switch_conds.size(); i++) {
      result += this.switch_conds.elementAt(i).printNode(indent);
    }
    result += this.switch_st.printNode(indent);
    return result;
  }
  
  public int kind() {
    return Kind.SwitchBlockNode;
  }
}