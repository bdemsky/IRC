package IR.Flat;
import IR.TypeDescriptor;
import Analysis.Locality.LocalityBinding;

public class FlatGlobalConvNode extends FlatNode {
    TempDescriptor src;
    LocalityBinding lb;
    boolean makePtr;

    public FlatGlobalConvNode(TempDescriptor src, LocalityBinding lb, boolean makePtr) {
	this.src=src;
	this.lb=lb;
	this.makePtr=makePtr;
    }

    public String toString() {
	String str = "FlatGlobalConvNode_"+src.toString();
	if (makePtr)
	    str += "=(PTR)";
	else
	    str += "=(OID)";
	return str+src.toString()+" "+lb;
    }

    public int kind() {
	return FKind.FlatGlobalConvNode;
    }

    public LocalityBinding getLocality() {
	return lb;
    }

    public boolean getMakePtr() {
	return makePtr;
    }

    public TempDescriptor getSrc() {
	return src;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {src};
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[] {src};
    }
}
