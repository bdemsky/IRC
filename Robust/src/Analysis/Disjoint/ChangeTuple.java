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

// a change tuple is a pair that indicates if the
// first ReachState is found in a ReachSet,
// then the second ReachState should be added

public class ChangeTuple extends Canonical
{
  protected ReachState toMatch;
  protected ReachState toAdd;

  public static ChangeTuple factory( ReachState toMatch,
                                     ReachState toAdd ) {
    // we don't care about the predicates hanging on
    // change tuple states, so always set them to empty
    // to ensure change tuple sets work out
    ReachState toMatchNoPreds =
      ReachState.factory( toMatch.reachTuples,
                          ExistPredSet.factory()
                          );
    ReachState toAddNoPreds =
      ReachState.factory( toAdd.reachTuples,
                          ExistPredSet.factory()
                          );
    ChangeTuple out = new ChangeTuple( toMatchNoPreds,
                                       toAddNoPreds );
    out = (ChangeTuple) Canonical.makeCanonical( out );
    return out;
  }

  protected ChangeTuple( ReachState toMatch,
                         ReachState toAdd ) {
    this.toMatch = toMatch;
    this.toAdd   = toAdd;
  }

  public ReachState getStateToMatch() {
    return toMatch;
  }
  public ReachState getStateToAdd() {
    return toAdd;
  }
  

  public boolean equalsSpecific( Object o ) {
    if( o == null ) {
      return false;
    }
    
    if( !(o instanceof ChangeTuple) ) {
      return false;
    }

    ChangeTuple ct = (ChangeTuple) o;
    return
      toMatch.equals( ct.toMatch ) &&
      toAdd.equals( ct.toAdd );
  }

  public int hashCodeSpecific() {
    return
      toMatch.hashCode() ^
      toAdd.hashCode()*3;
  }

  public String toString() {
    return new String("("+toMatch+" -> "+toAdd+")");
  }
}
