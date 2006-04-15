package IR.Tree;
import IR.Operation;

public class FlagOpNode extends FlagExpressionNode {
    FlagExpressionNode left;
    FlagExpressionNode right;
    Operation op;

    public FlagOpNode(FlagExpressionNode l, FlagExpressionNode r, Operation o) {
	left=l;
	right=r;
	op=o;
    }

    public FlagOpNode(FlagExpressionNode l, Operation o) {
	left=l;
	right=null;
	op=o;
    }

    public FlagExpressionNode getLeft() {
	return left;
    }

    public FlagExpressionNode getRight() {
	return right;
    }

    public Operation getOp() {
	return op;
    }

    public String printNode(int indent) {
	if (right==null)
	    return op.toString()+"("+left.printNode(indent)+")";
	else
	    return left.printNode(indent)+" "+op.toString()+" "+right.printNode(indent);
    }

    public int kind() {
	return Kind.FlagOpNode;
    }
}
