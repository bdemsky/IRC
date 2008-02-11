package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipAnalysis {

    // from the compiler
    private State state;
    private int   allocationDepthK;

    // Use these data structures to track progress of 
    // processing all methods in the program
    private HashSet  <FlatMethod>                       flatMethodsToVisit;
    private Hashtable<FlatMethod, OwnershipGraph>       mapFlatMethodToCompleteOwnershipGraph;
    private Hashtable<Descriptor, HashSet<Descriptor> > mapMethodToDependentMethods;

    // Use these data structures to track progress of one pass of
    // processing the FlatNodes in a FlatMethod
    private HashSet  <FlatNode>                 flatNodesToVisit;
    private Hashtable<FlatNode, OwnershipGraph> mapFlatNodeToOwnershipGraph;    
    private HashSet  <FlatReturnNode>           returnNodesToCombineForComplete;

    // for generating unique ownership node ID's throughout the
    // ownership analysis
    static private int uniqueIDcount = 0;

    static public Integer generateUniqueID() {
	++uniqueIDcount;
	return new Integer( uniqueIDcount );
    }


    // this analysis generates an ownership graph for every task
    // in the program
    public OwnershipAnalysis( State state ) throws java.io.IOException {
	this.state            = state;      
	this.allocationDepthK = 3;

	// the analyzeMethods() function will keep analyzing the contents
	// of flatMethodsToVisit until it is empty.  The following function
	// will generate the method dependencies and put every task (subclass
	// of method) in flatMethodsToVisit so the ownership analysis will
	// cover at least every task, but might not analyze every method of
	// the program if there is no dependency chain back to a task
	prepareForAndEnqueueTasks();

	// as mentioned above, analyze methods one-by-one, possibly revisiting
	// a method if the methods that it calls are updated
	analyzeMethods();
    }

    // run once per program analysis
    private void prepareForAndEnqueueTasks() {
	flatMethodsToVisit = new HashSet<FlatMethod>();

	mapFlatMethodToCompleteOwnershipGraph =
	    new Hashtable<FlatMethod, OwnershipGraph>();

	mapMethodToDependentMethods =
	    new Hashtable<Descriptor, HashSet<Descriptor> >();

	// once the dependency map is generated here it
	// doesn't need to be modified anymore.  It is
	// used when a method is updated to enqueue the
	// methods that are dependent on the result.  They
	// may or may not have a different analysis outcome

	// first put all the tasks into the dependency map
	Iterator itrTasks = state.getTaskSymbolTable().getDescriptorsIterator();
	while( itrTasks.hasNext() ) {
	    TaskDescriptor td = itrTasks.next();
	    FlatMethod     fm = state.getMethodFlat( td );
	    
	    searchFlatMethodForCallNodes( td, fm );
	}

	// then put all the methods of classes into the map
    }

    // called on each task and class method before ownership
    // analysis is started so the method dependency map can
    // be generated
    private void searchFlatMethodForCallNodes( Descriptor caller, FlatMethod fm ) {
	// borrow this data structure before the ownership
	// analysis proper begins in order to search the IR
	flatNodesToVisit = new HashSet<FlatNode>();

	flatNodesToVisit.add( fm );

	while( !flatNodesToVisit.isEmpty() ) {
	    FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
	    flatNodesToVisit.remove( fn );
	    
	    if( fn.kind() == FKind.FlatClass ) {
		FlatCall fc = (FlatCall) fn;
		addMethodDependency( caller, fc.getMethod() );
	    }
	}
    }

    // supports creation of the method dependency map
    private void addMethodDependency( Descriptor caller, Descriptor callee ) {

	// the caller is calling the callee so the caller's
	// analysis depends on the callee's analysis,
	// therefore build a map that looks like this:
	//   callee: <caller1, caller2, ...>
	//
	// and only add the dependency if it doesn't already exist

	if( !mapMethodToDependentMethods.containsKey( callee ) ) {
	    // there is no map entry for this callee, so add it with
	    // just the caller as the only dependent method and return
	    mapMethodToDependentMethods.put( callee, caller );
	    return;
	}

	// if there is already an entry for the callee, check to see
	// if the caller is already in the value for that map entry
	HashSet dependentMethods = (HashSet) mapMethodToDependentMethods.get( callee );
	
	if( !dependentMethods.contains( caller ) ) {
	    // the caller is not in the set of dependent methods for
	    // this callee, so add it and return
	    dependentMethods.add( caller );
	    return;
	}

	// the dependency was already there, do nothing
    }


    private void analyzeMethods() throws java.io.IOException {
	for( Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();
	     it_tasks.hasNext();
	     ) {

	    // initialize the mapping of flat nodes to ownership graphs
	    // every flat node in the IR graph has its own ownership graph
	    flatNodeToOwnershipGraph = new Hashtable<FlatNode, OwnershipGraph>();

	    TaskDescriptor td = (TaskDescriptor) it_tasks.next();
	    FlatMethod     fm = state.getMethodFlat( td );

	    // give every node in the flat IR graph a unique label
	    // so a human being can inspect the graph and verify
	    // correctness
	    flatnodetolabel = new Hashtable<FlatNode, Integer>();
	    visited         = new HashSet<FlatNode>();
	    labelindex      = 0;
	    labelFlatNodes( fm );

	    String methodname = td.getSymbol();
	    analyzeFlatIRGraph( fm, methodname );
	}	
    }

    private OwnershipGraph getGraphFromFlatNode( FlatNode fn ) {
	if( !flatNodeToOwnershipGraph.containsKey( fn ) ) {
	    flatNodeToOwnershipGraph.put( fn, new OwnershipGraph( newDepthK ) );
	}

	return flatNodeToOwnershipGraph.get( fn );
    }

    private void setGraphForFlatNode( FlatNode fn, OwnershipGraph og ) {
	flatNodeToOwnershipGraph.put( fn, og );
    }

    private void analyzeFlatMethod( FlatMethod flatm, String methodname ) throws java.io.IOException {
	toVisit=new HashSet<FlatNode>();
	toVisit.add( flatm );

	while( !toVisit.isEmpty() ) {
	    FlatNode fn = (FlatNode) toVisit.iterator().next();
	    toVisit.remove( fn );

	    // perform this node's contributions to the ownership
	    // graph on a new copy, then compare it to the old graph
	    // at this node to see if anything was updated.
	    OwnershipGraph og = new OwnershipGraph( newDepthK );

	    // start by merging all node's parents' graphs
	    for( int i = 0; i < fn.numPrev(); ++i ) {
		FlatNode       pn       = fn.getPrev( i );
		OwnershipGraph ogParent = getGraphFromFlatNode( pn );
		og.merge( ogParent );
	    }
	    
	    analyzeFlatNode( fn );
	    
	    // if the results of the new graph are different from
	    // the current graph at this node, replace the graph
	    // with the update and enqueue the children for
	    // processing
	    OwnershipGraph ogOld = getGraphFromFlatNode( fn );

	    if( !og.equals( ogOld ) ) {
		setGraphForFlatNode( fn, og );

		for( int i = 0; i < fn.numNext(); i++ ) {
		    FlatNode nn = fn.getNext( i );
		    visited.remove( nn );
		    toVisit.add( nn );
		}
	    }
	}
    }


    private void analyzeFlatNode( FlatNode fn, String methodname ) {
	    TempDescriptor  src;
	    TempDescriptor  dst;
	    FieldDescriptor fld;
	    String nodeDescription = "No description";
	    boolean writeGraph = false;

	    // use node type to decide what alterations to make
	    // to the ownership graph	    
	    switch( fn.kind() ) {
		
	    case FKind.FlatMethod:
		FlatMethod fm = (FlatMethod) fn;

		// add method parameters to the list of heap regions
		// and remember names for analysis
		for( int i = 0; i < fm.numParameters(); ++i ) {
		    TempDescriptor tdParam = fm.getParameter( i );
		    og.parameterAllocation( tdParam );
		    og.addAnalysisRegion( tdParam );
		}

		nodeDescription = "Method";
		writeGraph = true;
		break;

	    case FKind.FlatOpNode:
		FlatOpNode fon = (FlatOpNode) fn;
		if( fon.getOp().getOp() == Operation.ASSIGN ) {
		    src = fon.getLeft();
		    dst = fon.getDest();
		    og.assignTempToTemp( src, dst );
		    nodeDescription = "Op";
		    writeGraph = true;
		}
		break;

	    case FKind.FlatFieldNode:
		FlatFieldNode ffn = (FlatFieldNode) fn;
		src = ffn.getSrc();
		dst = ffn.getDst();
		fld = ffn.getField();
		if( !fld.getType().isPrimitive() ) {
		    og.assignTempToField( src, dst, fld );
		    nodeDescription = "Field";
		    writeGraph = true;
		}
		break;

	    case FKind.FlatSetFieldNode:
		FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
		src = fsfn.getSrc();
		dst = fsfn.getDst();
		fld = fsfn.getField();
		og.assignFieldToTemp( src, dst, fld );
		nodeDescription = "SetField";
		writeGraph = true;
		break;

	    case FKind.FlatNew:
		FlatNew fnn = (FlatNew) fn;
		dst = fnn.getDst();
		og.assignTempToNewAllocation( dst, fnn );

		// !!!!!!!!!!!!!!
		// do this if the new object is a flagged type
		//og.addAnalysisRegion( tdParam );

		nodeDescription = "New";
		writeGraph = true;
		break;

	    case FKind.FlatReturnNode:
		nodeDescription = "Return";
		writeGraph = true;
		og.writeCondensedAnalysis( makeCondensedAnalysisName( methodname, flatnodetolabel.get(fn) ) );
		break;
	    }

	    if( writeGraph ) {
		og.writeGraph( makeNodeName( methodname, 
					     flatnodetolabel.get( fn ), 
					     nodeDescription ) );
	    }
    }


    private String makeNodeName( String methodname, Integer id, String type ) {
	String s = String.format( "%05d", id );
	return "method_"+methodname+"_FN"+s+"_"+type;
    }

    private String makeCondensedAnalysisName( String methodname, Integer id ) {
	return "method_"+methodname+"_Ownership_from"+id;
    }
}
