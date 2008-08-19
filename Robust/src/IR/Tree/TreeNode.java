package IR.Tree;

public class TreeNode {
  public static final int INDENT=2;

  public String printNode(int indent) {
    return null;
  }
  public static String printSpace(int x) {
    String sp="";
    for(int i=0; i<x; i++)
      sp+=" ";
    return sp;
  }
  public int kind() {
    throw new Error();
  }
}
