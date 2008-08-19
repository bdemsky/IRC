package IR.Tree;
import IR.VarDescriptor;

public class DeclarationNode extends BlockStatementNode {
  VarDescriptor vd;
  ExpressionNode init_en;
  public DeclarationNode(VarDescriptor var, ExpressionNode en) {
    vd=var;
    init_en=en;
  }

  public String printNode(int indent) {
    if (init_en==null)
      return vd.toString();
    else return vd.toString()+"="+init_en.printNode(0);
  }

  public ExpressionNode getExpression() {
    return init_en;
  }

  public VarDescriptor getVarDescriptor() {
    return vd;
  }

  public int kind() {
    return Kind.DeclarationNode;
  }
}
