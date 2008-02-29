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

    public MethodDescriptor getMethod() {
	return method;
    }

    public TempDescriptor getThis() {
	return this_temp;
    }

    public TempDescriptor getReturnTemp() {
	return dst;
    }

    public int numArgs() {
	return args.length;
    }

    public TempDescriptor getArg(int i) {
	return args[i];
    }

    public String toString() {
	String st="FlatCall_";
	if (dst==null) {
	    if (method==null)
		st+="null(";
	    else
		st+=method.getSymbol()+"(";
	} else
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

    public int kind() {
	return FKind.FlatCall;
    }

    public TempDescriptor [] readsTemps() {
	int size=args.length;
	if (this_temp!=null)
	    size++;
	TempDescriptor [] t=new TempDescriptor[size];
	int offset=0;
	if (this_temp!=null)
	    t[offset++]=this_temp;
	for(int i=0;i<args.length;i++)
	    t[offset++]=args[i];
	return t;
    }

    public TempDescriptor [] writesTemps() {
	if (dst!=null)
	    return new TempDescriptor[] {dst};
	else
	    return new TempDescriptor[0];
    }
}
