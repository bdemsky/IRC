package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class MethodContextQWrapper implements Comparable {

  private int priority;
  private MethodContext mc;

  public MethodContextQWrapper(Integer p, MethodContext m) {
    priority = p.intValue();
    mc = m;
  }

  public MethodContextQWrapper(int p, MethodContext m) {
    priority = p;
    mc = m;
  }

  public MethodContext getMethodContext() {
    return mc;
  }

  public int compareTo(Object o) throws ClassCastException {

    if( !(o instanceof MethodContextQWrapper) ) {
      throw new ClassCastException();
    }

    MethodContextQWrapper mcqw = (MethodContextQWrapper) o;
    return priority - mcqw.priority;
  }

  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !( o instanceof MethodContextQWrapper) ) {
      return false;
    }

    MethodContextQWrapper mcqw = (MethodContextQWrapper) o;

    return mc.equals(mcqw.mc);
  }
}
