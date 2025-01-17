package IR.Flat;
import java.util.Vector;

public class FlatAtomicExitNode extends FlatNode {
  FlatAtomicEnterNode faen;
  public FlatAtomicExitNode(FlatAtomicEnterNode faen) {
    this.faen=faen;
    faen.addExit(this);
  }
  public FlatAtomicEnterNode getAtomicEnter() {
    return faen;
  }
  public void rewriteUse() {
  }
  public void rewriteDef() {
  }

  public String toString() {
    return "atomicexit";
  }

  public int kind() {
    return FKind.FlatAtomicExitNode;
  }
}
