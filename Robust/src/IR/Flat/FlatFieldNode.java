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

    public String toString() {
	return dst.toString()+"="+src.toString()+"."+field.toString();
    }
}
