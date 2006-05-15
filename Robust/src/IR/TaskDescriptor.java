package IR;
import IR.Tree.FlagExpressionNode;
import IR.Tree.FlagEffects;
import java.util.Vector;
import java.util.Hashtable;
import IR.Tree.Modifiers;

/**
 * Descriptor 
 *
 */

public class TaskDescriptor extends MethodDescriptor {

    protected Hashtable flagstable;
    protected Vector vfe;

    public TaskDescriptor(String identifier) {
	super(identifier);
	this.identifier=identifier;
	this.uniqueid=count++;
	flagstable=new Hashtable();
	params=new Vector();
	paramtable=new SymbolTable();
    }

    public void addFlagEffects(Vector vfe) {
	this.vfe=vfe;
    }

    public Vector getFlagEffects() {
	return vfe;
    }

    public Modifiers getModifiers() {
	throw new Error();
    }
    public boolean matches(MethodDescriptor md) {
	throw new Error();
    }
    public void setThis(VarDescriptor vd) {
	throw new Error();
    }
    public VarDescriptor getThis() {
	throw new Error();
    }
    public String getSafeMethodDescriptor() {
	throw new Error();
    }
    public boolean isStatic() {
	throw new Error();
    }
    public boolean isConstructor() {
	throw new Error();
    }
    public TypeDescriptor getReturnType() {
	throw new Error();
    }
    public void setClassDesc(ClassDescriptor cd) {
	throw new Error();
    }
    public ClassDescriptor getClassDesc() {
	throw new Error();
    }

    public void addParameter(TypeDescriptor type, String paramname, FlagExpressionNode fen) {
	if (paramname.equals("this"))
	    throw new Error("Can't have parameter named this");
	VarDescriptor vd=new VarDescriptor(type, paramname);
	params.add(vd);
	flagstable.put(vd, fen);
	if (paramtable.getFromSameScope(paramname)!=null) {
	    throw new Error("Parameter "+paramname+" already defined");
	}
	paramtable.add(vd);
    }

    public FlagExpressionNode getFlag(VarDescriptor vd) {
	return (FlagExpressionNode) flagstable.get(vd);
    }

    public String toString() {
	String st=identifier+"(";
	for(int i=0;i<params.size();i++) {
	    st+=getParamType(i)+" "+getParamName(i);
	    if ((i+1)!=params.size())
		st+=", ";
	}
	st+=")";
	return st;
    }
}
