package IR.Flat;
import java.util.Vector;

public class FlatBackEdge extends FlatNode {
  public FlatBackEdge() {
  }
  public void rewriteUse() {
  }
  public void rewriteDst() {
  }
  public String toString() {
    return "backedge";
  }
  public FlatNode clone(TempMap t) {
    return new FlatBackEdge();  
  }

  public int kind() {
    return FKind.FlatBackEdge;
  }
}
