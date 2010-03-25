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

// a reach touple is a pair that indicates a
// heap region node and an arity

public class ReachTuple extends Canonical {

  // defined by the source heap region
  protected Integer hrnID;
  protected boolean isMultiObject;

  // arity of reachability paths from the
  // given heap region
  public static final int ARITY_ZEROORMORE = 0;
  public static final int ARITY_ONE        = 1;
  public static final int ARITY_ONEORMORE  = 2;
  protected int arity;

  // whether this represents heap regions out
  // of the current calling context or not
  protected boolean isOutOfContext;


  public static ReachTuple factory( Integer hrnID,
                                    boolean isMultiObject,
                                    int     arity,
                                    boolean ooc ) {
    ReachTuple out = new ReachTuple( hrnID,
                                     isMultiObject,
                                     arity,
                                     ooc );
    out = (ReachTuple) Canonical.makeCanonical( out );
    return out;
  }
  
  public static ReachTuple factory( HeapRegionNode hrn ) {
    ReachTuple out = new ReachTuple( hrn.getID(),
                                     !hrn.isSingleObject(),
                                     ARITY_ONE,
                                     false );
    out = (ReachTuple) Canonical.makeCanonical( out );
    return out;
  }

  protected ReachTuple( Integer hrnID,
                        boolean isMultiObject,
                        int     arity,
                        boolean ooc ) {
    assert hrnID != null;

    this.hrnID          = hrnID;
    this.isMultiObject  = isMultiObject;
    this.arity          = arity;
    this.isOutOfContext = ooc;

    // just make sure this stuff is true now
    // that analysis doesn't use ONEORMORE
    assert arity != ARITY_ONEORMORE;
    if( !isMultiObject ) {
      assert arity == ARITY_ONE;
    }
  }


  public Integer getHrnID() {
    return hrnID;
  }

  public boolean isMultiObject() {
    return isMultiObject;
  }

  public int getArity() {
    return arity;
  }

  public boolean isOutOfContext() {
    return isOutOfContext;
  }


  public boolean equalsSpecific( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachTuple) ) {
      return false;
    }

    ReachTuple rt = (ReachTuple) o;

    return hrnID.equals( rt.hrnID )       &&
      arity          == rt.arity          &&
      isOutOfContext == rt.isOutOfContext;
  }

  public int hashCodeSpecific() {
    int hash = (hrnID.intValue() << 2) ^ arity;
    if( isOutOfContext ) {
      hash = ~hash;
    }
    return hash;
  }


  public String toString() {
    String s = hrnID.toString();

    if( isMultiObject ) {
      s += "M";
    }

    if( isOutOfContext ) {
      s += "?";
    }

    if( arity == ARITY_ZEROORMORE ) {
      s += "*";
    } else if( arity == ARITY_ONEORMORE ) {
      s += "+";
    }

    return s;
  }
}
