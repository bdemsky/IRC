package IR.Tree;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.FlatSESEExitNode;

public class SESENode extends BlockStatementNode {

  protected String id;

  protected SESENode start;
  protected SESENode end;

  protected FlatSESEEnterNode enter;
  protected FlatSESEExitNode exit;


  public SESENode(String id) {
    this.id = id;
    start = null;
    end   = null;
    enter = null;
    exit  = null;
  }

  public String getID() {
    return id;
  }

  public void setStart(SESENode n) {
    start = n;
  }

  public void setEnd(SESENode n) {
    end = n;
  }

  public boolean isStart() {
    return end != null;
  }

  public SESENode getStart() {
    return start;
  }

  public SESENode getEnd() {
    return end;
  }

  public void setFlatEnter(FlatSESEEnterNode fsen) {
    enter = fsen;
  }

  public void setFlatExit(FlatSESEExitNode fsexn) {
    exit = fsexn;
  }

  public FlatSESEEnterNode getFlatEnter() {
    return enter;
  }

  public FlatSESEExitNode getFlatExit() {
    return exit;
  }


  public String printNode(int indent) {
    if( isStart() ) {
      return printSpace(indent)+"START SESE\n";
    } else {
      return printSpace(indent)+"END SESE\n";
    }
  }

  public int kind() {
    return Kind.SESENode;
  }
}
