package IR;

public class AssignOperation {
    public static final int EQ=1;
    public static final int MULTEQ=2;
    public static final int DIVEQ=3;
    public static final int MODEQ=4;
    public static final int PLUSEQ=5;
    public static final int MINUSEQ=6;
    public static final int LSHIFTEQ=7;
    public static final int RSHIFTEQ=8;
    public static final int URSHIFTEQ=9;
    public static final int ANDEQ=10;
    public static final int XOREQ=11;
    public static final int OREQ=12;
    public static final int POSTINC=13;
    public static final int POSTDEC=14;

    private int operation;
    public AssignOperation(int op) {
	this.operation=op;
    }

    public AssignOperation(String op) {
	this.operation=parseOp(op);
    }

    public Operation getBaseOp() {
	switch(operation) {
	case EQ:
	    return null;
	case MULTEQ:
	    return new Operation(Operation.MULT);
	case DIVEQ:
	    return new Operation(Operation.DIV);
	case MODEQ:
	    return new Operation(Operation.MOD);
	case PLUSEQ:
	    return new Operation(Operation.ADD);
	case MINUSEQ:
	    return new Operation(Operation.SUB);
	case LSHIFTEQ:
	    return new Operation(Operation.LEFTSHIFT);
	case RSHIFTEQ:
	    return new Operation(Operation.RIGHTSHIFT);
	case ANDEQ:
	    return new Operation(Operation.BIT_AND);
	case XOREQ:
	    return new Operation(Operation.BIT_XOR);
	case OREQ:
	    return new Operation(Operation.BIT_OR);
	case POSTINC:
	    return new Operation(Operation.POSTINC);
	case POSTDEC:
	    return new Operation(Operation.POSTDEC);
	}
	throw new Error();
    }

    public static int parseOp(String st) {
	if (st.equals("eq"))
	    return EQ;
	else if (st.equals("multeq"))
	    return MULTEQ;
	else if (st.equals("diveq"))
	    return DIVEQ;
	else if (st.equals("modeq"))
	    return MODEQ;
	else if (st.equals("pluseq"))
	    return PLUSEQ;
	else if (st.equals("minuseq"))
	    return MINUSEQ;
	else if (st.equals("lshifteq"))
	    return LSHIFTEQ;
	else if (st.equals("rshifteq"))
	    return RSHIFTEQ;
	else if (st.equals("andeq"))
	    return ANDEQ;
	else if (st.equals("xoreq"))
	    return XOREQ;
	else if (st.equals("oreq"))
	    return OREQ;
	else if (st.equals("postinc"))
	    return POSTINC;
	else if (st.equals("postdec"))
	    return POSTDEC;
	else throw new Error();
    }

    public String toString() {
	if (operation==EQ)
	    return "=";
	else if (operation==MULTEQ)
	    return "*=";
	else if (operation==DIVEQ)
	    return "/=";
	else if (operation==MODEQ)
	    return "%=";
	else if (operation==PLUSEQ)
	    return "+=";
	else if (operation==MINUSEQ)
	    return "-=";
	else if (operation==LSHIFTEQ)
	    return "<=";
	else if (operation==RSHIFTEQ)
	    return ">=";
	else if (operation==ANDEQ)
	    return "&=";
	else if (operation==XOREQ)
	    return "^=";
	else if (operation==OREQ)
	    return "|=";
	else if (operation==POSTINC)
	    return "postinc";
	else if (operation==POSTDEC)
	    return "postdec";
	else throw new Error();
    }


}
