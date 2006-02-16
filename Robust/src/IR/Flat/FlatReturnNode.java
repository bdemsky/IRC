package IR.Flat;

public class FlatReturnNode extends FlatNode {
    TempDescriptor tempdesc;

    public FlatReturnNode(TempDescriptor td) {
	this.tempdesc=td;
    }

    public String toString() {
	return "return "+tempdesc;
    }

}
