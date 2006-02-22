package IR.Flat;
import java.util.Vector;

public class FlatCondBranch extends FlatNode {
    TempDescriptor test_cond;

    public FlatCondBranch(TempDescriptor td) {
	test_cond=td;
    }

    public void addTrueNext(FlatNode n) {
	next.setElementAt(n,0);
	n.addPrev(this);
    }

    public void addFalseNext(FlatNode n) {
	next.setElementAt(n,1);
	n.addPrev(this);
    }

    public String toString() {
	return "conditional branch";
    }

    public String toString(String negjump) {
	return "if (!"+test_cond.toString()+") goto "+negjump;
    }

    public void addNext(FlatNode n) {
	throw new Error();
    }
}
