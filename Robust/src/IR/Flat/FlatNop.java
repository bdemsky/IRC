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
  public FlatNode clone(TempMap t) {
    return new FlatNop();
  }
  public void rewriteUse(TempMap t) {
  }
}
