package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipGraph {

    protected int heapRegionNodeIDs;
    protected Vector<OwnershipHeapRegionNode> heapRoots;

    protected int labelNodeIDs;
    protected Hashtable<TempDescriptor, OwnershipLabelNode> td2ln;

    protected Vector<TempDescriptor> analysisRegionLabels;
    protected Hashtable<TempDescriptor, TempDescriptor> linkedRegions;
    
    public OwnershipGraph() {
	heapRegionNodeIDs = 0;
	heapRoots = new Vector<OwnershipHeapRegionNode>();

	labelNodeIDs = 0;
	td2ln = new Hashtable<TempDescriptor, OwnershipLabelNode>();

	analysisRegionLabels = new Vector<TempDescriptor>(); 
	linkedRegions = new Hashtable<TempDescriptor, TempDescriptor>();
    }

    public void assignTempToTemp( TempDescriptor src, 
				  TempDescriptor dst ) {
	OwnershipLabelNode srcln = getLabelNodeFromTemp( src );
	OwnershipHeapRegionNode hrn = srcln.getOwnershipHeapRegionNode();
	OwnershipLabelNode dstln = getLabelNodeFromTemp( dst );
	dstln.setOwnershipHeapRegionNode( hrn );
    }

    public void assignTempToField( TempDescriptor src, 
				   TempDescriptor dst,
				   FieldDescriptor fd ) {
	OwnershipLabelNode srcln = getLabelNodeFromTemp( src );
	OwnershipHeapRegionNode hrn = srcln.getOwnershipHeapRegionNode();
	OwnershipLabelNode dstln = getLabelNodeFromTemp( dst );
	dstln.setOwnershipHeapRegionNode( hrn.getField( fd ) );
    }

    public void assignFieldToTemp( TempDescriptor src, 
				   TempDescriptor dst,
				   FieldDescriptor fd ) {
	OwnershipLabelNode srcln = getLabelNodeFromTemp( src );
	OwnershipHeapRegionNode srchrn = srcln.getOwnershipHeapRegionNode();
	OwnershipLabelNode dstln = getLabelNodeFromTemp( dst );
	OwnershipHeapRegionNode dsthrn = dstln.getOwnershipHeapRegionNode();
	dsthrn.setField( fd, srchrn );
    }

    public void newHeapRegion( TempDescriptor td ) {
	TypeDescriptor typeDesc = td.getType();
	OwnershipHeapRegionNode hrn = allocate( typeDesc );
	OwnershipLabelNode ln = getLabelNodeFromTemp( td );
	ln.setOwnershipHeapRegionNode( hrn );
	heapRoots.add( hrn );
    }

    public void addAnalysisRegion( TempDescriptor td ) {
	analysisRegionLabels.add( td );
    }

    protected OwnershipHeapRegionNode allocate( TypeDescriptor typeDesc ) {
	OwnershipHeapRegionNode hrn = 
	    new OwnershipHeapRegionNode( heapRegionNodeIDs );
	++heapRegionNodeIDs;
	
	if( typeDesc.isClass() ) {
	    ClassDescriptor classDesc = typeDesc.getClassDesc();
	    Iterator fieldItr = classDesc.getFields();
	    while( fieldItr.hasNext() ) {
		FieldDescriptor fd = (FieldDescriptor)fieldItr.next();
		TypeDescriptor fieldType = fd.getType();
		OwnershipHeapRegionNode fieldNode = allocate( fieldType );
		hrn.setField( fd, fieldNode );
	    }	    
	}

	return hrn;
    }
    
    protected OwnershipLabelNode getLabelNodeFromTemp( TempDescriptor td ) {
	if( !td2ln.containsKey( td ) ) {
	    td2ln.put( td, new OwnershipLabelNode( labelNodeIDs, td ) );
	    ++labelNodeIDs;
	}

	return td2ln.get( td );
    }

    /*
    public OwnershipGraph copy() {
	OwnershipGraph newog = new OwnershipGraph();

	// first create a node in the new graph from
	// every temp desc in the old graph
	Set s = td2on.entrySet();
	Iterator i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    OwnershipLabelNode nu = (OwnershipLabelNode) me.getValue();
	    newog.getOwnershipFromTemp( nu.getTempDescriptor() );
	}

	// then use every out-edge of the old graph to
	// create the edges of the new graph
	i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    OwnershipLabelNode nu = (OwnershipLabelNode) me.getValue();
	    for( int j = 0; j < nu.numOutEdges(); ++j ) {
		OwnershipLabelNode nv = nu.getOutEdge( j );
		newog.addEdge( nu.getTempDescriptor(), nv.getTempDescriptor() );
	    }
	}

	return newog;
    }    
    */

    public void writeGraph( String graphName ) throws java.io.IOException {
	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "digraph "+graphName+" {\n" );

	for( int i = 0; i < heapRoots.size(); ++i ) {
	    OwnershipHeapRegionNode hrn = heapRoots.get( i );
	    visitHeapNodes( bw, hrn );
	}

	Set s = td2ln.entrySet();
	Iterator i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    OwnershipLabelNode ln = (OwnershipLabelNode) me.getValue();
	    OwnershipHeapRegionNode lnhrn = ln.getOwnershipHeapRegionNode();
	    bw.write( "  "+ln.toString()+" -> "+lnhrn.toString()+";\n" );
	}

	bw.write( "}\n" );
	bw.close();
    }

    protected void visitHeapNodes( BufferedWriter bw,
				   OwnershipHeapRegionNode hrn ) throws java.io.IOException {
	bw.write( "  "+hrn.toString()+"[shape=box];\n" );	

	Iterator fitr = hrn.getFieldIterator();
	while( fitr.hasNext() ) {
	    Map.Entry me = (Map.Entry) fitr.next();
	    FieldDescriptor         fd       = (FieldDescriptor)         me.getKey();
	    OwnershipHeapRegionNode childhrn = (OwnershipHeapRegionNode) me.getValue();
	    bw.write( "  "+hrn.toString()+" -> "+childhrn.toString()+
		      "[label=\""+fd.getSymbol()+"\"];\n" );
	    visitHeapNodes( bw, childhrn );
	}
    }

    public void writeCondensedAnalysis( String graphName ) throws java.io.IOException {
	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "graph "+graphName+" {\n" );

	// find linked regions
	for( int i = 0; i < analysisRegionLabels.size(); ++i ) {
	    TempDescriptor td = analysisRegionLabels.get( i );
	    bw.write( "  "+td.getSymbol()+";\n" );

	    OwnershipLabelNode      oln = getLabelNodeFromTemp( td );
	    OwnershipHeapRegionNode hrn = oln.getOwnershipHeapRegionNode();
	    condensedVisitHeapNodes( bw, hrn, td );
	}

	// write out linked regions	
	Set s = linkedRegions.entrySet();
	Iterator lri = s.iterator();
	while( lri.hasNext() ) {
	    Map.Entry me = (Map.Entry) lri.next();
	    TempDescriptor t1 = (TempDescriptor) me.getKey();
	    TempDescriptor t2 = (TempDescriptor) me.getValue();
	    bw.write( "  "+t1.getSymbol()+" -- "+t2.getSymbol()+";\n" );
	}

	bw.write( "}\n" );
	bw.close();
    }

    protected void condensedVisitHeapNodes( BufferedWriter bw,
					    OwnershipHeapRegionNode hrn,
					    TempDescriptor td ) throws java.io.IOException {
	hrn.addAnalysisRegionAlias( td );

	Iterator fitr = hrn.getFieldIterator();
	while( fitr.hasNext() ) {
	    Map.Entry me = (Map.Entry) fitr.next();
	    FieldDescriptor         fd       = (FieldDescriptor)         me.getKey();
	    OwnershipHeapRegionNode childhrn = (OwnershipHeapRegionNode) me.getValue();

	    Vector<TempDescriptor> aliases = childhrn.getAnalysisRegionAliases();
	    for( int i = 0; i < aliases.size(); ++i ) {
		TempDescriptor tdn = aliases.get( i );

		// only add this alias if it has not been already added		
		TempDescriptor tdAlias = null;
		if( linkedRegions.containsKey( td ) ) {
		    tdAlias = linkedRegions.get( td );
		}

		TempDescriptor tdnAlias = null;		
		if( linkedRegions.containsKey( tdn ) ) {
		    tdnAlias = linkedRegions.get( tdn );
		}

		if( tdn != tdAlias && td != tdnAlias ) {
		    linkedRegions.put( td, tdn );
		}
	    }

	    condensedVisitHeapNodes( bw, childhrn, td );
	}
    }
}
