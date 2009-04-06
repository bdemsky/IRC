package IR.Flat;
import IR.Tree.SESENode;
import java.util.HashSet;

public class FlatSESEEnterNode extends FlatNode {
  private static int identifier=0;
  private int id;
  protected FlatSESEExitNode exit;
  protected SESENode treeNode;
  protected HashSet<TempDescriptor> inVars;
  protected HashSet<TempDescriptor> outVars;

  public FlatSESEEnterNode( SESENode sn ) {
    this.id  = identifier++;
    treeNode = sn;
    inVars   = new HashSet<TempDescriptor>();
    outVars  = new HashSet<TempDescriptor>();
  }

  public void rewriteUse() {
  }

  public void rewriteDef() {
  }

  public void addInVar( TempDescriptor td ) {
    inVars.add( td );
  }

  public void addOutVar( TempDescriptor td ) {
    outVars.add( td );
  }

  public void addInVarSet( HashSet<TempDescriptor> s ) {
    inVars.addAll( s );
  }

  public void addOutVarSet( HashSet<TempDescriptor> s ) {
    outVars.addAll( s );
  }

  public HashSet<TempDescriptor> getInVarSet() {
    return inVars;
  }

  public HashSet<TempDescriptor> getOutVarSet() {
    return outVars;
  }

  public SESENode getTreeNode() {
    return treeNode;
  }

  public int getIdentifier() {
    return id;
  }

  public String getPrettyIdentifier() {    
    if( treeNode.getID() != null ) {
      return treeNode.getID();
    }     
    return ""+id;
  }

  public String toString() {
    return "sese "+getPrettyIdentifier()+" enter";
  }

  public void setFlatExit( FlatSESEExitNode fsexn ) {
    exit = fsexn;
  }

  public FlatSESEExitNode getFlatExit() {
    return exit;
  }

  public int kind() {
    return FKind.FlatSESEEnterNode;
  }
}
