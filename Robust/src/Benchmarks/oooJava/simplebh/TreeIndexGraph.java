public class TreeIndexGraph {

  // HashSet nodes;

  public TreeIndexGraph() {
    // this.nodes = new HashSet();
  }

  public TreeIndexNode createNode(LeafNodeData data) {
    TreeIndexNode treeNode = new TreeIndexNode(data);
    return treeNode;
  }

  public void addNode(TreeIndexNode treeIndexNode) {
    // nodes.add(treeIndexNode);
  }

}
