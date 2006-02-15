package IR.Tree;

public class FieldAccessNode extends ExpressionNode {
    ExpressionNode left;
    String fieldname;

    public FieldAccessNode(ExpressionNode l, String field) {
	fieldname=field;
	left=l;
    }

    public String printNode() {
	return left.printNode()+"."+fieldname;
    }
}
