package IR.Tree;

public class TreeNode {
  public static final int INDENT=2;
  int numLine=-1;

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

  public void setNumLine(int numLine) {
    this.numLine=numLine;
  }

  public int getNumLine() {
    return this.numLine;
  }

}
