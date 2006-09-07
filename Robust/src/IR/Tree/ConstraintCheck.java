package IR.Tree;

import IR.*;
import java.util.Vector;

public class ConstraintCheck {
    String specname;
    Vector args;
    Vector vars;

    public ConstraintCheck(String specname) {
	this.specname=specname;
	args=new Vector();
	vars=new Vector();
    }

    public void addVariable(String var) {
	vars.add(var);
    }

    public void addArgument(ExpressionNode en) {
	args.add(en);
    }

    public String getSpec() {
	return specname;
    }

    public int numArgs() {
	return args.size();
    }

    public ExpressionNode getArg(int i) {
	return (ExpressionNode) args.get(i);
    }

    public String getVar(int i) {
	return (String) args.get(i);
    }

    public String printNode(int indent) {
	String str="assert("+specname+"(";
	for(int i=0;i<numArgs();i++) {
	    if (i>0)
		str+=",";
	    str+=getVar(i)+" : ";
	    str+=getArg(i).printNode(0);
	}
	return str+")";
    }
}
