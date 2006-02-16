package IR;
import java.util.*;
import IR.Tree.*;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;

public class ClassDescriptor {
    public ClassDescriptor() {
	classname=null;
	superclass=null;
	fields=new Vector();
	methods=new Vector();
    }
    String classname;
    NameDescriptor superclass;
    Modifiers modifiers;
    Vector fields;
    Vector methods;
    
    public Iterator getMethods() {
	return methods.iterator();
    }
    
    public String printTree(State state) {
	int indent;
	String st=modifiers.toString()+"class "+classname;
	if (superclass!=null) 
	    st+="extends "+superclass.toString();
	st+=" {\n";
	indent=TreeNode.INDENT;
	for(int i=0;i<fields.size();i++) {
	    FieldDescriptor fd=(FieldDescriptor)fields.get(i);
	    st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
	}
	if (fields.size()>0)
	    st+="\n";

	for(int i=0;i<methods.size();i++) {
	    MethodDescriptor md=(MethodDescriptor)methods.get(i);
	    st+=TreeNode.printSpace(indent)+md.toString()+" ";
	    BlockNode bn=state.getMethodBody(md);
	    st+=bn.printNode(indent)+"\n\n";
	}
	st+="}\n";
	return st;
    }

    public void addField(FieldDescriptor fd) {
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

    public void setSuper(NameDescriptor superclass) {
	this.superclass=superclass;
    }
}
