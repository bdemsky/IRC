package IR.Tree;
import java.util.Vector;
import java.util.Hashtable;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;

public class ClassNode extends TreeNode {
    ClassNode() {
	classname=null;
	superclass=null;
	fields=new Vector();
	methods=new Vector();
	methodmap=new Hashtable();
    }
    String classname;
    NameDescriptor superclass;
    Modifiers modifiers;
    Vector fields;
    Vector methods;
    Hashtable methodmap;
    
    public String printNode(int indent) {
	String st=modifiers.toString()+"class "+classname;
	if (superclass!=null) 
	    st+="extends "+superclass.toString();
	st+=" {\n";
	indent+=INDENT;
	for(int i=0;i<fields.size();i++) {
	    FieldDescriptor fd=(FieldDescriptor)fields.get(i);
	    st+=printSpace(indent)+fd.toString()+"\n";
	}
	if (fields.size()>0)
	    st+="\n";

	for(int i=0;i<methods.size();i++) {
	    MethodDescriptor md=(MethodDescriptor)methods.get(i);
	    st+=printSpace(indent)+md.toString()+" ";
	    BlockNode bn=(BlockNode)methodmap.get(md);
	    st+=bn.printNode(indent)+"\n\n";
	}
	st+="}\n";
	return st;
    }

    public void addField(FieldDescriptor fd) {
	fields.add(fd);
    }

    public void addMethod(MethodDescriptor md, BlockNode b) {
	methods.add(md);
	methodmap.put(md,b);
    }
  
    public void setModifiers(Modifiers modifiers) {
	this.modifiers=modifiers;
    }
    void setName(String name) {
	classname=name;
    }
    void setSuper(NameDescriptor superclass) {
	this.superclass=superclass;
    }
}
