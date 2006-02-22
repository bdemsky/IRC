package IR.Tree;
import java.util.Vector;
import IR.TypeDescriptor;
import IR.MethodDescriptor;

public class CreateObjectNode extends ExpressionNode {
    TypeDescriptor td;
    Vector argumentlist;
    MethodDescriptor md;

    public CreateObjectNode(TypeDescriptor type) {
	td=type;
	argumentlist=new Vector();
    }
    public void addArgument(ExpressionNode en) {
	argumentlist.add(en);
    }

    public void setConstructor(MethodDescriptor md) {
	this.md=md;
    }

    public MethodDescriptor getConstructor() {
	return md;
    }

    public TypeDescriptor getType() {
	return td;
    }

    public int numArgs() {
	return argumentlist.size();
    }

    public ExpressionNode getArg(int i) {
	return (ExpressionNode) argumentlist.get(i);
    }

    public String printNode(int indent) {
	String st="new "+td.toString()+"(";
	for(int i=0;i<argumentlist.size();i++) {
	    ExpressionNode en=(ExpressionNode)argumentlist.get(i);
	    st+=en.printNode(indent);
	    if ((i+1)!=argumentlist.size())
		st+=", ";
	}
	return st+")";
    }

    public int kind() {
	return Kind.CreateObjectNode;
    }
}
