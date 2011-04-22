package Analysis.SSJava;

import java.util.Hashtable;

import IR.State;
import IR.Flat.TempDescriptor;
import IR.Tree.TreeNode;

public class SSJavaAnalysis {

  public static final String DELTA = "delta";
  State state;
  FlowDownCheck flowDownChecker;
  Hashtable<TempDescriptor, Location> td2Loc;

  public SSJavaAnalysis(State state) {
    this.state = state;
    this.td2Loc = new Hashtable<TempDescriptor, Location>();
  }

  public void doCheck() {
    doFlowDownCheck();
    doLoopCheck();
  }

  public void doFlowDownCheck() {
    flowDownChecker = new FlowDownCheck(state);
    flowDownChecker.flowDownCheck();
  }

  public void doLoopCheck() {
    DefinitelyWrittenCheck checker = new DefinitelyWrittenCheck(state);
    checker.definitelyWrittenCheck();
  }

}
