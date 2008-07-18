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
				     ReferenceEdgeProperties rep ) {
	assert referencer != null;
	assert referencee != null;
	assert rep        != null;
	referencer.addReferencedRegion( referencee, rep );
	referencee.addReferencer( referencer );
	rep.setSrc( referencer );
	rep.setDst( referencee );
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
    
    protected void propagateTokens( HeapRegionNode                   nPrime,
				    ChangeTupleSet                   c0,
				    HashSet<HeapRegionNode>          nodesWithNewAlpha,
				    HashSet<ReferenceEdgeProperties> edgesWithNewBeta ) {

	HashSet<HeapRegionNode> todoNodes
	    = new HashSet<HeapRegionNode>();
	todoNodes.add( nPrime );

	HashSet<ReferenceEdgeProperties> todoEdges 
	    = new HashSet<ReferenceEdgeProperties>();
	
	Hashtable<HeapRegionNode, ChangeTupleSet> nodePlannedChanges 
	    = new Hashtable<HeapRegionNode, ChangeTupleSet>();
	nodePlannedChanges.put( nPrime, c0 );

	Hashtable<ReferenceEdgeProperties, ChangeTupleSet> edgePlannedChanges 
	    = new Hashtable<ReferenceEdgeProperties, ChangeTupleSet>();
	
	Hashtable<HeapRegionNode, ChangeTupleSet> nodeChangesMade
	    = new Hashtable<HeapRegionNode, ChangeTupleSet>();

	while( !todoNodes.isEmpty() ) {
	    HeapRegionNode n = todoNodes.iterator().next();
	    todoNodes.remove( n );
	    
	    ChangeTupleSet C = nodePlannedChanges.get( n );

	    if( !nodeChangesMade.containsKey( n ) ) {
		nodeChangesMade.put( n, new ChangeTupleSet().makeCanonical() );
	    }

	    Iterator itrC = C.iterator();
	    while( itrC.hasNext() ) {
		ChangeTuple c = (ChangeTuple) itrC.next();

		if( n.getAlpha().contains( c.getSetToMatch() ) ) {
		    ReachabilitySet withChange = n.getAlpha().union( c.getSetToAdd() );
		    n.setAlphaNew( n.getAlphaNew().union( withChange ) );
		    nodesWithNewAlpha.add( n );
		    nodeChangesMade.put( n, nodeChangesMade.get( n ).union( c ) );
		}
	    }

	    ChangeTupleSet Cprime = nodeChangesMade.get( n );

	    Iterator referItr = n.iteratorToReferencers();
	    while( referItr.hasNext() ) {
		OwnershipNode           on  = (OwnershipNode) referItr.next();
		ReferenceEdgeProperties rep = on.getReferenceTo( n );
		todoEdges.add( rep );

		if( !edgePlannedChanges.containsKey( rep ) ) {
		    edgePlannedChanges.put( rep, new ChangeTupleSet().makeCanonical() );
		}

		edgePlannedChanges.put( rep, edgePlannedChanges.get( rep ).union( Cprime ) );
	    }

	    HeapRegionNode          m = null;
	    ReferenceEdgeProperties f = null;
	    Iterator refeeItr = n.setIteratorToReferencedRegions();
	    while( refeeItr.hasNext() ) {
		Map.Entry me = (Map.Entry)               refeeItr.next();
		m            = (HeapRegionNode)          me.getKey();
		f            = (ReferenceEdgeProperties) me.getValue();

		ChangeTupleSet changesToPass = new ChangeTupleSet().makeCanonical();

		Iterator itrCprime = Cprime.iterator();
		while( itrCprime.hasNext() ) {
		    ChangeTuple c = (ChangeTuple) itrCprime.next();
		    if( f.getBeta().contains( c.getSetToMatch() ) ) {
			changesToPass = changesToPass.union( c );
		    }
		}

		if( !changesToPass.isEmpty() ) {
		    if( !nodePlannedChanges.containsKey( m ) ) {
			nodePlannedChanges.put( m, new ChangeTupleSet().makeCanonical() );
		    }

		    ChangeTupleSet currentChanges = nodePlannedChanges.get( m );

		    if( !changesToPass.isSubset( currentChanges ) ) {
			todoNodes.add( m );
			nodePlannedChanges.put( m, currentChanges.union( changesToPass ) );
		    }
		}
	    }
	}

       
	while( !todoEdges.isEmpty() ) {
	    ReferenceEdgeProperties e = todoEdges.iterator().next();
	    todoEdges.remove( e );

	    if( !edgePlannedChanges.containsKey( e ) ) {
		edgePlannedChanges.put( e, new ChangeTupleSet().makeCanonical() );
	    }
	    
	    ChangeTupleSet C = edgePlannedChanges.get( e );

	    ChangeTupleSet changesToPass = new ChangeTupleSet().makeCanonical();

	    Iterator itrC = C.iterator();
	    while( itrC.hasNext() ) {
		ChangeTuple c = (ChangeTuple) itrC.next();
		if( e.getBeta().contains( c.getSetToMatch() ) ) {
		    ReachabilitySet withChange = e.getBeta().union( c.getSetToAdd() );
		    e.setBetaNew( e.getBetaNew().union( withChange ) );
		    edgesWithNewBeta.add( e );
		    changesToPass = changesToPass.union( c );
		}
	    }

	    OwnershipNode onSrc = e.getSrc();

	    if( !changesToPass.isEmpty() && onSrc instanceof HeapRegionNode ) {		
		HeapRegionNode n = (HeapRegionNode) onSrc;
		Iterator referItr = n.iteratorToReferencers();

		while( referItr.hasNext() ) {
		    OwnershipNode onRef = (OwnershipNode) referItr.next();
		    ReferenceEdgeProperties f = onRef.getReferenceTo( n );
		    
		    if( !edgePlannedChanges.containsKey( f ) ) {
			edgePlannedChanges.put( f, new ChangeTupleSet().makeCanonical() );
		    }
		  
		    ChangeTupleSet currentChanges = edgePlannedChanges.get( f );
		
		    if( !changesToPass.isSubset( currentChanges ) ) {
			todoEdges.add( f );
			edgePlannedChanges.put( f, currentChanges.union( changesToPass ) );
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
    public void assignTempToTemp( TempDescriptor src, 
				  TempDescriptor dst ) {
	LabelNode srcln = getLabelNodeFromTemp( src );
	LabelNode dstln = getLabelNodeFromTemp( dst );

	clearReferenceEdgesFrom( dstln );

	HeapRegionNode newReferencee = null;
        Iterator       srcRegionsItr = srcln.setIteratorToReferencedRegions();
	while( srcRegionsItr.hasNext() ) {
	    Map.Entry me                = (Map.Entry)               srcRegionsItr.next();
	    newReferencee               = (HeapRegionNode)          me.getKey();
	    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();

	    addReferenceEdge( dstln, newReferencee, rep.copy() );
	}
    }

    public void assignTempToField( TempDescriptor  src,
				   TempDescriptor  dst,
				   FieldDescriptor fd ) {
	LabelNode srcln = getLabelNodeFromTemp( src );
	LabelNode dstln = getLabelNodeFromTemp( dst );

	clearReferenceEdgesFrom( dstln );

	HeapRegionNode hrn           = null;
	Iterator       srcRegionsItr = srcln.setIteratorToReferencedRegions();
	while( srcRegionsItr.hasNext() ) {
	    Map.Entry me                 = (Map.Entry)               srcRegionsItr.next();
	    hrn                          = (HeapRegionNode)          me.getKey();
	    ReferenceEdgeProperties rep1 = (ReferenceEdgeProperties) me.getValue();
	    ReachabilitySet beta1        = rep1.getBeta();

	    HeapRegionNode hrnOneHop = null;
	    Iterator hrnRegionsItr = hrn.setIteratorToReferencedRegions();
	    while( hrnRegionsItr.hasNext() ) {
		Map.Entry meH                = (Map.Entry)               hrnRegionsItr.next();
		hrnOneHop                    = (HeapRegionNode)          meH.getKey();
		ReferenceEdgeProperties rep2 = (ReferenceEdgeProperties) meH.getValue();
		ReachabilitySet beta2        = rep2.getBeta();

		ReferenceEdgeProperties rep = rep2.copy();
		rep.setIsInitialParamReflexive( false );
		rep.setBeta( beta1.intersection( beta2 ) );

		addReferenceEdge( dstln, hrnOneHop, rep );
	    }
	}
    }

    public void assignFieldToTemp( TempDescriptor  src, 
				   TempDescriptor  dst,
				   FieldDescriptor fd ) {

	// I think my use of src and dst are actually backwards in this method!
	// acccording to the Reachability Notes, think of dst at N and src as N prime

	LabelNode srcln = getLabelNodeFromTemp( src );
	LabelNode dstln = getLabelNodeFromTemp( dst );

	HashSet<HeapRegionNode>          nodesWithNewAlpha = new HashSet<HeapRegionNode>();
	HashSet<ReferenceEdgeProperties> edgesWithNewBeta  = new HashSet<ReferenceEdgeProperties>();

	HeapRegionNode          hrn = null;
	ReferenceEdgeProperties rep = null;
	Iterator dstRegionsItr = dstln.setIteratorToReferencedRegions();
	while( dstRegionsItr.hasNext() ) {
	    Map.Entry me = (Map.Entry)               dstRegionsItr.next();
	    hrn          = (HeapRegionNode)          me.getKey();
	    rep          = (ReferenceEdgeProperties) me.getValue();

	    ReachabilitySet R = hrn.getAlpha().intersection( rep.getBeta() );

	    HeapRegionNode          hrnSrc = null;
	    ReferenceEdgeProperties repSrc = null;
	    Iterator srcRegionsItr = srcln.setIteratorToReferencedRegions();
	    while( srcRegionsItr.hasNext() ) {
		Map.Entry meS = (Map.Entry)               srcRegionsItr.next();
		hrnSrc        = (HeapRegionNode)          meS.getKey();
		repSrc        = (ReferenceEdgeProperties) meS.getValue();
		
		ReachabilitySet O = srcln.getReferenceTo( hrnSrc ).getBeta();

		ChangeTupleSet Cy = O.unionUpArityToChangeSet( R );
		ChangeTupleSet Cx = R.unionUpArityToChangeSet( O );

		propagateTokens( hrnSrc, Cy, nodesWithNewAlpha, edgesWithNewBeta );
		propagateTokens( hrn,    Cx, nodesWithNewAlpha, edgesWithNewBeta );

		// note that this picks up the beta after the propogation has
		// been applied
		ReferenceEdgeProperties repNew 
		    = new ReferenceEdgeProperties( false, false, repSrc.getBetaNew() );

		addReferenceEdge( hrn, hrnSrc, repNew );
	    }
	}	

	Iterator nodeItr = nodesWithNewAlpha.iterator();
	while( nodeItr.hasNext() ) {
	    ((HeapRegionNode) nodeItr.next()).applyAlphaNew();
	}

	Iterator edgeItr = edgesWithNewBeta.iterator();
	while( edgeItr.hasNext() ) {
	    ((ReferenceEdgeProperties) edgeItr.next()).applyBetaNew();
	}
    }

    public void assignTempToParameterAllocation( boolean        isTask,
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
	addReferenceEdge( lnParam, hrn, new ReferenceEdgeProperties( false, false, beta ) );
	addReferenceEdge( hrn,     hrn, new ReferenceEdgeProperties( false, true,  beta ) );
    }
    
    public void assignTempToNewAllocation( TempDescriptor td,
					   AllocationSite as ) {
	assert td != null;
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

	LabelNode dst = getLabelNodeFromTemp( td );
	
	clearReferenceEdgesFrom( dst );
	
	addReferenceEdge( dst, hrnNewest, new ReferenceEdgeProperties( false, false, hrnNewest.getAlpha() ) );
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


	//////////////////////////////////////////////////////////////////
	//
	//  move existing references down the line toward
	//  the oldest element, starting with the oldest
	// 
	//  An illustration:
	//    TempDescriptor = the td passed into this function, left side of new statement
	//    AllocationSite = { alpha0, alpha1, alpha2, alphaSummary }
	//
	//  1. Specially merge refs in/out at alpha2 into alphaSummary
	//  2. Move refs in/out at alpha1 over to alpha2 (alpha1 becomes alpha2)
	//  3. Move refs in/out at alpha0 over to alpha1
	//  4. Assign reference from td to alpha0, which now represents a freshly allocated object
	//
	//////////////////////////////////////////////////////////////////


	// first specially merge the references from the oldest
	// node into the summary node, keeping track of 1-to-1 edges
	Integer        idSummary  = as.getSummary();
	HeapRegionNode hrnSummary = id2hrn.get( idSummary );
	
	// if this is null then we haven't touched this allocation site
	// in the context of the current ownership graph, so simply
	// allocate an appropriate heap region node
	// this should only happen once per ownership per allocation site,
	// and a particular integer id can be used to locate the heap region
	// in different ownership graphs that represents the same part of an
	// allocation site
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

	// first transfer the references out of alpha_k to alpha_s
	Integer        idK  = as.getOldest();
	HeapRegionNode hrnK = id2hrn.get( idK );

	HeapRegionNode hrnReferencee = null;
	Iterator       itrReferencee = hrnK.setIteratorToReferencedRegions();
	while( itrReferencee.hasNext() ) {
	    Map.Entry               me  = (Map.Entry)               itrReferencee.next();
	    hrnReferencee               = (HeapRegionNode)          me.getKey();
	    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();
	    
	    // determine if another summary node is already referencing this referencee
	    boolean       hasSummaryReferencer = false;
	    OwnershipNode onReferencer         = null;
	    Iterator      itrReferencer        = hrnReferencee.iteratorToReferencers();
	    while( itrReferencer.hasNext() ) {
		onReferencer = (OwnershipNode) itrReferencer.next();
		if( onReferencer instanceof HeapRegionNode ) {
		    HeapRegionNode hrnPossibleSummary = (HeapRegionNode) onReferencer;
		    if( hrnPossibleSummary.isNewSummary() ) {
			hasSummaryReferencer = true;
		    }
		}
	    }

	    addReferenceEdge( hrnSummary,
			      hrnReferencee,
			      new ReferenceEdgeProperties( !hasSummaryReferencer ) );
	}

	// next transfer references to alpha_k over to alpha_s
	OwnershipNode onReferencer  = null;
	Iterator      itrReferencer = hrnK.iteratorToReferencers();
	while( itrReferencer.hasNext() ) {
	    onReferencer = (OwnershipNode) itrReferencer.next();
	    
	    ReferenceEdgeProperties rep = onReferencer.getReferenceTo( hrnK );
	    assert rep != null;	    
	    ReferenceEdgeProperties repSummary = onReferencer.getReferenceTo( hrnSummary );
	    ReferenceEdgeProperties repMerged = rep.copy();

	    if( repSummary == null ) {	    
		// the merge is trivial, nothing to be done
	    } else {
		// otherwise an edge from the referencer to alpha_S exists already
		// and the edge referencer->alpha_K should be merged with it
		repMerged.setBeta( repMerged.getBeta().union( repSummary.getBeta() ) );
	    }

	    addReferenceEdge( onReferencer, hrnSummary, repMerged );
	}

	
	// then move down the line of heap region nodes
	// clobbering the ith and transferring all references
	// to and from i-1 to node i.  Note that this clobbers
	// the oldest node (alpha_k) that was just merged into
	// the summary above and should move everything from
	// alpha_0 to alpha_1 before we finish
	for( int i = allocationDepth - 1; i > 0; --i ) {	    

	    // move references from the i-1 oldest to the ith oldest
	    Integer        idIth     = as.getIthOldest( i );
	    HeapRegionNode hrnI      = id2hrn.get( idIth );
	    Integer        idImin1th = as.getIthOldest( i - 1 );
	    HeapRegionNode hrnImin1  = id2hrn.get( idImin1th );

	    // clear references in and out of node i
	    clearReferenceEdgesFrom( hrnI );
	    clearReferenceEdgesTo  ( hrnI );

	    // copy each edge in and out of i-1 to i	    
	    hrnReferencee = null;
	    itrReferencee = hrnImin1.setIteratorToReferencedRegions();
	    while( itrReferencee.hasNext() ) {
		Map.Entry               me  = (Map.Entry)               itrReferencee.next();
		hrnReferencee               = (HeapRegionNode)          me.getKey();
		ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();
		
		addReferenceEdge( hrnI, hrnReferencee, rep.copy() );
	    }

	    onReferencer  = null;
	    itrReferencer = hrnImin1.iteratorToReferencers();
	    while( itrReferencer.hasNext() ) {
		onReferencer = (OwnershipNode) itrReferencer.next();

		ReferenceEdgeProperties rep = onReferencer.getReferenceTo( hrnImin1 );
		assert rep != null;

		addReferenceEdge( onReferencer, hrnI, rep.copy() );
	    }	    
	}

	// as stated above, the newest node alpha_0 should have had its
	// references moved over to alpha_1, so we can wipe alpha_0 clean
	// in preparation for operations that want to reference a freshly
	// allocated object from this allocation site
	Integer        id0th = as.getIthOldest( 0 );
	HeapRegionNode hrn0  = id2hrn.get( id0th );

	// the loop to move references from i-1 to i should
	// have touched this node, therefore assert it is non-null
	assert hrn0 != null;

	// clear all references in and out of newest node
	clearReferenceEdgesFrom( hrn0 );
	clearReferenceEdgesTo  ( hrn0 );
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
				   OwnershipGraph          ogCallee ) { //,
	//HashSet<AllocationSite> allocSiteSet ) {
	
	// first age all of the allocation sites from
	// the callee graph in this graph
	Iterator i = ogCallee.allocationSites.iterator();
	while( i.hasNext() ) {
	    AllocationSite allocSite = (AllocationSite) i.next();	    
	    this.age( allocSite );
	}

	// in non-static methods there is a "this" pointer
	// that should be taken into account
	if( isStatic ) {
	    assert fc.numArgs()     == fm.numParameters();
	} else {
	    assert fc.numArgs() + 1 == fm.numParameters();
	}

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
		    if( !ogCallee.id2hrn.contains( idChildCallee ) ) {
			//System.out.println( "Houston, we got a problem." );
			//System.out.println( "idCallee is "+idCallee );
			//System.out.println( "idChildCallee is "+idChildCallee );
			
			try {
			    writeGraph( "caller", false, false );
			    ogCallee.writeGraph( "callee", false, false );
			} catch( IOException e ) {}
		    }

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
    }

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
	// there is a data structure for storing label nodes
	// retireved by temp descriptors, and a data structure
	// for stroing heap region nodes retrieved by integer
	// ids.  Because finding edges requires interacting
	// with these disparate data structures frequently the
	// process is nearly duplicated, one for each structure
	// that stores edges

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

		HeapRegionNode          hrnChildB = null;
		ReferenceEdgeProperties repB      = null;
		Iterator heapRegionsItrB = hrnB.setIteratorToReferencedRegions();
		while( heapRegionsItrB.hasNext() ) {
		    Map.Entry meC               = (Map.Entry)               heapRegionsItrB.next();
		    hrnChildB                   = (HeapRegionNode)          meC.getKey();
		    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) meC.getValue();

		    if( hrnChildB.equals( idChildA ) ) {
			edgeFound = true;
			repB      = rep;
		    }
		}

		// if the edge from A was not found in B,
		// add it to B.
		if( !edgeFound ) {
		    assert id2hrn.containsKey( idChildA );
		    hrnChildB = id2hrn.get( idChildA );
		    repB = repA.copy();
		    addReferenceEdge( hrnB, hrnChildB, repB );
		}
		// otherwise, the edge already existed in both graphs
		// so merge their reachability sets
		else {
		    // just replace this beta set with the union
		    assert repB != null;
		    repB.setBeta( repB.getBeta().union( repA.getBeta() ) );
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

		HeapRegionNode          hrnChildB = null;
		ReferenceEdgeProperties repB      = null;
		Iterator heapRegionsItrB = lnB.setIteratorToReferencedRegions();
		while( heapRegionsItrB.hasNext() ) {
		    Map.Entry meC               = (Map.Entry)               heapRegionsItrB.next();
		    hrnChildB                   = (HeapRegionNode)          meC.getKey();
		    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) meC.getValue();

		    if( hrnChildB.equals( idChildA ) ) {
			edgeFound = true;
			repB      = rep;
		    }
		}

		// if the edge from A was not found in B,
		// add it to B.
		if( !edgeFound ) {
		    assert id2hrn.containsKey( idChildA );
		    hrnChildB = id2hrn.get( idChildA );
		    repB = repA.copy();
		    addReferenceEdge( lnB, hrnChildB, repB );
		}
		// otherwise, the edge already existed in both graphs
		// so merge the reachability sets
		else {
		    // just replace this beta set with the union
		    assert repB != null;
		    repB.setBeta( repB.getBeta().union( repA.getBeta() ) );
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

	if( !areHeapRegionToHeapRegionEdgesEqual( og ) ) {
	    return false;
	}

	if( !areLabelNodesEqual( og ) ) {
	    return false;
	}

	if( !areLabelToHeapRegionEdgesEqual( og ) ) {
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

	    //HeapRegionNode hrnB = og.id2hrn.get( idA );	    
	    HeapRegionNode hrnB = id2hrn.get( idA );	    
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


    protected boolean areId2paramIndexEqual( OwnershipGraph og ) {
	return id2paramIndex.size() == og.id2paramIndex.size();
    }



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

	/*
	HashSet<HeapRegionNode> hrnSet = new HashSet<HeapRegionNode>();

	Iterator i = idSet.iterator();
	while( i.hasNext() ) {
	    Integer idFromSet = (Integer) i.next();
	    assert id2hrn.contains( idFromSet );
	    hrnSet.add( id2hrn.get( idFromSet ) );
	}
	*/

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
   


    // for writing ownership graphs to dot files
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
		   writeReferencers
		    );
    }

    public void writeGraph( String graphName,
			    boolean writeLabels,
			    boolean writeReferencers 
			    ) throws java.io.IOException {

	// remove all non-word characters from the graph name so
	// the filename and identifier in dot don't cause errors
	graphName = graphName.replaceAll( "[\\W]", "" );

	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "digraph "+graphName+" {\n" );
	//bw.write( "  size=\"7.5,10\";\n" );


	// then visit every heap region node
	HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

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

	bw.write( "  graphTitle[label=\""+graphName+"\",shape=box];\n" );


	// then visit every label node, useful for debugging
	if( writeLabels ) {
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
		    
		    bw.write( "  "        + ln.toString() +
			      " -> "      + hrn.toString() +
			      "[label=\"" + rep.toEdgeLabelString() +
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


	HeapRegionNode hrnChild = null;
	Iterator childRegionsItr = hrn.setIteratorToReferencedRegions();
	while( childRegionsItr.hasNext() ) {
	    Map.Entry me                = (Map.Entry)               childRegionsItr.next();
	    hrnChild                    = (HeapRegionNode)          me.getKey();
	    ReferenceEdgeProperties rep = (ReferenceEdgeProperties) me.getValue();

	    switch( mode ) {
	    case VISIT_HRN_WRITE_FULL:
		bw.write( "  "        + hrn.toString() +
			  " -> "      + hrnChild.toString() +
			  "[label=\"" + rep.toEdgeLabelString() +
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
