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
// sese (rblock) and in-

public class Taint extends Canonical {

  // taints can either be associated with
  // a callsite and parameter index or
  // an sese (rblock) and an in-set var
  // only one set of identifying objects
  // will be non-null

  // identify a parameter index
  protected FlatCall callSite;
  protected Integer  paramIndex;
  
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


  public static Taint factory( FlatCall          fc,
                               Integer           pi,
                               FlatSESEEnterNode s,
                               TempDescriptor    iv,
                               AllocSite         as ) {
    Taint out = new Taint( fc, pi, s, iv, as );
    out.preds = ExistPredSet.factory();
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  public static Taint factory( FlatCall          fc,
                               Integer           pi,
                               FlatSESEEnterNode s,
                               TempDescriptor    iv,
                               AllocSite         as,
                               ExistPredSet      eps ) {
    Taint out = new Taint( fc, pi, s, iv, as );
    out.preds = eps;
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  protected Taint( FlatCall          fc,
                   Integer           pi,
                   FlatSESEEnterNode s,
                   TempDescriptor    iv,
                   AllocSite         as ) {    

    // either fc and pi are non-null, OR s and iv are non-null
    assert 
      (fc != null && pi != null && s == null && iv == null) ||
      (fc == null && pi == null && s != null && iv != null);

    assert as != null;
    
    callSite   = fc;
    paramIndex = pi;
    sese       = s;
    insetVar   = iv;
    allocSite  = as;
  }

  public boolean isParamTaint() {
    return callSite != null;
  }

  public boolean isSESETaint() {
    return sese != null;
  }

  public FlatCall getCallSite() {
    return callSite;
  }

  public Integer getParamIndex() {
    return paramIndex;
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

    boolean fcMatches = true;
    if( callSite == null ) {
      fcMatches = t.callSite == null;
    } else {
      fcMatches = callSite.equals( t.callSite );
    }

    boolean piMatches = true;
    if( paramIndex == null ) {
      piMatches = t.paramIndex == null;
    } else {
      piMatches = paramIndex.equals( t.paramIndex );
    }

    boolean sMatches = true;
    if( sese == null ) {
      sMatches = t.sese == null;
    } else {
      sMatches = sese.equals( t.sese );
    }

    boolean ivMatches = true;
    if( insetVar == null ) {
      ivMatches = t.insetVar == null;
    } else {
      ivMatches = insetVar.equals( t.insetVar );
    }

    return allocSite.equals( t.allocSite ) &&
      piMatches && sMatches && ivMatches;
  }

  public int hashCodeSpecific() {
    int hash = allocSite.hashCode();

    if( callSite != null ) {
      hash = hash ^ callSite.hashCode();
    }

    if( paramIndex != null ) {
      hash = hash ^ paramIndex.hashCode();
    }

    if( sese != null ) {
      hash = hash ^ sese.hashCode();
    }

    if( insetVar != null ) {
      hash = hash ^ insetVar.hashCode();
    }

    return hash;
  }

  public String toString() {
    String s = "(";

    if( isParamTaint() ) {
      s += "cs"+callSite.nodeid+"-"+paramIndex;
    } else {
      s += sese.toPrettyString()+"-"+insetVar;
    }

    return s+", "+allocSite.toStringBrief()+"):"+preds;
  }
}
