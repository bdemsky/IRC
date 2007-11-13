package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipAnalysis {

    private State state;

    private BufferedWriter flatbw;

    private HashSet<FlatNode> visited;
    private HashSet<FlatNode> toVisit;
    
    private int labelindex;
    private Hashtable<FlatNode, Integer> flatnodetolabel;
    private Hashtable<FlatNode, OwnershipGraph> flatNodeToOwnershipGraph;


    // this analysis generates an ownership graph for every task
    // in the program
    public OwnershipAnalysis(State state) throws java.io.IOException {
	this.state=state;
	
	System.out.println( "Performing Ownership Analysis..." );

	// analyzeTasks();
    }

    /*
    public void analyzeTasks() throws java.io.IOException {
	for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();it_tasks.hasNext();) {

	    // extract task data and flat IR graph of the task
	    TaskDescriptor td = (TaskDescriptor)it_tasks.next();
	    FlatMethod fm = state.getMethodFlat(td);
	    String taskname=td.getSymbol();

	    // give every node in the flat IR graph a unique label
	    // so a human being can inspect the graph and verify
	    // correctness
	    flatnodetolabel=new Hashtable<FlatNode, Integer>();
	    visited=new HashSet<FlatNode>();
	    labelindex=0;
	    labelFlatNodes(fm);

	    // initialize the mapping of flat nodes to ownership graphs
	    // every flat node in the IR graph has its own ownership graph
	    flatNodeToOwnershipGraph = new Hashtable<FlatNode, OwnershipGraph>();

	    flatbw=new BufferedWriter(new FileWriter("task"+taskname+"_FlatIR.dot"));
	    flatbw.write("digraph Task"+taskname+" {\n");

	    analyzeFlatIRGraph(fm,taskname);

	    flatbw.write("}\n");
	    flatbw.close();
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

	    if( fn.kind() == FKind.FlatMethod ) {

		// FlatMethod does not have toString
		flatbw.write( makeDotNodeDec( taskname, flatnodetolabel.get(fn), fn.getClass().getName(), "FlatMethod" ) );

		FlatMethod fmd = (FlatMethod) fn;
		// the FlatMethod is the top-level node, so take the opportunity to
		// generate regions of the heap for each parameter to start the
		// analysis
		for( int i = 0; i < fmd.numParameters(); ++i ) {
		    TempDescriptor tdParam = fmd.getParameter( i );
		    og.newHeapRegion( tdParam );
		}
	    } else {
		flatbw.write( makeDotNodeDec( taskname, flatnodetolabel.get(fn), fn.getClass().getName(), fn.toString() ) );
	    }

	    TempDescriptor  src;
	    TempDescriptor  dst;
	    FieldDescriptor fld;
	    switch(fn.kind()) {
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
		break;
	    }
	    
	    // send this flat node's ownership graph along the out edges
	    // to be taken by the next flat edges in the flow, or if they
	    // already have a graph, to be merged
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode nn=fn.getNext(i);

		flatbw.write( "  node"+flatnodetolabel.get(fn)+" -> node"+flatnodetolabel.get(nn)+";\n" );

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

    private String makeDotNodeDec( String taskname, Integer id, String type, String details ) {
	if( details == null ) {
	    return "  node"+id+"[label=\""+makeNodeName(taskname,id,type)+"\"];\n";
	} else {
	    return "  node"+id+"[label=\""+makeNodeName(taskname,id,type)+"\\n"+details+"\"];\n";
	}
    }
    */
}
