package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class OwnershipGraph {

    protected static int heapRegionNodeIDs = 0;
    public Hashtable<Integer, OwnershipHeapRegionNode> id2ohrn;
    public Hashtable<Integer, OwnershipHeapRegionNode> heapRoots;

    protected static int labelNodeIDs = 0;
    public Hashtable<TempDescriptor, OwnershipLabelNode> td2ln;

    protected Vector<TempDescriptor> analysisRegionLabels;
    protected Hashtable<TempDescriptor, TempDescriptor> linkedRegions;

    protected static final int VISIT_OHRN_WRITE_FULL      = 0;
    protected static final int VISIT_OHRN_WRITE_CONDENSED = 1;

    public OwnershipGraph() {
	id2ohrn = new Hashtable<Integer, OwnershipHeapRegionNode>();
	heapRoots = new Hashtable<Integer, OwnershipHeapRegionNode>();

	td2ln = new Hashtable<TempDescriptor, OwnershipLabelNode>();

	analysisRegionLabels = new Vector<TempDescriptor>(); 
	linkedRegions = new Hashtable<TempDescriptor, TempDescriptor>();
    }

    public void assignTempToTemp( TempDescriptor src, 
				  TempDescriptor dst ) {

	OwnershipLabelNode srcln = getLabelNodeFromTemp( src );
	OwnershipLabelNode dstln = getLabelNodeFromTemp( dst );

	dstln.clearReachableRegions();
	OwnershipHeapRegionNode ohrn = null;
        Iterator srcRegionsItr = srcln.iteratorToReachableRegions();
	while( srcRegionsItr.hasNext() ) {
	    ohrn = (OwnershipHeapRegionNode)srcRegionsItr.next();

	    dstln.addReachableRegion( ohrn );
	}
    }

    public void assignTempToField( TempDescriptor src,
				   TempDescriptor dst,
				   FieldDescriptor fd ) {

	OwnershipLabelNode srcln = getLabelNodeFromTemp( src );
	OwnershipLabelNode dstln = getLabelNodeFromTemp( dst );

	dstln.clearReachableRegions();
	OwnershipHeapRegionNode ohrn = null;
	Iterator srcRegionsItr = srcln.iteratorToReachableRegions();
	while( srcRegionsItr.hasNext() ) {
	    ohrn = (OwnershipHeapRegionNode)srcRegionsItr.next();

	    OwnershipHeapRegionNode ohrnOneHop = null;
	    Iterator ohrnRegionsItr = ohrn.iteratorToReachableRegions();
	    while( ohrnRegionsItr.hasNext() ) {
		ohrnOneHop = (OwnershipHeapRegionNode)ohrnRegionsItr.next();

		dstln.addReachableRegion( ohrnOneHop );
	    }
	}
    }

    public void assignFieldToTemp( TempDescriptor src, 
				   TempDescriptor dst,
				   FieldDescriptor fd ) {

	OwnershipLabelNode srcln = getLabelNodeFromTemp( src );
	OwnershipLabelNode dstln = getLabelNodeFromTemp( dst );

	OwnershipHeapRegionNode ohrn = null;
	Iterator dstRegionsItr = dstln.iteratorToReachableRegions();
	while( dstRegionsItr.hasNext() ) {
	    ohrn = (OwnershipHeapRegionNode)dstRegionsItr.next();

	    OwnershipHeapRegionNode ohrnSrc = null;
	    Iterator srcRegionsItr = srcln.iteratorToReachableRegions();
	    while( srcRegionsItr.hasNext() ) {
		ohrnSrc = (OwnershipHeapRegionNode)srcRegionsItr.next();	       
		ohrn.addReachableRegion( ohrnSrc );
	    }
	}	
    }

    // for parameters
    public void newHeapRegion( TempDescriptor td ) {

	Integer id = new Integer( heapRegionNodeIDs );
	++heapRegionNodeIDs;
	OwnershipHeapRegionNode ohrn = new OwnershipHeapRegionNode( id );
	ohrn.addReachableRegion( ohrn );
	id2ohrn.put( id, ohrn );

	OwnershipLabelNode ln = getLabelNodeFromTemp( td );
	ln.clearReachableRegions();
	ln.addReachableRegion( ohrn );

	heapRoots.put( ohrn.getID(), ohrn );
    }

    public void addAnalysisRegion( TempDescriptor td ) {
	analysisRegionLabels.add( td );
    }

    protected OwnershipLabelNode getLabelNodeFromTemp( TempDescriptor td ) {
	if( !td2ln.containsKey( td ) ) {
	    Integer id = new Integer( labelNodeIDs );
	    td2ln.put( td, new OwnershipLabelNode( id, td ) );
	    ++labelNodeIDs;
	}

	return td2ln.get( td );
    }



    ////////////////////////////////////////////////////
    //
    //  In the functions merge() and equivalent(),
    //  use the heap region node IDs to equate heap
    //  region nodes and use temp descriptors to equate 
    //  label nodes.
    //
    //  in these functions the graph of this object is B
    //  and the graph of the incoming object is A
    //
    ////////////////////////////////////////////////////

    public void merge( OwnershipGraph og ) {

	// make sure all the heap region nodes from the
	// incoming graph that this graph does not have
	// are allocated-their edges will be added later
	Set sA = og.id2ohrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    Integer                 idA   = (Integer)                 meA.getKey();
	    OwnershipHeapRegionNode ohrnA = (OwnershipHeapRegionNode) meA.getValue();
	    
	    // if this graph doesn't have a node the
	    // incoming graph has, allocate it
	    if( !id2ohrn.containsKey( idA ) ) {
		OwnershipHeapRegionNode ohrnNewB = new OwnershipHeapRegionNode( idA );
		id2ohrn.put( idA, ohrnNewB );
	    }
	}

	// add heap region->heap region edges that are
	// in the incoming graph and not in this graph
	sA = og.id2ohrn.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    Integer                 idA   = (Integer)                 meA.getKey();
	    OwnershipHeapRegionNode ohrnA = (OwnershipHeapRegionNode) meA.getValue();

	    OwnershipHeapRegionNode ohrnChildA = null;
	    Iterator heapRegionsItrA = ohrnA.iteratorToReachableRegions();
	    
	    while( heapRegionsItrA.hasNext() ) {
		ohrnChildA = (OwnershipHeapRegionNode)heapRegionsItrA.next();

		Integer idChildA = ohrnChildA.getID();

		// at this point we know an edge in graph A exists
		// idA -> idChildA, does this exist in B?
		boolean edgeFound = false;
		assert id2ohrn.containsKey( idA );
		OwnershipHeapRegionNode ohrnB = id2ohrn.get( idA );

		OwnershipHeapRegionNode ohrnChildB = null;
		Iterator heapRegionsItrB = ohrnB.iteratorToReachableRegions();
		while( heapRegionsItrB.hasNext() ) {
		    ohrnChildB = (OwnershipHeapRegionNode)heapRegionsItrB.next();

		    if( ohrnChildB.getID() == idChildA ) {
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    assert id2ohrn.containsKey( idChildA );
		    OwnershipHeapRegionNode ohrnChildToAddB = id2ohrn.get( idChildA );
		    ohrnB.addReachableRegion( ohrnChildToAddB );
		}
	    } 
	}

	// now add any label nodes that are in graph B but
	// not in A, and at the same time construct any
	// edges that are not in A
        sA = og.td2ln.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    TempDescriptor     tdA  = (TempDescriptor)     meA.getKey();
	    OwnershipLabelNode olnA = (OwnershipLabelNode) meA.getValue();

	    // if the label doesn't exist in B, allocate and add it
	    if( !td2ln.containsKey( tdA ) ) {
		Integer idA = olnA.getID();
		OwnershipLabelNode olnNewB = new OwnershipLabelNode( idA, tdA );
		td2ln.put( tdA, olnNewB );
	    }

	    OwnershipHeapRegionNode ohrnChildA = null;
	    Iterator heapRegionsItrA = olnA.iteratorToReachableRegions();
	    while( heapRegionsItrA.hasNext() ) {
		ohrnChildA = (OwnershipHeapRegionNode)heapRegionsItrA.next();

		Integer idChildA = ohrnChildA.getID();

		// at this point we know an edge in graph A exists
		// tdA -> idChildA, does this edge exist in B?
		boolean edgeFound = false;
		assert td2ln.containsKey( tdA );
		OwnershipLabelNode olnB = td2ln.get( tdA );

		OwnershipHeapRegionNode ohrnChildB = null;
		Iterator heapRegionsItrB = olnB.iteratorToReachableRegions();
		while( heapRegionsItrB.hasNext() ) {
		    ohrnChildB = (OwnershipHeapRegionNode)heapRegionsItrB.next();

		    if( ohrnChildB.getID() == idChildA ) {
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    assert id2ohrn.containsKey( idChildA );
		    OwnershipHeapRegionNode ohrnChildToAddB = id2ohrn.get( idChildA );
		    olnB.addReachableRegion( ohrnChildToAddB );
		}
	    } 
	}

	// also merge the heapRoots
	sA = og.heapRoots.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    Integer                 idA   = (Integer)                 meA.getKey();
	    OwnershipHeapRegionNode ohrnA = (OwnershipHeapRegionNode) meA.getValue();

	    if( !heapRoots.containsKey( idA ) ) {
		
		assert id2ohrn.containsKey( idA );
		OwnershipHeapRegionNode ohrnB = id2ohrn.get( idA );
		heapRoots.put( idA, ohrnB );
	    }
	}
    }

    // see notes for merge() above about how to equate
    // nodes in ownership graphs
    public boolean equivalent( OwnershipGraph og ) {
	
	// are all heap region nodes in B also in A?
	Set sB = id2ohrn.entrySet();
	Iterator iB = sB.iterator();
	while( iB.hasNext() ) {
	    Map.Entry meB = (Map.Entry) iB.next();
	    Integer idB = (Integer) meB.getKey();
	    if( !og.id2ohrn.containsKey( idB ) ) {
		return false;
	    }
	}	

	// for every heap region node in A, make sure
	// it is in B and then check that they have
	// all the same edges
	Set sA = og.id2ohrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    Integer                 idA   = (Integer)                 meA.getKey();
	    OwnershipHeapRegionNode ohrnA = (OwnershipHeapRegionNode) meA.getValue();
	    
	    if( !id2ohrn.containsKey( idA ) ) {
		return false;
	    }

	    OwnershipHeapRegionNode ohrnChildA = null;
	    Iterator heapRegionsItrA = ohrnA.iteratorToReachableRegions();
	    while( heapRegionsItrA.hasNext() ) {
		ohrnChildA = (OwnershipHeapRegionNode)heapRegionsItrA.next();

		Integer idChildA = ohrnChildA.getID();

		// does this child exist in B?
		if( !id2ohrn.containsKey( idChildA ) ) {
		    return false;
		}

		// at this point we know an edge in graph A exists
		// idA -> idChildA, does this edge exist in B?
		boolean edgeFound = false;
		assert id2ohrn.containsKey( idA );
		OwnershipHeapRegionNode ohrnB = id2ohrn.get( idA );

		OwnershipHeapRegionNode ohrnChildB = null;
		Iterator heapRegionsItrB = ohrnB.iteratorToReachableRegions();
		while( heapRegionsItrB.hasNext() ) {
		    ohrnChildB = (OwnershipHeapRegionNode)heapRegionsItrB.next();

		    if( ohrnChildB.getID() == idChildA ) {
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    return false;
		}
	    } 
	}

	// are all label nodes in B also in A?
	sB = td2ln.entrySet();
	iB = sB.iterator();
	while( iB.hasNext() ) {
	    Map.Entry meB = (Map.Entry) iB.next();
	    TempDescriptor tdB = (TempDescriptor) meB.getKey();
	    if( !og.td2ln.containsKey( tdB ) ) {
		return false;
	    }
	}		

	// for every label node in A make sure it is in
	// B and has the same references
        sA = og.td2ln.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    TempDescriptor     tdA  = (TempDescriptor)     meA.getKey();
	    OwnershipLabelNode olnA = (OwnershipLabelNode) meA.getValue();

	    if( !td2ln.containsKey( tdA ) ) {
		return false;
	    }

	    OwnershipHeapRegionNode ohrnChildA = null;
	    Iterator heapRegionsItrA = olnA.iteratorToReachableRegions();
	    while( heapRegionsItrA.hasNext() ) {
		ohrnChildA = (OwnershipHeapRegionNode)heapRegionsItrA.next();

		Integer idChildA = ohrnChildA.getID();

		// does this child exist in B?
		if( !id2ohrn.containsKey( idChildA ) ) {
		    return false;
		}

		// at this point we know an edge in graph A exists
		// tdA -> idChildA, does this edge exist in B?
		boolean edgeFound = false;
		assert td2ln.containsKey( tdA );
		OwnershipLabelNode olnB = td2ln.get( tdA );

		OwnershipHeapRegionNode ohrnChildB = null;
		Iterator heapRegionsItrB = olnB.iteratorToReachableRegions();
		while( heapRegionsItrB.hasNext() ) {
		    ohrnChildB = (OwnershipHeapRegionNode)heapRegionsItrB.next();

		    if( ohrnChildB.getID() == idChildA ) {
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    return false;
		}
	    } 
	}

	// finally check if the heapRoots are equivalent
	sA = og.heapRoots.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    Integer                 idA   = (Integer)                 meA.getKey();
	    OwnershipHeapRegionNode ohrnA = (OwnershipHeapRegionNode) meA.getValue();

	    if( !heapRoots.containsKey( idA ) ) {
		return false;
	    }
	}

	return true;
    }

    public void writeGraph( String graphName ) throws java.io.IOException {

	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "digraph "+graphName+" {\n" );

	HashSet<OwnershipHeapRegionNode> visited = new HashSet<OwnershipHeapRegionNode>();

	Set s = heapRoots.entrySet();
	Iterator i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    OwnershipHeapRegionNode ohrn = (OwnershipHeapRegionNode) me.getValue();
	    traverseHeapNodes( VISIT_OHRN_WRITE_FULL, ohrn, bw, null, visited );
	}

	s = td2ln.entrySet();
	i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    OwnershipLabelNode oln = (OwnershipLabelNode) me.getValue();

	    OwnershipHeapRegionNode ohrn = null;
	    Iterator heapRegionsItr = oln.iteratorToReachableRegions();
	    while( heapRegionsItr.hasNext() ) {
		ohrn = (OwnershipHeapRegionNode)heapRegionsItr.next();

		bw.write( "  "+oln.toString()+" -> "+ohrn.toString()+";\n" );
	    }
	}

	bw.write( "}\n" );
	bw.close();
    }

    public void writeCondensedAnalysis( String graphName ) throws java.io.IOException {
	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "graph "+graphName+" {\n" );

	HashSet<OwnershipHeapRegionNode> visited = new HashSet<OwnershipHeapRegionNode>();

	// find linked regions
	for( int i = 0; i < analysisRegionLabels.size(); ++i ) {
	    TempDescriptor td = analysisRegionLabels.get( i );
	    bw.write( "  "+td.getSymbol()+";\n" );
	    OwnershipLabelNode oln = getLabelNodeFromTemp( td );

	    OwnershipHeapRegionNode ohrn = null;
	    Iterator heapRegionsItr = oln.iteratorToReachableRegions();
	    while( heapRegionsItr.hasNext() ) {
		ohrn = (OwnershipHeapRegionNode)heapRegionsItr.next();

		traverseHeapNodes( VISIT_OHRN_WRITE_CONDENSED, ohrn, bw, td, visited );
	    }
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

    /*
    protected void traverseHeapNodesTop(  int mode,
					  OwnershipHeapRegionNode ohrn,
					  BufferedWriter bw,
					  TempDescriptor td
					  ) throws java.io.IOException {
	HashSet<OwnershipHeapRegionNode> visited = new HashSet<OwnershipHeapRegionNode>();
	traverseHeapNodes( mode, ohrn, bw, td, visited );
    }
    */

    protected void traverseHeapNodes( int mode,
				      OwnershipHeapRegionNode ohrn,
				      BufferedWriter bw,
				      TempDescriptor td,
				      HashSet<OwnershipHeapRegionNode> visited
				      ) throws java.io.IOException {
	visited.add( ohrn );

	switch( mode ) {
	case VISIT_OHRN_WRITE_FULL:
	    bw.write( "  "+ohrn.toString()+"[shape=box];\n" );
	    break;

	case VISIT_OHRN_WRITE_CONDENSED:
	    ohrn.addAnalysisRegionAlias( td );
	    break;
	}

	OwnershipHeapRegionNode ohrnChild = null;
	Iterator childRegionsItr = ohrn.iteratorToReachableRegions();
	while( childRegionsItr.hasNext() ) {
	    ohrnChild = (OwnershipHeapRegionNode) childRegionsItr.next();

	    switch( mode ) {
	    case VISIT_OHRN_WRITE_FULL:
		bw.write( "  "+ohrn.toString()+" -> "+ohrnChild.toString()+";\n" );
		break;
		
	    case VISIT_OHRN_WRITE_CONDENSED:
		Vector<TempDescriptor> aliases = ohrnChild.getAnalysisRegionAliases();
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
		break;	
	    }	

	    if( !visited.contains( ohrnChild ) ) {
		traverseHeapNodes( mode, ohrnChild, bw, td, visited );
	    }
	}
    }
}
