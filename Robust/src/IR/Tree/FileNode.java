package IR.Tree;
import java.util.Vector;

class FileNode extends TreeNode {
    private Vector type_decls;

    FileNode() {
	type_decls=new Vector();
    }

    public void addClass(ClassNode tdn) {
	type_decls.add(tdn);
    }

    public String printNode() {
	String st="";
	for(int i=0;i<type_decls.size();i++) {
	    ClassNode cn=(ClassNode) type_decls.get(i);
	    st+=cn.printNode();

	}
	return st;
    }
}
