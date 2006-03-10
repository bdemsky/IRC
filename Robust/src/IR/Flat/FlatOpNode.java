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
	if (right!=null)
	    return dest.toString()+"="+left.toString()+op.toString()+right.toString();
	else if (op.getOp()==Operation.ASSIGN)
	    return dest.toString()+" = "+left.toString();
	else
	    return dest.toString()+" "+op.toString() +" "+left.toString();
    }

    public int kind() {
	return FKind.FlatOpNode;
    }

    public TempDescriptor [] readsTemps() {
	if (right!=null)
	    return new TempDescriptor [] {left,right};
	else
	    return new TempDescriptor [] {left};
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor [] {dest};
    }
}
