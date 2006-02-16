package IR.Flat;
import IR.MethodDescriptor;

public class FlatMethod extends FlatNode {
    FlatNode method_entry;
    MethodDescriptor method;

    FlatMethod(MethodDescriptor md, FlatNode entry) {
	method=md;
	method_entry=entry;
    }
    
    public String toString() {
	return method.toString()+"\n"+method_entry.toString();
    }
}
