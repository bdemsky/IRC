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
    public static final int BOOLEAN=6;
    public static final int FLOAT=7;
    public static final int DOUBLE=8;
    public static final int VOID=9;
    public static final int NULL=10;
    public static final int CLASS=11;



    int type;
    ClassDescriptor class_desc;

    public boolean isNumber() {
	return (isIntegerType()||isFloat()||isDouble());
    }

    public boolean isByte() {
	return type==BYTE;
    }
    public boolean isNull() {
	return type==NULL;
    }
    public boolean isShort() {
	return type==SHORT;
    }
    public boolean isInt() {
	return type==INT;
    }
    public boolean isLong() {
	return type==LONG;
    }
    public boolean isChar() {
	return type==CHAR;
    }
    public boolean isBoolean() {
	return type==BOOLEAN;
    }
    public boolean isFloat() {
	return type==FLOAT;
    }
    public boolean isDouble() {
	return type==DOUBLE;
    }
    public boolean isVoid() {
	return type==VOID;
    }

    public boolean isPtr() {
	return (isClass()||isNull());
    }

    public boolean isIntegerType() {
	return (isInt()||isLong()||isShort()||isChar()||isByte());
    }

    public void setClassDescriptor(ClassDescriptor cd) {
	class_desc=cd;
    }
  
    public boolean isPrimitive() {
	return ((type>=BYTE)&&(type<=DOUBLE));
    }

    public boolean isClass() {
	return type==CLASS;
    }

    public TypeDescriptor(NameDescriptor name) {
	super(name.toString());
	this.type=CLASS;
	this.class_desc=null;
    }

    public ClassDescriptor getClassDesc() {
	return class_desc;
    }

    public TypeDescriptor(ClassDescriptor cd) {
	super(cd.getSymbol());
	this.type=CLASS;
	this.class_desc=cd;
    }

    public TypeDescriptor(int t) {
	super(decodeInt(t));
	this.type=t;
    }

    public String toString() {
	if (type==CLASS)
	    return name;
	else 
	    return decodeInt(type);
    }

    private static String decodeInt(int type) {
	if (type==BYTE)
	    return "byte";
	else if (type==BOOLEAN)
	    return "boolean";
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
	else if (type==NULL)
	    return "null";
	else throw new Error();
    }
}
