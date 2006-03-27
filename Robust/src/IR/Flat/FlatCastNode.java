package IR.Flat;
import IR.TypeDescriptor;

public class FlatCastNode extends FlatNode {
    TempDescriptor src;
    TempDescriptor dst;
    TypeDescriptor type;
    
    public FlatCastNode(TypeDescriptor type, TempDescriptor src, TempDescriptor dst) {
	this.type=type;
	this.src=src;
	this.dst=dst;
    }

    public String toString() {
	return dst.toString()+"=("+type.toString()+")"+src.toString();
    }

    public int kind() {
	return FKind.FlatCastNode;
    }

    public TypeDescriptor getType() {
	return type;
    }

    public TempDescriptor getSrc() {
	return src;
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[] {src};
    }

}
