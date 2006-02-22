package IR.Tree;
import IR.FieldDescriptor;

public class FieldAccessNode extends ExpressionNode {
    ExpressionNode left;
    String fieldname;
    FieldDescriptor field;

    public FieldAccessNode(ExpressionNode l, String field) {
	fieldname=field;
	left=l;
    }

    public void setField(FieldDescriptor fd) {
	field=fd;
    }

    public FieldDescriptor getField() {
	return field;
    }

    public ExpressionNode getExpression() {
	return left;
    }

    public String printNode(int indent) {
	return left.printNode(indent)+"."+fieldname;
    }
    public int kind() {
	return Kind.FieldAccessNode;
    }
}
