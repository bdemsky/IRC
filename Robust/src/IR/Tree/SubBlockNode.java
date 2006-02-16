package IR.Tree;

class SubBlockNode extends BlockStatementNode {
    BlockNode bn;
    public SubBlockNode(BlockNode bn) {
	this.bn=bn;
    }
    
    public String printNode(int indent) {
	return bn.printNode(indent);
    }
    public int kind() {
	return Kind.SubBlockNode;
    }
}
