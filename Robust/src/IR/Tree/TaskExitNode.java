package IR.Tree;
import java.util.Vector;

public class TaskExitNode extends BlockStatementNode {
  Vector vfe;
  Vector ccs;
  public TaskExitNode(Vector vfe, Vector ccs) {
    this.vfe=vfe;
    this.ccs=ccs;
  }

  public String printNode(int indent) {
    return "taskexit";
  }

  public Vector getFlagEffects() {
    return vfe;
  }

  public Vector getChecks() {
    return ccs;
  }

  public int kind() {
    return Kind.TaskExitNode;
  }
}
