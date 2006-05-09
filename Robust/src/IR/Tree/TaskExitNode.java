package IR.Tree;

public class TaskExitNode extends BlockStatementNode {
    FlagEffects fe;
    public TaskExitNode(FlagEffects fe) {
	this.fe=fe;
    }

    public String printNode(int indent) {
	return "taskexit";
    }

    public int kind() {
	return Kind.TaskExitNode;
    }
}
