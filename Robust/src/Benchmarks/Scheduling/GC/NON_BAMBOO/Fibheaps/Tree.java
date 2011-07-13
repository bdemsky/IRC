// the bionomial class
public class Tree {
  public int root;
  public Vector v_trees;
  
  public Tree() {
    this.root = 0;
    this.v_trees = null;
  }
  
  public Tree(int root,
              Vector trees) {
    this.root = root;
    this.v_trees = trees;
  }

  public Tree link(Tree t) {
    int root = 0;
    Tree tmp = null;
    Vector tmp_v = null;
    if(this.root <= t.root) {
      root = this.root;
      tmp = t;
      tmp_v = this.v_trees;
    } else {
      root = t.root;
      tmp = this;
      tmp_v = t.v_trees;
    }
    Tree nt = new Tree(root, tmp_v);
    if(nt.v_trees == null) {
      nt.v_trees = new Vector();
    }
    nt.v_trees.insertElementAt(tmp, 0);
    return nt;
  }
}

public class TaggedTree {
  public int degree;
  public Tree tree;
  
  public TaggedTree() {
    this.degree = 0;
    this.tree = null;
  }
  
  public TaggedTree(int degree,
                    Tree tree) {
    this.degree = degree;
    this.tree = tree;
  }
  
  public Vector getChildren() {
    Vector rst = new Vector();
    Vector v = tree.v_trees;
    int d = this.degree-1;
    if(v != null) {
      for(int i = 0; i < v.size(); i++) {
        rst.addElement(new TaggedTree(d, (Tree)v.elementAt(i)));
        d--;
      }
    }
    return rst;
  }
}