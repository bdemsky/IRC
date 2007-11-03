package IR;

public class Operation {
    public static final int LOGIC_OR=1;
    public static final int LOGIC_AND=2;
    public static final int BIT_OR=3;
    public static final int BIT_XOR=4;
    public static final int BIT_AND=5;
    public static final int EQUAL=6;
    public static final int NOTEQUAL=7;
    public static final int LT=8;
    public static final int GT=9;
    public static final int LTE=10;
    public static final int GTE=11;
    public static final int LEFTSHIFT=12;
    public static final int RIGHTSHIFT=13;
    public static final int SUB=14;
    public static final int ADD=15;
    public static final int MULT=16;
    public static final int DIV=17;
    public static final int MOD=18;
    public static final int UNARYPLUS=19;
    public static final int UNARYMINUS=20;
    public static final int POSTINC=21;
    public static final int POSTDEC=22;
    public static final int PREINC=23;
    public static final int PREDEC=24;
    public static final int LOGIC_NOT=25;
    public static final int ISAVAILABLE=26;
    /* Flat Operations */
    public static final int ASSIGN=100;

    private int operation;
    public Operation(int op) {
	this.operation=op;
    }

    public Operation(String op) {
	this.operation=parseOp(op);
    }

    public int getOp() {
	return operation;
    }
    
    public static int parseOp(String st) {
	if (st.equals("logical_or"))
	    return LOGIC_OR;
	else if (st.equals("logical_and"))
	    return LOGIC_AND;
	else if (st.equals("bitwise_or"))
	    return BIT_OR;
	else if (st.equals("bitwise_xor"))
	    return BIT_XOR;
	else if (st.equals("bitwise_and"))
	    return BIT_AND;
	else if (st.equals("equal"))
	    return EQUAL;
	else if (st.equals("not_equal"))
	    return NOTEQUAL;
	else if (st.equals("comp_lt"))
	    return LT;
	else if (st.equals("comp_gt"))
	    return GT;
	else if (st.equals("comp_lte"))
	    return LTE;
	else if (st.equals("comp_gte"))
	    return GTE;
	else if (st.equals("leftshift"))
	    return LEFTSHIFT;
	else if (st.equals("rightshift"))
	    return RIGHTSHIFT;
	else if (st.equals("sub"))
	    return SUB;
	else if (st.equals("add"))
	    return ADD;
	else if (st.equals("mult"))
	    return MULT;
	else if (st.equals("div"))
	    return DIV;
	else if (st.equals("mod"))
	    return MOD;
	else if (st.equals("unaryplus"))
	    return UNARYPLUS;
	else if (st.equals("unaryminus"))
	    return UNARYMINUS;
	else if (st.equals("postinc"))
	    return POSTINC;
	else if (st.equals("postdec"))
	    return POSTDEC;
	else if (st.equals("preinc"))
	    return PREINC;
	else if (st.equals("predec"))
	    return PREDEC;
	else if (st.equals("not"))
	    return LOGIC_NOT;
	else
	    throw new Error();
    }

    public String toString() {
	if (operation==LOGIC_OR)
	    return "||";
	else if (operation==LOGIC_AND)
	    return "&&";
	else if (operation==LOGIC_NOT)
	    return "not";
	else if (operation==BIT_OR)
	    return "|";
	else if (operation==BIT_XOR)
	    return "^";
	else if (operation==BIT_AND)
	    return "&";
	else if (operation==EQUAL)
	    return "==";
	else if (operation==NOTEQUAL)
	    return "!=";
	else if (operation==LT)
	    return "<";
	else if (operation==GT)
	    return ">";
	else if (operation==LTE)
	    return "<=";
	else if (operation==GTE)
	    return ">=";
	else if (operation==LEFTSHIFT)
	    return "<<";
	else if (operation==RIGHTSHIFT)
	    return ">>";
	else if (operation==SUB)
	    return "-";
	else if (operation==ADD)
	    return "+";
	else if (operation==MULT)
	    return "*";
	else if (operation==DIV)
	    return "/";
	else if (operation==MOD)
	    return "%";
	else if (operation==UNARYPLUS)
	    return "unaryplus";
	else if (operation==UNARYMINUS)
	    return "unaryminus";
	else if (operation==POSTINC)
	    return "postinc";
	else if (operation==POSTDEC)
	    return "postdec";
	else if (operation==PREINC)
	    return "preinc";
	else if (operation==PREDEC)
	    return "predec";
	else if (operation==ASSIGN)
	    return "assign";
	else throw new Error();
    }


}
