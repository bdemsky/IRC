package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// A edge existence predicate is satisfied if the elements
// defining an edge are part of the given graph.
// The reach state may be null--if not the predicate is
// satisfied when the edge exists AND it has the state.

public class ExistPredEdge extends Canonical {  

  // the source of an edge is *either* a variable
  // node or a heap region node
  protected TempDescriptor tdSrc;
  protected Integer        hrnSrcID;

  // dst is always a heap region
  protected Integer        hrnDstID;

  // a reference has a field name and type
  protected TypeDescriptor type;
  protected String         field;

  // state can be null
  protected ReachState     state;


  public ExistPredEdge( TempDescriptor tdSrc, 
                        Integer        hrnSrcID, 
                        Integer        hrnDstID,
                        TypeDescriptor type,
                        String         field,
                        ReachState     state ) {

    assert (vnSrc == null) || (hrnSrcID == null);
    assert hrnDstID != null;
    assert type     != null;
    assert field    != null;

    this.tdSrc    = tdSrc;
    this.hrnSrcID = hrnSrcID;
    this.hrnDstID = hrnDstID;
    this.type     = type;
    this.field    = field;
    this.state    = state;
  }

  public boolean isSatisfiedBy( ReachGraph rg ) {

    // first establish whether the source of the
    // reference edge exists
    VariableNode   vnSrc  = rg.td2vn.get( tdSrc );
    HeapRegionNode hrnSrc = rg.id2hrn.get( hrnSrcID );
    assert (vnSrc == null) || (hrnSrc == null);
    
    // the source is not present in graph
    if( vnSrc == null && hrnSrc == null ) {
      return false;
    }

    RefSrcNode rsn;
    if( vnSrc != null ) {
      rsn = vnSrc;
    } else {
      rsn = hrnSrc;
    }

    // is the destination present?
    HeapRegionNode hrnDst = rg.id2hrn.get( hrnDstID );    
    if( hrnDst == null ) {
      return false;
    }
    
    // is there an edge between them with the given
    // type and field?
    // TODO: type OR a subtype?
    RefEdge edge = rsn.getReferenceTo( hrnDst, type, field );
    if( edge == null ) {
      return false;
    }
                                                
    // when state is null it is not part of the predicate
    // so we've satisfied the edge existence
    if( state == null ) {
      return true;
    }

    // otherwise look for state too
    // TODO: contains OR containsSuperSet OR containsWithZeroes??
    return hrnDst.getAlpha().contains( state );
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ExistPredEdge) ) {
      return false;
    }

    ExistPredEdge epn = (ExistPredEdge) o;

    this.tdSrc    = tdSrc;
    this.hrnSrcID = hrnSrcID;
    this.hrnDstID = hrnDstID;
    this.type     = type;
    this.field    = field;
    this.state    = state;

    if( tdSrc == null && epn.tdSrc != null ) {
      return false;
    } else if( !tdSrc.equals( epn.tdSrc ) ) {
      return false;
    }

    if( hrnSrcID == null && epn.hrnSrcID != null ) {
      return false;
    } else if( !hrnSrcID.equals( epn.hrnSrcID ) ) {
      return false;
    }

    if( !hrnDstID.equals( epn.hrnDstID ) ) {
      return false;
    }

    if( !type.equals( epn.type ) ) {
      return false;
    }

    if( !field.equals( epn.field ) ) {
      return false;
    }

    // ReachState objects are cannonical
    return state == epn.state;
  }

  public int hashCode() {    
    int hash = 0;

    hash += type.hashCode()*17;

    if( field != null ) {
      hash += field.hashCode()*7;
    }
    
    if( tdSrc != null ) {
      hash += tdSrc.hashCode()*11;
    } else {
      hash += hrnSrcID.hashCode()*11;
    }

    hash += hrnDst.hashCode();

    if( state != null ) {
      hash += state.hashCode();
    }

    return hash;
  }

  
  public String toString() {
    String s = "(";
    
    if( tdSrc != null ) {
      s += tdSrc.toString();
    } else {
      s += hrnSrcID.toString();
    }

    s += "-->"+hrnDstID+")";

    if( state != null ) {
      s += "w"+state;
    }

    return s;
  }
}
