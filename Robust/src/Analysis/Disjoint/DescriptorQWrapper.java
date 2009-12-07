package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class DescriptorQWrapper implements Comparable {

  private int priority;
  private Descriptor d;

  public DescriptorQWrapper( Integer p, Descriptor d ) {
    priority = p.intValue();
    this.d   = d;
  }

  public DescriptorQWrapper( int p, Descriptor d ) {
    priority = p;
    this.d   = d;
  }

  public Descriptor getDescriptor() {
    return d;
  }
 
  public int compareTo( Object o ) throws ClassCastException {

    if( !(o instanceof DescriptorQWrapper) ) {
      throw new ClassCastException();
    }

    DescriptorQWrapper dqw = (DescriptorQWrapper) o;
    return priority - dqw.priority;
  }

  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !( o instanceof DescriptorQWrapper) ) {
      return false;
    }
    
    DescriptorQWrapper dqw = (DescriptorQWrapper) o;

    return d.equals( dqw.d );
  }  
}
