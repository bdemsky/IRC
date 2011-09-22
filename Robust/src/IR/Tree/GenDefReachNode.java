package IR.Tree;

public class GenDefReachNode extends BlockStatementNode {
  String outputName;

  public GenDefReachNode(String outputName) {
    assert outputName != null;
    this.outputName = outputName;
  }

  public String printNode(int indent) {
    return "genDefReach "+outputName;
  }

  public String getOutputName() {
    return outputName;
  }

  public int kind() {
    return Kind.GenDefReachNode;
  }
}
