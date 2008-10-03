package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a token touple is a pair that indicates a
// heap region node and an arity

// THIS CLASS IS IMMUTABLE!

public class TokenTuple extends Canonical {

  private Integer token;
  private boolean isMultiObject;

  public static final int ARITY_ZEROORMORE = 0;
  public static final int ARITY_ONE        = 1;
  public static final int ARITY_ONEORMORE  = 2;
  private int arity;


  public TokenTuple(HeapRegionNode hrn) {
    assert hrn != null;

    token         = hrn.getID();
    isMultiObject = !hrn.isSingleObject();
    arity         = ARITY_ONE;
  }

  public TokenTuple(Integer token,
                    boolean isMultiObject,
                    int arity) {
    assert token != null;

    this.token         = token;
    this.isMultiObject = isMultiObject;
    this.arity         = arity;
  }


  public TokenTuple makeCanonical() {
    return (TokenTuple) Canonical.makeCanonical(this);
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


  public TokenTuple unionArity(TokenTuple tt) {
    assert tt            != null;
    assert token         == tt.token;
    assert isMultiObject == tt.isMultiObject;

    if( isMultiObject ) {
      // for multiple objects only zero-or-mores combined are still zero-or-more
      // when two tokens are present (absence of a token is arity=zero and is
      // handled outside of this method)
      if( arity == ARITY_ZEROORMORE && tt.arity == ARITY_ZEROORMORE ) {
	return new TokenTuple(token, true, ARITY_ZEROORMORE).makeCanonical();
      } else {
	return new TokenTuple(token, true, ARITY_ONEORMORE).makeCanonical();
      }

    } else {
      // a single object region's token can only have ZEROORMORE or ONE
      if( arity == ARITY_ZEROORMORE && tt.arity == ARITY_ZEROORMORE ) {
	return new TokenTuple(token, false, ARITY_ZEROORMORE).makeCanonical();
      } else {
	return new TokenTuple(token, false, ARITY_ONE).makeCanonical();
      }
    }
  }


  public TokenTuple changeTokenTo(Integer tokenToChangeTo) {
    assert tokenToChangeTo != null;

    return new TokenTuple(tokenToChangeTo,
                          isMultiObject,
                          arity).makeCanonical();
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof TokenTuple) ) {
      return false;
    }

    TokenTuple tt = (TokenTuple) o;

    return token.equals(tt.getToken() ) &&
           arity ==     tt.getArity();
  }

  private boolean oldHashSet = false;
  private int oldHash    = 0;
  public int hashCode() {
    int currentHash = token.intValue()*31 + arity;

    if( oldHashSet == false ) {
      oldHash = currentHash;
      oldHashSet = true;
    } else {
      if( oldHash != currentHash ) {
	System.out.println("IF YOU SEE THIS A CANONICAL TokenTuple CHANGED");
	Integer x = null;
	x.toString();
      }
    }

    return currentHash;
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
