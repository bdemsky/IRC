package IR.Tree;

import IR.*;

public class ConstraintCheck {
    String varname;
    String specname;

    public ConstraintCheck(String varname, String specname) {
	this.varname=varname;
	this.specname=specname;
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
