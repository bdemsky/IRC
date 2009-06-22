package IR.Flat;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;

public class FlatAtomicEnterNode extends FlatNode {
  private static int identifier=0;
  HashSet<FlatNode> exits;

  private int id;

  public FlatAtomicEnterNode() {
    this.id=identifier++;
    this.exits=new HashSet<FlatNode>();
  }

  public void addExit(FlatAtomicExitNode faen) {
    exits.add(faen);
  }

  public Set<FlatNode> getExits() {
    return exits;
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
