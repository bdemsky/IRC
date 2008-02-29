package IR.Flat;
import java.util.Vector;

public class FlatCondBranch extends FlatNode {
    TempDescriptor test_cond;

    public FlatCondBranch(TempDescriptor td) {
	test_cond=td;
    }

    public void addTrueNext(FlatNode n) {
	if (next.size()==0)
	    next.setSize(1);
	next.setElementAt(n,0);
	n.addPrev(this);
    }

    public void addFalseNext(FlatNode n) {
	next.setSize(2);
	next.setElementAt(n,1);
	n.addPrev(this);
    }

    public TempDescriptor getTest() {
	return test_cond;
    }

    public String toString() {
	return "conditional branch";
    }

    public String toString(String negjump) {
	return "FlatCondBranch_if (!"+test_cond.toString()+") goto "+negjump;
    }

    public void addNext(FlatNode n) {
	throw new Error();
    }

    public int kind() {
	return FKind.FlatCondBranch;
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[] {test_cond};
    }
}
