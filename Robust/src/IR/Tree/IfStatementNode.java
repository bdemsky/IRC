package IR.Tree;

class IfStatementNode extends BlockStatementNode {
    ExpressionNode cond;
    BlockNode true_st;
    BlockNode else_st;
    
    public IfStatementNode(ExpressionNode cond, BlockNode true_st, BlockNode else_st) {
	this.cond=cond;
	this.true_st=true_st;
	this.else_st=else_st;
    }
    
    public String printNode() {
	if (else_st==null)
	    return "if("+cond.printNode()+") {\n"+true_st.printNode()+"\n}\n";
	else 
	    return "if("+cond.printNode()+") {\n"+true_st.printNode()+"\n} else {\n"+
		else_st.printNode()+"}\n";
    }
}
