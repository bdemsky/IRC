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

// a reach state is a set of reach tuples
// such that any heap region ID in a tuple
// appears at most once in the state

public class ReachState extends Canonical {

  protected HashSet<ReachTuple> reachTuples;

  // existance predicates must be true in a caller
  // context for this state to transfer from this
  // callee to that context
  protected ExistPredSet preds;


  public static ReachState factory() {
    ReachState out = new ReachState();
    out = (ReachState) Canonical.makeCanonical( out );
    return out;
  }

  public static ReachState factory( ReachTuple rt ) {
    assert rt != null;
    assert rt.isCanonical();
    ReachState out = new ReachState();    
    out.reachTuples.add( rt );
    out = (ReachState) Canonical.makeCanonical( out );
    return out;
  }

  public static ReachState factory( HashSet<ReachTuple> reachTuples,
                                    ExistPredSet        preds
                                    ) {
    assert reachTuples != null;
    assert preds != null;
    assert preds.isCanonical();
    ReachState out = new ReachState();
    out.reachTuples.addAll( reachTuples );
    out.preds = preds;
    out = (ReachState) Canonical.makeCanonical( out );
    return out;
  }

  protected ReachState() {
    reachTuples = new HashSet<ReachTuple>();
    preds       = ExistPredSet.factory();
  }


  public Iterator iterator() {
    return reachTuples.iterator();
  }

  public boolean isEmpty() {
    return reachTuples.isEmpty();
  }

  public boolean isSubset( ReachState rsIn ) {
    assert rsIn != null;
    return rsIn.reachTuples.containsAll( this.reachTuples );
  }

  public boolean containsTuple( ReachTuple rt ) {
    assert rt != null;
    return reachTuples.contains( rt );
  }

  // this should be a hash table so we can do this by key
  public ReachTuple containsHrnID( Integer hrnID,
                                   boolean isOutOfContext ) {
    assert hrnID != null;

    Iterator<ReachTuple> rtItr = reachTuples.iterator();
    while( rtItr.hasNext() ) {
      ReachTuple rt = rtItr.next();
      if( hrnID.equals( rt.getHrnID() ) &&
          isOutOfContext == rt.isOutOfContext()
          ) {
	return rt;
      }
    }
    
    return null;
  }

  public ExistPredSet getPreds() {
    return preds;
  }


  public boolean equalsSpecific( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachState) ) {
      return false;
    }

    ReachState rs = (ReachState) o;
    return 
      reachTuples.equals( rs.reachTuples ) &&
      preds.equals( rs.preds );
  }


  public int hashCodeSpecific() {
    return 
      reachTuples.hashCode() ^ 
      preds.hashCode();
  }


  public boolean equalsIgnorePreds( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachState) ) {
      return false;
    }

    ReachState rs = (ReachState) o;
    return 
      reachTuples.equals( rs.reachTuples );
  }



  public String toString() {
    return reachTuples.toString();
    //return reachTuples+":"+preds;
  }

  public String toStringPreds() {
    return reachTuples+":"+preds;
  }
}
