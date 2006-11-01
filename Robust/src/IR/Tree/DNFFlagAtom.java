package IR.Tree;

import IR.*;

public class DNFFlagAtom {
    private final FlagNode flag;
    private final boolean negated;

    public DNFFlagAtom(FlagNode flag, boolean negated) {
	this.flag=flag;
	this.negated=negated;
    }

    public FlagNode getFlagNode() {
	return flag;
    }

    public FlagDescriptor getFlag() {
	return flag.getFlag();
    }

    public boolean getNegated() {
	return negated;
    }

    public String toString() {
	if (negated)
	    return "!"+flag.toString();
	else
	    return flag.toString();
    }
}
