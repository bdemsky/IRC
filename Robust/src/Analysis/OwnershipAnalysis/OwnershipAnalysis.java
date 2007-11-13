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
	    FlatMethod fm     = state.getMethodFlat( td );

	    // give every node in the flat IR graph a unique label
	    // so a human being can inspect the graph and verify
	    // correctness
	    flatnodetolabel = new Hashtable<FlatNode, Integer>();
	    visited         = new HashSet<FlatNode>();
	    labelindex      = 0;
	    labelFlatNodes( fm );

	    // add method parameters to the list of heap regions
	    // and remember names for analysis
	    OwnershipGraph og = getGraphFromFlat( fm );
	    for( int i = 0; i < fm.numParameters(); ++i ) {
		TempDescriptor tdParam = fm.getParameter( i );
		og.newHeapRegion( tdParam );
		og.addAnalysisRegion( tdParam );
	    }

	    String taskname   = td.getSymbol();
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
	if( !flatNodeToOwnershipGraph.containsKey(fn) ) {
	    flatNodeToOwnershipGraph.put( fn, new OwnershipGraph() );
	}

	return flatNodeToOwnershipGraph.get(fn);
    }

    private void analyzeFlatIRGraph( FlatMethod fm, String taskname ) throws java.io.IOException {
	visited=new HashSet<FlatNode>();
	toVisit=new HashSet<FlatNode>();
	toVisit.add(fm);

	while( !toVisit.isEmpty() ) {
	    FlatNode fn=(FlatNode)toVisit.iterator().next();
	    toVisit.remove(fn);
	    visited.add(fn);

	    // get this node's ownership graph, or create a new one
	    OwnershipGraph og = getGraphFromFlat( fn );

	    TempDescriptor  src;
	    TempDescriptor  dst;
	    FieldDescriptor fld;

	    switch(fn.kind()) {
		
	    case FKind.FlatMethod:
		og.writeGraph( makeNodeName( taskname, flatnodetolabel.get(fn), "Method" ) );
		break;

	    case FKind.FlatOpNode:
		FlatOpNode fon = (FlatOpNode) fn;
		if(fon.getOp().getOp()==Operation.ASSIGN) {
		    src = fon.getLeft();
		    dst = fon.getDest();
		    og.assignTempToTemp( src, dst );
		    og.writeGraph( makeNodeName( taskname, flatnodetolabel.get(fn), "Op" ) );
		}
		break;

	    case FKind.FlatFieldNode:
		FlatFieldNode ffn = (FlatFieldNode) fn;
		src = ffn.getSrc();
		dst = ffn.getDst();
		fld = ffn.getField();
		og.assignTempToField( src, dst, fld );
		og.writeGraph( makeNodeName( taskname, flatnodetolabel.get(fn), "Field" ) );
		break;

	    case FKind.FlatSetFieldNode:
		FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
		src = fsfn.getSrc();
		dst = fsfn.getDst();
		fld = fsfn.getField();
		og.assignFieldToTemp( src, dst, fld );
		og.writeGraph( makeNodeName( taskname, flatnodetolabel.get(fn), "SetField" ) );
		break;

	    case FKind.FlatReturnNode:
		og.writeGraph( makeNodeName( taskname, flatnodetolabel.get(fn), "Return" ) );
		og.writeCondensedAnalysis( makeCondensedAnalysisName( taskname, flatnodetolabel.get(fn) ) );
		break;
	    }
	    
	    // send this flat node's ownership graph along the out edges
	    // to be taken by the next flat edges in the flow, or if they
	    // already have a graph, to be merged
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode nn=fn.getNext(i);

		if( !visited.contains( nn ) ) {
		    // FIX THE COPY!!!!!!!!!!!!!!!
		    //flatNodeToOwnershipGraph.put( nn, og.copy() );
		    flatNodeToOwnershipGraph.put( nn, og );
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
