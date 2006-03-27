package IR.Flat;
import IR.FieldDescriptor;

public class FlatFieldNode extends FlatNode {
    TempDescriptor src;
    TempDescriptor dst;
    FieldDescriptor field;
    
    public FlatFieldNode(FieldDescriptor field, TempDescriptor src, TempDescriptor dst) {
	this.field=field;
	this.src=src;
	this.dst=dst;
    }

    public FieldDescriptor getField() {
	return field;
    }

    public TempDescriptor getSrc() {
	return src;
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public String toString() {
	return dst.toString()+"="+src.toString()+"."+field.getSymbol();
    }

    public int kind() {
	return FKind.FlatFieldNode;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[] {src};
    }
}
