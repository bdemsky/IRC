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
    protected Vector params;
    protected SymbolTable paramtable;
    protected ClassDescriptor cd;
    protected VarDescriptor thisvd;


    public MethodDescriptor(Modifiers m, TypeDescriptor rt, String identifier) {
	super(identifier);
	this.modifier=m;
	this.returntype=rt;
	this.identifier=identifier;
        this.safename = "__" + name + "__";
	this.uniqueid=count++;
	params=new Vector();
	paramtable=new SymbolTable();
	thisvd=null;
    }

    public MethodDescriptor(Modifiers m, String identifier) {
	super(identifier);
	this.modifier=m;
	this.returntype=null;
	this.identifier=identifier;
        this.safename = "__" + name + "__";
	this.uniqueid=count++;
	params=new Vector();
	paramtable=new SymbolTable();
	thisvd=null;
    }

    public void setThis(VarDescriptor vd) {
	thisvd=vd;
	paramtable.add(vd);
    }

    public VarDescriptor getThis() {
	return thisvd;
    }

    public boolean isStatic() {
	return modifier.isStatic();
    }

    public boolean isConstructor() {
	return (returntype==null);
    }

    public TypeDescriptor getReturnType() {
	return returntype;
    }

    public void setClassDesc(ClassDescriptor cd) {
	this.cd=cd;
    }

    public ClassDescriptor getClassDesc() {
	return cd;
    }

    public SymbolTable getParameterTable() {
	return paramtable;
    }

    public void addParameter(TypeDescriptor type, String paramname) {
	if (paramname.equals("this"))
	    throw new Error("Can't have parameter named this");
	VarDescriptor vd=new VarDescriptor(type, paramname);

	params.add(vd);
	if (paramtable.getFromSameScope(paramname)!=null) {
	    throw new Error("Parameter "+paramname+" already defined");
	}
	paramtable.add(vd);
    }

    public int numParameters() {
	return params.size();
    }

    public VarDescriptor getParameter(int i) {
	return (VarDescriptor)params.get(i);
    }

    public String getParamName(int i) {
	return ((VarDescriptor)params.get(i)).getName();
    }

    public TypeDescriptor getParamType(int i) {
	return ((VarDescriptor)params.get(i)).getType();
    }

    public String toString() {
	String st="";
	if (returntype!=null)
	    st=modifier.toString()+returntype.toString()+" "+identifier+"(";
	else
	    st=modifier.toString()+" "+identifier+"(";
	for(int i=0;i<params.size();i++) {
	    st+=getParamType(i)+" "+getParamName(i);
	    if ((i+1)!=params.size())
		st+=", ";
	}
	st+=")";
	return st;
    }
}
