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

// a taint set is simply the union of possible
// taints for an abstract reference edge--in a
// concrete heap each reference would have
// exactly one taint

public class TaintSet extends Canonical {

  protected HashSet<Taint> taints;

  public static TaintSet factory(HashSet<Taint> taints) {
    TaintSet out = new TaintSet(taints);
    out = (TaintSet) Canonical.makeCanonical(out);
    return out;
  }

  public TaintSet reTaint(FlatNode fn) {
    HashSet<Taint> taintset=new HashSet<Taint>();
    for(Taint t : taints) {
      if (t.getWhereDefined()!=fn) {
	t=t.reTaint(fn);
      }
      taintset.add(t);
    }

    TaintSet out=new TaintSet(taintset);
    out = (TaintSet) Canonical.makeCanonical(out);
    return out;
  }

  public static TaintSet factory() {
    TaintSet out = new TaintSet();
    out = (TaintSet) Canonical.makeCanonical(out);
    return out;
  }

  public static TaintSet factory(Taint t) {
    assert t != null;
    assert t.isCanonical();
    TaintSet out = new TaintSet();
    out.taints.add(t);
    out = (TaintSet) Canonical.makeCanonical(out);
    return out;
  }

  public static TaintSet factory(TaintSet ts,
                                 ExistPredSet preds) {
    assert ts != null;
    assert ts.isCanonical();

    TaintSet out = new TaintSet();

    Iterator<Taint> tItr = ts.iterator();
    while( tItr.hasNext() ) {
      Taint t    = tItr.next();
      Taint tOut = Taint.factory(t.sese,
                                 t.stallSite,
                                 t.var,
                                 t.allocSite,
                                 t.fnDefined,
                                 preds);
      out.taints.add(tOut);
    }

    out = (TaintSet) Canonical.makeCanonical(out);
    return out;
  }

  public TaintSet add(Taint t) {
    return Canonical.addPTR(this, t);
    /*    TaintSet newt=new TaintSet();
       newt.taints.addAll(taints);
       newt.taints.add(t);
       return (TaintSet) Canonical.makeCanonical(newt);*/
  }

  public TaintSet merge(TaintSet ts) {
    return Canonical.unionPTR(this, ts);
    /*    TaintSet newt=new TaintSet();
       newt.taints.addAll(taints);
       newt.taints.addAll(ts.taints);
       return (TaintSet) Canonical.makeCanonical(newt);*/
  }

  protected TaintSet() {
    taints = new HashSet<Taint>();
  }

  protected TaintSet(HashSet<Taint> taints) {
    this.taints = taints;
  }

  public Set<Taint> getTaints() {
    return taints;
  }

  public Iterator iterator() {
    return taints.iterator();
  }

  public boolean isEmpty() {
    return taints.isEmpty();
  }

  public Taint containsIgnorePreds(Taint t) {
    assert t != null;

    Iterator<Taint> tItr = taints.iterator();
    while( tItr.hasNext() ) {
      Taint tThis = tItr.next();
      if( tThis.equalsIgnorePreds(t) ) {
	return tThis;
      }
    }

    return null;
  }

  public boolean equalsSpecific(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof TaintSet) ) {
      return false;
    }

    TaintSet ts = (TaintSet) o;
    return taints.equals(ts.taints);
  }

  public int hashCodeSpecific() {
    return taints.hashCode();
  }

  public String toStringEscNewline() {
    String s = "taints[";

    Iterator<Taint> tItr = taints.iterator();
    while( tItr.hasNext() ) {
      Taint t = tItr.next();

      s += t.toString();
      if( tItr.hasNext() ) {
	s += ",\\n";
      }
    }
    s += "]";
    return s;
  }

  public String toString() {
    return taints.toString();
  }
}
