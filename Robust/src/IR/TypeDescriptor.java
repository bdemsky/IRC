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


    int arraycount;
    int type;
    ClassDescriptor class_desc;

    public boolean equals(Object o) {
	if (o instanceof TypeDescriptor) {
	    TypeDescriptor t=(TypeDescriptor)o;
	    if (t.type!=type)
		return false;
	    if ((type==CLASS)&&(t.class_desc!=class_desc))
		return false;
	    return true;
	}
	return false;
    }

    public TypeDescriptor makeArray() {
	TypeDescriptor td=new TypeDescriptor(getSymbol());
	td.arraycount=arraycount+1;
	td.type=type;
	td.class_desc=class_desc;
	return td;
    }

    public boolean isArray() {
	return arraycount>0;
    }

    public TypeDescriptor dereference() {
	TypeDescriptor td=new TypeDescriptor(getSymbol());
	if (arraycount==0)
	    throw new Error();
	td.arraycount=arraycount-1;
	td.type=type;
	td.class_desc=class_desc;
	return td;
    }

    public String getSafeSymbol() {
	if (isClass())
	    return class_desc.getSafeSymbol();
	else if (isByte())
	    return "char";
	else if (isChar())
	    return "short";
	else if (isShort())
	    return "short";
	else if (isInt())
	    return "int";
	else if (isBoolean()) //Booleans are ints in C
	    return "int";
	else if (isLong())
	    return "long long";
	else if (isVoid())
	    return "void";
	else if (isDouble())
	    return "double";
	else if (isFloat())
	    return "float";
	else throw new Error("Error Type: "+type);
    }

    public String getSafeDescriptor() {
	if (isClass())
	    return class_desc.getSafeDescriptor();
	else if (isByte())
	    return "C";
	else if (isChar())
	    return "C";
	else if (isShort())
	    return "S";
	else if (isBoolean())
	    return "Z";
	else if (isInt())
	    return "I";
	else if (isLong())
	    return "J";
	else if (isDouble())
	    return "D";
	else if (isFloat())
	    return "F";
	else throw new Error();	
    }

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
	this.arraycount=0;
    }

    private TypeDescriptor(String st) {
	super(st);
    }

    public ClassDescriptor getClassDesc() {
	return class_desc;
    }

    public TypeDescriptor(ClassDescriptor cd) {
	super(cd.getSymbol());
	this.type=CLASS;
	this.class_desc=cd;
	this.arraycount=0;
    }

    public TypeDescriptor(int t) {
	super(decodeInt(t));
	this.type=t;
	this.arraycount=0;
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
