package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a code plan contains information based on analysis results
// for injecting code before and/or after a flat node
public class CodePlan {

  private Set<VariableSourceToken> writeToDynamicSrc;
    
  private Hashtable< VariableSourceToken, Set<TempDescriptor> > stall2copySet;

  
  public CodePlan() {
    writeToDynamicSrc = null;

    stall2copySet = new Hashtable< VariableSourceToken, Set<TempDescriptor> >();
  }


  public void setWriteToDynamicSrc( 
		Set<VariableSourceToken> writeToDynamicSrc 
				  ) {
    this.writeToDynamicSrc = writeToDynamicSrc;
  }

  public Set<VariableSourceToken> getWriteToDynamicSrc() {
    return writeToDynamicSrc;
  }  
  
  public void addStall2CopySet( VariableSourceToken stallToken,
				Set<TempDescriptor> copySet ) {

    if( stall2copySet.containsKey( stallToken ) ) {
      Set<TempDescriptor> priorCopySet = stall2copySet.get( stallToken );
      priorCopySet.addAll( copySet );
    } else {
      stall2copySet.put( stallToken, copySet );
    }
  }

  public Set<VariableSourceToken> getStallTokens() {
    return stall2copySet.keySet();
  }

  public Set<TempDescriptor> getCopySet( VariableSourceToken stallToken ) {
    return stall2copySet.get( stallToken );
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

    boolean copySetsEq = (stall2copySet.equals( cp.stall2copySet ));
        
    return dynamicSetEq && copySetsEq;
  }

  public int hashCode() {
    int dynamicSetHC = 1;
    if( writeToDynamicSrc != null  ) {
      dynamicSetHC = writeToDynamicSrc.hashCode();
    }

    int copySetsHC = stall2copySet.hashCode();

    return dynamicSetHC ^ 3*copySetsHC;
  }

  public String toString() {
    String s = " PLAN: ";

    if( writeToDynamicSrc != null ) {
      s += "[WRITE DYN";

      Iterator<VariableSourceToken> vstItr = writeToDynamicSrc.iterator();
      while( vstItr.hasNext() ) {
	VariableSourceToken vst = vstItr.next();
	s += ", "+vst;
      }

      s += "]";
    }

    if( !stall2copySet.entrySet().isEmpty() ) {
      s += "[STALLS:";
    }
    Iterator cpsItr = stall2copySet.entrySet().iterator();
    while( cpsItr.hasNext() ) {
      Map.Entry           me         = (Map.Entry)           cpsItr.next();
      VariableSourceToken stallToken = (VariableSourceToken) me.getKey();
      Set<TempDescriptor> copySet    = (Set<TempDescriptor>) me.getValue();

      s += "("+stallToken+"->"+copySet+")";
    }
    if( !stall2copySet.entrySet().isEmpty() ) {
      s += "]";
    }

    return s;
  }
}
