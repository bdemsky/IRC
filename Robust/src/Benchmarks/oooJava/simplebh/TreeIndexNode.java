public class TreeIndexNode {

  LeafNodeData data;
  TreeIndexNode children[];
  int pos;
  int mass;

  // TreeIndexNode left;
  // TreeIndexNode right;

  public TreeIndexNode(LeafNodeData data) {
    this.data = data;
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

      for(int i=0;i<nn.children.length;i++) {
	recurseForce(nn.children[i]);
      }

      // } else { // nn is body
      if (nn != this) {
        data.acc += drPos + nn.mass;
      }
    }
  }

}
