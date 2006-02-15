package IR.Tree;
import java.util.Vector;
import IR.NameDescriptor;

public class MethodInvokeNode extends ExpressionNode {
    NameDescriptor nd;
    Vector argumentlist;
    String methodid;
    ExpressionNode en;

    public MethodInvokeNode(NameDescriptor name) {
	nd=name;
	argumentlist=new Vector();
	methodid=null;
	en=null;
    }

    public MethodInvokeNode(String methodid, ExpressionNode exp) {
	this.methodid=methodid;
	this.en=exp;
	nd=null;
	argumentlist=new Vector();
    }

    public void addArgument(ExpressionNode en) {
	argumentlist.add(en);
    }

    public String printNode() {
	String st;
	if (nd==null) {
	    st=en.printNode()+"."+methodid+"(";
   	} else {
	    st=nd.toString()+"(";
	}
	for(int i=0;i<argumentlist.size();i++) {
	    ExpressionNode en=(ExpressionNode)argumentlist.get(i);
	    st+=en.printNode();
	    if ((i+1)!=argumentlist.size())
		st+=", ";
	}
	return st+")";
    }
}
