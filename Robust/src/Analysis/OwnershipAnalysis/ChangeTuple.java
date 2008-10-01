package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a change touple is a pair that indicates if the
// first TokenTupleSet is found in a ReachabilitySet,
// then the second TokenTupleSet should be added

// THIS CLASS IS IMMUTABLE!

public class ChangeTuple extends Canonical
{
  private TokenTupleSet toMatch;
  private TokenTupleSet toAdd;

  public ChangeTuple(TokenTupleSet toMatch,
                     TokenTupleSet toAdd) {
    this.toMatch = toMatch;
    this.toAdd   = toAdd;
  }

  public ChangeTuple makeCanonical() {
    return (ChangeTuple) Canonical.makeCanonical(this);
  }

  public TokenTupleSet getSetToMatch() {
    return toMatch;
  }
  public TokenTupleSet getSetToAdd() {
    return toAdd;
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ChangeTuple) ) {
      return false;
    }

    ChangeTuple ct = (ChangeTuple) o;

    return toMatch.equals(ct.getSetToMatch() ) &&
           toAdd.equals(ct.getSetToAdd()   );
  }

  private boolean oldHashSet = false;
  private int     oldHash    = 0;
  public int hashCode() {
    int currentHash = toMatch.hashCode() + toAdd.hashCode()*3;

    if( oldHashSet == false ) {
      oldHash = currentHash;
      oldHashSet = true;
    } else {
      if( oldHash != currentHash ) {
	System.out.println( "IF YOU SEE THIS A CANONICAL ChangeTuple CHANGED" );
	Integer x = null;
	x.toString();
      }
    }

    return currentHash;
  }


  public String toString() {
    return new String("("+toMatch+" -> "+toAdd+")");
  }
}
