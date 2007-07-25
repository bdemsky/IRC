package IR.Tree;
import java.util.Vector;
import IR.TypeDescriptor;
import IR.MethodDescriptor;

public class CreateObjectNode extends ExpressionNode {
    TypeDescriptor td;
    Vector argumentlist;
    MethodDescriptor md;
    FlagEffects fe;
    boolean isglobal;

    public CreateObjectNode(TypeDescriptor type, boolean isglobal) {
	td=type;
	argumentlist=new Vector();
	this.isglobal=isglobal;
    }

    public boolean isGlobal() {
	return isglobal;
    }

    public void addFlagEffects(FlagEffects fe) {
	this.fe=fe;
    }

    public FlagEffects getFlagEffects() {
	return fe;
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
	String st;
	boolean isarray=td.isArray();
	if (isarray)
	    st="new "+td.toString()+"[";
	else
	    st="new "+td.toString()+"(";
	for(int i=0;i<argumentlist.size();i++) {
	    ExpressionNode en=(ExpressionNode)argumentlist.get(i);
	    st+=en.printNode(indent);
	    if ((i+1)!=argumentlist.size()) {
		if (isarray) 
		    st+="][";
		else
		    st+=", ";
	    }
	}
	if (isarray)
	    return st+"]";
	else
	    return st+")";
    }

    public int kind() {
	return Kind.CreateObjectNode;
    }
}
