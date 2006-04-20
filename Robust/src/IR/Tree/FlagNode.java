package IR.Tree;

import IR.*;

public class FlagNode extends FlagExpressionNode {
    FlagDescriptor flag;
    String name;

    public FlagNode(String flag) {
	this.name=flag;
    }

    public void setFlag(FlagDescriptor flag) {
	this.flag=flag;
    }

    public FlagDescriptor getFlag() {
	return flag;
    }

    public int kind() {
	return Kind.FlagNode;
    }

    public String printNode(int indent) {
	return name;
    }
}
