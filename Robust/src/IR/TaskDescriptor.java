package IR;
import IR.Tree.FlagExpressionNode;
import IR.Tree.TagExpressionList;
import IR.Tree.FlagEffects;
import java.util.Vector;
import java.util.Hashtable;
import IR.Tree.Modifiers;

/**
 * Descriptor 
 *
 */

public class TaskDescriptor extends Descriptor {

    protected Hashtable flagstable;
    protected Hashtable tagstable;
    protected Vector vfe;
    protected String identifier;
    protected Vector params;
    protected SymbolTable paramtable;

    public TaskDescriptor(String identifier) {
	super(identifier);
	this.identifier=identifier;
	this.uniqueid=count++;
	flagstable=new Hashtable();
	tagstable=new Hashtable(); //BUGFIX - added initialization here
	params=new Vector();
	paramtable=new SymbolTable();
    }

    public void addFlagEffects(Vector vfe) {
	this.vfe=vfe;
    }

    public Vector getFlagEffects() {
	return vfe;
    }

    public SymbolTable getParameterTable() {
	return paramtable;
    }

    public void addParameter(TypeDescriptor type, String paramname, FlagExpressionNode fen, TagExpressionList tel) {
	if (paramname.equals("this"))
	    throw new Error("Can't have parameter named this");
	VarDescriptor vd=new VarDescriptor(type, paramname);
	params.add(vd);
	flagstable.put(vd, fen);
	if (tel!=null) {//BUGFIX - added null check here...test with any bristlecone program
	    tagstable.put(vd, tel);
	    for(int i=0;i<tel.numTags();i++) {
		TagVarDescriptor tvd=new TagVarDescriptor(new TagDescriptor(tel.getType(i)), tel.getName(i));
		if (paramtable.getFromSameScope(tel.getName(i))==null) {
		    paramtable.add(tvd);
		} else if (!(((paramtable.getFromSameScope(tel.getName(i)) instanceof TagVarDescriptor)&&paramtable.getFromSameScope(tel.getName(i))).equals(tvd))) 
		    throw new Error("Parameter "+paramname+" already defined");
	    }
	}
	
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

    public FlagExpressionNode getFlag(VarDescriptor vd) {
	return (FlagExpressionNode) flagstable.get(vd);
    }

    public TagExpressionList getTag(VarDescriptor vd) {
	return (TagExpressionList) flagstable.get(vd);
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
