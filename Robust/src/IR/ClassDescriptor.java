package IR;
import java.util.*;
import IR.Tree.*;
import IR.SymbolTable;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;

public class ClassDescriptor extends Descriptor {
    public ClassDescriptor(String classname) {
	super(classname);
	this.classname=classname;
	superclass=null;
	fields=new SymbolTable();
	methods=new SymbolTable();
    }

    String classname;
    String superclass;
    ClassDescriptor superdesc;

    Modifiers modifiers;

    SymbolTable fields;
    SymbolTable methods;

    
    public Iterator getMethods() {
	return methods.getDescriptorsIterator();
    }

    public Iterator getFields() {
	return fields.getDescriptorsIterator();
    }
    
    public SymbolTable getFieldTable() {
	return fields;
    }

    public SymbolTable getMethodTable() {
	return methods;
    }

    public String getSafeDescriptor() {
	return "L"+safename.replace('.','/');
    }

    public String printTree(State state) {
	int indent;
	String st=modifiers.toString()+"class "+classname;
	if (superclass!=null) 
	    st+="extends "+superclass.toString();
	st+=" {\n";
	indent=TreeNode.INDENT;
	boolean printcr=false;

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

    public void setName(String name) {
	classname=name;
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
