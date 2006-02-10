package IR.Tree;
import java.util.Vector;
import IR.FieldDescriptor;
import IR.NameDescriptor;

class ClassNode extends TreeNode {
    ClassNode() {
	classname=null;
	superclass=null;
	fields=new Vector();
    }
    String classname;
    NameDescriptor superclass;
    Modifiers modifiers;
    Vector fields;
    
    public String printNode() {
	String st=modifiers.toString()+classname;
	if (superclass!=null) 
	    st+="extends "+superclass.toString();
	st+=" {\n";
	for(int i=0;i<fields.size();i++) {
	    FieldDescriptor fd=(FieldDescriptor)fields.get(i);
	    st+=fd.toString()+"\n";
	}
	st+="}\n";
	return st;
    }

    public void addField(FieldDescriptor fd) {
	fields.add(fd);
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
