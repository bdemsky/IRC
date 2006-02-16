package IR.Tree;

public class FieldAccessNode extends ExpressionNode {
    ExpressionNode left;
    String fieldname;

    public FieldAccessNode(ExpressionNode l, String field) {
	fieldname=field;
	left=l;
    }

    public String printNode(int indent) {
	return left.printNode(indent)+"."+fieldname;
    }
    public int kind() {
	return Kind.FieldAccessNode;
    }
}
