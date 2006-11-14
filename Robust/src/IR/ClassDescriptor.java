package IR;
import java.util.*;
import IR.Tree.*;

public class ClassDescriptor extends Descriptor {
    private static int UIDCount=0; 
    private final int classid;
    String superclass;
    ClassDescriptor superdesc;
    boolean hasFlags=false;
    String packagename;

    Modifiers modifiers;

    SymbolTable fields;
    SymbolTable flags;
    SymbolTable methods;

    public ClassDescriptor(String classname) {
	this("", classname);
    }

    public ClassDescriptor(String packagename, String classname) {
	super(classname);
	superclass=null;
	flags=new SymbolTable();
	fields=new SymbolTable();
	methods=new SymbolTable();
	classid=UIDCount++;
	this.packagename=packagename;
    }

    public int getId() {
	return classid;
    }
    
    public Iterator getMethods() {
	return methods.getDescriptorsIterator();
    }

    public Iterator getFields() {
	return fields.getDescriptorsIterator();
    }

    public Iterator getFlags() {
	return flags.getDescriptorsIterator();
    }
    
    public SymbolTable getFieldTable() {
	return fields;
    }

    public SymbolTable getFlagTable() {
	return flags;
    }

    public SymbolTable getMethodTable() {
	return methods;
    }

    public String getSafeDescriptor() {
	return "L"+safename.replace('.','/');
    }

    public String printTree(State state) {
	int indent;
	String st=modifiers.toString()+"class "+getSymbol();
	if (superclass!=null) 
	    st+="extends "+superclass.toString();
	st+=" {\n";
	indent=TreeNode.INDENT;
	boolean printcr=false;

	for(Iterator it=getFlags();it.hasNext();) {
	    FlagDescriptor fd=(FlagDescriptor)it.next();
	    st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
	    printcr=true;
	}
	if (printcr)
	    st+="\n";

	printcr=false;

	for(Iterator it=getFields();it.hasNext();) {
	    FieldDescriptor fd=(FieldDescriptor)it.next();
	    st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
	    printcr=true;
	}
	if (printcr)
	    st+="\n";

	for(Iterator it=getMethods();it.hasNext();) {
	    MethodDescriptor md=(MethodDescriptor)it.next();
	    st+=TreeNode.printSpace(indent)+md.toString()+" ";
	    BlockNode bn=state.getMethodBody(md);
	    st+=bn.printNode(indent)+"\n\n";
	}
	st+="}\n";
	return st;
    }

    public void addFlag(FlagDescriptor fd) {
	if (flags.contains(fd.getSymbol()))
	    throw new Error(fd.getSymbol()+" already defined");
	hasFlags=true;
	flags.add(fd);
    }

    public boolean hasFlags() {
	return hasFlags;
    }

    public void addField(FieldDescriptor fd) {
	if (fields.contains(fd.getSymbol()))
	    throw new Error(fd.getSymbol()+" already defined");
	fields.add(fd);
    }

    public void addMethod(MethodDescriptor md) {
	methods.add(md);
    }
  
    public void setModifiers(Modifiers modifiers) {
	this.modifiers=modifiers;
    }

    public void setSuper(String superclass) {
	this.superclass=superclass;
    }

    public ClassDescriptor getSuperDesc() {
	return superdesc;
    }

    public void setSuper(ClassDescriptor scd) {
	this.superdesc=scd;
    }

    public String getSuper() {
	return superclass;
    }
}
