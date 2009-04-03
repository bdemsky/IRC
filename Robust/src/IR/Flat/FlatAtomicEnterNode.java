package IR.Flat;
import java.util.Vector;

public class FlatAtomicEnterNode extends FlatNode {
  private static int identifier=0;

  private int id;

  public FlatAtomicEnterNode() {
    this.id=identifier++;
  }

  public void rewriteUse() {
  }
  public void rewriteDef() {
  }

  /* Returns an unique identifier for this atomic enter node */

  public int getIdentifier() {
    return id;
  }

  public String toString() {
    return "atomicenter";
  }

  public int kind() {
    return FKind.FlatAtomicEnterNode;
  }
}
