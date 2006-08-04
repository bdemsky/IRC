package IR.Tree;

import IR.*;

public class DNFFlagAtom {
    final FlagNode flag;
    final boolean negated;

    public DNFFlagAtom(FlagNode flag, boolean negated) {
	this.flag=flag;
	this.negated=negated;
    }
}
