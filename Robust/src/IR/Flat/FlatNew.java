package IR.Flat;
import IR.TypeDescriptor;

public class FlatNew extends FlatNode {
    TempDescriptor dst;
    TypeDescriptor type;
    
    public FlatNew(TypeDescriptor type, TempDescriptor dst) {
	this.type=type;
	this.dst=dst;
    }

    public String toString() {
	return dst.toString()+"= NEW "+type.toString();
    }

    public int kind() {
	return FKind.FlatNew;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public TypeDescriptor getType() {
	return type;
    }
}
