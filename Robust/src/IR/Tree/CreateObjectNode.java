package IR.Tree;
import java.util.Vector;
import IR.TypeDescriptor;

public class CreateObjectNode extends ExpressionNode {
    TypeDescriptor td;
    Vector argumentlist;

    public CreateObjectNode(TypeDescriptor type) {
	td=type;
	argumentlist=new Vector();
    }
    public void addArgument(ExpressionNode en) {
	argumentlist.add(en);
    }

    public String printNode() {
	String st="new "+td.toString()+"(";
	for(int i=0;i<argumentlist.size();i++) {
	    ExpressionNode en=(ExpressionNode)argumentlist.get(i);
	    st+=en.printNode();
	    if ((i+1)!=argumentlist.size())
		st+=", ";
	}
	return st+")";
    }
}
