package IR.Flat;
import IR.Tree.SESENode;
import java.util.Vector;

public class FlatSESEExitNode extends FlatNode {
  protected SESENode treeNode;
  FlatSESEEnterNode enter;

  public FlatSESEExitNode( SESENode sn ) {
    treeNode = sn;
  }
  public void rewriteUse() {
  }
  public void rewriteDef() {
  }
  public SESENode getTreeNode() {
    return treeNode;
  }

  public void setFlatEnter( FlatSESEEnterNode fsen ) {
    enter = fsen;
  }

  public FlatSESEEnterNode getFlatEnter() {
    return enter;
  }

  public String toString() {
    assert enter != null;
    return "sese "+enter.getPrettyIdentifier()+" exit";
  }

  public int kind() {
    return FKind.FlatSESEExitNode;
  }
}
