package IR.Flat;
import java.util.Vector;

public class FlatAtomicExitNode extends FlatNode {
    public FlatAtomicExitNode() {
    }

    public String toString() {
	return "atomicexit";
    }

    public int kind() {
	return FKind.FlatAtomicExitNode;
    }
}
