package IR.Tree;
import java.util.Vector;
import IR.NameDescriptor;
import IR.MethodDescriptor;
import IR.TypeDescriptor;

public class MethodInvokeNode extends ExpressionNode {
  Vector argumentlist;
  String methodid;
  NameDescriptor basename;
  ExpressionNode en;
  MethodDescriptor md;
  boolean isSuper;

  public MethodInvokeNode(NameDescriptor name) {
    methodid=name.getIdentifier();
    if (name.getBase()!=null) {
      basename=name.getBase();
    }
    argumentlist=new Vector();
    en=null;
    md=null;
    isSuper=false;
  }

  public MethodInvokeNode(String methodid, ExpressionNode exp) {
    this.methodid=methodid;
    this.en=exp;
    argumentlist=new Vector();
    md=null;
    this.basename=null;
    isSuper=false;
  }

  public void setSuper() {
    isSuper=true;
  }

  public boolean getSuper() {
    return isSuper;
  }

  public NameDescriptor getBaseName() {
    return basename;
  }

  public String getMethodName() {
    return methodid;
  }

  public ExpressionNode getExpression() {
    return en;
  }

  public TypeDescriptor getType() {
    return md.getReturnType();
  }

  public void setExpression(ExpressionNode en) {
    this.en=en;
  }

  public void setMethod(MethodDescriptor md) {
    this.md=md;
  }

  public MethodDescriptor getMethod() {
    return md;
  }

  public void addArgument(ExpressionNode en) {
    argumentlist.add(en);
  }
  
  public void setArgument(ExpressionNode en, int index) {
    argumentlist.setElementAt(en, index);
  }

  public int numArgs() {
    return argumentlist.size();
  }

  public ExpressionNode getArg(int i) {
    return (ExpressionNode) argumentlist.get(i);
  }

  public String printNode(int indent) {
    String st;
    if (en!=null)
      st=en.printNode(indent)+"."+methodid+"(";
    else
      st=methodid+"(";

    for(int i=0; i<argumentlist.size(); i++) {
      ExpressionNode en=(ExpressionNode)argumentlist.get(i);
      st+=en.printNode(indent);
      if ((i+1)!=argumentlist.size())
	st+=", ";
    }
    return st+")";
  }
  public int kind() {
    return Kind.MethodInvokeNode;
  }
  
  public Long evaluate() {
    eval = null;
    return eval; //null;
  }
}
