package IR.Flat;
import IR.TypeDescriptor;

public class FlatNew extends FlatNode {
    TempDescriptor dst;
    TypeDescriptor type;
    TempDescriptor size;
    
    public FlatNew(TypeDescriptor type, TempDescriptor dst) {
	this.type=type;
	this.dst=dst;
	this.size=null;
    }

    public FlatNew(TypeDescriptor type, TempDescriptor dst, TempDescriptor size) {
	this.type=type;
	this.dst=dst;
	this.size=size;
    }

    public String toString() {
	if (size==null)
	    return dst.toString()+"= NEW "+type.toString();
	else
	    return dst.toString()+"= NEW "+type.toString()+"["+size.toString()+"]";
    }

    public int kind() {
	return FKind.FlatNew;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }

    public TempDescriptor [] readsTemps() {
	if (size!=null)
	    return new TempDescriptor[] {size};
	else
	    return new TempDescriptor[0];
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public TypeDescriptor getType() {
	return type;
    }
}
