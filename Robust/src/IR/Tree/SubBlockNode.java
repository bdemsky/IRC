package IR.Tree;

class SubBlockNode extends BlockStatementNode {
    BlockNode bn;
    public SubBlockNode(BlockNode bn) {
	this.bn=bn;
    }
    
    public String printNode() {
	return bn.printNode();
    }

}
