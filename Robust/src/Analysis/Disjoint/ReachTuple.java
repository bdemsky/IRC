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


  public static ReachTuple factory( Integer hrnID,
                                    boolean isMultiObject,
                                    int     arity ) {
    ReachTuple out = new ReachTuple( hrnID,
                                     isMultiObject,
                                     arity );
    out = (ReachTuple) Canonical.makeCanonical( out );
    return out;
  }
  
  public static ReachTuple factory( HeapRegionNode hrn ) {
    ReachTuple out = new ReachTuple( hrn.getID(),
                                     !hrn.isSingleObject(),
                                     ARITY_ONE );
    out = (ReachTuple) Canonical.makeCanonical( out );
    return out;
  }

  private ReachTuple( Integer hrnID,
                      boolean isMultiObject,
                      int     arity ) {
    assert hrnID != null;

    this.hrnID         = hrnID;
    this.isMultiObject = isMultiObject;
    this.arity         = arity;

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


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachTuple) ) {
      return false;
    }

    ReachTuple rt = (ReachTuple) o;
    assert this.isCanonical();
    assert rt.isCanonical();
    return this == rt;
  }

  public int hashCodeSpecific() {
    return (hrnID.intValue() << 2) ^ arity;
  }


  public String toString() {
    String s = hrnID.toString();

    if( isMultiObject ) {
      s += "M";
    }

    if( arity == ARITY_ZEROORMORE ) {
      s += "*";
    } else if( arity == ARITY_ONEORMORE ) {
      s += "+";
    }

    return s;
  }
}
