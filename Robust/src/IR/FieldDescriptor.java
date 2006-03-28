package IR;
import IR.Tree.Modifiers;
import IR.Tree.ExpressionNode;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class FieldDescriptor extends Descriptor {

    protected Modifiers modifier;
    protected TypeDescriptor td;
    protected String identifier;
    protected ExpressionNode en;
    
    public FieldDescriptor(Modifiers m, TypeDescriptor t, String identifier, ExpressionNode e) {
	super(identifier);
	this.modifier=m;
	this.td=t;
	this.identifier=identifier;
	this.en=e;
        this.safename = "___" + name + "___";
	this.uniqueid=count++;
    }

    public TypeDescriptor getType() {
	return td;
    }

    public String toString() {
	if (en==null)
	    return modifier.toString()+td.toString()+" "+identifier+";";
	else
	    return modifier.toString()+td.toString()+" "+identifier+"="+en.printNode(0)+";";
    }
}
