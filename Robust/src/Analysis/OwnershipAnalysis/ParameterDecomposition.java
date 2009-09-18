package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

// This class is useful to instantiate from objects out
// of a completed analysis.  Given a method's reachability
// graph and a call site, what heap regions might the
// parameter regions be decomposed into?
//
// Also you can build a call chain by constructing
// a new decomposition from another decomp and a
// flat call one step back in the chain.
public class ParameterDecomposition {

  // used to help user realize when they are
  // asking for results of an invalid call chain
  MethodDescriptor mdCallee;
  MethodDescriptor mdCaller;

  public ParameterDecomposition( MethodDescriptor md,
				 FlatCall fc ) {

  }

  public ParameterDecomposition( ParameterDecomposition pd,
				 FlatCall fc ) {
    
  }

  // this family of "gets" returns, for some 
  // parameter index, all of the associated data
  // that parameter might decompose into
  public Set<Integer> 
    getHeapRegionNodeIDs( Integer paramIndex ) {
    return null;
  }

  public Set<HeapRegionNode> 
    getHeapRegionNodes( Integer paramIndex ) {
    return null;
  }

  public Set<AllocationSite> 
    getAllocationSites( Integer paramIndex ) {
    return null;
  }

  public Set<TypeDescriptor> 
    getTypeDescriptors( Integer paramIndex ) {
    return null;
  }

}
