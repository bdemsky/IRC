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

// a set of existence predicates that are
// OR'ed terms, if any predicate is true
// then the set evaluates to true

public class ExistPredSet extends Canonical {

  protected Set<ExistPred> preds;

  
  public static ExistPredSet factory() {
    ExistPredSet out = new ExistPredSet();
    out = (ExistPredSet) Canonical.makeCanonical( out );
    return out;
  }

  public static ExistPredSet factory( ExistPred pred ) {
    ExistPredSet out = new ExistPredSet();
    out.preds.add( pred );
    out = (ExistPredSet) Canonical.makeCanonical( out );
    return out;
  }

  protected ExistPredSet() {
    preds = new HashSet<ExistPred>();
  }


  public boolean isSatisfiedBy( ReachGraph rg ) {
    Iterator<ExistPred> predItr = preds.iterator();
    while( predItr.hasNext() ) {
      if( predItr.next().isSatisfiedBy( rg ) ) {
        return true;
      }
    }
    return false;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ExistPredSet) ) {
      return false;
    }

    ExistPredSet eps = (ExistPredSet) o;

    return preds.equals( eps.preds );
  }


  public int hashCodeSpecific() {
    return preds.hashCode();
  }

  
  public String toString() {
    String s = "P[";
    Iterator<ExistPred> predItr = preds.iterator();
    while( predItr.hasNext() ) {
      ExistPred pred = predItr.next();
      s += pred.toString();
      if( predItr.hasNext() ) {
        s += " || ";
      }
    }
    s += "]";
    return s;
  }

}
