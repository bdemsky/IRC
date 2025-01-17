package IR.Flat;
import java.util.Vector;

public class FlatCondBranch extends FlatNode {
  TempDescriptor test_cond;
  FlatNode loopEntrance;
  double trueprob=0.5;
  boolean loop=false;

  public FlatCondBranch(TempDescriptor td) {
    test_cond=td;
  }
  public void rewriteDef(TempMap t) {
  }
  public void rewriteUse(TempMap t) {
    test_cond=t.tempMap(test_cond);
  }

  public FlatNode clone(TempMap t) {
    FlatCondBranch fcb=new FlatCondBranch(t.tempMap(test_cond));
    fcb.trueprob=trueprob;
    fcb.loop=loop;
    return fcb;
  }

  public void setLoop() {
    loop=true;
  }

  public boolean isLoopBranch() {
    return loop;
  }
  
  public void setLoopEntrance(FlatNode loopEntrance){
    this.loopEntrance=loopEntrance;
  }
  
  public FlatNode getLoopEntrance(){
    return loopEntrance;
  }

  public void setTrueProb(double p) {
    trueprob=p;
  }

  public double getTrueProb() {
    return trueprob;
  }

  public double getFalseProb() {
    return 1-trueprob;
  }

  public void addTrueNext(FlatNode n) {
    if (next.size()==0)
      next.setSize(1);
    next.setElementAt(n,0);
    n.addPrev(this);
  }

  public void addFalseNext(FlatNode n) {
    next.setSize(2);
    next.setElementAt(n,1);
    n.addPrev(this);
  }

  public TempDescriptor getTest() {
    return test_cond;
  }

  public String toString() {
    return "conditional branch("+test_cond.toString()+")";
  }

  public String toString(String negjump) {
    return "FlatCondBranch_if (!"+test_cond.toString()+") goto "+negjump;
  }

  public void addNext(FlatNode n) {
    throw new Error();
  }

  public int kind() {
    return FKind.FlatCondBranch;
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor[] {test_cond};
  }
}
