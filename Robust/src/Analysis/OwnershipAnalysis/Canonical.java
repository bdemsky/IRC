package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class Canonical {

  private static Hashtable<Canonical, Canonical> canon = new Hashtable<Canonical, Canonical>();

  int canonicalvalue;
  private static int canonicalcount=1;

  public static Canonical makeCanonical(Canonical c) {

    if( canon.containsKey(c) ) {
      return canon.get(c);
    }
    c.canonicalvalue=canonicalcount++;
    canon.put(c, c);
    return c;
  }

  static Hashtable<ReachOperation, ReachOperation> unionhash=new Hashtable<ReachOperation, ReachOperation>();
  static Hashtable<ReachOperation, ReachOperation> interhash=new Hashtable<ReachOperation, ReachOperation>();
  static Hashtable<CanonicalWrapper, CanonicalWrapper> lookuphash=new Hashtable<CanonicalWrapper, CanonicalWrapper>();
}