package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class OwnershipGraph {

    private int allocationDepth;

    // there was already one other very similar reason
    // for traversing heap nodes that is no longer needed
    // instead of writing a new heap region visitor, use
    // the existing method with a new mode to describe what
    // actions to take during the traversal
    protected static final int VISIT_HRN_WRITE_FULL = 0;


    public Hashtable<Integer,        HeapRegionNode> id2hrn;
    public Hashtable<TempDescriptor, LabelNode     > td2ln;
    public Hashtable<Integer,        Integer       > id2paramIndex;
    public Hashtable<Integer,        Integer       > paramIndex2id;

    public HashSet<AllocationSite> allocationSites;


    public OwnershipGraph( int allocationDepth ) {
	this.allocationDepth = allocationDepth;

	id2hrn        = new Hashtable<Integer,        HeapRegionNode>();
	td2ln         = new Hashtable<TempDescriptor, LabelNode     >();
	id2paramIndex = new Hashtable<Integer,        Integer       >();
	paramIndex2id = new Hashtable<Integer,        Integer       >();

	allocationSites = new HashSet <AllocationSite>();
    }


    // label nodes are much easier to deal with than
    // heap region nodes.  Whenever there is a request
    // for the label node that is associated with a
    // temp descriptor we can either find it or make a
    // new one and return it.  This is because temp
    // descriptors are globally unique and every label
    // node is mapped to exactly one temp descriptor.
    protected LabelNode getLabelNodeFromTemp( TempDescriptor td ) {
	assert td != null;
	
	if( !td2ln.containsKey( td ) ) {
	    td2ln.put( td, new LabelNode( td ) );
	}

	return td2ln.get( td );
    }


    // the reason for this method is to have the option
    // creating new heap regions with specific IDs, or
    // duplicating heap regions with specific IDs (especially
    // in the merge() operation) or to create new heap
    // regions with a new unique ID.
    protected HeapRegionNode 
	createNewHeapRegionNode( Integer         id,
				 boolean         isSingleObject,
				 boolean         isFlagged,
				 boolean         isNewSummary,
				 boolean         isParameter,
				 AllocationSite  allocSite,
				 ReachabilitySet alpha,
				 String          description ) {

	if( id == null ) {
	    id = OwnershipAnalysis.generateUniqueHeapRegionNodeID();
	}

	if( alpha == null ) {
	    if( isFlagged || isParameter ) {
		alpha = new ReachabilitySet( new TokenTuple( id, 
							     isNewSummary,
							     TokenTuple.ARITY_ONE ) );
	    } else {
		alpha = new ReachabilitySet();
	    }
	}

	HeapRegionNode hrn = new HeapRegionNode( id,
						 isSingleObject,
						 isFlagged,
						 isNewSummary,
						 allocSite,
						 alpha,
						 description );
	id2hrn.put( id, hrn );
	return hrn;
    }

    

    ////////////////////////////////////////////////
    //
    //  Low-level referencee and referencer methods
    // 
    //  These methods provide the lowest level for
    //  creating references between ownership nodes
    //  and handling the details of maintaining both
    //  list of referencers and referencees.
    // 
    ////////////////////////////////////////////////
    protected void addReferenceEdge( OwnershipNode  referencer,
				     HeapRegionNode referencee,
				     ReferenceEdge  edge ) {
	assert referencer != null;
	assert referencee != null;
	assert edge       != null;
	assert edge.getSrc() == referencer;
	assert edge.getDst() == referencee;

	referencer.addReferencee( edge );
	referencee.addReferencer( edge );
    }

    protected void removeReferenceEdge( OwnershipNode   referencer,
					HeapRegionNode  referencee,
					FieldDescriptor fieldDesc ) {
	assert referencer != null;
	assert referencee != null;

	ReferenceEdge edge = referencer.getReferenceTo( referencee,
							fieldDesc );
	assert edge != null;
	assert edge == referencee.getReferenceFrom( referencer,
						    fieldDesc );
	
	referencer.removeReferencee( edge );
	referencee.removeReferencer( edge );
    }

    protected void clearReferenceEdgesFrom( OwnershipNode   referencer,
					    FieldDescriptor fieldDesc,
					    boolean         removeAll ) {
	assert referencer != null;

	// get a copy of the set to iterate over, otherwise
	// we will be trying to take apart the set as we
	// are iterating over it, which won't work
	Iterator<ReferenceEdge> i = referencer.iteratorToReferenceesClone();
	while( i.hasNext() ) {
	    ReferenceEdge edge = i.next();

	    if( removeAll || edge.getFieldDesc() == fieldDesc ) {
		HeapRegionNode referencee = edge.getDst();

		removeReferenceEdge( referencer,
				     referencee,
				     edge.getFieldDesc() );
	    }
	}    
    }

    protected void clearReferenceEdgesTo( HeapRegionNode  referencee,
					  FieldDescriptor fieldDesc,
					  boolean         removeAll ) {
	assert referencee != null;

	// get a copy of the set to iterate over, otherwise
	// we will be trying to take apart the set as we
	// are iterating over it, which won't work
	Iterator<ReferenceEdge> i = referencee.iteratorToReferencersClone();
	while( i.hasNext() ) {
	    ReferenceEdge edge = i.next();

	    if( removeAll || edge.getFieldDesc() == fieldDesc ) {
		OwnershipNode referencer = edge.getSrc();
		removeReferenceEdge( referencer,
				     referencee,
				     edge.getFieldDesc() );
	    }    
	}
    }
    

    protected void propagateTokensOverNodes( HeapRegionNode          nPrime,
					     ChangeTupleSet          c0,
					     HashSet<HeapRegionNode> nodesWithNewAlpha,
					     HashSet<ReferenceEdge>  edgesWithNewBeta ) {	

	HashSet<HeapRegionNode> todoNodes
	    = new HashSet<HeapRegionNode>();
	todoNodes.add( nPrime );

	HashSet<ReferenceEdge> todoEdges 
	    = new HashSet<ReferenceEdge>();
	
	Hashtable<HeapRegionNode, ChangeTupleSet> nodePlannedChanges 
	    = new Hashtable<HeapRegionNode, ChangeTupleSet>();
	nodePlannedChanges.put( nPrime, c0 );

	Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges 
	    = new Hashtable<ReferenceEdge, ChangeTupleSet>();
	

	while( !todoNodes.isEmpty() ) {
	    HeapRegionNode n = todoNodes.iterator().next();	   
	    ChangeTupleSet C = nodePlannedChanges.get( n );

	    Iterator itrC = C.iterator();
	    while( itrC.hasNext() ) {
		ChangeTuple c = (ChangeTuple) itrC.next();

		if( n.getAlpha().contains( c.getSetToMatch() ) ) {
		    ReachabilitySet withChange = n.getAlpha().union( c.getSetToAdd() );
		    n.setAlphaNew( n.getAlphaNew().union( withChange ) );
		    nodesWithNewAlpha.add( n );
		}
	    }

	    Iterator<ReferenceEdge> referItr = n.iteratorToReferencers();
	    while( referItr.hasNext() ) {
		ReferenceEdge edge = referItr.next();
		todoEdges.add( edge );

		if( !edgePlannedChanges.containsKey( edge ) ) {
		    edgePlannedChanges.put( edge, new ChangeTupleSet().makeCanonical() );
		}

		edgePlannedChanges.put( edge, edgePlannedChanges.get( edge ).union( C ) );
	    }

	    Iterator<ReferenceEdge> refeeItr = n.iteratorToReferencees();
	    while( refeeItr.hasNext() ) {
		ReferenceEdge  edgeF = refeeItr.next();
		HeapRegionNode m     = edgeF.getDst();

		ChangeTupleSet changesToPass = new ChangeTupleSet().makeCanonical();

		Iterator<ChangeTuple> itrCprime = C.iterator();
		while( itrCprime.hasNext() ) {
		    ChangeTuple c = itrCprime.next();
		    if( edgeF.getBeta().contains( c.getSetToMatch() ) ) {
			changesToPass = changesToPass.union( c );
		    }
		}

		if( !changesToPass.isEmpty() ) {
		    if( !nodePlannedChanges.containsKey( m ) ) {
			nodePlannedChanges.put( m, new ChangeTupleSet().makeCanonical() );
		    }

		    ChangeTupleSet currentChanges = nodePlannedChanges.get( m );

		    if( !changesToPass.isSubset( currentChanges ) ) {

			nodePlannedChanges.put( m, currentChanges.union( changesToPass ) );
			todoNodes.add( m );
		    }
		}
	    }

	    todoNodes.remove( n );
	}

	propagateTokensOverEdges( todoEdges, edgePlannedChanges, nodesWithNewAlpha, edgesWithNewBeta );
    }


    protected void propagateTokensOverEdges( 
	 HashSet<ReferenceEdge>                   todoEdges,
	 Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges,
	 HashSet<HeapRegionNode>                  nodesWithNewAlpha,
	 HashSet<ReferenceEdge>                   edgesWithNewBeta ) {
       

	while( !todoEdges.isEmpty() ) {
	    ReferenceEdge edgeE = todoEdges.iterator().next();
	    todoEdges.remove( edgeE );

	    if( !edgePlannedChanges.containsKey( edgeE ) ) {
		edgePlannedChanges.put( edgeE, new ChangeTupleSet().makeCanonical() );
	    }
	    
	    ChangeTupleSet C = edgePlannedChanges.get( edgeE );

	    ChangeTupleSet changesToPass = new ChangeTupleSet().makeCanonical();

	    Iterator<ChangeTuple> itrC = C.iterator();
	    while( itrC.hasNext() ) {
		ChangeTuple c = itrC.next();
		if( edgeE.getBeta().contains( c.getSetToMatch() ) ) {
		    ReachabilitySet withChange = edgeE.getBeta().union( c.getSetToAdd() );
		    edgeE.setBetaNew( edgeE.getBetaNew().union( withChange ) );
		    edgesWithNewBeta.add( edgeE );
		    changesToPass = changesToPass.union( c );
		}
	    }

	    OwnershipNode onSrc = edgeE.getSrc();

	    if( !changesToPass.isEmpty() && onSrc instanceof HeapRegionNode ) {		
		HeapRegionNode n = (HeapRegionNode) onSrc;

		Iterator<ReferenceEdge> referItr = n.iteratorToReferencers();
		while( referItr.hasNext() ) {
		    ReferenceEdge edgeF = referItr.next();
		    
		    if( !edgePlannedChanges.containsKey( edgeF ) ) {
			edgePlannedChanges.put( edgeF, new ChangeTupleSet().makeCanonical() );
		    }
		  
		    ChangeTupleSet currentChanges = edgePlannedChanges.get( edgeF );
		
		    if( !changesToPass.isSubset( currentChanges ) ) {
			todoEdges.add( edgeF );
			edgePlannedChanges.put( edgeF, currentChanges.union( changesToPass ) );
		    }
		}
	    }	    
	}	
    }


    ////////////////////////////////////////////////////
    //
    //  Assignment Operation Methods
    //
    //  These methods are high-level operations for
    //  modeling program assignment statements using 
    //  the low-level reference create/remove methods
    //  above.
    //
    //  The destination in an assignment statement is
    //  going to have new references.  The method of
    //  determining the references depends on the type
    //  of the FlatNode assignment and the predicates
    //  of the nodes and edges involved.
    //
    ////////////////////////////////////////////////////
    public void assignTempYToTempX( TempDescriptor y, 
				    TempDescriptor x ) {

	LabelNode lnX = getLabelNodeFromTemp( x );
	LabelNode lnY = getLabelNodeFromTemp( y );

	clearReferenceEdgesFrom( lnX, null, true );

        Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
	while( itrYhrn.hasNext() ) {
	    ReferenceEdge  edgeY       = itrYhrn.next();
	    HeapRegionNode referencee = edgeY.getDst();
	    ReferenceEdge  edgeNew    = edgeY.copy();
	    edgeNew.setSrc( lnX );

	    addReferenceEdge( lnX, referencee, edgeNew );
	}
    }


    public void assignTempYFieldFToTempX( TempDescriptor  y,
					  FieldDescriptor f,
					  TempDescriptor  x ) {

	LabelNode lnX = getLabelNodeFromTemp( x );
	LabelNode lnY = getLabelNodeFromTemp( y );

	clearReferenceEdgesFrom( lnX, null, true );

	Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
	while( itrYhrn.hasNext() ) {
	    ReferenceEdge   edgeY = itrYhrn.next();
	    HeapRegionNode  hrnY  = edgeY.getDst();
	    ReachabilitySet betaY = edgeY.getBeta();

	    Iterator<ReferenceEdge> itrHrnFhrn = hrnY.iteratorToReferencees();
	    while( itrHrnFhrn.hasNext() ) {
		ReferenceEdge   edgeHrn = itrHrnFhrn.next();
		HeapRegionNode  hrnHrn  = edgeHrn.getDst();
		ReachabilitySet betaHrn = edgeHrn.getBeta();

		if( edgeHrn.getFieldDesc() == null ||
		    edgeHrn.getFieldDesc() == f ) {

		    ReferenceEdge edgeNew = new ReferenceEdge( lnX,
							       hrnHrn,
							       f,
							       false,
							       betaY.intersection( betaHrn ) );
		    		    
		    addReferenceEdge( lnX, hrnHrn, edgeNew );
		}
	    }
	}
    }


    public void assignTempYToTempXFieldF( TempDescriptor  y,
					  TempDescriptor  x,
					  FieldDescriptor f ) {

	LabelNode lnX = getLabelNodeFromTemp( x );
	LabelNode lnY = getLabelNodeFromTemp( y );

	HashSet<HeapRegionNode> nodesWithNewAlpha = new HashSet<HeapRegionNode>();
	HashSet<ReferenceEdge>  edgesWithNewBeta  = new HashSet<ReferenceEdge>();

	Iterator<ReferenceEdge> itrXhrn = lnX.iteratorToReferencees();
	while( itrXhrn.hasNext() ) {
	    ReferenceEdge   edgeX = itrXhrn.next();
	    HeapRegionNode  hrnX  = edgeX.getDst();
	    ReachabilitySet betaX = edgeX.getBeta();

	    ReachabilitySet R = hrnX.getAlpha().intersection( edgeX.getBeta() );

	    Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
	    while( itrYhrn.hasNext() ) {
		ReferenceEdge   edgeY = itrYhrn.next();
		HeapRegionNode  hrnY  = edgeY.getDst();		
		ReachabilitySet O     = edgeY.getBeta();

		// propagate tokens over nodes starting from hrnSrc, and it will
		// take care of propagating back up edges from any touched nodes
		ChangeTupleSet Cy = O.unionUpArityToChangeSet( R );
		propagateTokensOverNodes( hrnY, Cy, nodesWithNewAlpha, edgesWithNewBeta );


		// then propagate back just up the edges from hrn
		ChangeTupleSet Cx = R.unionUpArityToChangeSet( O );

		HashSet<ReferenceEdge> todoEdges = new HashSet<ReferenceEdge>();

		Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges =
		    new Hashtable<ReferenceEdge, ChangeTupleSet>();

		Iterator<ReferenceEdge> referItr = hrnX.iteratorToReferencers();
		while( referItr.hasNext() ) {
		    ReferenceEdge edgeUpstream = referItr.next();
		    todoEdges.add( edgeUpstream );
		    edgePlannedChanges.put( edgeUpstream, Cx );
		}

		propagateTokensOverEdges( todoEdges, 
					  edgePlannedChanges,
					  nodesWithNewAlpha,
					  edgesWithNewBeta );

		// finally, create the actual reference edge hrnX.f -> hrnY
		ReferenceEdge edgeNew = new ReferenceEdge( hrnX,
							   hrnY,
							   f,
							   false,
							   edgeY.getBetaNew().pruneBy( hrnX.getAlpha() )
							 );

		addReferenceEdge( hrnX, hrnY, edgeNew );
	    }
	}	

	Iterator<HeapRegionNode> nodeItr = nodesWithNewAlpha.iterator();
	while( nodeItr.hasNext() ) {
	    nodeItr.next().applyAlphaNew();
	}

	Iterator<ReferenceEdge> edgeItr = edgesWithNewBeta.iterator();
	while( edgeItr.hasNext() ) {
	    edgeItr.next().applyBetaNew();
	}
    }


    public void assignParameterAllocationToTemp( boolean        isTask,
						 TempDescriptor td,
						 Integer        paramIndex ) {
	assert td != null;

	LabelNode      lnParam = getLabelNodeFromTemp( td );
	HeapRegionNode hrn     = createNewHeapRegionNode( null, 
							  false,
							  isTask,
							  false,
							  true,
							  null,
							  null,
							  "param" + paramIndex );

	// keep track of heap regions that were created for
	// parameter labels, the index of the parameter they
	// are for is important when resolving method calls
	Integer newID = hrn.getID();
	assert !id2paramIndex.containsKey  ( newID );
	assert !id2paramIndex.containsValue( paramIndex );
	id2paramIndex.put( newID, paramIndex );
	paramIndex2id.put( paramIndex, newID );

	ReachabilitySet beta = new ReachabilitySet( new TokenTuple( newID, 
								    false,
								    TokenTuple.ARITY_ONE ) );

	// heap regions for parameters are always multiple object (see above)
	// and have a reference to themselves, because we can't know the
	// structure of memory that is passed into the method.  We're assuming
	// the worst here.

	ReferenceEdge edgeFromLabel = 
	    new ReferenceEdge( lnParam, hrn, null, false, beta );

	ReferenceEdge edgeReflexive = 
	    new ReferenceEdge( hrn,     hrn, null, true,  beta );

	addReferenceEdge( lnParam, hrn, edgeFromLabel );
	addReferenceEdge( hrn,     hrn, edgeReflexive );
    }

    
    public void assignNewAllocationToTempX( TempDescriptor x,
					    AllocationSite as ) {
	assert x  != null;
	assert as != null;

	age( as );

	// after the age operation the newest (or zero-ith oldest)
	// node associated with the allocation site should have
	// no references to it as if it were a newly allocated
	// heap region, so make a reference to it to complete
	// this operation

	Integer        idNewest  = as.getIthOldest( 0 );
	HeapRegionNode hrnNewest = id2hrn.get( idNewest );
	assert hrnNewest != null;

	LabelNode lnX = getLabelNodeFromTemp( x );	
	clearReferenceEdgesFrom( lnX, null, true );

	ReferenceEdge edgeNew = 
	    new ReferenceEdge( lnX, hrnNewest, null, false, hrnNewest.getAlpha() );	
	
	addReferenceEdge( lnX, hrnNewest, edgeNew );
    }


    // use the allocation site (unique to entire analysis) to
    // locate the heap region nodes in this ownership graph
    // that should be aged.  The process models the allocation
    // of new objects and collects all the oldest allocations
    // in a summary node to allow for a finite analysis
    //
    // There is an additional property of this method.  After
    // running it on a particular ownership graph (many graphs
    // may have heap regions related to the same allocation site)
    // the heap region node objects in this ownership graph will be
    // allocated.  Therefore, after aging a graph for an allocation
    // site, attempts to retrieve the heap region nodes using the
    // integer id's contained in the allocation site should always
    // return non-null heap regions.
    public void age( AllocationSite as ) {

	// aging adds this allocation site to the graph's
	// list of sites that exist in the graph, or does
	// nothing if the site is already in the list
	allocationSites.add( as );

	// get the summary node for the allocation site in the context
	// of this particular ownership graph
	HeapRegionNode hrnSummary = getSummaryNode( as );

	// merge oldest node into summary
	Integer        idK  = as.getOldest();
	HeapRegionNode hrnK = id2hrn.get( idK );
	mergeIntoSummary( hrnK, hrnSummary );
	
	// move down the line of heap region nodes
	// clobbering the ith and transferring all references
	// to and from i-1 to node i.  Note that this clobbers
	// the oldest node (hrnK) that was just merged into
	// the summary
	for( int i = allocationDepth - 1; i > 0; --i ) {	    

	    // move references from the i-1 oldest to the ith oldest
	    Integer        idIth     = as.getIthOldest( i );
	    HeapRegionNode hrnI      = id2hrn.get( idIth );
	    Integer        idImin1th = as.getIthOldest( i - 1 );
	    HeapRegionNode hrnImin1  = id2hrn.get( idImin1th );

	    transferOnto( hrnImin1, hrnI );
	}

	// as stated above, the newest node should have had its
	// references moved over to the second oldest, so we wipe newest
	// in preparation for being the new object to assign something to
	Integer        id0th = as.getIthOldest( 0 );
	HeapRegionNode hrn0  = id2hrn.get( id0th );
	assert hrn0 != null;

	// clear all references in and out of newest node
	clearReferenceEdgesFrom( hrn0, null, true );
	clearReferenceEdgesTo  ( hrn0, null, true );
	
	
	// now tokens in reachability sets need to "age" also
	Iterator itrAllLabelNodes = td2ln.entrySet().iterator();
	while( itrAllLabelNodes.hasNext() ) {
	    Map.Entry me = (Map.Entry) itrAllLabelNodes.next();
	    LabelNode ln = (LabelNode) me.getValue();

	    Iterator<ReferenceEdge> itrEdges = ln.iteratorToReferencees();
	    while( itrEdges.hasNext() ) {
		ageTokens( as, itrEdges.next() );
	    }
	}

	Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
	while( itrAllHRNodes.hasNext() ) {
	    Map.Entry      me       = (Map.Entry)      itrAllHRNodes.next();
	    HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

	    ageTokens( as, hrnToAge );

	    Iterator<ReferenceEdge> itrEdges = hrnToAge.iteratorToReferencees();
	    while( itrEdges.hasNext() ) {
		ageTokens( as, itrEdges.next() );
	    }
	}


	// after tokens have been aged, reset newest node's reachability
	hrn0.setAlpha( new ReachabilitySet( 
			   new TokenTupleSet( 
			       new TokenTuple( hrn0 ) 
					    ) 
					  ).makeCanonical() 
		       );
    }
 

    protected HeapRegionNode getSummaryNode( AllocationSite as ) {
	
	Integer        idSummary  = as.getSummary();
	HeapRegionNode hrnSummary = id2hrn.get( idSummary );

	// If this is null then we haven't touched this allocation site
	// in the context of the current ownership graph, so allocate
	// heap region nodes appropriate for the entire allocation site.
	// This should only happen once per ownership graph per allocation site,
	// and a particular integer id can be used to locate the heap region
	// in different ownership graphs that represents the same part of an
	// allocation site.
	if( hrnSummary == null ) {

	    boolean hasFlags = false;
	    if( as.getType().isClass() ) {
		hasFlags = as.getType().getClassDesc().hasFlags();
	    }

	    hrnSummary = createNewHeapRegionNode( idSummary,
						  false,
						  hasFlags,
						  true,
						  false,
						  as,
						  null,
						  as + "\\n" + as.getType() + "\\nsummary" );

	    for( int i = 0; i < as.getAllocationDepth(); ++i ) {
		Integer idIth = as.getIthOldest( i );
		assert !id2hrn.containsKey( idIth );
		createNewHeapRegionNode( idIth,
					 true,
					 hasFlags,
					 false,
					 false,
					 as,
					 null,
					 as + "\\n" + as.getType() + "\\n" + i + " oldest" );
	    }
	}

	return hrnSummary;
    }


    protected HeapRegionNode getShadowSummaryNode( AllocationSite as ) {
	
	Integer        idShadowSummary  = -(as.getSummary());
	HeapRegionNode hrnShadowSummary = id2hrn.get( idShadowSummary );

	if( hrnShadowSummary == null ) {

	    boolean hasFlags = false;
	    if( as.getType().isClass() ) {
		hasFlags = as.getType().getClassDesc().hasFlags();
	    }

	    hrnShadowSummary = createNewHeapRegionNode( idShadowSummary,
							false,
							hasFlags,
							true,
							false,
							as,
							null,
							as + "\\n" + as.getType() + "\\nshadowSum" );

	    for( int i = 0; i < as.getAllocationDepth(); ++i ) {
		Integer idShadowIth = -(as.getIthOldest( i ));
		assert !id2hrn.containsKey( idShadowIth );
		createNewHeapRegionNode( idShadowIth,
					 true,
					 hasFlags,
					 false,
					 false,
					 as,
					 null,
					 as + "\\n" + as.getType() + "\\n" + i + " shadow" );
	    }
	}

	return hrnShadowSummary;
    }


    protected void mergeIntoSummary( HeapRegionNode hrn, HeapRegionNode hrnSummary ) {
	assert hrnSummary.isNewSummary();

	// transfer references _from_ hrn over to hrnSummary
	Iterator<ReferenceEdge> itrReferencee = hrn.iteratorToReferencees();
	while( itrReferencee.hasNext() ) {
	    ReferenceEdge edge       = itrReferencee.next();
	    ReferenceEdge edgeMerged = edge.copy();
	    edgeMerged.setSrc( hrnSummary );

	    HeapRegionNode hrnReferencee = edge.getDst();
	    ReferenceEdge  edgeSummary   = hrnSummary.getReferenceTo( hrnReferencee, edge.getFieldDesc() );

	    if( edgeSummary == null ) {	    
		// the merge is trivial, nothing to be done
	    } else {
		// otherwise an edge from the referencer to hrnSummary exists already
		// and the edge referencer->hrn should be merged with it
		edgeMerged.setBeta( edgeMerged.getBeta().union( edgeSummary.getBeta() ) );
	    }

	    addReferenceEdge( hrnSummary, hrnReferencee, edgeMerged );
	}
	
	// next transfer references _to_ hrn over to hrnSummary
	Iterator<ReferenceEdge> itrReferencer = hrn.iteratorToReferencers();
	while( itrReferencer.hasNext() ) {
	    ReferenceEdge edge         = itrReferencer.next();
	    ReferenceEdge edgeMerged   = edge.copy();
	    edgeMerged.setDst( hrnSummary );

	    OwnershipNode onReferencer = edge.getSrc();
	    ReferenceEdge edgeSummary  = onReferencer.getReferenceTo( hrnSummary, edge.getFieldDesc() );
		
	    if( edgeSummary == null ) {	    
		// the merge is trivial, nothing to be done
	    } else {
		// otherwise an edge from the referencer to alpha_S exists already
		// and the edge referencer->alpha_K should be merged with it
		edgeMerged.setBeta( edgeMerged.getBeta().union( edgeSummary.getBeta() ) );
	    }
	    
	    addReferenceEdge( onReferencer, hrnSummary, edgeMerged );
	}	

	// then merge hrn reachability into hrnSummary
	hrnSummary.setAlpha( hrnSummary.getAlpha().union( hrn.getAlpha() ) );
    }


    protected void transferOnto( HeapRegionNode hrnA, HeapRegionNode hrnB ) {

	// clear references in and out of node i
	clearReferenceEdgesFrom( hrnB, null, true );
	clearReferenceEdgesTo  ( hrnB, null, true );
	
	// copy each edge in and out of A to B
	Iterator<ReferenceEdge> itrReferencee = hrnA.iteratorToReferencees();
	while( itrReferencee.hasNext() ) {
	    ReferenceEdge  edge          = itrReferencee.next();
	    HeapRegionNode hrnReferencee = edge.getDst();
	    ReferenceEdge  edgeNew       = edge.copy();
	    edgeNew.setSrc( hrnB );

	    addReferenceEdge( hrnB, hrnReferencee, edgeNew );
	}
	
	Iterator<ReferenceEdge> itrReferencer = hrnA.iteratorToReferencers();
	while( itrReferencer.hasNext() ) {
	    ReferenceEdge edge         = itrReferencer.next();
	    OwnershipNode onReferencer = edge.getSrc();
	    ReferenceEdge edgeNew      = edge.copy();
	    edgeNew.setDst( hrnB );
	    	    
	    addReferenceEdge( onReferencer, hrnB, edgeNew );
	}	    
	
	// replace hrnB reachability with hrnA's
	hrnB.setAlpha( hrnA.getAlpha() );
    }


    protected void ageTokens( AllocationSite as, ReferenceEdge edge ) {
	edge.setBeta( edge.getBeta().ageTokens( as ) );
    }

    protected void ageTokens( AllocationSite as, HeapRegionNode hrn ) {
	hrn.setAlpha( hrn.getAlpha().ageTokens( as ) );
    }

    protected void majorAgeTokens( AllocationSite as, ReferenceEdge edge ) {
	//edge.setBeta( edge.getBeta().majorAgeTokens( as ) );
    }

    protected void majorAgeTokens( AllocationSite as, HeapRegionNode hrn ) {
	//hrn.setAlpha( hrn.getAlpha().majorAgeTokens( as ) );
    }

    
    // some notes:
    // the heap regions that are specially allocated as multiple-object
    // regions for method parameters need to be remembered in order to
    // resolve a function call.  So actually, we need a mapping from
    // caller argument descriptors to the callee parameter heap regions
    // to apply reference edges in the callee to the caller graph.
    // 
    // also, Constructors and virtual dispatch methods have a "this"
    // argument that make the mapping of arguments to parameters a little
    // tricky.  What happens to that this region?


    public void resolveMethodCall( FlatCall                fc,
				   boolean                 isStatic,
				   FlatMethod              fm,
				   OwnershipGraph          ogCallee ) {

	/*
	// verify the existence of allocation sites and their
	// shadows from the callee in the context of this caller graph
	Iterator<AllocationSite> asItr = ogCallee.allocationSites.iterator();
	while( asItr.hasNext() ) {
	    AllocationSite allocSite        = asItr.next();    
	    HeapRegionNode hrnSummary       = getSummaryNode      ( allocSite );
	    HeapRegionNode hrnShadowSummary = getShadowSummaryNode( allocSite );
	}      

	// in non-static methods there is a "this" pointer
	// that should be taken into account
	if( isStatic ) {
	    assert fc.numArgs()     == fm.numParameters();
	} else {
	    assert fc.numArgs() + 1 == fm.numParameters();
	}

	// make a change set to translate callee tokens into caller tokens
	ChangeTupleSet C = new ChangeTupleSet().makeCanonical();

	for( int i = 0; i < fm.numParameters(); ++i ) {
	    
	    Integer indexParam = new Integer( i );

	    System.out.println( "In method "+fm+ " on param "+indexParam );

	    assert ogCallee.paramIndex2id.containsKey( indexParam );	    
	    Integer idParam = ogCallee.paramIndex2id.get( indexParam );

	    assert ogCallee.id2hrn.containsKey( idParam );
	    HeapRegionNode hrnParam = ogCallee.id2hrn.get( idParam );
	    assert hrnParam != null;

	    TokenTupleSet calleeTokenToMatch = 
		new TokenTupleSet( new TokenTuple( hrnParam ) ).makeCanonical();

	    
	    // now depending on whether the callee is static or not
	    // we need to account for a "this" argument in order to
	    // find the matching argument in the caller context
	    TempDescriptor argTemp;
	    if( isStatic ) {
		argTemp = fc.getArg( indexParam );
	    } else {
		if( indexParam == 0 ) {
		    argTemp = fc.getThis();
		} else {
		    argTemp = fc.getArg( indexParam - 1 );
		}
	    }
	    
	    LabelNode argLabel = getLabelNodeFromTemp( argTemp );
	    Iterator argHeapRegionsItr = argLabel.setIteratorToReferencedRegions();
	    while( argHeapRegionsItr.hasNext() ) {
		Map.Entry meArg                = (Map.Entry)               argHeapRegionsItr.next();
		HeapRegionNode argHeapRegion   = (HeapRegionNode)          meArg.getKey();
		ReferenceEdgeProperties repArg = (ReferenceEdgeProperties) meArg.getValue();
		
		Iterator<TokenTupleSet> ttsItr = repArg.getBeta().iterator();
		while( ttsItr.hasNext() ) {
		    TokenTupleSet callerTokensToReplace = ttsItr.next();

		    ChangeTuple ct = new ChangeTuple( calleeTokenToMatch,
						      callerTokensToReplace ).makeCanonical();

		    C = C.union( ct );
		}
	    }
	}

	System.out.println( "Applying method call "+fm );
	System.out.println( "  Change: "+C );


	// the heap regions represented by the arguments (caller graph)
	// and heap regions for the parameters (callee graph)
	// don't correspond to each other by heap region ID.  In fact,
	// an argument label node can be referencing several heap regions
	// so the parameter label always references a multiple-object
	// heap region in order to handle the range of possible contexts
	// for a method call.  This means we need to make a special mapping
	// of argument->parameter regions in order to update the caller graph

	// for every heap region->heap region edge in the
	// callee graph, create the matching edge or edges
	// in the caller graph
	Set      sCallee = ogCallee.id2hrn.entrySet();
	Iterator iCallee = sCallee.iterator();
	while( iCallee.hasNext() ) {
	    Map.Entry      meCallee  = (Map.Entry)      iCallee.next();
	    Integer        idCallee  = (Integer)        meCallee.getKey();
	    HeapRegionNode hrnCallee = (HeapRegionNode) meCallee.getValue();

	    HeapRegionNode hrnChildCallee = null;
	    Iterator heapRegionsItrCallee = hrnCallee.setIteratorToReferencedRegions();	    
	    while( heapRegionsItrCallee.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               heapRegionsItrCallee.next();
		hrnChildCallee               = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties repC = (ReferenceEdgeProperties) me.getValue();

		Integer idChildCallee = hrnChildCallee.getID();

		// only address this edge if it is not a special reflexive edge
		if( !repC.isInitialParamReflexive() ) {
		
		    // now we know that in the callee method's ownership graph
		    // there is a heap region->heap region reference edge given
		    // by heap region pointers:
		    // hrnCallee -> heapChildCallee
		    //
		    // or by the ownership-graph independent ID's:
		    // idCallee -> idChildCallee		
		    //
		    // So now make a set of possible source heaps in the caller graph
		    // and a set of destination heaps in the caller graph, and make
		    // a reference edge in the caller for every possible (src,dst) pair 
		    HashSet<HeapRegionNode> possibleCallerSrcs =  
			getHRNSetThatPossiblyMapToCalleeHRN( ogCallee,
							     idCallee,
							     fc,
							     isStatic );

		    HashSet<HeapRegionNode> possibleCallerDsts = 
			getHRNSetThatPossiblyMapToCalleeHRN( ogCallee,
							     idChildCallee,
							     fc,
							     isStatic );

		    // make every possible pair of {srcSet} -> {dstSet} edges in the caller
		    Iterator srcItr = possibleCallerSrcs.iterator();
		    while( srcItr.hasNext() ) {
			HeapRegionNode src = (HeapRegionNode) srcItr.next();

			Iterator dstItr = possibleCallerDsts.iterator();
			while( dstItr.hasNext() ) {
			    HeapRegionNode dst = (HeapRegionNode) dstItr.next();

			    addReferenceEdge( src, dst, repC.copy() );
			}
		    }
		}
	    } 
	}	
	*/
    }

    /*
    private HashSet<HeapRegionNode> getHRNSetThatPossiblyMapToCalleeHRN( OwnershipGraph ogCallee,
									 Integer        idCallee,
									 FlatCall       fc,
									 boolean        isStatic ) {

	HashSet<HeapRegionNode> possibleCallerHRNs = new HashSet<HeapRegionNode>();

	if( ogCallee.id2paramIndex.containsKey( idCallee ) ) {
	    // the heap region that is part of this
	    // reference edge won't have a matching ID in the
	    // caller graph because it is specifically allocated
	    // for a particular parameter.  Use that information
	    // to find the corresponding argument label in the
	    // caller in order to create the proper reference edge
	    // or edges.
	    assert !id2hrn.containsKey( idCallee );
	    
	    Integer paramIndex = ogCallee.id2paramIndex.get( idCallee );
	    TempDescriptor argTemp;
	    
	    // now depending on whether the callee is static or not
	    // we need to account for a "this" argument in order to
	    // find the matching argument in the caller context
	    if( isStatic ) {
		argTemp = fc.getArg( paramIndex );
	    } else {
		if( paramIndex == 0 ) {
		    argTemp = fc.getThis();
		} else {
		    argTemp = fc.getArg( paramIndex - 1 );
		}
	    }
	    
	    LabelNode argLabel = getLabelNodeFromTemp( argTemp );
	    Iterator argHeapRegionsItr = argLabel.setIteratorToReferencedRegions();
	    while( argHeapRegionsItr.hasNext() ) {
		Map.Entry meArg                = (Map.Entry)               argHeapRegionsItr.next();
		HeapRegionNode argHeapRegion   = (HeapRegionNode)          meArg.getKey();
		ReferenceEdgeProperties repArg = (ReferenceEdgeProperties) meArg.getValue();
		
		possibleCallerHRNs.add( (HeapRegionNode) argHeapRegion );
	    }
	    
	} else {
	    // this heap region is not a parameter, so it should
	    // have a matching heap region in the caller graph	   	    
	    assert id2hrn.containsKey( idCallee );
	    possibleCallerHRNs.add( id2hrn.get( idCallee ) );
	}

	return possibleCallerHRNs;
    }
    */    


    ////////////////////////////////////////////////////
    // in merge() and equals() methods the suffix A 
    // represents the passed in graph and the suffix
    // B refers to the graph in this object
    // Merging means to take the incoming graph A and
    // merge it into B, so after the operation graph B
    // is the final result.
    ////////////////////////////////////////////////////
    public void merge( OwnershipGraph og ) {

        if( og == null ) {
	  return;
        }

	mergeOwnershipNodes ( og );
	mergeReferenceEdges ( og );
	mergeId2paramIndex  ( og );	
	mergeAllocationSites( og );
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
		
	    } else {
		// otherwise this is a node present in both graphs
		// so make the new reachability set a union of the
		// nodes' reachability sets
		HeapRegionNode hrnB = id2hrn.get( idA );
		hrnB.setAlpha( hrnB.getAlpha().union( hrnA.getAlpha() ) );
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

	// heap regions
	Set      sA = og.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

	    Iterator<ReferenceEdge> heapRegionsItrA = hrnA.iteratorToReferencees();	    
	    while( heapRegionsItrA.hasNext() ) {
		ReferenceEdge  edgeA     = heapRegionsItrA.next();
		HeapRegionNode hrnChildA = edgeA.getDst();
		Integer        idChildA  = hrnChildA.getID();

		// at this point we know an edge in graph A exists
		// idA -> idChildA, does this exist in B?
		assert id2hrn.containsKey( idA );
		HeapRegionNode hrnB        = id2hrn.get( idA );
		ReferenceEdge  edgeToMerge = null;

		Iterator<ReferenceEdge> heapRegionsItrB = hrnB.iteratorToReferencees();
		while( heapRegionsItrB.hasNext() &&
		       edgeToMerge == null          ) {

		    ReferenceEdge  edgeB     = heapRegionsItrB.next();
		    HeapRegionNode hrnChildB = edgeB.getDst();
		    Integer        idChildB  = hrnChildB.getID();

		    // don't use the ReferenceEdge.equals() here because
		    // we're talking about existence between graphs
		    if( idChildB.equals( idChildA ) &&
			edgeB.getFieldDesc() == edgeA.getFieldDesc() ) {
			edgeToMerge = edgeB;
		    }
		}

		// if the edge from A was not found in B,
		// add it to B.
		if( edgeToMerge == null ) {
		    assert id2hrn.containsKey( idChildA );
		    HeapRegionNode hrnChildB = id2hrn.get( idChildA );
		    edgeToMerge = edgeA.copy();
		    edgeToMerge.setSrc( hrnB );
		    edgeToMerge.setDst( hrnChildB );
		    addReferenceEdge( hrnB, hrnChildB, edgeToMerge );
		}
		// otherwise, the edge already existed in both graphs
		// so merge their reachability sets
		else {
		    // just replace this beta set with the union
		    assert edgeToMerge != null;
		    edgeToMerge.setBeta( 
		      edgeToMerge.getBeta().union( edgeA.getBeta() ) 
				       );
		    if( !edgeA.isInitialParamReflexive() ) {
			edgeToMerge.setIsInitialParamReflexive( false );
		    }
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

	    Iterator<ReferenceEdge> heapRegionsItrA = lnA.iteratorToReferencees();	    
	    while( heapRegionsItrA.hasNext() ) {
		ReferenceEdge  edgeA     = heapRegionsItrA.next();
		HeapRegionNode hrnChildA = edgeA.getDst();
		Integer        idChildA  = hrnChildA.getID();		

		// at this point we know an edge in graph A exists
		// tdA -> idChildA, does this exist in B?
		assert td2ln.containsKey( tdA );
		LabelNode     lnB         = td2ln.get( tdA );
		ReferenceEdge edgeToMerge = null;

		// labels never have edges with a field
		//assert edgeA.getFieldDesc() == null;

		Iterator<ReferenceEdge> heapRegionsItrB = lnB.iteratorToReferencees();
		while( heapRegionsItrB.hasNext() &&
		       edgeToMerge == null          ) {

		    ReferenceEdge  edgeB     = heapRegionsItrB.next();
		    HeapRegionNode hrnChildB = edgeB.getDst();
		    Integer        idChildB  = hrnChildB.getID();

		    // labels never have edges with a field
		    //assert edgeB.getFieldDesc() == null;

		    // don't use the ReferenceEdge.equals() here because
		    // we're talking about existence between graphs
		    if( idChildB.equals( idChildA ) &&
			edgeB.getFieldDesc() == edgeA.getFieldDesc() ) {
			edgeToMerge = edgeB;
		    }
		}

		// if the edge from A was not found in B,
		// add it to B.
		if( edgeToMerge == null ) {
		    assert id2hrn.containsKey( idChildA );
		    HeapRegionNode hrnChildB = id2hrn.get( idChildA );
		    edgeToMerge = edgeA.copy();
		    edgeToMerge.setSrc( lnB );
		    edgeToMerge.setDst( hrnChildB );
		    addReferenceEdge( lnB, hrnChildB, edgeToMerge );
		}
		// otherwise, the edge already existed in both graphs
		// so merge their reachability sets
		else {
		    // just replace this beta set with the union
		    edgeToMerge.setBeta( 
		      edgeToMerge.getBeta().union( edgeA.getBeta() ) 
				       );
		    if( !edgeA.isInitialParamReflexive() ) {
			edgeToMerge.setIsInitialParamReflexive( false );
		    }
		}  
	    } 
	}
    }

    // you should only merge ownership graphs that have the
    // same number of parameters, or if one or both parameter
    // index tables are empty
    protected void mergeId2paramIndex( OwnershipGraph og ) {
	if( id2paramIndex.size() == 0 ) {
	    id2paramIndex = og.id2paramIndex;
	    paramIndex2id = og.paramIndex2id;
	    return;
	}

	if( og.id2paramIndex.size() == 0 ) {
	    return;
	}

	assert id2paramIndex.size() == og.id2paramIndex.size();
    }

    protected void mergeAllocationSites( OwnershipGraph og ) {
	allocationSites.addAll( og.allocationSites );
    }



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

	if( !areLabelNodesEqual( og ) ) {
	    return false;
	}

	if( !areReferenceEdgesEqual( og ) ) {
	    return false;
	}

	if( !areId2paramIndexEqual( og ) ) {
	    return false;
	}

	// if everything is equal up to this point,
	// assert that allocationSites is also equal--
	// this data is redundant and kept for efficiency
	assert allocationSites.equals( og.allocationSites );

	return true;
    }

    protected boolean areHeapRegionNodesEqual( OwnershipGraph og ) {       

	if( !areallHRNinAalsoinBandequal( this, og ) ) {
	    return false;
	}

	if( !areallHRNinAalsoinBandequal( og, this ) ) {
	    return false;
	}

	return true;
    }

    static protected boolean areallHRNinAalsoinBandequal( OwnershipGraph ogA,
							  OwnershipGraph ogB ) {
	Set      sA = ogA.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();
	    
	    if( !ogB.id2hrn.containsKey( idA ) ) {
		return false;
	    }

	    HeapRegionNode hrnB = ogB.id2hrn.get( idA );	    
	    if( !hrnA.equalsIncludingAlpha( hrnB ) ) {
		return false;
	    }       
	}

	return true;
    }


    protected boolean areLabelNodesEqual( OwnershipGraph og ) {

	if( !areallLNinAalsoinBandequal( this, og ) ) {
	    return false;
	}

	if( !areallLNinAalsoinBandequal( og, this ) ) {
	    return false;
	}

	return true;
    }

    static protected boolean areallLNinAalsoinBandequal( OwnershipGraph ogA,
							 OwnershipGraph ogB ) {
	Set      sA = ogA.td2ln.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA = (Map.Entry)      iA.next();
	    TempDescriptor tdA = (TempDescriptor) meA.getKey();

	    if( !ogB.td2ln.containsKey( tdA ) ) {
		return false;
	    }
	}
	
	return true;
    }


    protected boolean areReferenceEdgesEqual( OwnershipGraph og ) {
	if( !areallREinAandBequal( this, og ) ) {
	    return false;
	}

	return true;
    }

    static protected boolean areallREinAandBequal( OwnershipGraph ogA,
						   OwnershipGraph ogB ) {

	// check all the heap region->heap region edges
	Set      sA = ogA.id2hrn.entrySet();
	Iterator iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA  = (Map.Entry)      iA.next();
	    Integer        idA  = (Integer)        meA.getKey();
	    HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

	    // we should have already checked that the same
	    // heap regions exist in both graphs
	    assert ogB.id2hrn.containsKey( idA );

	    if( !areallREfromAequaltoB( ogA, hrnA, ogB ) ) {
		return false;
	    }

	    // then check every edge in B for presence in A, starting
	    // from the same parent HeapRegionNode
	    HeapRegionNode hrnB = ogB.id2hrn.get( idA );

	    if( !areallREfromAequaltoB( ogB, hrnB, ogA ) ) {
		return false;
	    }
	}

	// then check all the label->heap region edges
	sA = ogA.td2ln.entrySet();
	iA = sA.iterator();
	while( iA.hasNext() ) {
	    Map.Entry      meA = (Map.Entry)      iA.next();
	    TempDescriptor tdA = (TempDescriptor) meA.getKey();
	    LabelNode      lnA = (LabelNode)      meA.getValue();

	    // we should have already checked that the same
	    // label nodes exist in both graphs
	    assert ogB.td2ln.containsKey( tdA );

	    if( !areallREfromAequaltoB( ogA, lnA, ogB ) ) {
		return false;
	    }

	    // then check every edge in B for presence in A, starting
	    // from the same parent LabelNode
	    LabelNode lnB = ogB.td2ln.get( tdA );

	    if( !areallREfromAequaltoB( ogB, lnB, ogA ) ) {
		return false;
	    }
	}

	return true;
    }


    static protected boolean areallREfromAequaltoB( OwnershipGraph ogA,
						    OwnershipNode  onA,
						    OwnershipGraph ogB ) {
	
	Iterator<ReferenceEdge> itrA = onA.iteratorToReferencees();
	while( itrA.hasNext() ) {
	    ReferenceEdge  edgeA     = itrA.next();
	    HeapRegionNode hrnChildA = edgeA.getDst();
	    Integer        idChildA  = hrnChildA.getID();

	    assert ogB.id2hrn.containsKey( idChildA );

	    // at this point we know an edge in graph A exists
	    // onA -> idChildA, does this exact edge exist in B?
	    boolean edgeFound = false;

	    OwnershipNode onB = null;	    
	    if( onA instanceof HeapRegionNode ) {
		HeapRegionNode hrnA = (HeapRegionNode) onA;
		onB = ogB.id2hrn.get( hrnA.getID() );
	    } else {
		LabelNode lnA = (LabelNode) onA;
		onB = ogB.td2ln.get( lnA.getTempDescriptor() );
	    }

	    Iterator<ReferenceEdge> itrB = onB.iteratorToReferencees();
	    while( itrB.hasNext() ) {
		ReferenceEdge  edgeB     = itrB.next();
		HeapRegionNode hrnChildB = edgeB.getDst();
		Integer        idChildB  = hrnChildB.getID();

		if( idChildA.equals( idChildB ) &&
		    edgeA.getFieldDesc() == edgeB.getFieldDesc() ) {

		    // there is an edge in the right place with the right field,
		    // but do they have the same attributes?
		    if( edgeA.isInitialParamReflexive() == edgeB.isInitialParamReflexive() &&
			edgeA.getBeta().equals( edgeB.getBeta() )                          ) {
			
			edgeFound = true;
		    } else {
			return false;
		    }
		}
	    }

	    if( !edgeFound ) {
		return false;
	    }		
	}
	
	return true;
    }


    protected boolean areId2paramIndexEqual( OwnershipGraph og ) {
	return id2paramIndex.size() == og.id2paramIndex.size();
    }


    /*
    // given a set B of heap region node ID's, return the set of heap
    // region node ID's that is reachable from B
    public HashSet<Integer> getReachableSet( HashSet<Integer> idSetB ) {

	HashSet<HeapRegionNode> toVisit = new HashSet<HeapRegionNode>();
	HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

	// initial nodes to visit are from set B
	Iterator initialItr = idSetB.iterator();
	while( initialItr.hasNext() ) {
	    Integer idInitial = (Integer) initialItr.next();
	    assert id2hrn.contains( idInitial );
	    HeapRegionNode hrnInitial = id2hrn.get( idInitial );
	    toVisit.add( hrnInitial );
	}

	HashSet<Integer> idSetReachableFromB = new HashSet<Integer>();

	// do a heap traversal
	while( !toVisit.isEmpty() ) {
	    HeapRegionNode hrnVisited = (HeapRegionNode) toVisit.iterator().next();
	    toVisit.remove( hrnVisited );
	    visited.add   ( hrnVisited );

	    // for every node visited, add it to the total
	    // reachable set
	    idSetReachableFromB.add( hrnVisited.getID() );

	    // find other reachable nodes
	    Iterator referenceeItr = hrnVisited.setIteratorToReferencedRegions();
	    while( referenceeItr.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               referenceeItr.next();
		HeapRegionNode hrnReferencee = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties rep  = (ReferenceEdgeProperties) me.getValue();

		if( !visited.contains( hrnReferencee ) ) {
		    toVisit.add( hrnReferencee );
		}
	    }
	}

	return idSetReachableFromB;
    }


    // used to find if a heap region can possibly have a reference to
    // any of the heap regions in the given set
    // if the id supplied is in the set, then a self-referencing edge
    // would return true, but that special case is specifically allowed
    // meaning that it isn't an external alias
    public boolean canIdReachSet( Integer id, HashSet<Integer> idSet ) {

	assert id2hrn.contains( id );
	HeapRegionNode hrn = id2hrn.get( id );

	
	//HashSet<HeapRegionNode> hrnSet = new HashSet<HeapRegionNode>();

	//Iterator i = idSet.iterator();
	//while( i.hasNext() ) {
	//    Integer idFromSet = (Integer) i.next();
	//   assert id2hrn.contains( idFromSet );
	//    hrnSet.add( id2hrn.get( idFromSet ) );
	//}
	

	// do a traversal from hrn and see if any of the
	// heap regions from the set come up during that
	HashSet<HeapRegionNode> toVisit = new HashSet<HeapRegionNode>();
	HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();
	
	toVisit.add( hrn );
	while( !toVisit.isEmpty() ) {
	    HeapRegionNode hrnVisited = (HeapRegionNode) toVisit.iterator().next();
	    toVisit.remove( hrnVisited );
	    visited.add   ( hrnVisited );

	    Iterator referenceeItr = hrnVisited.setIteratorToReferencedRegions();
	    while( referenceeItr.hasNext() ) {
		Map.Entry me                 = (Map.Entry)               referenceeItr.next();
		HeapRegionNode hrnReferencee = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties rep  = (ReferenceEdgeProperties) me.getValue();

		if( idSet.contains( hrnReferencee.getID() ) ) {
		    if( !id.equals( hrnReferencee.getID() ) ) {
			return true;
		    }
		}

		if( !visited.contains( hrnReferencee ) ) {
		    toVisit.add( hrnReferencee );
		}
	    }
	}

	return false;
    }
    */   


    // for writing ownership graphs to dot files
    public void writeGraph( Descriptor methodDesc,
			    FlatNode   fn,
			    boolean    writeLabels,
			    boolean    labelSelect,
			    boolean    pruneGarbage,
			    boolean    writeReferencers 
			    ) throws java.io.IOException {
	writeGraph(
		   methodDesc.getSymbol() +
		   methodDesc.getNum() +
		   fn.toString(),
		   writeLabels,
		   labelSelect,
		   pruneGarbage,
		   writeReferencers
		   );
    }

    public void writeGraph( Descriptor methodDesc,
			    FlatNode   fn,
			    boolean    writeLabels,
			    boolean    writeReferencers 
			    ) throws java.io.IOException {
	writeGraph(
		   methodDesc.getSymbol() +
		   methodDesc.getNum() +
		   fn.toString(),
		   writeLabels,
		   false,
		   false,
		   writeReferencers
		   );
    }

    public void writeGraph( Descriptor methodDesc,
			    boolean    writeLabels,
			    boolean    writeReferencers 
			    ) throws java.io.IOException {
	writeGraph( 
		   methodDesc.getSymbol() +
		   methodDesc.getNum() +
		   "COMPLETE",
		   writeLabels,
		   false,
		   false,
		   writeReferencers
		    );
    }

    public void writeGraph( Descriptor methodDesc,
			    boolean    writeLabels,
			    boolean    labelSelect,
			    boolean    pruneGarbage,
			    boolean    writeReferencers 
			    ) throws java.io.IOException {
	writeGraph( 
		   methodDesc.getSymbol() +
		   methodDesc.getNum() +
		   "COMPLETE",
		   writeLabels,
		   labelSelect,
		   pruneGarbage,
		   writeReferencers
		    );
    }

    public void writeGraph( String graphName,
			    boolean writeLabels,
			    boolean labelSelect,
			    boolean pruneGarbage,
			    boolean writeReferencers 
			    ) throws java.io.IOException {

	// remove all non-word characters from the graph name so
	// the filename and identifier in dot don't cause errors
	graphName = graphName.replaceAll( "[\\W]", "" );

	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "digraph "+graphName+" {\n" );
	//bw.write( "  size=\"7.5,10\";\n" );

	HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

	// then visit every heap region node
	if( !pruneGarbage ) {	    
	    Set      s = id2hrn.entrySet();
	    Iterator i = s.iterator();
	    while( i.hasNext() ) {
		Map.Entry      me  = (Map.Entry)      i.next();
		HeapRegionNode hrn = (HeapRegionNode) me.getValue();
		if( !visited.contains( hrn ) ) {
		    traverseHeapRegionNodes( VISIT_HRN_WRITE_FULL, 
					     hrn, 
					     bw, 
					     null, 
					     visited, 
					     writeReferencers );
		}
	    }
	}

	bw.write( "  graphTitle[label=\""+graphName+"\",shape=box];\n" );


	// then visit every label node, useful for debugging
	if( writeLabels ) {
	    Set      s = td2ln.entrySet();
	    Iterator i = s.iterator();
	    while( i.hasNext() ) {
		Map.Entry me = (Map.Entry) i.next();
		LabelNode ln = (LabelNode) me.getValue();
		
		if( labelSelect ) {
		    String labelStr = ln.getTempDescriptorString();
		    if( labelStr.startsWith( "___temp"      ) ||
			labelStr.startsWith( "___dst"       ) ||
			labelStr.startsWith( "___srctmp"   ) ||
			labelStr.startsWith( "___neverused" )   ) {
			continue;
		    }
		}

		bw.write( ln.toString() + ";\n" );

		Iterator<ReferenceEdge> heapRegionsItr = ln.iteratorToReferencees();
		while( heapRegionsItr.hasNext() ) {
		    ReferenceEdge  edge = heapRegionsItr.next();
		    HeapRegionNode hrn  = edge.getDst();

		    if( pruneGarbage && !visited.contains( hrn ) ) {
			traverseHeapRegionNodes( VISIT_HRN_WRITE_FULL, 
						 hrn, 
						 bw, 
						 null, 
						 visited, 
						 writeReferencers );
		    }
		    
		    bw.write( "  "        + ln.toString() +
			      " -> "      + hrn.toString() +
			      "[label=\"" + edge.toGraphEdgeString() +
			      "\",decorate];\n" );
		}
	    }
	}

	
	bw.write( "}\n" );
	bw.close();
    }

    protected void traverseHeapRegionNodes( int mode,
					    HeapRegionNode hrn,
					    BufferedWriter bw,
					    TempDescriptor td,
					    HashSet<HeapRegionNode> visited,
					    boolean writeReferencers
					    ) throws java.io.IOException {

	if( visited.contains( hrn ) ) {
	    return;
	}
	visited.add( hrn );

	switch( mode ) {
	case VISIT_HRN_WRITE_FULL:
	    
	    String attributes = "[";
	    
	    if( hrn.isSingleObject() ) {
		attributes += "shape=box";
	    } else {
		attributes += "shape=Msquare";
	    }

	    if( hrn.isFlagged() ) {
		attributes += ",style=filled,fillcolor=lightgrey";
	    }

	    attributes += ",label=\"ID"        +
		          hrn.getID()          +
		          "\\n"                +
            		  hrn.getDescription() + 
		          "\\n"                +
		          hrn.getAlphaString() +
		          "\"]";

	    bw.write( "  " + hrn.toString() + attributes + ";\n" );
	    break;
	}


	// useful for debugging
	if( writeReferencers ) {
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
	}

	Iterator<ReferenceEdge> childRegionsItr = hrn.iteratorToReferencees();
	while( childRegionsItr.hasNext() ) {
	    ReferenceEdge  edge     = childRegionsItr.next();
	    HeapRegionNode hrnChild = edge.getDst();

	    switch( mode ) {
	    case VISIT_HRN_WRITE_FULL:
		bw.write( "  "        + hrn.toString() +
			  " -> "      + hrnChild.toString() +
			  "[label=\"" + edge.toGraphEdgeString() +
			  "\",decorate];\n" );
		break;
	    }

	    traverseHeapRegionNodes( mode,
				     hrnChild,
				     bw,
				     td,
				     visited,
				     writeReferencers );
	}
    }
}
