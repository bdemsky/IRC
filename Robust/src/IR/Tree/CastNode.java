package IR.Tree;
import IR.TypeDescriptor;

public class CastNode extends ExpressionNode  {
  TypeDescriptor td;
  ExpressionNode etd;
  ExpressionNode exp;

  public CastNode(TypeDescriptor type, ExpressionNode exp) {
    this.td=type;
    this.exp=exp;
    this.etd=null;
  }

  public CastNode(ExpressionNode type, ExpressionNode exp) {
    this.td=null;
    this.exp=exp;
    this.etd=type;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public ExpressionNode getExpression() {
    return exp;
  }

  public void setType(TypeDescriptor td) {
    this.td=td;
  }

  public NameNode getTypeName() {
    return (NameNode) etd;
  }

  public String printNode(int indentlevel) {
    if (etd==null)
      return "("+td.toString()+")"+exp.printNode(indentlevel);
    else
      return "("+etd.printNode(indentlevel)+")"+exp.printNode(indentlevel);
  }

  public int kind() {
    return Kind.CastNode;
  }
}
