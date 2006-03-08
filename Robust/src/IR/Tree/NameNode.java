package IR.Tree;
import IR.NameDescriptor;
import IR.VarDescriptor;
import IR.TypeDescriptor;
import IR.FieldDescriptor;

public class NameNode extends ExpressionNode {
    NameDescriptor name;
    VarDescriptor vd;
    FieldDescriptor fd;

    public NameNode(NameDescriptor nd) {
	this.name=nd;
	this.vd=null;
	this.fd=null;
    }

    public void setVar(VarDescriptor vd) {
	this.vd=vd;
    }

    public void setField(FieldDescriptor fd) {
	this.fd=fd;
    }

    public TypeDescriptor getType() {
	if (vd!=null)
	    return vd.getType();
	else
	    return fd.getType();
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
