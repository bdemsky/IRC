package IR;
import IR.Tree.Modifiers;
import IR.Tree.ExpressionNode;
import java.util.Vector;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class MethodDescriptor extends Descriptor {

    protected Modifiers modifier;
    protected TypeDescriptor returntype;
    protected String identifier;
    protected Vector param_name;
    protected Vector param_type;
    protected SymbolTable paramtable;
    
    public MethodDescriptor(Modifiers m, TypeDescriptor rt, String identifier) {
	super(identifier);
	this.modifier=m;
	this.returntype=rt;
	this.identifier=identifier;
        this.safename = "__" + name + "__";
	this.uniqueid=count++;
	param_name=new Vector();
	param_type=new Vector();
	paramtable=new SymbolTable();
    }
    public TypeDescriptor getReturnType() {
	return returntype;
    }

    public SymbolTable getParameterTable() {
	return paramtable;
    }

    public void addParameter(TypeDescriptor type, String paramname) {
	param_name.add(paramname);
	param_type.add(type);
	if (paramtable.getFromSameScope(paramname)!=null) {
	    throw new Error("Parameter "+paramname+" already defined");
	}
	paramtable.add(paramname,type);
    }

    public int numParameters() {
	return param_name.size();
    }

    public String getParamName(int i) {
	return (String) param_name.get(i);
    }

    public TypeDescriptor getParamType(int i) {
	return (TypeDescriptor) param_type.get(i);
    }

    public String toString() {
	String st=modifier.toString()+returntype.toString()+" "+identifier+"(";
	for(int i=0;i<param_type.size();i++) {
	    st+=param_type.get(i).toString()+" "+param_name.get(i).toString();
	    if ((i+1)!=param_type.size())
		st+=", ";
	}
	st+=")";
	return st;
    }
}
