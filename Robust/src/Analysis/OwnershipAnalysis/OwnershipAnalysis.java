package Analysis.OwnershipAnalysis;

import Analysis.CallGraph.*;
import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipAnalysis {

    ///////////////////////////////////////////
    //
    //  Public interface to discover possible
    //  aliases in the program under analysis
    //
    ///////////////////////////////////////////
    /*
    public HashSet<AllocationSite> 
	getFlaggedAllocationSitesReachableFromTask( TaskDescriptor td ) {

	return getFlaggedAllocationSitesReachableFromTaskPRIVATE( td );
    }

    public AllocationSite getAllocationSiteFromFlatNew( FlatNew fn ) {
	return getAllocationSiteFromFlatNewPRIVATE( fn );
    }

    public boolean createsPotentialAliases( Descriptor     taskOrMethod,
					    int            paramIndex1,
					    int            paramIndex2 ) {

	OwnershipGraph og = mapDescriptorToCompleteOwnershipGraph.get( taskOrMethod );
	assert( og != null );

	return createsPotentialAliases( og,
					getHeapRegionIDset( og, paramIndex1 ),
					getHeapRegionIDset( og, paramIndex2 ) );
    }

    public boolean createsPotentialAliases( Descriptor     taskOrMethod,
					    int            paramIndex,
					    AllocationSite alloc ) {

	OwnershipGraph og = mapDescriptorToCompleteOwnershipGraph.get( taskOrMethod );
	assert( og != null );

	return createsPotentialAliases( og,
					getHeapRegionIDset( og, paramIndex ),
					getHeapRegionIDset( alloc ) );
    }

    public boolean createsPotentialAliases( Descriptor     taskOrMethod, 
					    AllocationSite alloc,
					    int            paramIndex ) {

 	OwnershipGraph og = mapDescriptorToCompleteOwnershipGraph.get( taskOrMethod );
	assert( og != null );

	return createsPotentialAliases( og,
					getHeapRegionIDset( og, paramIndex ),
					getHeapRegionIDset( alloc ) );
    }

    public boolean createsPotentialAliases( Descriptor     taskOrMethod,
					    AllocationSite alloc1,
					    AllocationSite alloc2 ) {

	OwnershipGraph og = mapDescriptorToCompleteOwnershipGraph.get( taskOrMethod );
	assert( og != null );

	return createsPotentialAliases( og,
					getHeapRegionIDset( alloc1 ),
					getHeapRegionIDset( alloc2 ) );
    }

    public boolean createsPotentialAliases( Descriptor              taskOrMethod,
					    AllocationSite          alloc,
					    HashSet<AllocationSite> allocSet ) {

	OwnershipGraph og = mapDescriptorToCompleteOwnershipGraph.get( taskOrMethod );
	assert( og != null );

	return createsPotentialAliases( og,
					getHeapRegionIDset( alloc ),
					getHeapRegionIDset( allocSet ) );
    }
    */

    // use the methods given above to check every possible alias
    // between task parameters and flagged allocation sites reachable
    // from the task
    public void writeAllAliases( String outputFile ) throws java.io.IOException {
	
	BufferedWriter bw = new BufferedWriter( new FileWriter( outputFile ) );
	/*
	// look through every task for potential aliases
	Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
	while( taskItr.hasNext() ) {
	    TaskDescriptor td = (TaskDescriptor) taskItr.next();

	    HashSet<AllocationSite> allocSites = getFlaggedAllocationSitesReachableFromTask( td );

	    // for each task parameter, check for aliases with
	    // other task parameters and every allocation site
	    // reachable from this task	    
	    FlatMethod fm = state.getMethodFlat( td );
	    for( int i = 0; i < fm.numParameters(); ++i ) {

		// for the ith parameter check for aliases to all
		// higher numbered parameters
		for( int j = i + 1; j < fm.numParameters(); ++j ) {
		    if( createsPotentialAliases( td, i, j ) ) {
			bw.write( "Task "+td+" potentially aliases parameters "+i+" and "+j+".\n" );
		    }
		}

		// for the ith parameter, check for aliases against
		// the set of allocation sites reachable from this
		// task context
		Iterator allocItr = allocSites.iterator();
		while( allocItr.hasNext() ) {
		    AllocationSite as = (AllocationSite) allocItr.next();
		    if( createsPotentialAliases( td, i, as ) ) {
			bw.write( "Task "+td+" potentially aliases parameter "+i+" and "+as+".\n" );
		    }
		}
	    }

	    // for each allocation site check for aliases with
	    // other allocation sites in the context of execution
	    // of this task
	    Iterator allocItr = allocSites.iterator();
	    while( allocItr.hasNext() ) {
		AllocationSite as = (AllocationSite) allocItr.next();
		if( createsPotentialAliases( td, as, allocSites ) ) {
		    bw.write( "Task "+td+" potentially aliases "+as+" and the rest of the set.\n" );
		}
	    }
	}

	bw.close();
        */
    }
    
    ///////////////////////////////////////////
    //
    // end public interface
    //
    ///////////////////////////////////////////







    
    // data from the compiler
    private State     state;
    private CallGraph callGraph;
    private int       allocationDepth;

    // used to identify HeapRegionNode objects
    // A unique ID equates an object in one
    // ownership graph with an object in another
    // graph that logically represents the same
    // heap region
    static private int uniqueIDcount = 0;


    // Use these data structures to track progress of 
    // processing all methods in the program, and by methods
    // TaskDescriptor and MethodDescriptor are combined 
    // together, with a common parent class Descriptor
    private HashSet  <Descriptor>                           descriptorsToVisit;
    private Hashtable<Descriptor, OwnershipGraph>           mapDescriptorToCompleteOwnershipGraph;
    private Hashtable<FlatNew,    AllocationSite>           mapFlatNewToAllocationSite;
    private Hashtable<Descriptor, HashSet<AllocationSite> > mapDescriptorToAllocationSiteSet;

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

	descriptorsToVisit = new HashSet<Descriptor>();

	mapDescriptorToCompleteOwnershipGraph =
	    new Hashtable<Descriptor, OwnershipGraph>();

	mapFlatNewToAllocationSite =
	    new Hashtable<FlatNew, AllocationSite>();

	mapDescriptorToAllocationSiteSet =
	    new Hashtable<Descriptor, HashSet<AllocationSite> >();

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
	
	// before beginning analysis, initialize every scheduled method
	// with an ownership graph that has populated parameter index tables
	// by analyzing the first node which is always a FlatMethod node
	Iterator<Descriptor> dItr = calleesScheduled.iterator();
	while( dItr.hasNext() ) {
	    Descriptor     d  = dItr.next();
	    OwnershipGraph og = new OwnershipGraph( allocationDepth );
	    
	    FlatMethod fm;
	    if( d instanceof MethodDescriptor ) {
		fm = state.getMethodFlat( (MethodDescriptor) d );
	    } else {
		assert d instanceof TaskDescriptor;
		fm = state.getMethodFlat( (TaskDescriptor) d );
	    }

	    analyzeFlatNode( d, fm, null, og );
	    mapDescriptorToCompleteOwnershipGraph.put( d, og );
	}

	// as mentioned above, analyze methods one-by-one, possibly revisiting
	// a method if the methods that it calls are updated
	analyzeMethods();
    }

    // called from the constructor to help initialize the set
    // of methods that needs to be analyzed by ownership analysis
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

	    FlatMethod fm;
	    if( d instanceof MethodDescriptor ) {
		fm = state.getMethodFlat( (MethodDescriptor) d );
	    } else {
		assert d instanceof TaskDescriptor;
		fm = state.getMethodFlat( (TaskDescriptor) d );
	    }
	    
	    OwnershipGraph og     = analyzeFlatMethod( d, fm );
	    OwnershipGraph ogPrev = mapDescriptorToCompleteOwnershipGraph.get( d );
	    if( !og.equals( ogPrev ) ) {
		mapDescriptorToCompleteOwnershipGraph.put( d, og );
		
		/*
		boolean writeLabels,
		boolean labelSelect,
		boolean pruneGarbage,
		boolean writeReferencers 
		*/
		og.writeGraph( d, true, true, false, false );

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


    int x = 0;


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
	    analyzeFlatNode( mDesc,
			     fn, 
			     returnNodesToCombineForCompleteOwnershipGraph,
			     og );
	    
	    // if the results of the new graph are different from
	    // the current graph at this node, replace the graph
	    // with the update and enqueue the children for
	    // processing
	    OwnershipGraph ogPrev = getGraphFromFlatNode( fn );

	    if( !og.equals( ogPrev ) ) {
		setGraphForFlatNode( fn, og );



		x++;
		if( x > 0 ) {
		    String s = String.format( "debug%04d", x );
		    //og.writeGraph( s, true, true, true, false );
		}



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
	analyzeFlatNode( Descriptor              methodDesc,
			 FlatNode                fn,
			 HashSet<FlatReturnNode> setRetNodes,
			 OwnershipGraph          og ) throws java.io.IOException {

	TempDescriptor  src;
	TempDescriptor  dst;
	FieldDescriptor fld;

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
		og.assignParameterAllocationToTemp( methodDesc instanceof TaskDescriptor,
						    tdParam,
						    new Integer( i ) );
	    }

	    break;

	case FKind.FlatOpNode:
	    FlatOpNode fon = (FlatOpNode) fn;
	    if( fon.getOp().getOp() == Operation.ASSIGN ) {
		src = fon.getLeft();
		dst = fon.getDest();
		og.assignTempYToTempX( src, dst );
	    }
	    break;
	    
	case FKind.FlatFieldNode:
	    FlatFieldNode ffn = (FlatFieldNode) fn;
	    src = ffn.getSrc();
	    dst = ffn.getDst();
	    fld = ffn.getField();
	    if( !fld.getType().isPrimitive() ) {
		og.assignTempYFieldFToTempX( src, fld, dst );
	    }
	    break;
	    
	case FKind.FlatSetFieldNode:
	    FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
	    src = fsfn.getSrc();
	    dst = fsfn.getDst();
	    fld = fsfn.getField();
	    og.assignTempYToTempXFieldF( src, dst, fld );
	    break;
	    
	case FKind.FlatNew:
	    FlatNew fnn = (FlatNew) fn;
            dst = fnn.getDst();
	    AllocationSite as = getAllocationSiteFromFlatNewPRIVATE( fnn );

	    og.assignNewAllocationToTempX( dst, as );
	    break;

	case FKind.FlatCall:
	    FlatCall                fc           = (FlatCall) fn;
	    MethodDescriptor        md           = fc.getMethod();
	    FlatMethod              flatm        = state.getMethodFlat( md );
	    //HashSet<AllocationSite> allocSiteSet = getAllocationSiteSet( md );
	    OwnershipGraph ogAllPossibleCallees  = new OwnershipGraph( allocationDepth );

	    if( md.isStatic() ) {
		// a static method is simply always the same, makes life easy
		OwnershipGraph onlyPossibleCallee = mapDescriptorToCompleteOwnershipGraph.get( md );
		ogAllPossibleCallees.merge( onlyPossibleCallee );

		/*
		if( onlyPossibleCallee != null ) {
		    onlyPossibleCallee.writeGraph( "only", false, false );
		    System.out.println( "There was only one possible callee, "+md );
		}
		*/

	    } else {
		// if the method descriptor is virtual, then there could be a
		// set of possible methods that will actually be invoked, so
		// find all of them and merge all of their graphs together
		TypeDescriptor typeDesc        = fc.getThis().getType();
		Set            possibleCallees = callGraph.getMethods( md, typeDesc );

		//int j = 0;

		Iterator i = possibleCallees.iterator();
		while( i.hasNext() ) {
		    MethodDescriptor possibleMd = (MethodDescriptor) i.next();
		    //allocSiteSet.addAll( getAllocationSiteSet( possibleMd ) );
		    OwnershipGraph ogPotentialCallee = mapDescriptorToCompleteOwnershipGraph.get( possibleMd );

		    /*
		    if( ogPotentialCallee != null ) {
			ogPotentialCallee.writeGraph( "potential"+j, false, false );
			++j;
		    }
		    */

		    ogAllPossibleCallees.merge( ogPotentialCallee );
		}

		//System.out.println( "There were "+j+" potential callees merged together." );
	    }

	    //System.out.println( "AllocationSiteSet has "+allocSiteSet.size()+" items." );

	    // now we should have the following information to resolve this method call:
	    // 
	    // 1. A FlatCall fc to query for the caller's context (argument labels, etc)
	    //
	    // 2. Whether the method is static; if not we need to deal with the "this" pointer
	    //
	    // *******************************************************************************************
	    // 3. The original FlatMethod flatm to query for callee's context (paramter labels)
	    //   NOTE!  I assume FlatMethod before virtual dispatch accurately describes all possible methods!
	    // *******************************************************************************************
	    //
	    // 4. The OwnershipGraph ogAllPossibleCallees is a merge of every ownership graph of all the possible
	    // methods to capture any possible references made.
	    //
	    // 5. The Set of AllocationSite objects, allocSiteSet that is the set of allocation sites from
	    // every possible method we might have chosen
	    //
	    og.resolveMethodCall( fc, md.isStatic(), flatm, ogAllPossibleCallees );
	    break;

	case FKind.FlatReturnNode:
	    FlatReturnNode frn = (FlatReturnNode) fn;
	    setRetNodes.add( frn );
	    break;
	}
    }


    // this method should generate integers strictly greater than zero!
    // special "shadow" regions are made from a heap region by negating
    // the ID
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



    


    // return just the allocation site associated with one FlatNew node
    private AllocationSite getAllocationSiteFromFlatNewPRIVATE( FlatNew fn ) {

	if( !mapFlatNewToAllocationSite.containsKey( fn ) ) {
	    AllocationSite as = new AllocationSite( allocationDepth, fn.getType() );

	    // the newest nodes are single objects
	    for( int i = 0; i < allocationDepth; ++i ) {
		Integer id = generateUniqueHeapRegionNodeID();
		as.setIthOldest( i, id );
	    }

	    // the oldest node is a summary node
	    Integer idSummary = generateUniqueHeapRegionNodeID();
	    as.setSummary( idSummary );

	    mapFlatNewToAllocationSite.put( fn, as );
	}

	return mapFlatNewToAllocationSite.get( fn );
    }


    // return all allocation sites in the method (there is one allocation
    // site per FlatNew node in a method)
    private HashSet<AllocationSite> getAllocationSiteSet( Descriptor d ) {
	if( !mapDescriptorToAllocationSiteSet.containsKey( d ) ) {
	    buildAllocationSiteSet( d );   
	}

	return mapDescriptorToAllocationSiteSet.get( d );

    }

    private void buildAllocationSiteSet( Descriptor d ) {
	HashSet<AllocationSite> s = new HashSet<AllocationSite>();

	FlatMethod fm;
	if( d instanceof MethodDescriptor ) {
	    fm = state.getMethodFlat( (MethodDescriptor) d );
	} else {
	    assert d instanceof TaskDescriptor;
	    fm = state.getMethodFlat( (TaskDescriptor) d );
	}

	// visit every node in this FlatMethod's IR graph
	// and make a set of the allocation sites from the
	// FlatNew node's visited
	HashSet<FlatNode> visited = new HashSet<FlatNode>();
	HashSet<FlatNode> toVisit = new HashSet<FlatNode>();
	toVisit.add( fm );

	while( !toVisit.isEmpty() ) {
	    FlatNode n = toVisit.iterator().next();

	    if( n instanceof FlatNew ) {
		s.add( getAllocationSiteFromFlatNewPRIVATE( (FlatNew) n ) );
	    }

	    toVisit.remove( n );
	    visited.add( n );

	    for( int i = 0; i < n.numNext(); ++i ) {
		FlatNode child = n.getNext( i );
		if( !visited.contains( child ) ) {
		    toVisit.add( child );
		}
	    }
	}

	mapDescriptorToAllocationSiteSet.put( d, s );
    }


    private HashSet<AllocationSite> 
	getFlaggedAllocationSitesReachableFromTaskPRIVATE( TaskDescriptor td ) {

	HashSet<AllocationSite> asSetTotal = new HashSet<AllocationSite>();
	HashSet<Descriptor>     toVisit    = new HashSet<Descriptor>();
	HashSet<Descriptor>     visited    = new HashSet<Descriptor>();

	toVisit.add( td );

	// traverse this task and all methods reachable from this task
	while( !toVisit.isEmpty() ) {
	    Descriptor d = toVisit.iterator().next();
	    toVisit.remove( d );
	    visited.add( d );
	    
	    HashSet<AllocationSite> asSet = getAllocationSiteSet( d );
	    Iterator asItr = asSet.iterator();
	    while( asItr.hasNext() ) {
		AllocationSite as = (AllocationSite) asItr.next();
		if( as.getType().getClassDesc().hasFlags() ) {
		    asSetTotal.add( as );
		}
	    }
	    
	    // enqueue callees of this method to be searched for
	    // allocation sites also
	    Set callees = callGraph.getCalleeSet( d );
	    if( callees != null ) {
		Iterator methItr = callees.iterator();
		while( methItr.hasNext() ) {
		    MethodDescriptor md = (MethodDescriptor) methItr.next();

		    if( !visited.contains( md ) ) {
			toVisit.add( md );
		    }
		}
	    }
	}
	

	return asSetTotal;
    }



    private HashSet<Integer> getHeapRegionIDset( OwnershipGraph og,
						 int paramIndex ) {
	
	assert og.paramIndex2id.containsKey( paramIndex );
	Integer idParam = og.paramIndex2id.get( paramIndex );

	HashSet<Integer> idSet = new HashSet<Integer>();
	idSet.add( idParam );

	return idSet;
    }


    private HashSet<Integer> getHeapRegionIDset( AllocationSite alloc ) {

	HashSet<Integer> idSet = new HashSet<Integer>();
	
	for( int i = 0; i < alloc.getAllocationDepth(); ++i ) {	  
	    Integer id = alloc.getIthOldest( i );	  
	    idSet.add( id );
	}
	
	Integer idSummary = alloc.getSummary();
	idSet.add( idSummary );

	return idSet;
    }

    private HashSet<Integer> getHeapRegionIDset( HashSet<AllocationSite> allocSet ) {

	HashSet<Integer> idSet = new HashSet<Integer>();

	Iterator allocItr = allocSet.iterator();
	while( allocItr.hasNext() ) {
	    AllocationSite alloc = (AllocationSite) allocItr.next();

	    for( int i = 0; i < alloc.getAllocationDepth(); ++i ) {
		Integer id = alloc.getIthOldest( i );
		idSet.add( id );
	    }
       
	    Integer idSummary = alloc.getSummary();
	    idSet.add( idSummary );
	}

	return idSet;
    }

    private boolean createsPotentialAliases( OwnershipGraph   og,
					     HashSet<Integer> idSetA,
					     HashSet<Integer> idSetB ) {
	boolean potentialAlias = false;

	/*
	// first expand set B into the set of all heap region node ID's
	// reachable from the nodes in set B
	HashSet<Integer> idSetReachableFromB = og.getReachableSet( idSetB );

	// then see if anything in A can reach a node in the set reachable
	// from B.  If so, there is a potential alias.
	Iterator i = idSetA.iterator();
	while( i.hasNext() ) {
	    Integer id = (Integer) i.next();
	    if( og.canIdReachSet( id, idSetB ) ) {
		return true;
	    }
	}
	*/

	return false;
    }
}
