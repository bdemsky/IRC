package IR.Flat;

public class FlatCheckNode extends FlatNode {
    TempDescriptor [] temps;
    String spec;

    public FlatCheckNode(String spec, TempDescriptor[] temps) {
	this.spec=spec;
	this.temps=temps;
    }

    public int kind() {
        return FKind.FlatCheckNode;
    }

    public String getSpec() {
	return spec;
    }

    public TempDescriptor [] getTemps() {
	return temps;
    }
    
    public TempDescriptor [] readsTemps() {
	return temps;
    }
}
