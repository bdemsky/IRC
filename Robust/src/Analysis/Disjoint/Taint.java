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
  // parameters or seses (rblocks),
  // only one set of identifying objects
  // will be non-null

  // identify a parameter index
  protected Integer paramIndex;
  
  // identify an sese (rblock) + inset var
  protected FlatSESEEnterNode sese;
  protected TempDescriptor    insetVar;

  // either type of taint also includes
  // an allocation site
  protected AllocSite allocSite;


  public static Taint factory( Integer           pi,
                               FlatSESEEnterNode s,
                               TempDescriptor    iv,
                               AllocSite         as ) {
    Taint out = new Taint( pi, s, iv, as );
    out = (Taint) Canonical.makeCanonical( out );
    return out;
  }

  protected Taint( Integer           pi,
                   FlatSESEEnterNode s,
                   TempDescriptor    iv,
                   AllocSite         as ) {    
    assert 
      (pi != null && s == null && iv == null) ||
      (pi == null && s != null && iv != null);

    assert as != null;

    paramIndex = pi;
    sese       = s;
    insetVar   = iv;
    allocSite  = as;
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


  public boolean equalsSpecific( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof Taint) ) {
      return false;
    }

    Taint t = (Taint) o;

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
    return "";
  }
}
