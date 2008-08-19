package IR.Flat;
import java.util.Vector;

public class FlatNop extends FlatNode {
  public FlatNop() {
  }

  public String toString() {
    return "nop";
  }

  public int kind() {
    return FKind.FlatNop;
  }
}
