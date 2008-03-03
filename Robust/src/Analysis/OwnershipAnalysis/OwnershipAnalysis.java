package Analysis.OwnershipAnalysis;

import Analysis.CallGraph.*;
import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipAnalysis {

    // from the compiler
    private State     state;
    private CallGraph callGraph;
    private int       allocationDepth;


    static private int uniqueIDcount = 0;


    // Use these data structures to track progress of 
    // processing all methods in the program, and by methods
    // TaskDescriptor and MethodDescriptor are combined 
    // together, with a common parent class Descriptor
    private HashSet  <Descriptor>                 descriptorsToVisit;
    private Hashtable<Descriptor, OwnershipGraph> mapDescriptorToCompleteOwnershipGraph;
    private Hashtable<FlatNew,    AllocationSite> mapFlatNewToAllocationSite;


    // Use these data structures to track progress of one pass of
    // processing the FlatNodes of a particular method
    private HashSet  <FlatNode>                 flatNodesToVisit;
    private Hashtable<FlatNode, OwnershipGraph> mapFlatNodeToOwnershipGraph;    
    private HashSet  <FlatReturnNode>           returnNodesToCombineForCompleteOwnershipGraph;


    // this analysis generates an ownership graph for every task
    // in the program
    public OwnershipAnalysis( State     state,
			      CallGraph callGraph, 
			      int       allocationDepth ) throws java.io.IOException {
	this.state           = state;      
	this.callGraph       = callGraph;
	this.allocationDepth = allocationDepth;

	mapDescriptorToCompleteOwnershipGraph =
	    new Hashtable<Descriptor, OwnershipGraph>();

	mapFlatNewToAllocationSite =
	    new Hashtable<FlatNew, AllocationSite>();

	descriptorsToVisit = new HashSet<Descriptor>();

	// use this set to prevent infinite recursion when
	// traversing the call graph
	HashSet<Descriptor> calleesScheduled = new HashSet<Descriptor>();

	// initialize methods to visit as the set of all tasks in the
	// program and then any method that could be called starting
	// from those tasks
	Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
	while( taskItr.hasNext() ) {
	    Descriptor d = (Descriptor) taskItr.next();
	    descriptorsToVisit.add( d );

	    // recursively find all callees from this task
	    scheduleAllCallees( calleesScheduled, d );
	}
	
	// as mentioned above, analyze methods one-by-one, possibly revisiting
	// a method if the methods that it calls are updated
	analyzeMethods();
    }

    private void scheduleAllCallees( HashSet<Descriptor> calleesScheduled,
				     Descriptor d ) {
	if( calleesScheduled.contains( d ) ) {
	    return;
	}
	calleesScheduled.add( d );

	Set callees = callGraph.getCalleeSet( d );
	if( callees == null ) {
	    return;
	}

	Iterator methItr = callees.iterator();
	while( methItr.hasNext() ) {
	    MethodDescriptor md = (MethodDescriptor) methItr.next();
	    descriptorsToVisit.add( md );

	    // recursively find all callees from this task
	    scheduleAllCallees( calleesScheduled, md );
	}
    }


    // manage the set of tasks and methods to be analyzed
    // and be sure to reschedule tasks/methods when the methods
    // they call are updated
    private void analyzeMethods() throws java.io.IOException {
	
	while( !descriptorsToVisit.isEmpty() ) {
	    Descriptor d = (Descriptor) descriptorsToVisit.iterator().next();
	    descriptorsToVisit.remove( d );

	    // because the task or method descriptor just extracted
	    // was in the "to visit" set it either hasn't been analyzed
	    // yet, or some method that it depends on has been
	    // updated.  Recompute a complete ownership graph for
	    // this task/method and compare it to any previous result.
	    // If there is a change detected, add any methods/tasks
	    // that depend on this one to the "to visit" set.

	    System.out.println( "Analyzing " + d );

	    boolean    isTask;
	    FlatMethod fm;
	    if( d instanceof MethodDescriptor ) {
		//isTask = false;
		fm     = state.getMethodFlat( (MethodDescriptor) d );
	    } else {
		assert d instanceof TaskDescriptor;
		//isTask = true;
		fm     = state.getMethodFlat( (TaskDescriptor) d );
	    }
	    
	    OwnershipGraph og     = analyzeFlatMethod( d, fm );
	    OwnershipGraph ogPrev = mapDescriptorToCompleteOwnershipGraph.get( d );

	    if( !og.equals( ogPrev ) ) {
		mapDescriptorToCompleteOwnershipGraph.put( d, og );

		// only methods have dependents, tasks cannot
		// be invoked by any user program calls
		if( d instanceof MethodDescriptor ) {
		    MethodDescriptor md = (MethodDescriptor) d;
		    Set dependents = callGraph.getCallerSet( md );
		    if( dependents != null ) {
			descriptorsToVisit.addAll( dependents );
		    }
		}
	    }
	}

    }


    // keep passing the Descriptor of the method along for debugging
    // and dot file writing
    private OwnershipGraph
	analyzeFlatMethod( Descriptor mDesc,
			   FlatMethod flatm ) throws java.io.IOException {

	// initialize flat nodes to visit as the flat method
	// because all other nodes in this flat method are 
	// decendents of the flat method itself
	flatNodesToVisit = new HashSet<FlatNode>();
	flatNodesToVisit.add( flatm );

	// initilize the mapping of flat nodes in this flat method to
	// ownership graph results to an empty mapping
	mapFlatNodeToOwnershipGraph = new Hashtable<FlatNode, OwnershipGraph>();

	// initialize the set of return nodes that will be combined as
	// the final ownership graph result to return as an empty set
	returnNodesToCombineForCompleteOwnershipGraph = new HashSet<FlatReturnNode>();

	while( !flatNodesToVisit.isEmpty() ) {
	    FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
	    flatNodesToVisit.remove( fn );

	    // perform this node's contributions to the ownership
	    // graph on a new copy, then compare it to the old graph
	    // at this node to see if anything was updated.
	    OwnershipGraph og = new OwnershipGraph( allocationDepth );

	    // start by merging all node's parents' graphs
	    for( int i = 0; i < fn.numPrev(); ++i ) {
		FlatNode       pn       = fn.getPrev( i );
		OwnershipGraph ogParent = getGraphFromFlatNode( pn );
		og.merge( ogParent );
	    }
	    
	    // apply the analysis of the flat node to the
	    // ownership graph made from the merge of the
	    // parent graphs
	    analyzeFlatNode( mDesc, fn, og );
	    
	    // if the results of the new graph are different from
	    // the current graph at this node, replace the graph
	    // with the update and enqueue the children for
	    // processing
	    OwnershipGraph ogPrev = getGraphFromFlatNode( fn );

	    if( !og.equals( ogPrev ) ) {
		setGraphForFlatNode( fn, og );

		for( int i = 0; i < fn.numNext(); i++ ) {
		    FlatNode nn = fn.getNext( i );		  
		    flatNodesToVisit.add( nn );
		}
	    }
	}

	// end by merging all return nodes into a complete
	// ownership graph that represents all possible heap
	// states after the flat method returns
	OwnershipGraph completeGraph = new OwnershipGraph( allocationDepth );
	Iterator retItr = returnNodesToCombineForCompleteOwnershipGraph.iterator();
	while( retItr.hasNext() ) {
	    FlatReturnNode frn = (FlatReturnNode) retItr.next();
	    OwnershipGraph ogr = getGraphFromFlatNode( frn );
	    completeGraph.merge( ogr );
	}
	return completeGraph;
    }


    private void 
	analyzeFlatNode( Descriptor     methodDesc,
			 FlatNode       fn,
			 OwnershipGraph og ) throws java.io.IOException {

	//System.out.println( "Analyszing a flat node" );

	TempDescriptor  src;
	TempDescriptor  dst;
	FieldDescriptor fld;
	//String nodeDescription = "No description";
	//boolean writeGraph = false;

	// use node type to decide what alterations to make
	// to the ownership graph	    
	switch( fn.kind() ) {
	    
	case FKind.FlatMethod:
	    FlatMethod fm = (FlatMethod) fn;

	    // there should only be one FlatMethod node as the
	    // parent of all other FlatNode objects, so take
	    // the opportunity to construct the initial graph by
	    // adding parameters labels to new heap regions
	    for( int i = 0; i < fm.numParameters(); ++i ) {
		TempDescriptor tdParam = fm.getParameter( i );		
		og.parameterAllocation( methodDesc instanceof TaskDescriptor,
					tdParam );
		og.writeGraph( methodDesc, fn );
	    }
	    break;
	    
	case FKind.FlatOpNode:
	    FlatOpNode fon = (FlatOpNode) fn;
	    if( fon.getOp().getOp() == Operation.ASSIGN ) {
		src = fon.getLeft();
		dst = fon.getDest();
		og.assignTempToTemp( src, dst );
		og.writeGraph( methodDesc, fn );
	    }
	    break;
	    
	case FKind.FlatFieldNode:
	    FlatFieldNode ffn = (FlatFieldNode) fn;
	    src = ffn.getSrc();
	    dst = ffn.getDst();
	    fld = ffn.getField();
	    if( !fld.getType().isPrimitive() ) {
		og.assignTempToField( src, dst, fld );
		og.writeGraph( methodDesc, fn );
	    }
	    break;
	    
	case FKind.FlatSetFieldNode:
	    FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
	    src = fsfn.getSrc();
	    dst = fsfn.getDst();
	    fld = fsfn.getField();
	    og.assignFieldToTemp( src, dst, fld );
	    og.writeGraph( methodDesc, fn );
	    break;
	    
	    /*
	case FKind.FlatNew:
	    FlatNew fnn = (FlatNew) fn;
	    dst = fnn.getDst();
	    og.assignTempToNewAllocation( dst, fnn );
	    
	    // !!!!!!!!!!!!!!
	    // do this if the new object is a flagged type
	    //og.addAnalysisRegion( tdParam );
	    
	    //nodeDescription = "New";
	    //writeGraph = true;
	    og.writeGraph( fn );

	    break;
	    
	    */

	case FKind.FlatCall:
	    //FlatCall         fc = (FlatCall) fn;
	    //MethodDescriptor md = fc.getMethod();
	    //descriptorsToVisit.add( md );
	    //System.out.println( "    Descs to visit: " + descriptorsToVisit );
	    og.writeGraph( methodDesc, fn );
	    break;

	case FKind.FlatReturnNode:
	    //nodeDescription = "Return";
	    //writeGraph = true;
	    //og.writeCondensedAnalysis( makeCondensedAnalysisName( methodname, flatnodetolabel.get(fn) ) );
	    og.writeGraph( methodDesc, fn );
	    break;
	}
    }

    static public Integer generateUniqueHeapRegionNodeID() {
	++uniqueIDcount;
	return new Integer( uniqueIDcount );
    }    

    private OwnershipGraph getGraphFromFlatNode( FlatNode fn ) {
	if( !mapFlatNodeToOwnershipGraph.containsKey( fn ) ) {
	    mapFlatNodeToOwnershipGraph.put( fn, new OwnershipGraph( allocationDepth ) );
	}

	return mapFlatNodeToOwnershipGraph.get( fn );
    }

    private void setGraphForFlatNode( FlatNode fn, OwnershipGraph og ) {
	mapFlatNodeToOwnershipGraph.put( fn, og );
    }

    private AllocationSite getAllocationSiteFromFlatNew( FlatNew fn ) {
	if( !mapFlatNewToAllocationSite.containsKey( fn ) ) {
	    AllocationSite as = new AllocationSite( allocationDepth );

	    // the first k-1 nodes are single objects
	    for( int i = 0; i < allocationDepth - 1; ++i ) {
		//HeapRegionNode hrn = createNewHeapRegionNode( null, true, false, false );
		Integer id = generateUniqueHeapRegionNodeID();
		as.setIthOldest( i, id );
	    }

	    // the kth node is a summary node
	    //HeapRegionNode hrnNewSummary = createNewHeapRegionNode( null, false, false, true );
	    Integer id2 = generateUniqueHeapRegionNodeID();
	    as.setIthOldest( allocationDepth - 1, id2 );

	    mapFlatNewToAllocationSite.put( fn, as );
	}

	return mapFlatNewToAllocationSite.get( fn );
    }
}
