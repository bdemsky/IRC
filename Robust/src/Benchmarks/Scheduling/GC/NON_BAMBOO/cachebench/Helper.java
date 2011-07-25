public class Helper {
  public Node root;
  
  public Helper(int depth) {
    root = MakeTree(depth);
  }
  
  Node MakeTree(int iDepth) {
    if (iDepth<=0) {
      return new Node();
    } else {
      return new Node(MakeTree(iDepth-1),
          MakeTree(iDepth-1));
    }
  }
}

