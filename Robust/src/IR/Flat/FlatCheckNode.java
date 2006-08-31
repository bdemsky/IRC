package IR.Flat;

public class FlatCheckNode extends FlatNode {
    TempDescriptor td;
    String spec;

    public FlatCheckNode(TempDescriptor td, String spec) {
	this.td=td;
	this.spec=spec;
    }

    public int kind() {
        return FKind.FlatCheckNode;
    }
    
    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[] {td};
    }
}
