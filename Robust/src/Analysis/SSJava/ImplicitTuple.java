package Analysis.SSJava;

import IR.Tree.ExpressionNode;

//contains a varID and what branch that var has implicit flow on
public class ImplicitTuple{
    private VarID var;
    private ExpressionNode branchID;

    public ImplicitTuple(VarID var, ExpressionNode branchID){
	this.var = var;
	this.branchID = branchID;
    }

    public VarID getVar(){
	return var;
    }

    public boolean isFromBranch(ExpressionNode branchID){
	return this.branchID == branchID;
    }
}