package IR.Tree;
import IR.VarDescriptor;

public class DeclarationNode extends BlockStatementNode {
    VarDescriptor vd;
    public DeclarationNode(VarDescriptor var) {
	vd=var;
    }
    
    public String printNode(int indent) {
	return vd.toString();
    }

    public int kind() {
	return Kind.DeclarationNode;
    }
}
