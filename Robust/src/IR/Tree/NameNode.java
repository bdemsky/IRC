package IR.Tree;
import IR.NameDescriptor;

public class NameNode extends ExpressionNode {
    NameDescriptor name;
    public NameNode(NameDescriptor nd) {
	this.name=nd;
    }

    public String printNode() {
	return name.toString();
    }
}
