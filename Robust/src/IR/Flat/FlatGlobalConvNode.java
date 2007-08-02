package IR.Flat;
import IR.TypeDescriptor;

public class FlatGlobalConvNode extends FlatNode {
    TempDescriptor src;
    TempDescriptor dst;
    boolean makePtr;

    public FlatGlobalConvNode(TempDescriptor src, TempDescriptor dst, boolean makePtr) {
	this.src=src;
	this.dst=dst;
	this.makePtr=makePtr;
    }

    public String toString() {
	if (makePtr)
	    return dst.toString()+"=(PTR)"+src.toString();
	else
	    return dst.toString()+"=(OID)"+src.toString();
    }

    public int kind() {
	return FKind.FlatGlobalConvNode;
    }

    public boolean getMakePtr() {
	return makePtr;
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
