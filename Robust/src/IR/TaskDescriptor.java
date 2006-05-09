package IR;
import IR.Tree.FlagExpressionNode;
import IR.Tree.FlagEffects;
import java.util.Vector;
import java.util.Hashtable;

/**
 * Descriptor 
 *
 */

public class TaskDescriptor extends Descriptor {

    protected String identifier;
    protected Vector params;
    protected SymbolTable paramtable;
    protected VarDescriptor thisvd;
    protected Hashtable flagstable;
    protected FlagEffects fe;

    public TaskDescriptor(String identifier) {
	super(identifier);
	this.identifier=identifier;
	this.uniqueid=count++;
	flagstable=new Hashtable();
	params=new Vector();
	paramtable=new SymbolTable();
	thisvd=null;
    }

    public void addFlagEffects(FlagEffects fe) {
	this.fe=fe;
    }

    public String getSafeMethodDescriptor() {
	String st="";
	for(int i=0;i<numParameters();i++) {
	    st+=getParamType(i).getSafeDescriptor();
	    if ((i+1)<numParameters())
		st+="_";
	}
	return st;
    }

    public SymbolTable getParameterTable() {
	return paramtable;
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
