package IR.Flat;
import java.util.Vector;

public class FlatBackEdge extends FlatNode {
  public FlatBackEdge() {
  }

  public String toString() {
    return "backedge";
  }

  public int kind() {
    return FKind.FlatBackEdge;
  }
}
