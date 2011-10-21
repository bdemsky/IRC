package Analysis.SSJava;

import IR.Tree.BlockStatementNode;

//contains a varID and what branch that var has implicit flow on
public class ImplicitTuple{
    private VarID var;
    private BlockStatementNode branchID;

    public ImplicitTuple(VarID var, BlockStatementNode branchID){
	this.var = var;
	this.branchID = branchID;
    }

    public VarID getVar(){
	return var;
    }

    public boolean isFromBranch(BlockStatementNode branchID){
	return this.branchID.equals(branchID);
    }
}