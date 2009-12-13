package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a token touple is a pair that indicates a
// heap region node and an arity

// THIS CLASS IS IMMUTABLE!

public class ReachTuple extends Canonical {

  private Integer token;
  private boolean isMultiObject;

  public static final int ARITY_ZEROORMORE = 0;
  public static final int ARITY_ONE        = 1;
  public static final int ARITY_ONEORMORE  = 2;
  private int arity;


  public ReachTuple(HeapRegionNode hrn) {
    assert hrn != null;

    token         = hrn.getID();
    isMultiObject = !hrn.isSingleObject();
    arity         = ARITY_ONE;
    fixStuff();
  }

  public ReachTuple(Integer token,
                    boolean isMultiObject,
                    int arity) {
    assert token != null;

    this.token         = token;
    this.isMultiObject = isMultiObject;
    this.arity         = arity;
    fixStuff();
  }

  private void fixStuff() {
      //This is an evil hack...we should fix this stuff elsewhere...
      if (!isMultiObject) {
	  arity=ARITY_ONE;
      } else {
	  if (arity==ARITY_ONEORMORE)
	      arity=ARITY_ZEROORMORE;
      }
  }


  public ReachTuple makeCanonical() {
    return (ReachTuple) Canonical.makeCanonical(this);
  }


  public Integer getToken() {
    return token;
  }

  public boolean isMultiObject() {
    return isMultiObject;
  }

  public int getArity() {
    return arity;
  }


  public ReachTuple unionArity(ReachTuple tt) {
    assert tt            != null;
    assert token         == tt.token;
    assert isMultiObject == tt.isMultiObject;

    if( isMultiObject ) {
      // for multiple objects only zero-or-mores combined are still zero-or-more
      // when two tokens are present (absence of a token is arity=zero and is
      // handled outside of this method)
      if( arity == ARITY_ZEROORMORE && tt.arity == ARITY_ZEROORMORE ) {
	return new ReachTuple(token, true, ARITY_ZEROORMORE).makeCanonical();
      } else {
	return new ReachTuple(token, true, ARITY_ONEORMORE).makeCanonical();
      }

    } else {
      // a single object region's token can only have ZEROORMORE or ONE
      if( arity == ARITY_ZEROORMORE && tt.arity == ARITY_ZEROORMORE ) {
	return new ReachTuple(token, false, ARITY_ZEROORMORE).makeCanonical();
      } else {
	return new ReachTuple(token, false, ARITY_ONE).makeCanonical();
      }
    }
  }


  public ReachTuple changeTokenTo(Integer tokenToChangeTo) {
    assert tokenToChangeTo != null;

    return new ReachTuple(tokenToChangeTo,
                          isMultiObject,
                          arity).makeCanonical();
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachTuple) ) {
      return false;
    }

    ReachTuple tt = (ReachTuple) o;

    return token.equals(tt.getToken() ) &&
           arity ==     tt.getArity();
  }

  public int hashCode() {
      return (token.intValue() << 2) ^ arity;
  }


  public String toString() {
    String s = token.toString();

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