package IR.Flat;
import java.util.Vector;
import IR.*;

public class FlatOpNode extends FlatNode {
    TempDescriptor dest;
    TempDescriptor left;
    TempDescriptor right;
    Operation op;

    public FlatOpNode(TempDescriptor dest, TempDescriptor left, TempDescriptor right, Operation op) {
	this.dest=dest;
	this.left=left;
	this.right=right;
	this.op=op;
    }
    
    TempDescriptor getDest() {
	return dest;
    }

    TempDescriptor getLeft() {
	return left;
    }

    TempDescriptor getRight() {
	return right;
    }
    
    Operation getOp() {
	return op;
    }

    public String toString() {
	return dest.toString()+"="+left.toString()+op.toString()+right.toString();
    }
}
