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
  protected Integer n_hrnID;

  // the preds on this state are irrelevant, so always strip them off
  // when we store it in this ExistPred to make sure it equals other
  // ExistPred objects with the same ne_state, preds ignored.
  protected ReachState ne_state;

  // An edge existence predicate is satisfied if the elements
  // defining an edge are part of the given graph.
  // The reach state may be null--if not the predicate is
  // satisfied when the edge exists AND it has the state.
  // the source of an edge is *either* a variable
  // node or a heap region node
  protected TempDescriptor e_tdSrc;
  protected Integer e_hrnSrcID;

  // the source of an edge might be out of the callee
  // context but in the caller graph, a normal caller
  // heap region or variable, OR it might be out of the
  // caller context ALSO: an ooc node in the caller
  protected boolean e_srcOutCalleeContext;
  protected boolean e_srcOutCallerContext;

  // dst is always a heap region
  protected Integer e_hrnDstID;

  // a reference has a field name and type
  protected TypeDescriptor e_type;
  protected String e_field;

  // if the taint is non-null then the predicate
  // is true only if the edge exists AND has the
  // taint--ONLY ONE of the ne_state or e_taint
  // may be non-null for an edge predicate, AND
  // like the ne_state above, strip preds off this
  // taint within a pred itself
  protected Taint e_taint;



  // a static debug flag for higher abstraction code
  // to enable debug info at this level
  public static boolean debug = false;


  // to make the true predicate
  public static ExistPred factory() {
    ExistPred out = new ExistPred();
    out = (ExistPred) Canonical.makeCanonical(out);
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
    e_taint    = null;
    e_srcOutCalleeContext = false;
    e_srcOutCallerContext = false;
  }

  // node predicates
  public static ExistPred factory(Integer hrnID,
                                  ReachState state) {

    ExistPred out = new ExistPred(hrnID, state);

    out = (ExistPred) Canonical.makeCanonical(out);
    return out;
  }

  protected ExistPred(Integer hrnID,
                      ReachState state) {
    assert hrnID != null;
    this.n_hrnID  = hrnID;
    this.ne_state = removePreds( state );
    this.predType = TYPE_NODE;
    e_tdSrc    = null;
    e_hrnSrcID = null;
    e_hrnDstID = null;
    e_type     = null;
    e_field    = null;
    e_taint    = null;
    e_srcOutCalleeContext = false;
    e_srcOutCallerContext = false;
  }

  // edge predicates
  public static ExistPred factory(TempDescriptor tdSrc,
                                  Integer hrnSrcID,
                                  Integer hrnDstID,
                                  TypeDescriptor type,
                                  String field,
                                  ReachState state,
                                  Taint taint,
                                  boolean srcOutCalleeContext,
                                  boolean srcOutCallerContext) {

    ExistPred out = new ExistPred(tdSrc,
                                  hrnSrcID,
                                  hrnDstID,
                                  type,
                                  field,
                                  state,
                                  taint,
                                  srcOutCalleeContext,
                                  srcOutCallerContext);

    out = (ExistPred) Canonical.makeCanonical(out);
    return out;
  }

  protected ExistPred(TempDescriptor tdSrc,
                      Integer hrnSrcID,
                      Integer hrnDstID,
                      TypeDescriptor type,
                      String field,
                      ReachState state,
                      Taint taint,
                      boolean srcOutCalleeContext,
                      boolean srcOutCallerContext) {

    assert(tdSrc == null) || (hrnSrcID == null);
    assert hrnDstID != null;
    assert type     != null;
    assert(state == null) || (taint == null);

    // fields can be null when the edge is from
    // a variable node to a heap region!

    this.e_srcOutCalleeContext = srcOutCalleeContext;
    this.e_srcOutCallerContext = srcOutCallerContext;

    assert !(e_srcOutCalleeContext && e_srcOutCallerContext);

    this.e_tdSrc    = tdSrc;
    this.e_hrnSrcID = hrnSrcID;
    this.e_hrnDstID = hrnDstID;
    this.e_type     = type;
    this.e_field    = field;
    this.ne_state   = removePreds( state );
    this.e_taint    = removePreds( taint );
    this.predType   = TYPE_EDGE;
    n_hrnID = null;
  }

  // for node or edge, check inputs
  public static ExistPred factory(Integer hrnID,
                                  TempDescriptor tdSrc,
                                  Integer hrnSrcID,
                                  Integer hrnDstID,
                                  TypeDescriptor type,
                                  String field,
                                  ReachState state,
                                  Taint taint,
                                  boolean srcOutCalleeContext,
                                  boolean srcOutCallerContext) {
    ExistPred out;

    if( hrnID != null ) {
      out = new ExistPred(hrnID, state);

    } else {
      out = new ExistPred(tdSrc,
                          hrnSrcID,
                          hrnDstID,
                          type,
                          field,
                          state,
                          taint,
                          srcOutCalleeContext,
                          srcOutCallerContext);
    }

    out = (ExistPred) Canonical.makeCanonical(out);
    return out;
  }



  private ReachState removePreds( ReachState state ) {
    return state == null ? null : Canonical.attach( state, ExistPredSet.factory() );
  }

  private Taint removePreds( Taint taint ) {
    return taint == null ? null : Canonical.attach( taint, ExistPredSet.factory() );
  }



  // only consider the subest of the caller elements that
  // are reachable by callee when testing predicates--if THIS
  // predicate is satisfied, return the predicate set of the
  // element that satisfied it, or null for false
  public ExistPredSet isSatisfiedBy(ReachGraph rg,
                                    Set<Integer> calleeReachableNodes,
                                    Set<RefSrcNode> callerSrcMatches
                                    ) {
    if( predType == TYPE_TRUE ) {
      return ExistPredSet.factory(ExistPred.factory() );
    }

    if( predType == TYPE_NODE ) {
      // first find node
      HeapRegionNode hrn = rg.id2hrn.get(n_hrnID);
      if( hrn == null ) {
        return null;
      }

      if( !calleeReachableNodes.contains(n_hrnID) ) {
        return null;
      }

      // when the state is null we're done!
      if( ne_state == null ) {
        return hrn.getPreds();

      } else {
        // otherwise look for state too

        // TODO: contains OR containsSuperSet OR containsWithZeroes??
        ReachState stateCaller = hrn.getAlpha().containsIgnorePreds(ne_state);

        if( stateCaller == null ) {
          return null;

        } else {
          // it was here, return the predicates on the state!!
          return stateCaller.getPreds();
        }
      }

      // unreachable program point!
    }

    if( predType == TYPE_EDGE ) {

      // first establish whether the source of the
      // reference edge exists
      VariableNode vnSrc = null;
      if( e_tdSrc != null ) {
        vnSrc = rg.td2vn.get(e_tdSrc);
      }
      HeapRegionNode hrnSrc = null;
      if( e_hrnSrcID != null ) {
        hrnSrc = rg.id2hrn.get(e_hrnSrcID);
      }
      assert(vnSrc == null) || (hrnSrc == null);

      // the source is not present in graph
      if( vnSrc == null && hrnSrc == null ) {
        return null;
      }

      RefSrcNode rsn;
      if( vnSrc != null ) {
        rsn = vnSrc;
        assert e_srcOutCalleeContext;
        assert !e_srcOutCallerContext;

      } else {

        assert !(e_srcOutCalleeContext && e_srcOutCallerContext);

        if( e_srcOutCalleeContext ) {
          if( calleeReachableNodes.contains(e_hrnSrcID) ) {
            return null;
          }

        } else if( e_srcOutCallerContext ) {
          if( !hrnSrc.isOutOfContext() ) {
            return null;
          }

        } else {

          if( !calleeReachableNodes.contains(e_hrnSrcID) ) {
            return null;
          }
          if( hrnSrc.isOutOfContext() ) {
            return null;
          }

        }

        rsn = hrnSrc;
      }

      // is the destination present?
      HeapRegionNode hrnDst = rg.id2hrn.get(e_hrnDstID);
      if( hrnDst == null ) {
        return null;
      }

      if( !calleeReachableNodes.contains(e_hrnDstID) ) {
        return null;
      }

      // is there an edge between them with the given
      // type and field?
      // TODO: type OR a subtype?
      RefEdge edge = rsn.getReferenceTo(hrnDst,
                                        e_type,
                                        e_field);
      if( edge == null ) {
        return null;
      }

      // when the state and taint are null we're done!
      if( ne_state == null &&
          e_taint  == null ) {

        if( callerSrcMatches != null ) {
          callerSrcMatches.add( rsn );
        }

        return edge.getPreds();


      } else if( ne_state != null ) {
        // otherwise look for state too

        // TODO: contains OR containsSuperSet OR containsWithZeroes??
        ReachState stateCaller = edge.getBeta().containsIgnorePreds(ne_state);

        if( stateCaller == null ) {
          return null;

        } else {
          // it was here, return the predicates on the state!!
          return stateCaller.getPreds();
        }

      } else {
        // otherwise look for taint

        Taint tCaller = edge.getTaints().containsIgnorePreds(e_taint);

        if( tCaller == null ) {
          return null;

        } else {
          // it was here, return the predicates on the taint!!
          if( callerSrcMatches != null ) {
            callerSrcMatches.add( rsn );
          }

          return tCaller.getPreds();
        }
      }

      // unreachable program point!
    }

    throw new Error("Unknown predicate type");
  }



  // if this pred is an edge type pred, then given
  // a reach graph, find the element of the graph that
  // may exist and represents the source of the edge
  public RefSrcNode getEdgeSource( ReachGraph rg ) {
    if( predType != TYPE_EDGE ) {
      return null;
    }

    if( e_tdSrc != null ) {
      // variable node is the source, look for it in the graph
      return rg.getVariableNodeNoMutation( e_tdSrc );
    }

    // otherwise a heap region node is the source, look for it
    assert e_hrnDstID != null;
    return rg.id2hrn.get( e_hrnDstID );
  }



  public boolean equalsSpecific(Object o) {
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
    } else if( !ne_state.equalsIgnorePreds(pred.ne_state) ) {
      return false;
    }

    if( n_hrnID == null ) {
      if( pred.n_hrnID != null ) {
        return false;
      }
    } else if( !n_hrnID.equals(pred.n_hrnID) ) {
      return false;
    }

    if( e_tdSrc == null ) {
      if( pred.e_tdSrc != null ) {
        return false;
      }
    } else if( !e_tdSrc.equals(pred.e_tdSrc) ) {
      return false;
    }

    if( e_hrnSrcID == null ) {
      if( pred.e_hrnSrcID != null ) {
        return false;
      }
    } else {
      if( !e_hrnSrcID.equals(pred.e_hrnSrcID) ) {
        return false;
      }
      if( e_srcOutCalleeContext != pred.e_srcOutCalleeContext ) {
        return false;
      }
      if( e_srcOutCallerContext != pred.e_srcOutCallerContext ) {
        return false;
      }
    }

    if( e_hrnDstID == null ) {
      if( pred.e_hrnDstID != null ) {
        return false;
      }
    } else if( !e_hrnDstID.equals(pred.e_hrnDstID) ) {
      return false;
    }

    if( e_type == null ) {
      if( pred.e_type != null ) {
        return false;
      }
    } else if( !e_type.equals(pred.e_type) ) {
      return false;
    }

    if( e_field == null ) {
      if( pred.e_field != null ) {
        return false;
      }
    } else if( !e_field.equals(pred.e_field) ) {
      return false;
    }

    if( e_taint == null ) {
      if( pred.e_taint != null ) {
        return false;
      }
    } else if( !e_taint.equalsIgnorePreds(pred.e_taint) ) {
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
        hash ^= ne_state.hashCodeNoPreds();
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
        hash ^= e_tdSrc.hashCode()*11;
      } else {
        hash ^= e_hrnSrcID.hashCode()*11;
        if( e_srcOutCalleeContext ) {
          hash ^= 0x01;
        }
        if( e_srcOutCallerContext ) {
          hash ^= 0x80;
        }
      }

      hash += e_hrnDstID.hashCode();

      if( ne_state != null ) {
        hash ^= ne_state.hashCodeNoPreds();
      }

      if( e_taint != null ) {
        hash ^= e_taint.hashCodeNoPreds();
      }

      return hash;
    }

    throw new Error("Unknown predicate type");
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

      if( e_srcOutCalleeContext ) {
        s += "(ooCLEEc)";
      }

      if( e_srcOutCallerContext ) {
        s += "(ooCLERc)";
      }

      s += "-->"+e_hrnDstID+")";

      if( ne_state != null ) {
        s += "w"+ne_state;
      }

      if( e_taint != null ) {
        s += "w"+e_taint;
      }

      return s;
    }

    throw new Error("Unknown predicate type");
  }

}
