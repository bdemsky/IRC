package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;

// An access path is relevant in a callee method to
// a caller's heap.  When mapping edges from a callee
// into the caller, if the caller's heap does not have
// any matching access paths, then the edge could not
// exist in that context and is ignored.

public class AccessPath {

  public AccessPath() {
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof AccessPath) ) {
      return false;
    }

    return true;
    /*
    VariableSourceToken vst = (VariableSourceToken) o;

    // the reference vars have no bearing on equality
    return    sese.equals( vst.sese    ) &&
           addrVar.equals( vst.addrVar ) &&
           seseAge.equals( vst.seseAge );
    */
  }

  public int hashCode() {
    // the reference vars have no bearing on hashCode
    return 1; //(sese.hashCode() << 3) * (addrVar.hashCode() << 4) ^ seseAge.intValue();
  }

  public String toString() {
    return "ap";
  }

  public String toStringForDOT() {
    /*
    if( disjointId != null ) {
      return "disjoint "+disjointId+"\\n"+toString()+"\\n"+getType().toPrettyString();
    } else {
      return                              toString()+"\\n"+getType().toPrettyString();
    }
    */
    return "do";
  }  
}
