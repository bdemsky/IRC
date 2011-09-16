package Analysis.SSJava;

import IR.Tree.TreeNode;

//contains a varID and what branch that var has implicit flow on
public class ImplicitTuple {
  private VarID var;
  private TreeNode branchID;  // interim fixes

  // interim fixes
  public ImplicitTuple(VarID var, TreeNode branchID) {
    this.var = var;
    this.branchID = branchID;
  }

  public VarID getVar() {
    return var;
  }

  public boolean isFromBranch(TreeNode ln) {
    // interim fixes
    return true;
  }

}