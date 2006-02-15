package IR.Tree;
import IR.AssignOperation;

public class AssignmentNode extends ExpressionNode {
    ExpressionNode left;
    ExpressionNode right;
    AssignOperation op;

    public AssignmentNode(ExpressionNode l, ExpressionNode r, AssignOperation op) {
	left=l;
	right=r;
	this.op=op;
    }

    public String printNode() {
	return left.printNode()+" "+op.toString()+" "+right.printNode();
    }
}
