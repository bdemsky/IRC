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

    public String printNode(int indent) {
	/*	if (type==NULL)
	    return dst+"=null";
	    if (type==STRING) {
	    return dst+"="+'"'+escapeString(value.toString())+'"';
	    }*/
	//return dst+"="+"/*"+getType()+ "*/"+value.toString();
	return "";
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
}
