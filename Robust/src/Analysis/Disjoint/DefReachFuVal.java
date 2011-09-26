package Analysis.Disjoint;

import java.util.*;

import IR.*;
import IR.Flat.*;
import Util.*;

////////////////////////////////////////////
//
//  This is just a logical union of the set
//  of temp descriptors in a program and the
//  value "unknown" to support the Fu map
//  implemented in DefiniteReachState.
//
////////////////////////////////////////////
public class DefReachFuVal {
  
  // When v == null, that means "unknown"
  // Note, a DefReachFuVal composite itself
  // can be null in the analysis state, which
  // means yet a different thing (the analysis
  // has not run for that pp/var yet).
  TempDescriptor v;

  // use this instead of null so callers of this
  // code see the meaning
  public enum Val {
    UNKNOWN,
  }


  public static DefReachFuVal factory( Val unknown ) {
    return new DefReachFuVal( null );
  }

  public static DefReachFuVal factory( TempDescriptor v ) {
    return new DefReachFuVal( v );
  }


  private DefReachFuVal( TempDescriptor v ) {
    this.v = v;
  }


  public boolean isUnknown() {
    return v == null;
  }

  public TempDescriptor getVar() {
    assert( v != null );
    return v;
  }

  
  public boolean equals( Object o ) {
    if( this == o ) {
      return true;
    }
    if( o == null ) {
      return false;
    }
    if( !(o instanceof DefReachFuVal) ) {
      return false;
    }
    DefReachFuVal that = (DefReachFuVal) o;
    if( this.v == null ) {
      return that.v == null;
    }
    return this.v.equals( that.v );
  }


  public int hashCode() {
    if( v == null ) {
      return 1;
    }
    return v.hashCode();
  }


  public String toString() {
    if( v == null ) {
      return "UNKNOWN";
    }
    return v.toString();
  }
}
