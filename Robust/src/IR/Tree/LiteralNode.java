package IR.Tree;

public class LiteralNode extends ExpressionNode {
    public final static int INTEGER=1;
    public final static int FLOAT=2;
    public final static int BOOLEAN=3;
    public final static int CHAR=4;
    public final static int STRING=5;
    public final static int NULL=6;


    Object value;
    int type;
    
    public LiteralNode(String type, Object o) {
	this.type=parseType(type);
	value=o;
    }

    private static int parseType(String type) {
	if (type.equals("integer"))
	    return INTEGER;
	else if (type.equals("float"))
	    return FLOAT;
	else if (type.equals("boolean"))
	    return BOOLEAN;
	else if (type.equals("char"))
	    return CHAR;
	else if (type.equals("string"))
	    return STRING;
	else if (type.equals("null"))
	    return NULL;
	else throw new Error();
    }

    private String getType() {
	if (type==INTEGER)
	    return "integer";
	else if (type==FLOAT)
	    return "float";	
	else if (type==BOOLEAN)
	    return "boolean";
	else if (type==CHAR)
	    return "char";
	else if (type==STRING)
	    return "string";
	else if (type==NULL)
	    return "null";
	else throw new Error();

    }

    public String printNode(int indent) {
	if (type==NULL)
	    return "null";
	if (type==STRING) {
	    return '"'+escapeString(value.toString())+'"';
	}
	return "/*"+getType()+ "*/"+value.toString();
    }
    private static String escapeString(String st) {
	String new_st="";
	for(int i=0;i<st.length();i++) {
	    char x=st.charAt(i);
	    if (x=='\n')
		new_st+="\\n";
	    else if (x=='"')
		new_st+="'"+'"'+"'";
	    else new_st+=x;
	}
	return new_st;
    }
    public int kind() {
	return Kind.LiteralNode;
    }
}
