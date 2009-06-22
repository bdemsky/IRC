package IR.Flat;
import java.util.Vector;

public class FlatExit extends FlatNode {
  public FlatExit() {
  }

  public String toString() {
    return "exit";
  }

  public int kind() {
    return FKind.FlatExit;
  }
  public FlatNode clone(TempMap t) {
    return new FlatExit();
  }
  public void rewriteUse(TempMap t) {
  }
  public void rewriteDst(TempMap t) {
  }
}
