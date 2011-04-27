package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

// This class must be instantiated from objects out
// of a completed analysis.  Given a method's reachability
// graph and a call site, what heap regions might the
// parameter regions be decomposed into?
//
// Also you can build a call chain by constructing
// a new decomposition from another decomp and a
// flat call one step back in the chain.
public class ParameterDecomposition {


  // the ownership analysis results to compute from
  protected OwnershipAnalysis oa;

  // info needed to use OwnershipGraph.resolveMethodCall()
  // to do the parameter decomp mapping itself
  protected FlatCall fcInCaller;
  protected FlatMethod fmPossible;
  protected MethodContext mcCallSite;
  protected OwnershipGraph ogCallee;
  protected OwnershipGraph ogCaller;

  // computed information:
  // a IDs are heap regions that map the primary parameter object region
  // r IDs are regions that map to the gamma parameter region
  // allocation sites are any that provide the regions a param could map to
  // type descriptors are any types of any allocation site from above
  protected Hashtable<Integer, Set<Integer>        > pi2a_id;
  protected Hashtable<Integer, Set<Integer>        > pi2r_id;
  protected Hashtable<Integer, Set<AllocationSite> > pi2a_as;
  protected Hashtable<Integer, Set<AllocationSite> > pi2r_as;
  protected Hashtable<Integer, Set<TypeDescriptor> > pi2a_td;
  protected Hashtable<Integer, Set<TypeDescriptor> > pi2r_td;


  public ParameterDecomposition(OwnershipAnalysis oa,
                                FlatCall fc,
                                FlatMethod fm,
                                MethodContext mc,
                                OwnershipGraph cee,
                                OwnershipGraph cer) {
    oa.checkAnalysisComplete();
    this.oa = oa;

    MethodDescriptor md = (MethodDescriptor) mc.getDescriptor();
    // the call site should be calling the method in question
    assert fc.getMethod() == md;

    this.fcInCaller = fc;
    this.fmPossible = fm;
    this.mcCallSite = mc;

    // make copies of the graphs so that resolveMethodCall can
    // destroy the graph while calculating the stuff we want
    this.ogCallee = new OwnershipGraph();
    this.ogCallee.merge(cee);

    this.ogCaller = new OwnershipGraph();
    this.ogCaller.merge(cer);

    allocOutputStructs();

    computeDecompositon();
  }

  /*
     public ParameterDecomposition( ParameterDecomposition pd,
                                 FlatCall fc ) {
     this.oa = pd.oa;

     // the call site should be calling the caller of
     // the input parameter decomposition object
     assert fc.getMethod() == pd.mdCaller;

     mdCallee = pd.mdCaller;
     mdCaller = getCaller( fc );
     }
   */

  protected void allocOutputStructs() {
    pi2a_id = new Hashtable<Integer, Set<Integer>        >();
    pi2r_id = new Hashtable<Integer, Set<Integer>        >();
    pi2a_as = new Hashtable<Integer, Set<AllocationSite> >();
    pi2r_as = new Hashtable<Integer, Set<AllocationSite> >();
    pi2a_td = new Hashtable<Integer, Set<TypeDescriptor> >();
    pi2r_td = new Hashtable<Integer, Set<TypeDescriptor> >();
  }

  protected void computeDecompositon() {
    MethodDescriptor mdCallee = (MethodDescriptor) mcCallSite.getDescriptor();

    ogCaller.resolveMethodCall(fcInCaller,
                               mdCallee.isStatic(),
                               fmPossible,
                               ogCallee,
                               mcCallSite,
                               this);
  }

  // called by resolveMethodCall in decomp mode
  // to report mapping results
  protected void mapRegionToParamObject(HeapRegionNode hrn, Integer paramIndex) {

    // extract region's intergraph ID
    Set<Integer> hrnIDs = pi2a_id.get(paramIndex);
    if( hrnIDs == null ) {
      hrnIDs = new HashSet<Integer>();
    }
    hrnIDs.add(hrn.getID() );
    pi2a_id.put(paramIndex, hrnIDs);

    // the regions allocation site (if any)
    AllocationSite as = hrn.getAllocationSite();
    if( as != null ) {
      Set<AllocationSite> asSet = pi2a_as.get(paramIndex);
      if( asSet == null ) {
        asSet = new HashSet<AllocationSite>();
      }
      asSet.add(as);
      pi2a_as.put(paramIndex, asSet);

      // and if there is an allocation site, grab type
      Set<TypeDescriptor> tdSet = pi2a_td.get(paramIndex);
      if( tdSet == null ) {
        tdSet = new HashSet<TypeDescriptor>();
      }
      tdSet.add(as.getType() );
      pi2a_td.put(paramIndex, tdSet);
    }
  }

  protected void mapRegionToParamReachable(HeapRegionNode hrn, Integer paramIndex) {

    // extract region's intergraph ID
    Set<Integer> hrnIDs = pi2r_id.get(paramIndex);
    if( hrnIDs == null ) {
      hrnIDs = new HashSet<Integer>();
    }
    hrnIDs.add(hrn.getID() );
    pi2r_id.put(paramIndex, hrnIDs);

    // the regions allocation site (if any)
    AllocationSite as = hrn.getAllocationSite();
    if( as != null ) {
      Set<AllocationSite> asSet = pi2r_as.get(paramIndex);
      if( asSet == null ) {
        asSet = new HashSet<AllocationSite>();
      }
      asSet.add(as);
      pi2r_as.put(paramIndex, asSet);

      // and if there is an allocation site, grab type
      Set<TypeDescriptor> tdSet = pi2r_td.get(paramIndex);
      if( tdSet == null ) {
        tdSet = new HashSet<TypeDescriptor>();
      }
      tdSet.add(as.getType() );
      pi2r_td.put(paramIndex, tdSet);
    }
  }


  // this family of "gets" returns, for some
  // parameter index, all of the associated data
  // that parameter might decompose into
  public Set<Integer> getParamObject_hrnIDs(Integer paramIndex) {
    Set<Integer> hrnIDs = pi2a_id.get(paramIndex);
    if( hrnIDs == null ) {
      hrnIDs = new HashSet<Integer>();
    }
    return hrnIDs;
  }

  public Set<AllocationSite> getParamObject_allocSites(Integer paramIndex) {
    Set<AllocationSite> asSet = pi2a_as.get(paramIndex);
    if( asSet == null ) {
      asSet = new HashSet<AllocationSite>();
    }
    return asSet;
  }

  public Set<TypeDescriptor> getParamObject_TypeDescs(Integer paramIndex) {
    Set<TypeDescriptor> tdSet = pi2a_td.get(paramIndex);
    if( tdSet == null ) {
      tdSet = new HashSet<TypeDescriptor>();
    }
    return tdSet;
  }


  public Set<Integer> getParamReachable_hrnIDs(Integer paramIndex) {
    Set<Integer> hrnIDs = pi2r_id.get(paramIndex);
    if( hrnIDs == null ) {
      hrnIDs = new HashSet<Integer>();
    }
    return hrnIDs;
  }

  public Set<AllocationSite> getParamReachable_allocSites(Integer paramIndex) {
    Set<AllocationSite> asSet = pi2r_as.get(paramIndex);
    if( asSet == null ) {
      asSet = new HashSet<AllocationSite>();
    }
    return asSet;
  }

  public Set<TypeDescriptor> getParamReachable_TypeDescs(Integer paramIndex) {
    Set<TypeDescriptor> tdSet = pi2r_td.get(paramIndex);
    if( tdSet == null ) {
      tdSet = new HashSet<TypeDescriptor>();
    }
    return tdSet;
  }
}
