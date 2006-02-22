package IR.Tree;
import java.util.Vector;
import IR.NameDescriptor;
import IR.MethodDescriptor;

public class MethodInvokeNode extends ExpressionNode {
    NameDescriptor nd;
    Vector argumentlist;
    String methodid;
    ExpressionNode en;
    MethodDescriptor md;

    public MethodInvokeNode(NameDescriptor name) {
	nd=name;
	argumentlist=new Vector();
	methodid=null;
	en=null;
	md=null;
    }

    public MethodInvokeNode(String methodid, ExpressionNode exp) {
	this.methodid=methodid;
	this.en=exp;
	nd=null;
	argumentlist=new Vector();
	md=null;
    }

    public ExpressionNode getExpression() {
	return en;
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

    public int numArgs() {
	return argumentlist.size();
    }

    public ExpressionNode getArg(int i) {
	return (ExpressionNode) argumentlist.get(i);
    }

    public String printNode(int indent) {
	String st;
	if (nd==null) {
	    st=en.printNode(indent)+"."+methodid+"(";
   	} else {
	    st=nd.toString()+"(";
	}
	for(int i=0;i<argumentlist.size();i++) {
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
}
