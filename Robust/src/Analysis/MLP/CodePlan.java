package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a code plan contains information based on analysis results
// for injecting code before and/or after a flat node
public class CodePlan {

  private String before;
  private String after;

  public CodePlan( String before,
                   String after ) {
    this.before = before;
    this.after  = after;
  }

  public String getBefore() {
    return before;
  }

  public String getAfter() {
    return after;
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof CodePlan) ) {
      return false;
    }

    CodePlan cp = (CodePlan) o;

    boolean beforeEq;
    if( before == null ) {
      beforeEq = (cp.before == null);
    } else {
      beforeEq = (before.equals( cp.before ));
    }

    boolean afterEq;
    if( after == null ) {
      afterEq = (cp.after == null);
    } else {
      afterEq = (after.equals( cp.after ));
    }
        
    return beforeEq && afterEq;
  }

  public int hashCode() {
    int beforeHC = 1;
    if( before != null  ) {
      beforeHC = before.hashCode();
    }

    int afterHC = 7;
    if( after != null  ) {
      afterHC = after.hashCode();
    }

    return beforeHC ^ afterHC;
  }

  public String toString() {
    return "plan { b="+before+" a="+after+" }";
  }
}
