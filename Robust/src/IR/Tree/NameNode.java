package IR.Tree;
import IR.NameDescriptor;
import IR.VarDescriptor;
import IR.TypeDescriptor;
import IR.FieldDescriptor;

public class NameNode extends ExpressionNode {
    NameDescriptor name;
    VarDescriptor vd;
    FieldDescriptor fd;
    ExpressionNode en;

    public NameNode(NameDescriptor nd) {
	this.name=nd;
	this.vd=null;
	this.fd=null;
    }

    public ExpressionNode getExpression() {
	return en;
    }

    /* Gross hack */
    public void setExpression(ExpressionNode en) {
	this.en=en;
    }

    public void setVar(VarDescriptor vd) {
	this.vd=vd;
    }

    public void setField(FieldDescriptor fd) {
	this.fd=fd;
    }

    public FieldDescriptor getField() {
	return fd;
    }

    public VarDescriptor getVar() {
	return vd;
    }

    public TypeDescriptor getType() {
	if (en!=null)
	    return en.getType();
	else if (fd!=null)
	    return fd.getType();
	else
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
