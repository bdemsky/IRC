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
  
  public Long evaluate() {
    eval = null;
    Long c = this.cond.evaluate();
    if(c != null) {
      Long t = this.trueExpr.evaluate();
      if(t != null) {
        Long f = this.falseExpr.evaluate();
        if(f != null) {
          if(c.intValue() > 0) {
            eval = t;
          } else {
            eval = f;
          }
        }
      }
    }
    return eval;
  }
}