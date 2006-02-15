package IR;
import IR.Tree.Modifiers;
import IR.Tree.ExpressionNode;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class VarDescriptor extends Descriptor {

    protected TypeDescriptor td;
    protected String identifier;
    protected ExpressionNode en;
    
    public VarDescriptor(TypeDescriptor t, String identifier, ExpressionNode e) {
	super(identifier);
	this.td=t;
	this.identifier=identifier;
	this.en=e;
        this.safename = "__" + name + "__";
	this.uniqueid=count++;
    }

    public String toString() {
	if (en==null)
	    return td.toString()+" "+identifier;
	else
	    return td.toString()+" "+identifier+"="+en.printNode(0);
    }
}
