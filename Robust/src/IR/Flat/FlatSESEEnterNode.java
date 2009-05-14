package IR.Flat;
import Analysis.MLP.VariableSourceToken;
import IR.Tree.SESENode;
import java.util.*;

public class FlatSESEEnterNode extends FlatNode {
  private static int identifier=0;
  private int id;
  protected FlatSESEExitNode exit;
  protected SESENode treeNode;
  protected FlatSESEEnterNode parent;
  protected Set<FlatSESEEnterNode> children;
  protected Set<TempDescriptor> inVars;
  protected Set<TempDescriptor> outVars;
  protected FlatMethod enclosing;

  public FlatSESEEnterNode( SESENode sn ) {
    this.id  = identifier++;
    treeNode = sn;
    children = new HashSet<FlatSESEEnterNode>();
    inVars   = new HashSet<TempDescriptor>();
    outVars  = new HashSet<TempDescriptor>();
  }

  public void rewriteUse() {
  }

  public void rewriteDef() {
  }

  public void setParent( FlatSESEEnterNode parent ) {
    this.parent = parent;
  }

  public FlatSESEEnterNode getParent() {
    return parent;
  }

  public void addChild( FlatSESEEnterNode child ) {
    children.add( child );
  }

  public Set<FlatSESEEnterNode> getChildren() {
    return children;
  }

  public void addInVar( TempDescriptor td ) {
    inVars.add( td );
  }

  public void addOutVar( TempDescriptor td ) {
    outVars.add( td );
  }

  public void addInVarSet( Set<TempDescriptor> s ) {
    inVars.addAll( s );
  }

  public void addOutVarSet( Set<TempDescriptor> s ) {
    outVars.addAll( s );
  }

  public Set<TempDescriptor> getInVarSet() {
    return inVars;
  }

  Vector<TempDescriptor> vecinVars;
  void buildvarVec() {
    HashSet<TempDescriptor> paramset=new HashSet<TempDescriptor>();
    paramset.addAll(inVars);
    paramset.addAll(outVars);
    vecinVars=new Vector<TempDescriptor>();
    vecinVars.addAll(paramset);
  }

  public TempDescriptor getParameter(int i) {
    if (vecinVars==null) {
      buildvarVec();
    }
    return vecinVars.get(i);
  }

  public int numParameters() {
    if (vecinVars==null) {
      buildvarVec();
    }
    return vecinVars.size();
  }

  public Set<FlatNode> getNodeSet() {
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    tovisit.add(this);
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);
      
      if (fn!=exit) {
	for(int i=0; i<fn.numNext(); i++) {
	  FlatNode nn=fn.getNext(i);
	  if (!visited.contains(nn))
	    tovisit.add(nn);
	}
      }
    }
    return visited;
  }

  public Set<TempDescriptor> getOutVarSet() {
    return outVars;
  }

  public void setEnclosingFlatMeth( FlatMethod fm ) {
    enclosing = fm;
  }

  public FlatMethod getEnclosingFlatMeth() {
    return enclosing;
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
