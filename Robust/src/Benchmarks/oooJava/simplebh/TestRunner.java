public class TestRunner {

  public static void main(String args[]) {

    TestRunner r = new TestRunner();
    r.run();

  }

  public void run() {

    int nBodies = 50;

    Vector   leafNodeVector=new Vector(nBodies);

//    // body setup
//    for (int i = 0; i < nBodies; i++) {
//      leafNodeVector.insertElementAt(new LeafNodeData(), i);
//    }

    // tree setup
    TreeIndexGraph tree = new TreeIndexGraph();
    TreeIndexNode root = tree.createNode(null);
    tree.addNode(root);

    for (int i = 0; i < nBodies; i++) {
      LeafNodeData leaf=new LeafNodeData();
      TreeIndexNode treenode = tree.createNode(leaf);
      leafNodeVector.insertElementAt(treenode, i);
      insert(tree, root,leaf);
    }
    
      for(int i=0; i<nBodies;i++){
        TreeIndexNode body =(TreeIndexNode)  leafNodeVector.elementAt(i);
        sese par{
//          body.computeForce(root);
          body.computeForce(root);
        }
      }
    
    for(int i=0; i<nBodies;i++){
      ((TreeIndexNode)leafNodeVector.elementAt(i)).advance();
    }
    
  }

  private void insert(TreeIndexGraph tree, TreeIndexNode nn, LeafNodeData leafNodeData) {
    if (nn.children[0] == null) {
      TreeIndexNode newNode = tree.createNode(leafNodeData);
      tree.addNode(newNode);
      nn.children[0] = newNode;
    } else { // if left is already occupied
      if (nn.children[1] != null) {
        insert(tree, nn.children[1], leafNodeData);
      } else {
        TreeIndexNode newNode = tree.createNode(null);
        tree.addNode(newNode);
        nn.children[1] = newNode;
        insert(tree, newNode, leafNodeData);
      }
    }
  }
}

// class Body {
// int pos;
// int acc;
// TreeNode root;
//
// public void setRoot(TreeNode r) {
// this.root = r;
// }
//
// public void computeForce() {
// traverse(root);
// }
//
// public void traverse(TreeNode node) {
// if (node.body != null) {
// // if current one is a body
// acc += node.body.pos;
// } else {
// traverse(node.left);
// traverse(node.right);
// }
// }
// }
//
// class TreeNode {
//
// TreeNode left;
// TreeNode right;
// TreeNode root;
// Body body;
//
// public TreeNode(Body b) {
// this.body = b;
// }
//
// public void addLeftChild(TreeNode n) {
// left = n;
// }
//
// public void addRightChild(TreeNode n) {
// right = n;
// }
//
// public TreeNode getLeftChild() {
// return left;
// }
//
// public TreeNode getRightChild() {
// return right;
// }

