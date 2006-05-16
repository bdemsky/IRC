package IR.Flat;

public class FlatTaskExitNode extends FlatNode {
    public FlatTaskExitNode() {
    }

    public int kind() {
	return FKind.FlatTaskExitNode;
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor [0];
    }
}
