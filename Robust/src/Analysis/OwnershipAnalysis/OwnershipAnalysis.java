package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipAnalysis {

    private State state;
    private HashSet<FlatNode> visited;
    private HashSet<FlatNode> toVisit;

    private int labelindex;
    private Hashtable<FlatNode, Integer> flatnodetolabel;

    private Hashtable<FlatNode, OwnershipGraph> flatNodeToOwnershipGraph;


    // this analysis generates an ownership graph for every task
    // in the program
    public OwnershipAnalysis(State state) throws java.io.IOException {
	this.state=state;      
	analyzeTasks();
    }

    public void analyzeTasks() throws java.io.IOException {
	for( Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();
	     it_tasks.hasNext();
	     ) {

	    // initialize the mapping of flat nodes to ownership graphs
	    // every flat node in the IR graph has its own ownership graph
	    flatNodeToOwnershipGraph = new Hashtable<FlatNode, OwnershipGraph>();

	    TaskDescriptor td = (TaskDescriptor)it_tasks.next();
	    FlatMethod     fm = state.getMethodFlat( td );

	    // give every node in the flat IR graph a unique label
	    // so a human being can inspect the graph and verify
	    // correctness
	    flatnodetolabel = new Hashtable<FlatNode, Integer>();
	    visited         = new HashSet<FlatNode>();
	    labelindex      = 0;
	    labelFlatNodes( fm );

	    String taskname = td.getSymbol();
	    analyzeFlatIRGraph( fm, taskname );
	}	
    }

    private void labelFlatNodes(FlatNode fn) {
	visited.add(fn);
	flatnodetolabel.put(fn,new Integer(labelindex++));
	for(int i=0;i<fn.numNext();i++) {
	    FlatNode nn=fn.getNext(i);
	    if(!visited.contains(nn)) {
		labelFlatNodes(nn);
	    }
	}
    }

    private OwnershipGraph getGraphFromFlat( FlatNode fn ) {
	if( !flatNodeToOwnershipGraph.containsKey( fn ) ) {
	    flatNodeToOwnershipGraph.put( fn, new OwnershipGraph() );
	}

	return flatNodeToOwnershipGraph.get( fn );
    }

    private void setGraphForFlat( FlatNode fn, OwnershipGraph og ) {
	flatNodeToOwnershipGraph.put( fn, og );
    }

    private void analyzeFlatIRGraph( FlatMethod flatm, String taskname ) throws java.io.IOException {
	visited=new HashSet<FlatNode>();
	toVisit=new HashSet<FlatNode>();
	toVisit.add( flatm );

	while( !toVisit.isEmpty() ) {
	    FlatNode fn = (FlatNode)toVisit.iterator().next();
	    toVisit.remove( fn );
	    visited.add( fn );

	    // perform this node's contributions to the ownership
	    // graph on a new copy, then compare it to the old graph
	    // at this node to see if anything was updated.
	    OwnershipGraph og = new OwnershipGraph();

	    // start by merging all incoming node's graphs
	    for( int i = 0; i < fn.numPrev(); ++i ) {
		FlatNode       pn       = fn.getPrev( i );
		OwnershipGraph ogParent = getGraphFromFlat( pn );
		og.merge( ogParent );
	    }
	    
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
		    og.newHeapRegion( tdParam );
		    og.addAnalysisRegion( tdParam );
		}

		nodeDescription = "Method";
		writeGraph = true;
		break;

	    case FKind.FlatOpNode:
		FlatOpNode fon = (FlatOpNode) fn;
		if(fon.getOp().getOp()==Operation.ASSIGN) {
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
		og.assignTempToField( src, dst, fld );
		nodeDescription = "Field";
		writeGraph = true;
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

	    case FKind.FlatReturnNode:
		nodeDescription = "Return";
		writeGraph = true;
		og.writeCondensedAnalysis( makeCondensedAnalysisName( taskname, flatnodetolabel.get(fn) ) );
		break;
	    }

	    if( writeGraph ) {
		og.writeGraph( makeNodeName( taskname, 
					     flatnodetolabel.get( fn ), 
					     nodeDescription ) );
	    }
	    
	    // if the results of the new graph are different from
	    // the current graph at this node, replace the graph
	    // with the update and enqueue the children for
	    // processing
	    OwnershipGraph ogOld = getGraphFromFlat( fn );

	    if( !og.equivalent( ogOld ) ) {
		setGraphForFlat( fn, og );

		for( int i = 0; i < fn.numNext(); i++ ) {
		    FlatNode nn = fn.getNext( i );
		    visited.remove( nn );
		    toVisit.add( nn );
		}
	    }
	}
    }

    private String makeNodeName( String taskname, Integer id, String type ) {
	String s = String.format( "%05d", id );
	return "task"+taskname+"_FN"+s+"_"+type;
    }

    private String makeCondensedAnalysisName( String taskname, Integer id ) {
	return "task"+taskname+"_Ownership_from"+id;
    }
}
