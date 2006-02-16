package IR.Flat;
import java.util.Vector;

public class FlatCondBranch extends FlatNode {
    TempDescriptor test_cond;

    public FlatCondBranch(TempDescriptor td) {
	test_cond=td;
    }

    public void addTrueNext(FlatNode n) {
	next.setElementAt(n,0);
    }

    public void addFalseNext(FlatNode n) {
	next.setElementAt(n,1);
    }

    public void addNext(FlatNode n) {
	throw new Error();
    }
}
