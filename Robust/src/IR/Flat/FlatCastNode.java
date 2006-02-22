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
}
