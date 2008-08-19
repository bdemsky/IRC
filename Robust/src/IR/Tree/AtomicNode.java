package IR.Tree;

public class AtomicNode extends BlockStatementNode {
  BlockNode bn;
  public AtomicNode(BlockNode bn) {
    this.bn=bn;
  }

  public String printNode(int indent) {
    return printSpace(indent)+"atomic {\n"+bn.printNode(indent)+"\n"+printSpace(indent)+"}";
  }

  public BlockNode getBlockNode() {
    return bn;
  }

  public int kind() {
    return Kind.AtomicNode;
  }
}
