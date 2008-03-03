package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class OwnershipGraph {


    protected static final int VISIT_HRN_WRITE_FULL      = 0;
    //protected static final int VISIT_HRN_WRITE_CONDENSED = 1;


    private int allocationDepth;

    //protected static int heapRegionNodeIDs = 0;
    public Hashtable<Integer, HeapRegionNode> id2hrn;
    public Hashtable<Integer, HeapRegionNode> heapRoots;

    //protected static int labelNodeIDs = 0;
    public Hashtable<TempDescriptor, LabelNode> td2ln;

    //public HashSet<TempDescriptor> analysisRegionLabels;
    //protected Hashtable<TempDescriptor, TempDescriptor> linkedRegions;


    //public Hashtable<FlatNew, NewCluster> fn2nc;


    public OwnershipGraph( int allocationDepth ) {
	this.allocationDepth = allocationDepth;

	id2hrn    = new Hashtable<Integer,        HeapRegionNode>();
	heapRoots = new Hashtable<Integer,        HeapRegionNode>();
	td2ln     = new Hashtable<TempDescriptor, LabelNode>();

	//analysisRegionLabels = new HashSet<TempDescriptor>(); 
	//linkedRegions = new Hashtable<TempDescriptor, TempDescriptor>();
	//fn2nc          = new Hashtable<FlatNew, NewCluster>();
    }

    protected LabelNode getLabelNodeFromTemp( TempDescriptor td ) {
	assert td != null;
	
	if( !td2ln.containsKey( td ) ) {
	    td2ln.put( td, new LabelNode( td ) );
	}

	return td2ln.get( td );
    }
    
    protected void addReferenceEdge( OwnershipNode  referencer,
				     HeapRegionNode referencee,
				     ReferenceEdgeProperties rep ) {
	assert referencer != null;
	assert referencee != null;	
	referencer.addReferencedRegion( referencee, rep );
	referencee.addReferencer( referencer );
    }

    protected void removeReferenceEdge( OwnershipNode  referencer,
					HeapRegionNode referencee ) {
	assert referencer != null;
	assert referencee != null;
	assert referencer.getReferenceTo( referencee ) != null;
	assert referencee.isReferencedBy( referencer );
	
	referencer.removeReferencedRegion( referencee );
	referencee.removeReferencer( referencer );	
    }

    protected void clearReferenceEdgesFrom( OwnershipNode referencer ) {
	assert referencer != null;

	// get a copy of the table to iterate over, otherwise
	// we will be trying to take apart the table as we
	// are iterating over it, which won't work
	Iterator i = referencer.setIteratorToReferencedRegionsClone();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    HeapRegionNode referencee = (HeapRegionNode) me.getKey();
	    removeReferenceEdge( referencer, referencee );
	}    
    }

    protected void clearReferenceEdgesTo( HeapRegionNode referencee ) {
	assert referencee != null;

	// get a copy of the table to iterate over, otherwise
	// we will be trying to take apart the table as we
	// are iterating over it, which won't work
	Iterator i = referencee.iteratorToReferencersClone();
	while( i.hasNext() ) {
	    OwnershipNode referencer = (OwnershipNode) i.next();
	    removeReferenceEdge( referencer, referencee );
	}    
    }
    

    ////////////////////////////////////////////////////
    //
    //  New Reference Methods
    //
    //  The destination in an assignment statement is
    //  going to have new references.  The method of
    //  determining the references depends on the type
    //  of the FlatNode assignment and the predicates
    //  of the nodes and edges involved.
    //
    ////////////////////////////////////////////////////
    public void assignTempToTemp( TempDescriptor src, 
				  TempDescriptor dst ) {
	LabelNode srcln = getLabelNodeFromTemp( src );
	LabelNode dstln = getLabelNodeFromTemp( dst );

	clearReferenceEdgesFrom( dstln );
	HeapRegionNode newReferencee = null;
        Iterator srcRegionsItr = srcln.setIteratorToReferencedRegions();
	while( srcRegionsItr.hasNext() ) {
	    Map.Entry               me  = (Map.Entry)               srcRegionsItr.next();
	    newReferencee               = (HeapRegionNode)          me.getKey();
	    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();

	    addReferenceEdge( dstln, newReferencee, rep.copy() );
	}
    }

    public void assignTempToField( TempDescriptor src,
				   TempDescriptor dst,
				   FieldDescriptor fd ) {
	LabelNode srcln = getLabelNodeFromTemp( src );
	LabelNode dstln = getLabelNodeFromTemp( dst );

	clearReferenceEdgesFrom( dstln );

	HeapRegionNode hrn = null;
	Iterator srcRegionsItr = srcln.setIteratorToReferencedRegions();
	while( srcRegionsItr.hasNext() ) {
	    Map.Entry me = (Map.Entry)      srcRegionsItr.next();
	    hrn          = (HeapRegionNode) me.getKey();

	    HeapRegionNode hrnOneHop = null;
	    Iterator hrnRegionsItr = hrn.setIteratorToReferencedRegions();
	    while( hrnRegionsItr.hasNext() ) {
		Map.Entry               meH = (Map.Entry)               hrnRegionsItr.next();
		hrnOneHop                   = (HeapRegionNode)          meH.getKey();
		ReferenceEdgeProperties rep = (ReferenceEdgeProperties) meH.getValue();

		addReferenceEdge( dstln, hrnOneHop, rep.copy() );
	    }
	}
    }

    public void assignFieldToTemp( TempDescriptor src, 
				   TempDescriptor dst,
				   FieldDescriptor fd ) {
	LabelNode srcln = getLabelNodeFromTemp( src );
	LabelNode dstln = getLabelNodeFromTemp( dst );

	HeapRegionNode hrn = null;
	Iterator dstRegionsItr = dstln.setIteratorToReferencedRegions();
	while( dstRegionsItr.hasNext() ) {
	    Map.Entry me = (Map.Entry)      dstRegionsItr.next();
	    hrn          = (HeapRegionNode) me.getKey();

	    HeapRegionNode hrnSrc = null;
	    Iterator srcRegionsItr = srcln.setIteratorToReferencedRegions();
	    while( srcRegionsItr.hasNext() ) {
		Map.Entry               meS = (Map.Entry)               srcRegionsItr.next();
		hrnSrc                      = (HeapRegionNode)          meS.getKey();
		ReferenceEdgeProperties rep = (ReferenceEdgeProperties) meS.getValue();

		addReferenceEdge( hrn, hrnSrc, rep.copy() );
	    }
	}	
    }
    ////////////////////////////////////////////////////
    // end new reference methods
    ////////////////////////////////////////////////////


    protected HeapRegionNode 
	createNewHeapRegionNode( Integer id,
				 boolean isSingleObject,
				 boolean isFlagged,
				 boolean isNewSummary ) {

	if( id == null ) {
	    //id = new Integer( heapRegionNodeIDs );
	    //++heapRegionNodeIDs;
	    id = OwnershipAnalysis.generateUniqueHeapRegionNodeID();
	}

	HeapRegionNode hrn = new HeapRegionNode( id,
						 isSingleObject,
						 isFlagged,
						 isNewSummary );
	id2hrn.put( id, hrn );
	return hrn;
    }

    public void parameterAllocation( boolean isTask, TempDescriptor td ) {
	assert td != null;

	LabelNode      lnParam = getLabelNodeFromTemp( td );
	HeapRegionNode hrn     = createNewHeapRegionNode( null, false, isTask, false );
	heapRoots.put( hrn.getID(), hrn );

	addReferenceEdge( lnParam, hrn, new ReferenceEdgeProperties( false ) );
	addReferenceEdge( hrn,     hrn, new ReferenceEdgeProperties( false ) );
    }
    
    /*
    public void assignTempToNewAllocation( TempDescriptor td, FlatNew fn ) {
	assert td != null;
	assert fn != null;

	NewCluster nc = getNewClusterFromFlatNew( fn );	

	// move existing references down the line toward
	// the oldest element
	for( int i = newDepthK - 2; i >= 0; --i ) {	    
	    // move references from the ith oldest to the i+1 oldest
	    HeapRegionNode hrnIthOld = nc.getIthOldest( i );
	    HeapRegionNode hrnIp1Old = nc.getIthOldest( i + 1 );

	    // clear i + 1 references in and out, unless it is the
	    // oldest node which keeps everything
	    if( !(i + 1 == newDepthK - 1) ) {
		clearReferenceEdgesFrom( hrnIp1Old );
		clearReferenceEdgesTo  ( hrnIp1Old );
	    }

	    // copy each edge in and out of i to i + 1	    
	    HeapRegionNode hrnReferencee = null;
	    Iterator       itrReferencee = hrnIthOld.setIteratorToReferencedRegions();
	    while( itrReferencee.hasNext() ) {
		Map.Entry               me  = (Map.Entry)               itrReferencee.next();
		hrnReferencee               = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();
		
		addReferenceEdge( hrnIp1Old, hrnReferencee, rep.copy() );
	    }

	    OwnershipNode onReferencer  = null;
	    Iterator      itrReferencer = hrnIthOld.iteratorToReferencers();
	    while( itrReferencer.hasNext() ) {
		onReferencer = (OwnershipNode) itrReferencer.next();

		ReferenceEdgeProperties rep = onReferencer.getReferenceTo( hrnIthOld );
		assert rep != null;

		addReferenceEdge( onReferencer, hrnIp1Old, rep.copy() );
	    }	    
	}

	HeapRegionNode hrnNewest    = nc.getIthOldest( 0 );
	ReferenceEdgeProperties rep = new ReferenceEdgeProperties( true );
	LabelNode dst               = getLabelNodeFromTemp( td );

	// clear all references in and out of newest node
	clearReferenceEdgesFrom( hrnNewest );
	clearReferenceEdgesTo  ( hrnNewest );

	// finally assign the temp descriptor to the newest
	// node in the new cluster
	addReferenceEdge( dst, hrnNewest, rep );
    }




    public void addAnalysisRegion( TempDescriptor td ) {
	assert td != null;
	analysisRegionLabels.add( td );
    }

    // This method gives an existing label node for a temp
    // descriptor or creates one if it has not been requested
    // yet.  The system is simple because temp descriptors and
    // label nodes have a one-to-one mapping and no special
    // predicates
    protected LabelNode getLabelNodeFromTemp( TempDescriptor td ) {
	assert td != null;

	if( !td2ln.containsKey( td ) ) {
	    Integer id = new Integer( labelNodeIDs );
	    td2ln.put( td, new LabelNode( td ) );
	    ++labelNodeIDs;
	}

	return td2ln.get( td );
    }
    */


    // use the allocation site (unique to entire analysis) to
    // locate the heap region nodes in this ownership graph
    // that should be aged.  The process models the allocation
    // of new objects and collects all the oldest allocations
    // in a summary node to allow for a finite analysis
    public void age( AllocationSite as ) {
	
    }



    ////////////////////////////////////////////////////
    // in merge() and equals() methods the suffix A 
    // represents the passed in graph and the suffix
    // B refers to the graph in this object
    ////////////////////////////////////////////////////

    public void merge( OwnershipGraph og ) {

        if( og == null ) {
	  return;
        }

	mergeOwnershipNodes ( og );
	mergeReferenceEdges ( og );
	mergeHeapRoots      ( og );
	//mergeAnalysisRegions( og );
	//mergeNewClusters    ( og );
    }

    protected void mergeOwnershipNodes( OwnershipGraph og ) {
	Set      sA = og.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();
	    
	    // if this graph doesn't have a node the
	    // incoming graph has, allocate it
	    if( !id2hrn.containsKey( idA ) ) {
		HeapRegionNode hrnB = hrnA.copy();
		id2hrn.put( idA, hrnB );
	    }
	}

	// now add any label nodes that are in graph B but
	// not in A
        sA = og.td2ln.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA = (Map.Entry)      iA.next();
	    TempDescriptor tdA = (TempDescriptor) meA.getKey();
	    LabelNode      lnA = (LabelNode)      meA.getValue();

	    // if the label doesn't exist in B, allocate and add it
	    LabelNode lnB = getLabelNodeFromTemp( tdA );
	}
    }

    protected void mergeReferenceEdges( OwnershipGraph og ) {
	// there is a data structure for storing label nodes
	// retireved by temp descriptors, and a data structure
	// for stroing heap region nodes retrieved by integer
	// ids.  Because finding edges requires interacting
	// with these disparate data structures frequently the
	// process is nearly duplicated, one for each

	// heap regions
	Set      sA = og.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

	    HeapRegionNode hrnChildA = null;
	    Iterator heapRegionsItrA = hrnA.setIteratorToReferencedRegions();	    
	    while( heapRegionsItrA.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               heapRegionsItrA.next();
		hrnChildA                    = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties repA = (ReferenceEdgeProperties) me.getValue();

		Integer idChildA = hrnChildA.getID();

		// at this point we know an edge in graph A exists
		// idA -> idChildA, does this exist in B?
		boolean edgeFound = false;
		assert id2hrn.containsKey( idA );
		HeapRegionNode hrnB = id2hrn.get( idA );

		HeapRegionNode hrnChildB = null;
		Iterator heapRegionsItrB = hrnB.setIteratorToReferencedRegions();
		while( heapRegionsItrB.hasNext() ) {
		    Map.Entry meC = (Map.Entry)      heapRegionsItrB.next();
		    hrnChildB     = (HeapRegionNode) meC.getKey();

		    if( hrnChildB.equals( idChildA ) ) {
			edgeFound = true;
		    }
		}

		// if the edge from A was not found in B,
		// add it to B.
		if( !edgeFound ) {
		    assert id2hrn.containsKey( idChildA );
		    hrnChildB = id2hrn.get( idChildA );
		    ReferenceEdgeProperties repB = repA.copy();
		    addReferenceEdge( hrnB, hrnChildB, repB );
		}
		// otherwise, the edge already existed in both graphs.
		// if this is the case, check to see whether the isUnique
		// predicate of the edges might change
		else
		{

		}  
	    } 
	}

	// and then again with label nodes
	sA = og.td2ln.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA = (Map.Entry)      iA.next();
	    TempDescriptor tdA = (TempDescriptor) meA.getKey();
	    LabelNode      lnA = (LabelNode)      meA.getValue();

	    HeapRegionNode hrnChildA = null;
	    Iterator heapRegionsItrA = lnA.setIteratorToReferencedRegions();	    
	    while( heapRegionsItrA.hasNext() ) {
		Map.Entry meH                = (Map.Entry)               heapRegionsItrA.next();
		hrnChildA                    = (HeapRegionNode)          meH.getKey();
		ReferenceEdgeProperties repA = (ReferenceEdgeProperties) meH.getValue();

		Integer idChildA = hrnChildA.getID();

		// at this point we know an edge in graph A exists
		// tdA -> idChildA, does this exist in B?
		boolean edgeFound = false;
		assert td2ln.containsKey( tdA );
		LabelNode lnB = td2ln.get( tdA );

		HeapRegionNode hrnChildB = null;
		Iterator heapRegionsItrB = lnB.setIteratorToReferencedRegions();
		while( heapRegionsItrB.hasNext() ) {
		    Map.Entry meC = (Map.Entry)      heapRegionsItrB.next();
		    hrnChildB     = (HeapRegionNode) meC.getKey();

		    if( hrnChildB.equals( idChildA ) ) {
			edgeFound = true;
		    }
		}

		// if the edge from A was not found in B,
		// add it to B.
		if( !edgeFound ) {
		    assert id2hrn.containsKey( idChildA );
		    hrnChildB = id2hrn.get( idChildA );
		    ReferenceEdgeProperties repB = repA.copy();
		    addReferenceEdge( lnB, hrnChildB, repB );
		}
		// otherwise, the edge already existed in both graphs.
		// if this is the case, check to see whether the isUnique
		// predicate of the edges might change
		else
		{

		}  
	    } 
	}
    }
    
    protected void mergeHeapRoots( OwnershipGraph og ) {
	Set      sA = og.heapRoots.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

	    if( !heapRoots.containsKey( idA ) ) {		
		assert id2hrn.containsKey( idA );
		HeapRegionNode hrnB = id2hrn.get( idA );
		heapRoots.put( idA, hrnB );
	    }
	}
    }

    /*
    protected void mergeAnalysisRegions( OwnershipGraph og ) {
	Iterator iA = og.analysisRegionLabels.iterator();
	while( iA.hasNext() ) {
	    TempDescriptor tdA = (TempDescriptor) iA.next();
	    if( !analysisRegionLabels.contains( tdA ) ) {
		analysisRegionLabels.add( tdA );
	    }
	}
    }

    protected void mergeNewClusters( OwnershipGraph og ) {
	Set      sA = og.fn2nc.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry  meA = (Map.Entry)  iA.next();
	    FlatNew    fnA = (FlatNew)    meA.getKey();
	    NewCluster ncA = (NewCluster) meA.getValue();
	    
	    // if the A cluster doesn't exist in B we have to construct
	    // it carefully because the nodes and their edges have already
	    // been merged above.  Just find the equivalent heap regions
	    // in the B graph by matching IDs		

	    // if the cluster already exists the edges of its elements
	    // should already have been merged by the above code that
	    // does not care whether the regions are part of clusters
	    NewCluster ncB = null;
	    if( !fn2nc.containsKey( fnA ) ) {
		ncB = new NewCluster( newDepthK );
		
		for( int i = 0; i < newDepthK; ++i ) {
		    HeapRegionNode hrnA = ncA.getIthOldest( i );

		    // this node shouldn't exist in graph B if the
		    // corresponding new cluster didn't exist in B
		    //assert !id2hrn.containsKey( hrnA.getID() );

		    HeapRegionNode hrnB = createNewHeapRegionNode( hrnA.getID(),
								   hrnA.isSingleObject(),
								   hrnA.isFlagged(),
								   hrnA.isNewSummary() );
		    ncB.setIthOldest( i, hrnB );
		}

		fn2nc.put( fnA, ncB );
	    }
	}
    }
    */


    // it is necessary in the equals() member functions
    // to "check both ways" when comparing the data
    // structures of two graphs.  For instance, if all
    // edges between heap region nodes in graph A are
    // present and equal in graph B it is not sufficient
    // to say the graphs are equal.  Consider that there
    // may be edges in graph B that are not in graph A.
    // the only way to know that all edges in both graphs
    // are equally present is to iterate over both data
    // structures and compare against the other graph.
    public boolean equals( OwnershipGraph og ) {

        if( og == null ) {
	  return false;
        }
	
	if( !areHeapRegionNodesEqual( og ) ) {
	    return false;
	}

	if( !areHeapRegionToHeapRegionEdgesEqual( og ) ) {
	    return false;
	}

	if( !areLabelNodesEqual( og ) ) {
	    return false;
	}

	if( !areLabelToHeapRegionEdgesEqual( og ) ) {
	    return false;
	}

	if( !areHeapRootsEqual( og ) ) {
	    return false;
	}

	/*
	if( !areAnalysisRegionLabelsEqual( og ) ) {
	    return false;
	}

	if( !areNewClustersEqual( og ) ) {
	    return false;
	}
	*/

	return true;
    }

    protected boolean areHeapRegionNodesEqual( OwnershipGraph og ) {
	// check all nodes in A for presence in graph B
	Set      sA = og.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();
	    
	    if( !id2hrn.containsKey( idA ) ) {
		return false;
	    }

	    HeapRegionNode hrnB = og.id2hrn.get( idA );	    
	    if( !hrnA.equals( hrnB ) ) {
		return false;
	    }       
	}	

	// then check all nodes in B verses graph A
	Set      sB = id2hrn.entrySet();
	Iterator iB = sB.iterator();
	while( iB.hasNext() ) {
	    Map.Entry      meB  = (Map.Entry)      iB.next();
	    Integer        idB  = (Integer)        meB.getKey();
	    HeapRegionNode hrnB = (HeapRegionNode) meB.getValue();

	    if( !og.id2hrn.containsKey( idB ) ) {
		return false;
	    }
	    
	    // we should have already checked the equality
	    // of this pairing in the last pass if they both
	    // exist so assert that they are equal now
	    HeapRegionNode hrnA = og.id2hrn.get( idB );
	    assert hrnB.equals( hrnA );
	}

	return true;
    }

    protected boolean areHeapRegionToHeapRegionEdgesEqual( OwnershipGraph og ) {
	Set      sA = og.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

	    // we should have already checked that the same
	    // heap regions exist in both graphs
	    assert id2hrn.containsKey( idA );

	    // and are their edges the same?  first check every
	    // edge in A for presence and equality in B
	    HeapRegionNode hrnChildA = null;
	    Iterator heapRegionsItrA = hrnA.setIteratorToReferencedRegions();
	    while( heapRegionsItrA.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               heapRegionsItrA.next();
		hrnChildA                    = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties repA = (ReferenceEdgeProperties) me.getValue();

		Integer idChildA = hrnChildA.getID();
		assert id2hrn.containsKey( idChildA );

		// at this point we know an edge in graph A exists
		// idA -> idChildA, does this edge exist in B?
		boolean edgeFound = false;
		HeapRegionNode hrnB = id2hrn.get( idA );

		HeapRegionNode hrnChildB = null;
		Iterator heapRegionsItrB = hrnB.setIteratorToReferencedRegions();
		while( heapRegionsItrB.hasNext() ) {
		    Map.Entry meH                = (Map.Entry)               heapRegionsItrB.next();
		    hrnChildB                    = (HeapRegionNode)          meH.getKey();
		    ReferenceEdgeProperties repB = (ReferenceEdgeProperties) meH.getValue();

		    if( idChildA.equals( hrnChildB.getID() ) ) {
			if( !repA.equals( repB ) ) {
			    return false;
			}
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    return false;
		}		
	    }

	    // then check every edge in B for presence in A, starting
	    // from the same parent HeapRegionNode
	    HeapRegionNode hrnB = id2hrn.get( idA );

	    HeapRegionNode hrnChildB = null;
	    Iterator heapRegionsItrB = hrnB.setIteratorToReferencedRegions();
	    while( heapRegionsItrB.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               heapRegionsItrB.next();
		hrnChildB                    = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties repB = (ReferenceEdgeProperties) me.getValue();

		Integer idChildB = hrnChildB.getID();

		// at this point we know an edge in graph B exists
		// idB -> idChildB, does this edge exist in A?
		boolean edgeFound = false;

		hrnChildA       = null;
		heapRegionsItrA = hrnA.setIteratorToReferencedRegions();
		while( heapRegionsItrA.hasNext() ) {
		    Map.Entry meH                = (Map.Entry)               heapRegionsItrA.next();
		    hrnChildA                    = (HeapRegionNode)          meH.getKey();
		    ReferenceEdgeProperties repA = (ReferenceEdgeProperties) meH.getValue();

		    if( idChildB.equals( hrnChildA.getID() ) ) {
			assert repB.equals( repA );
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    return false;
		}		
	    }	    
	}	

	return true;
    }

    protected boolean areLabelNodesEqual( OwnershipGraph og ) {
	// are all label nodes in A also in graph B?
	Set      sA = og.td2ln.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA = (Map.Entry)      iA.next();
	    TempDescriptor tdA = (TempDescriptor) meA.getKey();

	    if( !td2ln.containsKey( tdA ) ) {
		return false;
	    }
	}

	// are all label nodes in B also in A?
	Set      sB = td2ln.entrySet();
	Iterator iB = sB.iterator();
	while( iB.hasNext() ) {
	    Map.Entry      meB = (Map.Entry)      iB.next();
	    TempDescriptor tdB = (TempDescriptor) meB.getKey();

	    if( !og.td2ln.containsKey( tdB ) ) {
		return false;
	    }
	}

	return true;
    }

    protected boolean areLabelToHeapRegionEdgesEqual( OwnershipGraph og ) {
	Set      sA = og.td2ln.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA = (Map.Entry)      iA.next();
	    TempDescriptor tdA = (TempDescriptor) meA.getKey();
	    LabelNode      lnA = (LabelNode)      meA.getValue();

	    // we should have already checked that the same
	    // label nodes exist in both graphs
	    assert td2ln.containsKey( tdA );

	    // and are their edges the same?  first check every
	    // edge in A for presence and equality in B
	    HeapRegionNode hrnChildA = null;
	    Iterator heapRegionsItrA = lnA.setIteratorToReferencedRegions();
	    while( heapRegionsItrA.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               heapRegionsItrA.next();
		hrnChildA                    = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties repA = (ReferenceEdgeProperties) me.getValue();

		Integer idChildA = hrnChildA.getID();
		assert id2hrn.containsKey( idChildA );

		// at this point we know an edge in graph A exists
		// tdA -> idChildA, does this edge exist in B?
		boolean edgeFound = false;
		LabelNode lnB = td2ln.get( tdA );

		HeapRegionNode hrnChildB = null;
		Iterator heapRegionsItrB = lnB.setIteratorToReferencedRegions();
		while( heapRegionsItrB.hasNext() ) {
		    Map.Entry meH                = (Map.Entry)               heapRegionsItrB.next();
		    hrnChildB                    = (HeapRegionNode)          meH.getKey();
		    ReferenceEdgeProperties repB = (ReferenceEdgeProperties) meH.getValue();

		    if( idChildA.equals( hrnChildB.getID() ) ) {
			if( !repA.equals( repB ) ) {
			    return false;
			}
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    return false;
		}		
	    }

	    // then check every edge in B for presence in A, starting
	    // from the same parent LabelNode
	    LabelNode lnB = td2ln.get( tdA );

	    HeapRegionNode hrnChildB = null;
	    Iterator heapRegionsItrB = lnB.setIteratorToReferencedRegions();
	    while( heapRegionsItrB.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               heapRegionsItrB.next();
		hrnChildB                    = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties repB = (ReferenceEdgeProperties) me.getValue();

		Integer idChildB = hrnChildB.getID();

		// at this point we know an edge in graph B exists
		// tdB -> idChildB, does this edge exist in A?
		boolean edgeFound = false;

		hrnChildA       = null;
		heapRegionsItrA = lnA.setIteratorToReferencedRegions();
		while( heapRegionsItrA.hasNext() ) {
		    Map.Entry meH                = (Map.Entry)               heapRegionsItrA.next();
		    hrnChildA                    = (HeapRegionNode)          meH.getKey();
		    ReferenceEdgeProperties repA = (ReferenceEdgeProperties) meH.getValue();

		    if( idChildB.equals( hrnChildA.getID() ) ) {
			assert repB.equals( repA );
			edgeFound = true;
		    }
		}

		if( !edgeFound ) {
		    return false;
		}		
	    }	    
	}	

	return true;
    }

    protected boolean areHeapRootsEqual( OwnershipGraph og ) {
	if( og.heapRoots.size() != heapRoots.size() ) {
	    return false;
	}

	Set      sA = og.heapRoots.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    Integer   idA = (Integer)   meA.getKey();

	    if( !heapRoots.containsKey( idA ) ) {
		return false;
	    }
	}

	Set      sB = heapRoots.entrySet();
	Iterator iB = sB.iterator();
	while( iB.hasNext() ) {
	    Map.Entry meB = (Map.Entry) iB.next();
	    Integer   idB = (Integer)   meB.getKey();

	    if( !heapRoots.containsKey( idB ) ) {
		return false;
	    }
	}

	return true;
    }

    /*
    protected boolean areAnalysisRegionLabelsEqual( OwnershipGraph og ) {
	if( og.analysisRegionLabels.size() != analysisRegionLabels.size() ) {
	    return false;
	}

	Iterator iA = og.analysisRegionLabels.iterator();
	while( iA.hasNext() ) {
	    TempDescriptor tdA = (TempDescriptor) iA.next();
	    if( !analysisRegionLabels.contains( tdA ) ) {
		return false;
	    }
	}

	Iterator iB = analysisRegionLabels.iterator();
	while( iB.hasNext() ) {
	    TempDescriptor tdB = (TempDescriptor) iB.next();
	    if( !og.analysisRegionLabels.contains( tdB ) ) {
		return false;
	    }
	}

	return true;
    }

    protected boolean areNewClustersEqual( OwnershipGraph og ) {
	if( og.fn2nc.size() != fn2nc.size() ) {
	    return false;
	}

	Set      sA = og.fn2nc.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry meA = (Map.Entry) iA.next();
	    FlatNew   fnA = (FlatNew)   meA.getKey();

	    if( !fn2nc.containsKey( fnA ) ) {
		return false;
	    }
	}

	Set      sB = fn2nc.entrySet();
	Iterator iB = sB.iterator();
	while( iB.hasNext() ) {
	    Map.Entry meB = (Map.Entry) iB.next();
	    FlatNew   fnB = (FlatNew)   meB.getKey();

	    if( !fn2nc.containsKey( fnB ) ) {
		return false;
	    }
	}

	return true;
    }
    */

    public void writeGraph( Descriptor methodDesc,
			    FlatNode   fn ) throws java.io.IOException {
	
	String graphName =
	    methodDesc.getSymbol() +
	    methodDesc.getNum() +
	    fn.toString();

	// remove all non-word characters from the graph name so
	// the filename and identifier in dot don't cause errors
	graphName = graphName.replaceAll( "[\\W]", "" );


	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "digraph "+graphName+" {\n" );

	/*
	// first write out new clusters
	Integer newClusterNum = new Integer( 100 );
	Set      s = fn2nc.entrySet();
	Iterator i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry  me = (Map.Entry)  i.next();
	    FlatNew    fn = (FlatNew)    me.getKey();
	    NewCluster nc = (NewCluster) me.getValue();

	    bw.write( "  subgraph cluster" + newClusterNum + " {\n"     );
	    bw.write( "    color=blue;\n"                      );
	    bw.write( "    rankdir=LR;\n"                      );
	    bw.write( "    label=\"" + fn.toString() + "\";\n" );
	    
	    for( int j = 0; j < newDepthK; ++j ) {
		HeapRegionNode hrn = nc.getIthOldest( j );
		bw.write( "    " + hrn.toString() + ";\n" );
	    }

	    bw.write( "  }\n" );
	}
	*/


	// then visit every heap region node
	HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

	Set      s = heapRoots.entrySet();
	Iterator i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry      me  = (Map.Entry)      i.next();
	    HeapRegionNode hrn = (HeapRegionNode) me.getValue();
	    traverseHeapRegionNodes( VISIT_HRN_WRITE_FULL, hrn, bw, null, visited );
	}

	// then visit every label node
	s = td2ln.entrySet();
	i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry me = (Map.Entry) i.next();
	    LabelNode ln = (LabelNode) me.getValue();

	    HeapRegionNode hrn = null;
	    Iterator heapRegionsItr = ln.setIteratorToReferencedRegions();
	    while( heapRegionsItr.hasNext() ) {
		Map.Entry meH               = (Map.Entry)               heapRegionsItr.next();
		hrn                         = (HeapRegionNode)          meH.getKey();
		ReferenceEdgeProperties rep = (ReferenceEdgeProperties) meH.getValue();

		String edgeLabel = "";
		if( rep.isUnique() ) {
		    edgeLabel = "Unique";
		}
		bw.write( "  "        + ln.toString() +
			  " -> "      + hrn.toString() +
			  "[label=\"" + edgeLabel +
			  "\"];\n" );
	    }
	}

	bw.write( "}\n" );
	bw.close();
    }

    /*
    public void writeCondensedAnalysis( String graphName ) throws java.io.IOException {
	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "graph "+graphName+" {\n" );

	// find linked regions
	Iterator i = analysisRegionLabels.iterator();
	while( i.hasNext() ) {
	    TempDescriptor td = (TempDescriptor) i.next();
	    bw.write( "  "+td.getSymbol()+";\n" );
	    LabelNode ln = getLabelNodeFromTemp( td );

	    HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

	    HeapRegionNode hrn = null;
	    Iterator heapRegionsItr = ln.setIteratorToReferencedRegions();
	    while( heapRegionsItr.hasNext() ) {
		Map.Entry me                = (Map.Entry)               heapRegionsItr.next();
		hrn                         = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();

		traverseHeapRegionNodes( VISIT_HRN_WRITE_CONDENSED, hrn, bw, td, visited );
	    }
	}

	// write out linked regions	
	Set      s   = linkedRegions.entrySet();
	Iterator lri = s.iterator();
	while( lri.hasNext() ) {
	    Map.Entry      me = (Map.Entry)      lri.next();
	    TempDescriptor t1 = (TempDescriptor) me.getKey();
	    TempDescriptor t2 = (TempDescriptor) me.getValue();
	    bw.write( "  "+t1.getSymbol()+" -- "+t2.getSymbol()+";\n" );
	}

	bw.write( "}\n" );
	bw.close();
    }
    */

    protected void traverseHeapRegionNodes( int mode,
					    HeapRegionNode hrn,
					    BufferedWriter bw,
					    TempDescriptor td,
					    HashSet<HeapRegionNode> visited
					    ) throws java.io.IOException {

	if( visited.contains( hrn ) ) {
	    return;
	}
	visited.add( hrn );

	switch( mode ) {
	case VISIT_HRN_WRITE_FULL:
	    
	    String isSingleObjectStr = "isSingleObject";
	    if( !hrn.isSingleObject() ) {
		isSingleObjectStr = "!isSingleObject";
	    }

	    String isFlaggedStr = "isFlagged";
	    if( !hrn.isFlagged() ) {
		isFlaggedStr = "!isFlagged";
	    }

	    String isNewSummaryStr = "isNewSummary";
	    if( !hrn.isNewSummary() ) {
		isNewSummaryStr = "!isNewSummary";
	    }

	    bw.write( "  "                  + hrn.toString() + 
		      "[shape=box,label=\"" + isFlaggedStr +
		      "\\n"                 + isSingleObjectStr +
		      "\\n"                 + isNewSummaryStr +
                      "\"];\n" );
	    break;

	    /*
	case VISIT_HRN_WRITE_CONDENSED:	    

	    Iterator i = hrn.iteratorToAnalysisRegionAliases();
	    while( i.hasNext() ) {
		TempDescriptor tdn = (TempDescriptor) i.next();
		
		// only add a linked region if the td passed in and 
		// the td's aliased to this node haven't already been
		// added as linked regions
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

	    hrn.addAnalysisRegionAlias( td );
	    break;
	    */
	}

	OwnershipNode onRef  = null;
	Iterator      refItr = hrn.iteratorToReferencers();
	while( refItr.hasNext() ) {
	    onRef = (OwnershipNode) refItr.next();

	    switch( mode ) {
	    case VISIT_HRN_WRITE_FULL:
		bw.write( "  "                    + hrn.toString() + 
			  " -> "                  + onRef.toString() + 
			  "[color=lightgray];\n" );
		break;
	    }
	}


	HeapRegionNode hrnChild = null;
	Iterator childRegionsItr = hrn.setIteratorToReferencedRegions();
	while( childRegionsItr.hasNext() ) {
	    Map.Entry me                = (Map.Entry)               childRegionsItr.next();
	    hrnChild                    = (HeapRegionNode)          me.getKey();
	    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();

	    switch( mode ) {
	    case VISIT_HRN_WRITE_FULL:
		String edgeLabel = "";
		if( rep.isUnique() ) {
		    edgeLabel = "Unique";
		}
		bw.write( "  "        + hrn.toString() +
			  " -> "      + hrnChild.toString() +
			  "[label=\"" + edgeLabel +
			  "\"];\n" );
		break;
	    }

	    traverseHeapRegionNodes( mode, hrnChild, bw, td, visited );
	}
    }
}
