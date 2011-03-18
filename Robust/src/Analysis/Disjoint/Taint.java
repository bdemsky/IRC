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
// is used to associate an effect with a heap root

public class Taint extends Canonical {

  // taints can either be associated with
  // a stall site and live variable or
  // an sese (rblock) and an in-set var
  // only one identifer will be non-null
  
  // identify an sese (rblock) + inset var
  protected FlatSESEEnterNode sese;

  // identify a stall site + live variable
  protected FlatNode stallSite;

  // either type of taint includes a var
  // and allocation site
  protected TempDescriptor var;
  protected Alloc      allocSite;

  // taints have a new, possibly null element which is
  // the FlatNode at which the tainted reference was
  // defined, which currently supports DFJ but doesn't
  // hinder other analysis modes
  protected FlatNode fnDefined;

  // existance predicates must be true in a caller
  // context for this taint's effects to transfer from this
  // callee to that context
  protected ExistPredSet preds;

  public Taint reTaint(FlatNode fn) {
    Taint out=new Taint(sese, stallSite, var, allocSite, fn, preds);
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  public static Taint factory( FlatSESEEnterNode sese,
                               TempDescriptor    insetVar,
                               Alloc         as,
                               FlatNode          whereDefined,
                               ExistPredSet      eps ) {
    Taint out = new Taint( sese, null, insetVar, as, whereDefined, eps );
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  public static Taint factory( FlatNode       stallSite,
                               TempDescriptor var,
                               Alloc      as,
                               FlatNode       whereDefined,
                               ExistPredSet   eps ) {
    Taint out = new Taint( null, stallSite, var, as, whereDefined, eps );
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  public static Taint factory( FlatSESEEnterNode sese,
                               FlatNode          stallSite,
                               TempDescriptor    var,
                               Alloc         as,
                               FlatNode          whereDefined,
                               ExistPredSet      eps ) {
    Taint out = new Taint( sese, stallSite, var, as, whereDefined, eps );
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  protected Taint( FlatSESEEnterNode sese,
                   FlatNode          stallSite,
                   TempDescriptor    v,
                   Alloc         as,
                   FlatNode          fnDefined,
                   ExistPredSet      eps ) {
    assert 
      (sese == null && stallSite != null) ||
      (sese != null && stallSite == null);
      
    assert v   != null;
    assert as  != null;
    assert eps != null;
    
    this.sese      = sese;
    this.stallSite = stallSite;
    this.var       = v;
    this.allocSite = as;
    this.fnDefined = fnDefined;
    this.preds     = eps;
  }

  protected Taint( Taint t ) {
    this( t.sese, 
          t.stallSite, 
          t.var, 
          t.allocSite, 
          t.fnDefined,
          t.preds );
  }

  public boolean isRBlockTaint() {
    return sese != null;
  }

  public boolean isStallSiteTaint() {
    return stallSite != null;
  }

  public FlatSESEEnterNode getSESE() {
    return sese;
  }

  public FlatNode getStallSite() {
    return stallSite;
  }

  public TempDescriptor getVar() {
    return var;
  }

  public Alloc getAllocSite() {
    return allocSite;
  }

  public FlatNode getWhereDefined() {
    return fnDefined;
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

    boolean seseEqual;
    if( sese == null ) {
      seseEqual = (t.sese == null);      
    } else {
      seseEqual = sese.equals( t.sese );
    }

    boolean stallSiteEqual;
    if( stallSite == null ) {
      stallSiteEqual = (t.stallSite == null);
    } else {
      stallSiteEqual = stallSite.equals( t.stallSite );
    }

    boolean fnDefinedEqual;
    if( fnDefined == null ) {
      fnDefinedEqual = (t.fnDefined == null);
    } else {
      fnDefinedEqual = fnDefined.equals( t.fnDefined );
    }

    return 
      seseEqual                      && 
      stallSiteEqual                 &&
      fnDefinedEqual                 &&
      var      .equals( t.var  )     &&
      allocSite.equals( t.allocSite );
  }

  public int hashCodeSpecific() {
    int hash = allocSite.hashCode();
    hash = hash ^ var.hashCode();
  
    if( sese != null ) {
      hash = hash ^ sese.hashCode();
    }

    if( stallSite != null ) {
      hash = hash ^ stallSite.hashCode();
    }

    if( fnDefined != null ) {
      hash = hash ^ fnDefined.hashCode();
    }

    return hash;
  }

  public String toString() {

    String s;

    if( isRBlockTaint() ) {
      s = sese.getPrettyIdentifier();
    } else {
      s = stallSite.toString();
    }

    String f = "";
    if( fnDefined != null ) {
      f += ", "+fnDefined;
    }

    return 
      "("+s+
      "-"+var+
      ", "+allocSite.toStringBrief()+
      f+
      "):"+preds;
  }
}
