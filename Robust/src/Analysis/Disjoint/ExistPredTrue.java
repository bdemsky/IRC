package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// these existence predicates on elements of
// a callee graph allow a caller to prune the
// pieces of the graph that don't apply when
// predicates are not satisfied in the
// caller's context

public class ExistPredTrue extends ExistPred {  

  public boolean isSatisfiedBy( ReachGraph rg ) {    
    return true;
  }

  public String toString() {
    return "t";
  }
}
