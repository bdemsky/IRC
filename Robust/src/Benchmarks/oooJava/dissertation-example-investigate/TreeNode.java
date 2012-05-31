public class TreeNode {
  TreeNode left;
  TreeNode right;
  double nodeWeight;
  boolean treeWeightComputed;
  double treeWeight;
  
  /*
  public TreeNode( TreeNode left, TreeNode right, double nodeWeight ) {
    this.left = left;
    this.right = right;
    this.nodeWeight = nodeWeight;
    treeWeightComputed = false;
  }
  */

  public TreeNode( double nodeWeight ) {
    //this( null, null, nodeWeight );
    this.nodeWeight = nodeWeight;
    treeWeightComputed = false;
  }

  /*
  public void setLeft( TreeNode left ) {
    this.left = left;
  }

  public void setRight( TreeNode right ) {
    this.right = right;
  }
  */

  public double computeTreeWeight() {
    if( treeWeightComputed ) {
      return treeWeight;
    }
    treeWeight = nodeWeight;
    if( left != null ) {
      treeWeight += left.computeTreeWeight();
    }
    if( right != null ) {
      treeWeight += right.computeTreeWeight();
    }
    treeWeightComputed = true;
    return treeWeight;
  }

  /*
  public String toString() {
    String s = toString( "", "" );
    if( treeWeightComputed ) {
      s += "tree weight: "+treeWeight;
    }
    return s;
  }

  private String toString( String tab, String prefix ) {
    String s = tab+nodeWeight+" {\n";
    if( left != null ) {
      s += left.toString( tab+"  ", s );
    } else {
      s += tab+"  null\n";
    }
    if( right != null ) {
      s += right.toString( tab+"  ", s );
    } else {
      s += tab+"  null\n";
    }
    return s+tab+"}\n";
  }
  */
}
