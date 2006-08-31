package IR.Tree;

import IR.*;

public class ConstraintCheck {
    String varname;
    String specname;
    VarDescriptor vd;

    public ConstraintCheck(String varname, String specname) {
	this.varname=varname;
	this.specname=specname;
    }

    public void setVar(VarDescriptor vd) {
	this.vd=vd;
    }

    public VarDescriptor getVar() {
	return vd;
    }

    public String getVarName() {
	return varname;
    }

    public String getSpec() {
	return specname;
    }

    public String printNode(int indent) {
	return "assert "+varname+" "+specname;
    }
}
