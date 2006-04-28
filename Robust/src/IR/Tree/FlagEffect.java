package IR.Tree;

import IR.*;

public class FlagEffect {
    FlagDescriptor flag;
    boolean status;
    String name;

    public FlagEffect(String flag, boolean status) {
	this.name=flag;
	this.status=status;
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
	if (status)
	    return name;
	else
	    return "!"+name;
    }
}
