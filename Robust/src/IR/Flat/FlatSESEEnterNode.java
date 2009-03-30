package IR.Flat;
import IR.Tree.SESENode;
import java.util.Vector;

public class FlatSESEEnterNode extends FlatNode {
  private static int identifier=0;
  private int id;
  protected FlatSESEExitNode exit;
  protected SESENode treeNode;

  public FlatSESEEnterNode( SESENode sn ) {
    this.id=identifier++;
    treeNode = sn;
  }

  public SESENode getTreeNode() {
    return treeNode;
  }

  public int getIdentifier() {
    return id;
  }

  public String toString() {
    return "sese "+id+" enter";
  }

  public void setFlatExit( FlatSESEExitNode fsexn ) {
    exit = fsexn;
  }

  public FlatSESEExitNode getFlatExit() {
    return exit;
  }

  public int kind() {
    return FKind.FlatSESEEnterNode;
  }
}
