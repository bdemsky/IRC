package IR.Flat;
import IR.FieldDescriptor;

public class FlatSetElementNode extends FlatNode {
    TempDescriptor src;
    TempDescriptor dst;
    TempDescriptor index;
    
    public FlatSetElementNode(TempDescriptor dst, TempDescriptor index, TempDescriptor src) {
	this.index=index;
	this.src=src;
	this.dst=dst;
    }

    public boolean needsBoundsCheck() {
	return true;
    }

    public TempDescriptor getSrc() {
	return src;
    }

    public TempDescriptor getIndex() {
	return index;
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public String toString() {
	return dst.toString()+"["+index.toString()+"]="+src.toString();
    }

    public int kind() {
	return FKind.FlatSetElementNode;
    }
    
    public TempDescriptor [] readsTemps() {
	return new TempDescriptor [] {src,dst,index};
    }
}
