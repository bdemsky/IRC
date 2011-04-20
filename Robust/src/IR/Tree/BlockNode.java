package IR.Tree;
import java.util.Vector;
import IR.*;

public class BlockNode extends TreeNode {
  Vector blockstatements;
  int printStyle=0;
  protected SymbolTable table;

  public final static int NORMAL=0;
  public final static int NOBRACES=1;
  public final static int EXPRLIST=2;
  
  String label=null;

  public BlockNode() {
    blockstatements=new Vector();
    table=new SymbolTable();
  }

  public SymbolTable getVarTable() {
    return table;
  }

  public void addBlockStatement(BlockStatementNode bsn) {
    blockstatements.add(bsn);
  }

  public void addFirstBlockStatement(BlockStatementNode bsn) {
    blockstatements.insertElementAt(bsn,0);
  }
  
  public void addBlockStatementAt(BlockStatementNode bsn, int i) {
    blockstatements.insertElementAt(bsn,i);
  }

  public void setStyle(int style) {
    printStyle=style;
  }

  public int size() {
    return blockstatements.size();
  }

  public BlockStatementNode get(int i) {
    return (BlockStatementNode) blockstatements.get(i);
  }

  public String printNode(int indent) {
    if (printStyle==NORMAL) {
      String st="{\n";
      for(int i=0; i<blockstatements.size(); i++) {
	BlockStatementNode bsn=(BlockStatementNode)blockstatements.get(i);
	st+=printSpace(indent+INDENT)+bsn.printNode(indent+INDENT);
	if (!((bsn instanceof SubBlockNode)||
	      (bsn instanceof LoopNode)||
	      (bsn instanceof IfStatementNode)))
	  st+=";\n";
	if (bsn instanceof IfStatementNode)
	  st+="\n";
      }
      st+=printSpace(indent)+"}";
      return st;
    } else if (printStyle==NOBRACES) {
      String st="";
      for(int i=0; i<blockstatements.size(); i++) {
	BlockStatementNode bsn=(BlockStatementNode)blockstatements.get(i);
	st+=printSpace(indent)+bsn.printNode(indent);
	if (!((bsn instanceof SubBlockNode)||
	      (bsn instanceof LoopNode)||
	      (bsn instanceof IfStatementNode)))
	  st+=";";
      }
      return st;
    } else if (printStyle==EXPRLIST) {
      String st="";
      for(int i=0; i<blockstatements.size(); i++) {
	BlockStatementNode bsn=(BlockStatementNode)blockstatements.get(i);
	st+=bsn.printNode(0);
	if ((i+1)!=blockstatements.size())
	  st+=", ";
      }
      return st;
    } else throw new Error();
  }

  public int kind() {
    return Kind.BlockNode;
  }
  
  public void setLabel(String l){
    label=l;
  }
  
  public String getLabel(){
    return label;
  }

}
