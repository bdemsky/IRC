package IR.Flat;
import java.util.Vector;

public class FlatAtomicExitNode extends FlatNode {
  FlatAtomicEnterNode faen;
  public FlatAtomicExitNode(FlatAtomicEnterNode faen) {
    this.faen=faen;
  }

  public FlatAtomicEnterNode getAtomicEnter() {
    return faen;
  }

  public String toString() {
    return "atomicexit";
  }

  public int kind() {
    return FKind.FlatAtomicExitNode;
  }
}
