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

    // Use these data structures to track progress of 
    // processing all methods in the program, and by methods
    // TaskDescriptor and MethodDescriptor are combined 
    // together, with a common parent class Descriptor
    private HashSet  <Descriptor>                 descriptorsToVisit;
    private Hashtable<Descriptor, OwnershipGraph> mapDescriptorToCompleteOwnershipGraph;

    // Use these data structures to track progress of one pass of
    // processing the FlatNodes of a particular method
    private HashSet  <FlatNode>                 flatNodesToVisit;
    private Hashtable<FlatNode, OwnershipGraph> mapFlatNodeToOwnershipGraph;    
    private HashSet  <FlatReturnNode>           returnNodesToCombineForCompleteOwnershipGraph;


    // for generating unique ownership node ID's throughout the
    // ownership analysis
    static private int uniqueIDcount = 0;

    static public Integer generateUniqueID() {
	++uniqueIDcount;
	return new Integer( uniqueIDcount );
    }


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

	// initialize methods to visit as the set of all tasks
	descriptorsToVisit = new HashSet<Descriptor>();
	Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
	while( taskItr.hasNext() ) {
	    Descriptor d = (Descriptor) taskItr.next();
	    descriptorsToVisit.add( d );
	}	
	
	// as mentioned above, analyze methods one-by-one, possibly revisiting
	// a method if the methods that it calls are updated
	analyzeMethods();
    }

    private void analyzeMethods() throws java.io.IOException {
	
	while( !descriptorsToVisit.isEmpty() ) {
	    Descriptor d = (Descriptor) descriptorsToVisit.iterator().next();
	    descriptorsToVisit.remove( d );

	    System.out.println( "Analyzing " + d );

	    // because the task or method descriptor just extracted
	    // in the "to visit" set it either hasn't been analyzed
	    // yet, or some method that it depends on has been
	    // updated.  Recompute a complete ownership graph for
	    // this task/method and compare it to any previous result.
	    // If there is a change detected, add any methods/tasks
	    // that depend on this one to the "to visit" set.
	    OwnershipGraph og = new OwnershipGraph( allocationDepth );

	    
	    OwnershipGraph ogPrev = mapDescriptorToCompleteOwnershipGraph.get( d );

	    if( !og.equals( ogPrev ) ) {

		mapDescriptorToCompleteOwnershipGraph.put( d, og );

		// only methods have dependents, tasks cannot
		// be invoked by any user program calls
		if( d instanceof MethodDescriptor ) {
		    MethodDescriptor md = (MethodDescriptor) d;
		    Set dependents = callGraph.getCallerSet( md );
		    descriptorsToVisit.addAll( dependents );
		}
	    }
	}


	/*
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
	*/
    }

    /*
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
    */
}
