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

public class ChangeSet extends Canonical {

  protected HashSet<ChangeTuple> changeTuples;

  public static ChangeSet factory() {
    ChangeSet out = new ChangeSet();
    out = (ChangeSet) Canonical.makeCanonical( out );
    return out;
  }

  public static ChangeSet factory( ChangeTuple ct ) {
    assert ct != null;
    assert ct.isCanonical();
    ChangeSet out = new ChangeSet();
    out.changeTuples.add( ct );
    out = (ChangeSet) Canonical.makeCanonical( out );
    return out;
  }  

  protected ChangeSet() {
    changeTuples = new HashSet<ChangeTuple>();
  }

  public Iterator<ChangeTuple> iterator() {
    return changeTuples.iterator();
  }

  public int size() {
    return changeTuples.size();
  }

  public boolean isEmpty() {
    return changeTuples.isEmpty();
  }

  public boolean isSubset( ChangeSet ctsIn ) {
    assert ctsIn != null;
    return ctsIn.changeTuples.containsAll( this.changeTuples );
  }


  public boolean equalsSpecific( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ChangeSet) ) {
      return false;
    }

    ChangeSet cts = (ChangeSet) o;
    return changeTuples.equals( cts.changeTuples );
  }

  public int hashCodeSpecific() {
    return changeTuples.hashCode();
  }


  public String toString() {
    String s = "[";

    Iterator i = this.iterator();
    while( i.hasNext() ) {
      s += "\n  "+i.next();
    }

    s += "\n]";

    return s;
  }
}
