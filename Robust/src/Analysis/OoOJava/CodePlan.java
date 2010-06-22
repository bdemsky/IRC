package Analysis.OoOJava;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a code plan contains information based on analysis results
// for injecting code before and/or after a flat node
public class CodePlan {
    
  private Hashtable< VariableSourceToken, Set<TempDescriptor> > stall2copySet;
  private Set<TempDescriptor>                                   dynamicStallSet;
  private Hashtable<TempDescriptor, TempDescriptor>             dynAssign_lhs2rhs;
  private Set<TempDescriptor>                                   dynAssign_lhs2curr;
  private FlatSESEEnterNode                                     currentSESE;
  
  public CodePlan( FlatSESEEnterNode fsen ) {
    stall2copySet      = new Hashtable< VariableSourceToken, Set<TempDescriptor> >();
    dynamicStallSet    = new HashSet<TempDescriptor>();
    dynAssign_lhs2rhs  = new Hashtable<TempDescriptor, TempDescriptor>();
    dynAssign_lhs2curr = new HashSet<TempDescriptor>();
    currentSESE        = fsen;
  }

  public FlatSESEEnterNode getCurrentSESE() {
    return currentSESE;
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

  public void addDynAssign( TempDescriptor lhs,
			    TempDescriptor rhs ) {
    dynAssign_lhs2rhs.put( lhs, rhs );
  }

  public Hashtable<TempDescriptor, TempDescriptor> getDynAssigns() {
    return dynAssign_lhs2rhs;
  }

  public void addDynAssign( TempDescriptor lhs ) {
    dynAssign_lhs2curr.add( lhs );
  }

  public Set<TempDescriptor> getDynAssignCurr() {
    return dynAssign_lhs2curr;
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

    if( !dynAssign_lhs2rhs.isEmpty() ) {
      s += "[DYN ASSIGNS:"+dynAssign_lhs2rhs+"]";
    }

    if( !dynAssign_lhs2curr.isEmpty() ) {
      s += "[DYN ASS2CURR:"+dynAssign_lhs2curr+"]";
    }

    return s;
  }
}
