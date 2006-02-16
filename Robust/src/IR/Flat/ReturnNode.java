package IR.Flat;

public class ReturnNode extends FlatNode {
    TempDescriptor tempdesc;

    public ReturnNode(TempDescriptor td) {
	this.tempdesc=td;
    }

    public String toString() {
	return "return "+tempdesc;
    }

}
