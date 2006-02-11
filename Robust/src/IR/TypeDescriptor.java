package IR;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class TypeDescriptor extends Descriptor {
    public static final int BYTE=1;
    public static final int SHORT=2;
    public static final int INT=3;
    public static final int LONG=4;
    public static final int CHAR=5;
    public static final int FLOAT=6;
    public static final int DOUBLE=7;
    public static final int VOID=8;
    public static final int CLASS=9;


    int type;
    NameDescriptor name_desc;
    
    public TypeDescriptor(NameDescriptor name) {
	super(name.toString());
	this.type=CLASS;
	this.name_desc=name;
    }

    public TypeDescriptor(int t) {
	super(decodeInt(t));
	this.type=t;
    }

    public String toString() {
	if (type==CLASS)
	    return name_desc.toString();
	else 
	    return decodeInt(type);
    }

    private static String decodeInt(int type) {
	if (type==BYTE)
	    return "byte";
	else if (type==SHORT)
	    return "short";
	else if (type==INT)
	    return "int";
	else if (type==LONG)
	    return "long";
	else if (type==CHAR)
	    return "char";
	else if (type==FLOAT)
	    return "float";
	else if (type==DOUBLE)
	    return "double";
	else if (type==VOID)
	    return "void";
	else throw new Error();
    }
}
