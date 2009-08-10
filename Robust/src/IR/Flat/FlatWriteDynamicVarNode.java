package IR.Flat;
import Analysis.MLP.VariableSourceToken;
import java.util.Hashtable;


// This node is inserted by the MLP analysis
// in between a (tail -> head) IR graph edge.
// It is for tracking SESE variables with
// dynamic sources
public class FlatWriteDynamicVarNode extends FlatNode {


  protected FlatNode tailNode;
  protected FlatNode headNode;
  
  protected Hashtable<TempDescriptor, VariableSourceToken> var2src;


  public FlatWriteDynamicVarNode( FlatNode t,
				  FlatNode h,
				  Hashtable<TempDescriptor, VariableSourceToken> v2s
				) {
    tailNode = t;
    headNode = h;
    var2src  = v2s;
  }

  public void spliceIntoIR() {
    tailNode.removeNext( headNode );
    headNode.removePrev( tailNode );
    
    tailNode.addNext( this );
    this.addNext( headNode );
  }

  public void addMoreVar2Src( Hashtable<TempDescriptor, VariableSourceToken> more ) {
    var2src.putAll( more );
  }

  public Hashtable<TempDescriptor, VariableSourceToken> getVar2src() {
    return var2src;
  }

  public String toString() {
    return "writeDynVars "+var2src.keySet();
  }

  public int kind() {
    return FKind.FlatWriteDynamicVarNode;
  }

  public FlatNode clone(TempMap t) {
    return new FlatWriteDynamicVarNode( tailNode, headNode, var2src );
  }
  public void rewriteUse(TempMap t) {
  }
}
