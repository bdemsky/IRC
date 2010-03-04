package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

///////////////////////////////////////////
//  IMPORTANT
//  This class is an immutable Canonical, so
//
//  0) construct them with a factory pattern
//  to ensure only canonical versions escape
//
//  1) any operation that modifies a Canonical
//  is a static method in the Canonical class
//
//  2) operations that just read this object
//  should be defined here
//
//  3) every Canonical subclass hashCode should
//  throw an error if the hash ever changes
//
///////////////////////////////////////////

// Existence predicates in the callee final-result
// graph are relevant on the caller's callee-reachable
// graph parts.  Any callee result elements with
// predicates not satisfied in the caller are not 
// mapped in the call site transfer function

public class ExistPred extends Canonical {  

  // there are several types of predicates, note that
  // there are not subclasses of the ExistPred class
  // because they must be Canonical, no multiple inheritence

  // types: true, node, edge
  public static final int TYPE_TRUE = 0xa501;
  public static final int TYPE_NODE = 0x02fd;
  public static final int TYPE_EDGE = 0x414b;
  protected int predType;

  // true predicates always evaluate to true  

  // A node existence predicate is satisfied if the heap
  // region ID defining a node is part of the given graph
  // The reach state may be null--if not the predicate is
  // satisfied when the edge exists AND it has the state.
  protected Integer    n_hrnID;
  protected ReachState ne_state;

  // An edge existence predicate is satisfied if the elements
  // defining an edge are part of the given graph.
  // The reach state may be null--if not the predicate is
  // satisfied when the edge exists AND it has the state.
  // the source of an edge is *either* a variable
  // node or a heap region node
  protected TempDescriptor e_tdSrc;
  protected Integer        e_hrnSrcID;

  // dst is always a heap region
  protected Integer        e_hrnDstID;

  // a reference has a field name and type
  protected TypeDescriptor e_type;
  protected String         e_field;

  // edge uses same ReachState ne_state as node type above
  

  // to make the true predicate
  public static ExistPred factory() {
    ExistPred out = new ExistPred();
    out = (ExistPred) Canonical.makeCanonical( out );
    return out;
  }
  
  protected ExistPred() {
    this.predType = TYPE_TRUE;
    ne_state   = null;
    n_hrnID    = null;
    e_tdSrc    = null;
    e_hrnSrcID = null;
    e_hrnDstID = null;
    e_type     = null;
    e_field    = null;
  }

  // node predicates
  public static ExistPred factory( Integer    hrnID, 
                                   ReachState state ) {

    ExistPred out = new ExistPred( hrnID, state );

    out = (ExistPred) Canonical.makeCanonical( out );
    return out;
  }
  
  protected ExistPred( Integer    hrnID, 
                       ReachState state ) {
    assert hrnID != null;
    this.n_hrnID  = hrnID;
    this.ne_state = state;
    this.predType = TYPE_NODE;
    e_tdSrc    = null;
    e_hrnSrcID = null;
    e_hrnDstID = null;
    e_type     = null;
    e_field    = null;
  }

  // edge predicates
  public static ExistPred factory( TempDescriptor tdSrc,   
                                   Integer        hrnSrcID, 
                                   Integer        hrnDstID,
                                   TypeDescriptor type,    
                                   String         field,   
                                   ReachState     state ) {

    ExistPred out = new ExistPred( tdSrc,   
                                   hrnSrcID,
                                   hrnDstID,
                                   type,    
                                   field,   
                                   state );

    out = (ExistPred) Canonical.makeCanonical( out );
    return out;
  }

  protected ExistPred( TempDescriptor tdSrc, 
                       Integer        hrnSrcID, 
                       Integer        hrnDstID,
                       TypeDescriptor type,
                       String         field,
                       ReachState     state ) {
    
    assert (tdSrc == null) || (hrnSrcID == null);
    assert hrnDstID != null;
    assert type     != null;
    
    // fields can be null when the edge is from
    // a variable node to a heap region!
    // assert field    != null;
    
    this.e_tdSrc    = tdSrc;
    this.e_hrnSrcID = hrnSrcID;
    this.e_hrnDstID = hrnDstID;
    this.e_type     = type;
    this.e_field    = field;
    this.ne_state   = state;
    this.predType   = TYPE_EDGE;
    n_hrnID = null;
  }


  // only consider the subest of the caller elements that
  // are reachable by callee when testing predicates
  public boolean isSatisfiedBy( ReachGraph rg,
                                Set<HeapRegionNode> calleeReachableNodes,
                                Set<RefEdge>        calleeReachableEdges   
                                ) {

    if( predType == TYPE_TRUE ) {
      return true;
    }

    if( predType == TYPE_NODE ) {
      // first find node
      HeapRegionNode hrn = rg.id2hrn.get( n_hrnID );
      if( hrn == null ) {
        return false;
      }

      if( !calleeReachableNodes.contains( hrn ) ) {
        return false;
      }

      // when the state is null it is not part of the
      // predicate, so we've already satisfied
      if( ne_state == null ) {
        return true;
      }

      // otherwise look for state too
      // TODO: contains OR containsSuperSet OR containsWithZeroes??
      return hrn.getAlpha().contains( ne_state );
    }
    
    if( predType == TYPE_EDGE ) {

      System.out.println( "    type==edge" );

      // first establish whether the source of the
      // reference edge exists
      VariableNode vnSrc = null;
      if( e_tdSrc != null ) {
        vnSrc = rg.td2vn.get( e_tdSrc );
        System.out.println( "    vnSrc="+vnSrc );
      }
      HeapRegionNode hrnSrc = null;
      if( e_hrnSrcID != null ) {
        hrnSrc = rg.id2hrn.get( e_hrnSrcID );
      }
      assert (vnSrc == null) || (hrnSrc == null);
    
      // the source is not present in graph
      if( vnSrc == null && hrnSrc == null ) {
        return false;
      }

      RefSrcNode rsn;
      if( vnSrc != null ) {
        rsn = vnSrc;
      } else {
        if( !calleeReachableNodes.contains( hrnSrc ) ) {
          return false;
        }
        rsn = hrnSrc;
      }

      // is the destination present?
      HeapRegionNode hrnDst = rg.id2hrn.get( e_hrnDstID );
      if( hrnDst == null ) {
        return false;
      }

      if( !calleeReachableNodes.contains( hrnDst ) ) {
        return false;
      }
        
    
      System.out.println( "    check the edge..." );

      // is there an edge between them with the given
      // type and field?
      // TODO: type OR a subtype?
      RefEdge edge = rsn.getReferenceTo( hrnDst, 
                                         e_type, 
                                         e_field );
      if( edge == null ) {
        System.out.println( "    edge is null!" );
        return false;
      }
                                                
      if( !calleeReachableEdges.contains( edge ) ) {
        System.out.println( "    edge not reachable!" );
        return false;
      }

      // when state is null it is not part of the predicate
      // so we've satisfied the edge existence
      if( ne_state == null ) {
        return true;
      }
      
      // otherwise look for state too
      // TODO: contains OR containsSuperSet OR containsWithZeroes??
      return hrnDst.getAlpha().contains( ne_state );
    }

    throw new Error( "Unknown predicate type" );
  }



  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ExistPred) ) {
      return false;
    }

    ExistPred pred = (ExistPred) o;

    if( this.predType != pred.predType ) {
      return false;
    }

    if( ne_state == null ) {
      if( pred.ne_state != null ) {
        return false;
      }
    } else if( !ne_state.equals( pred.ne_state ) ) {
      return false;
    }
    
    if( n_hrnID == null ) {
      if( pred.n_hrnID != null ) {
        return false;
      }
    } else if( !n_hrnID.equals( pred.n_hrnID ) ) {
      return false;
    }
    
    if( e_tdSrc == null ) {
      if( pred.e_tdSrc != null ) {
        return false;
      }
    } else if( !e_tdSrc.equals( pred.e_tdSrc ) ) {
      return false;
    }

    if( e_hrnSrcID == null ) {
      if( pred.e_hrnSrcID != null ) {
        return false;
      }
    } else if( !e_hrnSrcID.equals( pred.e_hrnSrcID ) ) {
      return false;
    }

    if( e_hrnDstID == null ) {
      if( pred.e_hrnDstID != null ) {
        return false;
      }
    } else if( !e_hrnDstID.equals( pred.e_hrnDstID ) ) {
      return false;
    }
    
    if( e_type == null ) {
      if( pred.e_type != null ) {
        return false;
      }
    } else if( !e_type.equals( pred.e_type ) ) {
      return false;
    }
    
    if( e_field == null ) {
      if( pred.e_field != null ) {
        return false;
      }
    } else if( !e_field.equals( pred.e_field ) ) {
      return false;
    }

    return true;
  }


  public int hashCodeSpecific() {
    if( predType == TYPE_TRUE ) {
      return 1;
    }

    if( predType == TYPE_NODE ) {
      int hash = n_hrnID.intValue()*17;

      if( ne_state != null ) {
        hash += ne_state.hashCode();
      }
      
      return hash;
    }
    
    if( predType == TYPE_EDGE ) {
      int hash = 0; 

      hash += e_type.hashCode()*17;

      if( e_field != null ) {
        hash += e_field.hashCode()*7;
      }
    
      if( e_tdSrc != null ) {
        hash += e_tdSrc.hashCode()*11;
      } else {
        hash += e_hrnSrcID.hashCode()*11;
      }

      hash += e_hrnDstID.hashCode();

      if( ne_state != null ) {
        hash += ne_state.hashCode();
      }

      return hash;
    }

    throw new Error( "Unknown predicate type" );
  }

  
  public String toString() {
    if( predType == TYPE_TRUE ) {
      return "t";
    }

    if( predType == TYPE_NODE ) {
      String s = n_hrnID.toString();
      if( ne_state != null ) {
        s += "w"+ne_state;
      }
      return s;
    }

    if( predType == TYPE_EDGE ) {
      String s = "(";
    
      if( e_tdSrc != null ) {
        s += e_tdSrc.toString();
      } else {
        s += e_hrnSrcID.toString();
      }

      s += "-->"+e_hrnDstID+")";

      if( ne_state != null ) {
        s += "w"+ne_state;
      }

      return s;
    }

    throw new Error( "Unknown predicate type" );
  }
  
}
