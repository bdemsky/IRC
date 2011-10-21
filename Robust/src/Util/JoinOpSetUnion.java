package Util;

import java.util.Set;

public class JoinOpSetUnion implements JoinOp<Set> {
  public Set join( Set a, Set b ) {
    Set out = new HashSet();
    if( a != null ) {
      out.addAll( a );
    }
    if( b != null ) {
      out.addAll( b );
    }
    return out;
  }
}
