package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// a set of existence predicates that are
// OR'ed terms, if any predicate is true
// then the set evaluates to true

public class ExistPredSet extends Canonical {

  protected Set<ExistPred> preds;

  public ExistPredSet() {
    preds = new HashSet<ExistPred>();
  }

  public ExistPredSet makeCanonical() {
    return (ExistPredSet) ExistPredSet.makeCanonical( this );
  }


  public void add( ExistPred pred ) {
    preds.add( pred );
  }

  public boolean isSatisfiedBy( ReachGraph rg ) {
    Iterator<ExistPred> predItr = preds.iterator();
    while( predItr.hasNext() ) {
      if( !predItr.next().isSatisfiedBy( rg ) ) {
        return false;
      }
    }
    return true;
  }

  public String toString() {
    String s = "P[";
    Iterator<ExistPred> predItr = preds.iterator();
    while( predItr.hasNext() ) {
      ExistPred pred = predItr.next();
      s += pred.toString();
      if( predItr.hasNext() ) {
        s += " && ";
      }
    }
    s += "]";
    return s;
  }

}
