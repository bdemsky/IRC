package IR.Tree;
import IR.NameDescriptor;
import IR.VarDescriptor;
import IR.TypeDescriptor;

public class NameNode extends ExpressionNode {
    NameDescriptor name;
    VarDescriptor vd;

    public NameNode(NameDescriptor nd) {
	this.name=nd;
    }

    public void setVar(VarDescriptor vd) {
	this.vd=vd;
    }

    public TypeDescriptor getType() {
	return vd.getType();
    }

    NameDescriptor getName() {
	return name;
    }

    public String printNode(int indent) {
	return name.toString();
    }

    public int kind() {
	return Kind.NameNode;
    }
}
