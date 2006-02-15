package IR.Tree;

class BlockExpressionNode extends BlockStatementNode {
    ExpressionNode en;
    public BlockExpressionNode(ExpressionNode e) {
	this.en=e;
    }
    
    public String printNode() {
	return en.printNode()+";";
    }
}
