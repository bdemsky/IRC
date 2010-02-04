package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// A node existence predicate is satisfied if the heap
// region ID defining a node is part of the given graph
// The reach state may be null--if not the predicate is
// satisfied when the edge exists AND it has the state.

public class ExistPredNode extends ExistPred {  

  protected Integer    hrnID;
  protected ReachState state;

  public ExistPredNode( Integer hrnID, ReachState state ) {
    assert hrnID != null;

    this.hrnID = hrnID;
    this.state = state;
  }


  public boolean isSatisfiedBy( ReachGraph rg ) {

    // first find node
    HeapRegionNode hrn = rg.id2hrn.get( hrnID );
    if( hrn == null ) {
      return false;
    }

    // when the state is null it is not part of the
    // predicate, so we've already satisfied
    if( state == null ) {
      return true;
    }

    // otherwise look for state too
    // TODO: contains OR containsSuperSet OR containsWithZeroes??
    return hrn.getAlpha().contains( state );
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ExistPredNode) ) {
      return false;
    }

    ExistPredNode epn = (ExistPredNode) o;

    if( !hrnID.equals( epn.hrnID ) ) {
      return false;
    }

    // ReachState objects are canonical
    return state == epn.state;
  }

  public int hashCode() {
    int hash = hrnID.intValue()*17;

    if( state != null ) {
      hash += state.hashCode();
    }

    return hash;
  }


  public String toString() {
    String s = hrnID.toString();
    if( state != null ) {
      s += "w"+state;
    }
    return s;
  }
}
