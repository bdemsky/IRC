package IR.Tree;
import IR.Operation;

public class OpNode extends ExpressionNode {
    ExpressionNode left;
    ExpressionNode right;
    Operation op;

    public OpNode(ExpressionNode l, ExpressionNode r, Operation o) {
	left=l;
	right=r;
	op=o;
    }

    public OpNode(ExpressionNode l, Operation o) {
	left=l;
	right=null;
	op=o;
    }

    public String printNode(int indent) {
	if (right==null)
	    return op.toString()+"("+left.printNode(indent)+")";
	else
	    return left.printNode(indent)+" "+op.toString()+" "+right.printNode(indent);
    }
}
