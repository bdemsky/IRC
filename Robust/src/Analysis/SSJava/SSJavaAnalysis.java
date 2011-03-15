package Analysis.SSJava;

import java.util.HashSet;

import IR.State;

public class SSJavaAnalysis {

  public static final String DELTA = "delta";
  State state;
  HashSet toanalyze;

  public SSJavaAnalysis(State state) {
    this.state = state;
  }

  public void doCheck() {
    FlowDownCheck checker = new FlowDownCheck(state);
    checker.flowDownCheck();
    // doMoreAnalysis();
  }

}
