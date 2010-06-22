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

// a taint is applied to a reference edge, and
// is used to associate an effect with an
// sese (rblock) and live variable

public class Taint extends Canonical {

  // taints can either be associated with
  // a callsite and parameter index or
  // an sese (rblock) and an in-set var
  // only one set of identifying objects
  // will be non-null
  
  // identify an sese (rblock) + inset var
  protected FlatSESEEnterNode sese;
  protected TempDescriptor    insetVar;

  // either type of taint also includes
  // an allocation site
  protected AllocSite allocSite;

  // existance predicates must be true in a caller
  // context for this taint's effects to transfer from this
  // callee to that context
  protected ExistPredSet preds;


  public static Taint factory( FlatSESEEnterNode s,
                               TempDescriptor    iv,
                               AllocSite         as ) {
    Taint out = new Taint( s, iv, as );
    out.preds = ExistPredSet.factory();
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  public static Taint factory( FlatSESEEnterNode s,
                               TempDescriptor    iv,
                               AllocSite         as,
                               ExistPredSet      eps ) {
    Taint out = new Taint( s, iv, as );
    out.preds = eps;
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  protected Taint( FlatSESEEnterNode s,
                   TempDescriptor    iv,
                   AllocSite         as ) {
    assert s  != null;
    assert iv != null;
    assert as != null;
    sese       = s;
    insetVar   = iv;
    allocSite  = as;
  }

  public FlatSESEEnterNode getSESE() {
    return sese;
  }

  public TempDescriptor getInSetVar() {
    return insetVar;
  }

  public AllocSite getAllocSite() {
    return allocSite;
  }

  public ExistPredSet getPreds() {
    return preds;
  }

  public boolean equalsSpecific( Object o ) {
    if( !equalsIgnorePreds( o ) ) {
      return false;
    }
        
    Taint t = (Taint) o;
    return preds.equals( t.preds );
  }

  public boolean equalsIgnorePreds( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof Taint) ) {
      return false;
    }

    Taint t = (Taint) o;

    return 
      sese     .equals( t.sese      ) &&
      insetVar .equals( t.insetVar  ) &&
      allocSite.equals( t.allocSite );
  }

  public int hashCodeSpecific() {
    int hash = allocSite.hashCode();
    hash = hash ^ sese.hashCode();
    hash = hash ^ insetVar.hashCode();
    return hash;
  }

  public String toString() {
    return 
      "("+sese.toPrettyString()+
      "-"+insetVar+
      ", "+allocSite.toStringBrief()+
      "):"+preds;
  }
}
