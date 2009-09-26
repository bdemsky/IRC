public class TreeT extends Thread {
  Tree[] tt;
  int numTrees;
  int TreeDepth;

  public TreeT() {

  }
  
  public static void main(String[] argv) {
    TreeT t;
    atomic {
      t=global new TreeT();
      t.numTrees=5000;
      t.TreeDepth=10;
    }
	t.start((128<<24)|(195<<16)|(136<<8)|162);
	t.join();
    atomic {
      t.exec();
    }
    System.out.println("Done");
  }

  public void run () {
    atomic {
      tt = global new Tree[numTrees];
      for(int i =0; i<numTrees; i++) {
        tt[i] = global new Tree(TreeDepth); 
        tt[i].Populate(tt[i].root);
      }
    }
  }

  public void exec() {
    Random r = new Random(241);
    for(int i=0; i<numTrees; i++) {
      int choice = r.nextInt(2);
      //System.out.println("choice= " + choice);
      if(choice==0) {
        Node.Inorder1(tt[i].root);
        //tt[i].root.Inorder();
      } else {
        Node.Postorder1(tt[i].root);
        //tt[i].root.Postorder();
      }
    }
  }
}

class Tree {
  /** The tree root. */
  public Node root; 

  int iDepth;

  /**
   * Construct the tree.
   */
  public Tree(int iDepth) {
    root = global new Node(100);
    this.iDepth = iDepth;
  }

  // Build tree top down, assigning to older objects.
  public void Populate(Node n) {
    Random r = new Random(0);
    //pick a random seed
    if (iDepth<=0) {
      return;
    } else {
      iDepth--;
      //n.left = global new Node(r.nextInt(100));
      //n.right = global new Node(r.nextInt(100));
      n.left = global new Node(iDepth);
      n.left.parent = n;
      n.right = global new Node(iDepth+100);
      n.right.parent = n;
      this.Populate(n.left);
      this.Populate(n.right);
    }
  }

  
}

// Basic node stored in unbalanced binary search trees
class Node {
  int element;      // The data in the node
  Node parent;      //parent node
  Node left;         // Left child
  Node right;        // Right child
  int visited;

  // Constructors
  public Node( int data ) {
    element = data;
    parent = left = right = null;
    visited = 0;
  }

  public void Inorder() {
    if(left!=null)
      left.Inorder();
    if(right!=null)
      right.Inorder();
  }

  public static void Inorder1(Node root) {
    Node currNode = root;
    while (true) {
      if (currNode == null)
        break;
      if (currNode.left != null && currNode.left.visited != 1) {
        currNode = currNode.left;
      } else if (currNode.right != null && currNode.right.visited != 1) {
        currNode = currNode.right;
      } else if (currNode.visited == 0) {
        currNode.visited = 1;
      } else {
        currNode = currNode.parent;
      }
    }
  }

  public void Postorder() {
    if(right!=null)
      right.Postorder();
    if(left!=null)
      left.Postorder();
  }

  public static void Postorder1(Node root) {
    Node currNode = root;
    while (true) {
      if (currNode == null)
        break;
      if (currNode.right != null && currNode.right.visited != 1) {
        currNode = currNode.right;
      } else if (currNode.left != null && currNode.left.visited != 1) {
        currNode = currNode.left;
      } else if (currNode.visited == 0) {
        currNode.visited = 1;
      } else {
        currNode = currNode.parent;
      }
    }
  }
}