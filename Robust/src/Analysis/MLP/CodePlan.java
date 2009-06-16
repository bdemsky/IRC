package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a code plan contains information based on analysis results
// for injecting code before and/or after a flat node
public class CodePlan {

  private Set<VariableSourceToken> writeToDynamicSrc;
  
  public CodePlan() {
    writeToDynamicSrc = null;
  }

  public void setWriteToDynamicSrc( 
		Set<VariableSourceToken> writeToDynamicSrc 
				  ) {
    this.writeToDynamicSrc = writeToDynamicSrc;
  }

  public Set<VariableSourceToken> getWriteToDynamicSrc() {
    return writeToDynamicSrc;
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof CodePlan) ) {
      return false;
    }

    CodePlan cp = (CodePlan) o;

    boolean dynamicSetEq;
    if( writeToDynamicSrc == null ) {
      dynamicSetEq = (cp.writeToDynamicSrc == null);
    } else {
      dynamicSetEq = (writeToDynamicSrc.equals( cp.writeToDynamicSrc ));
    }
        
    return dynamicSetEq;
  }

  public int hashCode() {
    int dynamicSetHC = 1;
    if( writeToDynamicSrc != null  ) {
      dynamicSetHC = writeToDynamicSrc.hashCode();
    }

    return dynamicSetHC;
  }

  public String toString() {
    String s = "";

    if( writeToDynamicSrc != null ) {
      s += "[WRITE DYN";

      Iterator<VariableSourceToken> vstItr = writeToDynamicSrc.iterator();
      while( vstItr.hasNext() ) {
	VariableSourceToken vst = vstItr.next();
	s += ", "+vst;
      }

      s += "]";
    }

    return s;
  }
}
