package IR.Flat;
import IR.FieldDescriptor;

public class FlatElementNode extends FlatNode {
    TempDescriptor src;
    TempDescriptor dst;
    TempDescriptor index;
    
    public FlatElementNode(TempDescriptor src, TempDescriptor index, TempDescriptor dst) {
	this.index=index;
	this.src=src;
	this.dst=dst;
    }

    public TempDescriptor getIndex() {
	return index;
    }

    public TempDescriptor getSrc() {
	return src;
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public String toString() {
	return dst.toString()+"="+src.toString()+"["+index.toString()+"]";
    }

    public int kind() {
	return FKind.FlatElementNode;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[] {src,index};
    }
}
