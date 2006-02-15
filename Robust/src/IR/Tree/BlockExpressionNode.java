package IR.Tree;

class BlockExpressionNode extends BlockStatementNode {
    ExpressionNode en;
    public BlockExpressionNode(ExpressionNode e) {
	this.en=e;
    }
    
    public String printNode(int indent) {
	return en.printNode(indent);
    }
}
