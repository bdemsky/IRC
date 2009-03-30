package Analysis.Loops;

import IR.Flat.*;

public class LoopOptimize {
  LoopInvariant loopinv;
  public LoopOptimize(TypeUtil typeutil) {
    loopinv=new LoopInvariant(typeutil);
  }
  public void optimize(FlatMethod fm) {
    loopinv.analyze(fm);
    dooptimize(fm);
  } 
  private void dooptimize(FlatMethod fm) {
  }
  

}
