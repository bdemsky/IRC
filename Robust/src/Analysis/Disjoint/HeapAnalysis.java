package Analysis.Disjoint;

import java.util.*;

import IR.*;
import IR.Flat.TempDescriptor;
import IR.Flat.FlatNode;
import IR.Flat.FlatNew;


public interface HeapAnalysis {
  public EffectsAnalysis getEffectsAnalysis();
  public Alloc getAllocationSiteFromFlatNew(FlatNew node);
  public Alloc getCmdLineArgsAlloc();

  // Use these methods to find out what allocation sites
  // the given pointer might point to at or after the 
  // given program point.  In the case of a variable and
  // a field or element dereference you get a hashtable
  // where the keys are allocs for the variables and the
  // values are from following the second hop
  // NOTE: if the set of Alloc's that something can point
  // to is DONTCARE, this will mean "the analysis doesn't care
  // what it points to" so the client shouldn't either, NOT
  // interpreting it as "it can't point to anything."  The
  // meaning "it can't point to anything" will be represented
  // by an empty set of Alloc.
  static public final Set<Alloc>                     DONTCARE_PTR  = new HashSet<Alloc>();
  static public final Hashtable< Alloc, Set<Alloc> > DONTCARE_DREF = new Hashtable< Alloc, Set<Alloc> >();

  public Set<Alloc> canPointToAt( TempDescriptor x,
                                  FlatNode programPoint );

  public Set<Alloc> canPointToAfter( TempDescriptor x,
                                     FlatNode programPoint );
  
  public Hashtable< Alloc, Set<Alloc> > canPointToAt( TempDescriptor x,
                                                      FieldDescriptor f,
                                                      FlatNode programPoint );

  public Hashtable< Alloc, Set<Alloc> > canPointToAtElement( TempDescriptor x, // x[i]
                                                             FlatNode programPoint );
}

