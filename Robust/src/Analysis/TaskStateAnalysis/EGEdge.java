package Analysis.TaskStateAnalysis;
import java.util.*;
import Util.Edge;


public class EGEdge extends Edge {
  FlagState fs;
  public EGEdge(FlagState fs, EGTaskNode target) {
    super(target);
    this.fs=fs;
  }

  public FlagState getFS() {
    return fs;
  }

  public EGTaskNode getTarget() {
    return (EGTaskNode) target;
  }
}
