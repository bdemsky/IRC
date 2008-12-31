package IR.Tree;
import java.util.Vector;

public class TaskExitNode extends BlockStatementNode {
  Vector vfe;
  Vector ccs;
  int m_taskexitindex;

  public TaskExitNode(Vector vfe, Vector ccs, int taskexitindex) {
    this.vfe=vfe;
    this.ccs=ccs;
    this.m_taskexitindex = taskexitindex;
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

  public int getTaskExitIndex() {
    return m_taskexitindex;
  }
}
