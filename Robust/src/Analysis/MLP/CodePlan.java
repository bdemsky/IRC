package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a code plan contains information based on analysis results
// for injecting code before and/or after a flat node
public class CodePlan {
    
  private Hashtable< VariableSourceToken, Set<TempDescriptor> > stall2copySet;
  private Set<TempDescriptor> dynamicStallSet;

  
  public CodePlan() {
    stall2copySet = new Hashtable< VariableSourceToken, Set<TempDescriptor> >();
    dynamicStallSet = new HashSet<TempDescriptor>();
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


  public void addDynamicStall( TempDescriptor var ) {
    dynamicStallSet.add( var );
  }

  public Set<TempDescriptor> getDynamicStallSet() {
    return dynamicStallSet;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof CodePlan) ) {
      return false;
    }

    CodePlan cp = (CodePlan) o;

    boolean copySetsEq = (stall2copySet.equals( cp.stall2copySet ));

    boolean dynStallSetEq = (dynamicStallSet.equals( cp.dynamicStallSet ));
        
    return copySetsEq && dynStallSetEq;
  }

  public int hashCode() {

    int copySetsHC = stall2copySet.hashCode();

    int dynStallSetHC = dynamicStallSet.hashCode();

    int hash = 7;
    hash = 31*hash + copySetsHC;
    hash = 31*hash + dynStallSetHC;
    return hash;
  }

  public String toString() {
    String s = " PLAN: ";

    if( !stall2copySet.entrySet().isEmpty() ) {
      s += "[STATIC STALLS:";
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

    if( !dynamicStallSet.isEmpty() ) {
      s += "[DYN STALLS:"+dynamicStallSet+"]";
    }

    return s;
  }
}
