package IR.Flat;
import IR.TypeDescriptor;

public class FlatGenReachNode extends FlatNode {
  String graphName;

  public FlatGenReachNode(String graphName) {
    this.graphName = graphName;
  }

  public String getGraphName() {
    return graphName;
  }

  public FlatNode clone(TempMap t) {
    return new FlatGenReachNode(graphName);
  }
  public void rewriteUse(TempMap t) {
  }
  public void rewriteDst(TempMap t) {
  }


  public String toString() {
    return "FlatGenReachNode_"+graphName;
  }

  public int kind() {
    return FKind.FlatGenReachNode;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[0];
  }
}
