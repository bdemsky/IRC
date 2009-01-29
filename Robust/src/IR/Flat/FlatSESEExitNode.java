package IR.Flat;
import IR.Tree.SESENode;
import java.util.Vector;

public class FlatSESEExitNode extends FlatNode {
  protected SESENode treeNode;
  FlatSESEEnterNode enter;

  public FlatSESEExitNode( SESENode sn ) {
    treeNode = sn;
  }

  public SESENode getTreeNode() {
    return treeNode;
  }

  public void setFlatEnter( FlatSESEEnterNode fsen ) {
    enter = fsen;
  }

  public FlatSESEEnterNode getSESEEnter() {
    return enter;
  }

  public String toString() {
    return "seseexit";
  }

  public int kind() {
    return FKind.FlatSESEExitNode;
  }
}
