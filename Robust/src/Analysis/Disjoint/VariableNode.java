package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class VariableNode extends RefSrcNode {
  protected TempDescriptor td;

  public VariableNode(TempDescriptor td) {
    this.td = td;
  }

  public TempDescriptor getTempDescriptor() {
    return td;
  }

  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !( o instanceof VariableNode) ) {
      return false;
    }

    VariableNode ln = (VariableNode) o;

    return td == ln.getTempDescriptor();
  }

  public int hashCode() {
    return td.getNum();
  }

  public String getTempDescriptorString() {
    return td.toString();
  }

  public String toString() {
    return "LN_"+getTempDescriptorString();
  }
}
