package IR.Flat;
import IR.MethodDescriptor;

public class FlatCall extends FlatNode {
    TempDescriptor args[];
    TempDescriptor this_temp;
    TempDescriptor dst;
    MethodDescriptor method;
    
    public FlatCall(MethodDescriptor md, TempDescriptor dst, TempDescriptor this_temp, TempDescriptor[] args) {
	this.method=md;
	this.dst=dst;
	this.this_temp=this_temp;
	this.args=args;
    }

    public String toString() {
	String st="";
	if (dst==null)
	    st+=method.getSymbol()+"(";
	else
	    st+=dst+"="+method.getSymbol()+"(";
	if (this_temp!=null) {
	    st+=this_temp;
	    if (args.length!=0)
		st+=", ";
	}

	for(int i=0;i<args.length;i++) {
	    st+=args[i].toString();
	    if ((i+1)<args.length)
		st+=", ";
	}
	return st+")";
    }
}
