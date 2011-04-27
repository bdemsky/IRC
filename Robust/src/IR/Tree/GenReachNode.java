package IR.Tree;

public class GenReachNode extends BlockStatementNode {
  String graphName;

  public GenReachNode(String graphName) {
    assert graphName != null;
    this.graphName = graphName;
  }

  public String printNode(int indent) {
    return "genReach "+graphName;
  }

  public String getGraphName() {
    return graphName;
  }

  public int kind() {
    return Kind.GenReachNode;
  }
}
