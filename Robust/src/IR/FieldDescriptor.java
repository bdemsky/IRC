package IR;
import IR.Tree.Modifiers;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class FieldDescriptor extends Descriptor {

    protected Modifiers modifier;
    protected TypeDescriptor td;
    
    public FieldDescriptor(Modifiers m, TypeDescriptor t, String name) {
	super(name);
	this.modifier=m;
	this.td=t;
        this.safename = "__" + name + "__";
	this.uniqueid=count++;
    }

    public String toString() {
	return modifier.toString()+";";
    }
}
