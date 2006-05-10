package IR.Tree;
import java.util.Vector;

public class TaskExitNode extends BlockStatementNode {
    Vector vfe;
    public TaskExitNode(Vector vfe) {
	this.vfe=vfe;
    }

    public String printNode(int indent) {
	return "taskexit";
    }

    public int kind() {
	return Kind.TaskExitNode;
    }
}
