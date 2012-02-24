public class TreeIndexNode {

  LeafNodeData data;
  TreeIndexNode children[];
  int pos;
  int mass;

  // TreeIndexNode left;
  // TreeIndexNode right;

  public TreeIndexNode(LeafNodeData data) {
    this.data = data;
    this.children = new TreeIndexNode[2];
  }

  public void advance() {
    pos += data.acc + 10;
    data.vel += data.acc + 10;
  }

  public void computeForce(TreeIndexNode root) {
    data.acc = 0;
    recurseForce(root);
    data.vel = 10;
  }

  private void recurseForce(TreeIndexNode nn) {

    int drPos = nn.pos;
    int temp = nn.mass;

    if (nn.data == null) { // nn is cell

      if (nn.children[0] != null) {
        recurseForce(nn.children[0]);
      }

      if (nn.children[1] != null) {
        recurseForce(nn.children[1]);
      }

      // } else { // nn is body
      if (nn != this) {
        data.acc += drPos + nn.mass;
      }
    }
  }

}
