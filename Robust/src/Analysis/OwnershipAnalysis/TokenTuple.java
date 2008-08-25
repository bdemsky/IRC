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
  private boolean isNewSummary;


  // only summary tokens should have ARITY_MANY?
  // acutally, multiple-object regions can be arity-many
  // so isNewSummary actually means "multi-object" in
  // this class.  CHANGE THIS SOMETIME!
  public static final int ARITY_ONE  = 1;
  public static final int ARITY_MANY = 2;
  private int arity;


  public TokenTuple(HeapRegionNode hrn) {
    assert hrn != null;

    token        = hrn.getID();
    isNewSummary = hrn.isNewSummary();
    arity        = ARITY_ONE;
  }

  public TokenTuple(Integer token,
                    boolean isNewSummary,
                    int arity) {
    assert token != null;

    this.token        = token;
    this.isNewSummary = isNewSummary;
    this.arity        = arity;
  }


  public TokenTuple makeCanonical() {
    return (TokenTuple) Canonical.makeCanonical(this);
  }


  public Integer getToken() {
    return token;
  }
  public int     getArity() {
    return arity;
  }


  public TokenTuple increaseArity() {
    if( isNewSummary ) {
      return (TokenTuple) Canonical.makeCanonical(
               new TokenTuple(token, isNewSummary, ARITY_MANY)
               );
    }
    return this;
  }


  public TokenTuple changeTokenTo(Integer tokenToChangeTo) {
    assert tokenToChangeTo != null;

    return new TokenTuple(tokenToChangeTo,
                          isNewSummary,
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
           arity ==      tt.getArity();
  }

  public int hashCode() {
    return token.intValue()*31 + arity;
  }


  public String toString() {
    String s = token.toString();

    if( isNewSummary ) {
      s += "S";
    }

    if( arity == ARITY_MANY ) {
      s += "*";
    }

    return s;
  }
}
