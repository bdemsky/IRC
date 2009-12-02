package Analysis.DisjointAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ChangeSet extends Canonical {

  private HashSet<ChangeTuple> changeTuples;

  public ChangeSet() {
    changeTuples = new HashSet<ChangeTuple>();
  }

  public ChangeSet(ChangeTuple ct) {
    this();
    changeTuples.add(ct);
  }

  public ChangeSet(ChangeSet cts) {
    changeTuples = (HashSet<ChangeTuple>)cts.changeTuples.clone();
  }

  public ChangeSet makeCanonical() {
    return (ChangeSet) Canonical.makeCanonical(this);
  }

  public Iterator iterator() {
    return changeTuples.iterator();
  }

  public int size() {
    return changeTuples.size();
  }

  public ChangeSet union(ChangeSet ctsIn) {
    assert ctsIn != null;

    ChangeSet ctsOut = new ChangeSet(this);
    ctsOut.changeTuples.addAll(ctsIn.changeTuples);
    return ctsOut.makeCanonical();
  }

  public ChangeSet union(ChangeTuple ctIn) {
    assert ctIn != null;

    ChangeSet ctsOut = new ChangeSet(this);
    ctsOut.changeTuples.add(ctIn);
    return ctsOut.makeCanonical();
  }

  public boolean isEmpty() {
    return changeTuples.isEmpty();
  }

  public boolean isSubset(ChangeSet ctsIn) {
    assert ctsIn != null;
    return ctsIn.changeTuples.containsAll(this.changeTuples);
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ChangeSet) ) {
      return false;
    }

    ChangeSet cts = (ChangeSet) o;
    return changeTuples.equals(cts.changeTuples);
  }

  private boolean oldHashSet = false;
  private int oldHash    = 0;
  public int hashCode() {
    int currentHash = changeTuples.hashCode();

    if( oldHashSet == false ) {
      oldHash = currentHash;
      oldHashSet = true;
    } else {
      if( oldHash != currentHash ) {
	System.out.println("IF YOU SEE THIS A CANONICAL ChangeSet CHANGED");
	Integer x = null;
	x.toString();
      }
    }

    return currentHash;
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
