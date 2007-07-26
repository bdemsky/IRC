package IR.Flat;
import java.util.Vector;

public class FlatAtomicEnterNode extends FlatNode {
    public FlatAtomicEnterNode() {
    }

    public String toString() {
	return "atomicenter";
    }

    public int kind() {
	return FKind.FlatAtomicEnterNode;
    }
}
