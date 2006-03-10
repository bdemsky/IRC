package IR.Flat;
import IR.TypeDescriptor;

public class FlatLiteralNode extends FlatNode {
    Object value;
    TypeDescriptor type;
    TempDescriptor dst;

    public FlatLiteralNode(TypeDescriptor type, Object o, TempDescriptor dst) {
	this.type=type;
	value=o;
	this.dst=dst;
    }

    public Object getValue() {
	return value;
    }

    public String toString() {
	if (value==null)
	    return dst+"=null";
	else
	    return dst+"="+escapeString(value.toString());
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
	return FKind.FlatLiteralNode;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }
}
