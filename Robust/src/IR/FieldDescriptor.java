package IR;
import IR.Tree.Modifiers;
import IR.Tree.ExpressionNode;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class FieldDescriptor extends Descriptor {

    public static FieldDescriptor arrayLength=new FieldDescriptor(new Modifiers(Modifiers.PUBLIC|Modifiers.FINAL), new TypeDescriptor(TypeDescriptor.INT), "length", null);

    protected Modifiers modifier;
    protected TypeDescriptor td;
    protected String identifier;
    protected ExpressionNode en;
    
    public FieldDescriptor(Modifiers m, TypeDescriptor t, String identifier, ExpressionNode e) {
	super(identifier);
	this.modifier=m;
	this.td=t;
	this.en=e;
        this.safename = "___" + name + "___";
	this.uniqueid=count++;
	if (en!=null) throw new Error("Field initializers not implemented");
    }

    public TypeDescriptor getType() {
	return td;
    }

    public String toString() {
	if (en==null)
	    return modifier.toString()+td.toString()+" "+getSymbol()+";";
	else
	    return modifier.toString()+td.toString()+" "+getSymbol()+"="+en.printNode(0)+";";
    }
}
