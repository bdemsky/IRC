package Analysis.SSJava;

import IR.State;

public class SSJavaAnalysis {

  public static final String DELTA = "delta";
  State state;

  public SSJavaAnalysis(State state) {
    this.state = state;
  }

  public void doFlowDownCheck() {
    FlowDownCheck checker = new FlowDownCheck(state);
    checker.flowDownCheck();
  }

  public void doLoopCheck() {
    DefinitelyWrittenCheck checker = new DefinitelyWrittenCheck(state);
    checker.definitelyWrittenCheck();
  }

}
