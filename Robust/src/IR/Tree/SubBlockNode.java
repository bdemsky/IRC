package IR.Tree;

public class SubBlockNode extends BlockStatementNode {
  BlockNode bn;
  public SubBlockNode(BlockNode bn) {
    this.bn=bn;
  }

  public String printNode(int indent) {
    return bn.printNode(indent);
  }

  public BlockNode getBlockNode() {
    return bn;
  }

  public int kind() {
    return Kind.SubBlockNode;
  }
}
