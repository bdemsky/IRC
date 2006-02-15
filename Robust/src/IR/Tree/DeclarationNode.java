package IR.Tree;
import IR.VarDescriptor;

class DeclarationNode extends BlockStatementNode {
    VarDescriptor vd;
    public DeclarationNode(VarDescriptor var) {
	vd=var;
    }
    
    public String printNode(int indent) {
	return vd.toString();
    }

}
