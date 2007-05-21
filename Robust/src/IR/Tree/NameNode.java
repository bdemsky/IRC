package IR.Tree;
import IR.NameDescriptor;
import IR.Descriptor;
import IR.VarDescriptor;
import IR.TagVarDescriptor;
import IR.TypeDescriptor;
import IR.FieldDescriptor;

public class NameNode extends ExpressionNode {
    NameDescriptor name;
    Descriptor vd;
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

    public void setVar(Descriptor vd) {
	this.vd=vd;
    }

    public void setField(FieldDescriptor fd) {
	this.fd=fd;
    }

    public FieldDescriptor getField() {
	return fd;
    }

    public boolean isTag() {
	return (vd instanceof TagVarDescriptor);
    }

    public VarDescriptor getVar() {
	return (VarDescriptor) vd;
    }

    public TagVarDescriptor getTagVar() {
	return (TagVarDescriptor) vd;
    }

    public TypeDescriptor getType() {
	if (en!=null)
	    return en.getType();
	else if (fd!=null)
	    return fd.getType();
	else if (isTag())
	    return new TypeDescriptor(TypeDescriptor.TAG);
	else
	    return ((VarDescriptor)vd).getType();
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
