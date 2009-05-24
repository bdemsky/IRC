package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a code plan contains information based on analysis results
// for injecting code before and/or after a flat node
public class CodePlan {

  private FlatSESEEnterNode seseToIssue;


  public CodePlan() {
    seseToIssue = null;
  }


  public void setSESEtoIssue( FlatSESEEnterNode sese ) {
    seseToIssue = sese;
  }

  public FlatSESEEnterNode getSESEtoIssue() {
    return seseToIssue;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof CodePlan) ) {
      return false;
    }

    CodePlan cp = (CodePlan) o;

    boolean issueEq;
    if( seseToIssue == null ) {
      issueEq = (cp.seseToIssue == null);
    } else {
      issueEq = (seseToIssue.equals( cp.seseToIssue ));
    }
        
    return issueEq;
  }

  public int hashCode() {
    int issueHC = 1;
    if( seseToIssue != null  ) {
      issueHC = seseToIssue.hashCode();
    }

    return issueHC;
  }

  public String toString() {
    String s = "";

    if( seseToIssue != null ) {
      s += "[ISSUE "+seseToIssue.getPrettyIdentifier()+"]";
    }

    return s;
  }
}
