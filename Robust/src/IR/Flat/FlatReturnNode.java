package IR.Flat;

public class FlatReturnNode extends FlatNode {
    TempDescriptor tempdesc;

    public FlatReturnNode(TempDescriptor td) {
	this.tempdesc=td;
    }

    public String toString() {
	return "return "+tempdesc;
    }
    public int kind() {
	return FKind.FlatReturnNode;
    }
    public TempDescriptor [] readsTemps() {
	if (tempdesc==null)
	    return new TempDescriptor [0];
	else
	    return new TempDescriptor [] {tempdesc};
    }
}
