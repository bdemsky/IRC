package IR.Tree;
import java.util.Vector;

class BlockNode extends TreeNode {
    Vector blockstatements;
    public BlockNode() {
	blockstatements=new Vector();
    }

    public void addBlockStatement(BlockStatementNode bsn) {
	blockstatements.add(bsn);
    }

    public String printNode() {
	String st="{\n";
	for(int i=0;i<blockstatements.size();i++) {
	    BlockStatementNode bsn=(BlockStatementNode)blockstatements.get(i);
	    st+=bsn.printNode()+"\n";
	}
	st+="}\n";
	return st;
    }
}
