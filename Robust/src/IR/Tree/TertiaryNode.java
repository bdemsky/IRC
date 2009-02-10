package IR.Tree;
import IR.TypeDescriptor;

public class TertiaryNode extends ExpressionNode {
  ExpressionNode cond;
  ExpressionNode trueExpr;
  ExpressionNode falseExpr;

  public TertiaryNode( ExpressionNode cond,
		       ExpressionNode trueExpr,
		       ExpressionNode falseExpr ) {
    this.cond = cond;
    this.trueExpr = trueExpr;
    this.falseExpr = falseExpr;
  }

  public ExpressionNode getCond() {
    return cond;
  }

  public ExpressionNode getTrueExpr() {
    return trueExpr;
  }

  public ExpressionNode getFalseExpr() {
    return falseExpr;
  }
  
  public String printNode(int indent) {
    return cond.printNode(indent)+" ? "+trueExpr.printNode(indent)+" : "+falseExpr.printNode(indent);
  }

  public TypeDescriptor getType() {
    return trueExpr.getType();
  }

  public int kind() {
    return Kind.TertiaryNode;
  }
}