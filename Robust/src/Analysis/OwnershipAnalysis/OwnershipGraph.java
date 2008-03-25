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


    public OwnershipGraph( int allocationDepth ) {
	this.allocationDepth = allocationDepth;

	id2hrn = new Hashtable<Integer,        HeapRegionNode>();
	td2ln  = new Hashtable<TempDescriptor, LabelNode     >();
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
	createNewHeapRegionNode( Integer id,
				 boolean isSingleObject,
				 boolean isFlagged,
				 boolean isNewSummary,
				 String  description ) {

	if( id == null ) {
	    id = OwnershipAnalysis.generateUniqueHeapRegionNodeID();
	}

	HeapRegionNode hrn = new HeapRegionNode( id,
						 isSingleObject,
						 isFlagged,
						 isNewSummary,
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

    public void assignTempToParameterAllocation( boolean isTask,
						 TempDescriptor td ) {
	assert td != null;

	LabelNode      lnParam = getLabelNodeFromTemp( td );
	HeapRegionNode hrn     = createNewHeapRegionNode( null, 
							  false,
							  isTask,
							  false,
							  "param" );

	addReferenceEdge( lnParam, hrn, new ReferenceEdgeProperties( false ) );
	addReferenceEdge( hrn,     hrn, new ReferenceEdgeProperties( false ) );
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
	Integer        id        = as.getIthOldest( 0 );
	HeapRegionNode hrnNewest = id2hrn.get( id );
	assert hrnNewest != null;

	LabelNode dst = getLabelNodeFromTemp( td );

	addReferenceEdge( dst, hrnNewest, new ReferenceEdgeProperties( false ) );
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
	    hrnSummary = createNewHeapRegionNode( idSummary,
						  false,
						  false,
						  true,
						  as + "\\nsummary" );
	}

	// first transfer the references out of alpha_k to alpha_s
	Integer        idK  = as.getOldest();
	HeapRegionNode hrnK = id2hrn.get( idK );
	
	// see comment above about needing to allocate a heap region
	// for the context of this ownership graph
	if( hrnK == null ) {
	    hrnK = createNewHeapRegionNode( idK,
					    true,
					    false,
					    false,
					    as + "\\noldest" );
	}

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
	    
	    addReferenceEdge( onReferencer, hrnSummary, rep.copy() );
	}

	
	// then move down the line of heap region nodes
	// clobbering the ith and transferring all references
	// to and from i-1 to node i.  Note that this clobbers
	// the oldest node (alpha_k) that was just merged into
	// the summary above and should move everything from
	// alpha_0 to alpha_1 before we finish
	for( int i = allocationDepth - 1; i > 0; --i ) {	    

	    // move references from the ith oldest to the i+1 oldest
	    Integer        idIth     = as.getIthOldest( i );
	    HeapRegionNode hrnI      = id2hrn.get( idIth );
	    Integer        idImin1th = as.getIthOldest( i - 1 );
	    HeapRegionNode hrnImin1  = id2hrn.get( idImin1th );

	    // see comment above about needing to allocate a heap region
	    // for the context of this ownership graph
	    if( hrnI == null ) {
		hrnI = createNewHeapRegionNode( idIth,
						true,
						false,
						false,
						as + "\\n" + Integer.toString( i ) + "th" );
	    }
	    if( hrnImin1 == null ) {
		hrnImin1 = createNewHeapRegionNode( idImin1th,
						    true,
						    false,
						    false,
						    as + "\\n" + Integer.toString( i-1 ) + "th" );
	    }

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
				   OwnershipGraph          ogCallee,
				   HashSet<AllocationSite> allocSiteSet ) {
	
	// first age all of the allocation sites from
	// the callee graph in this graph
	Iterator i = allocSiteSet.iterator();
	while( i.hasNext() ) {
	    this.age( (AllocationSite) i.next() );
	}

	// the heap regions represented by the arguments (caller graph)
	// and heap regions for the parameters (callee graph)
	// don't correspond to each other by heap region ID.  In fact,
	// an argument label node can be referencing several heap regions
	// so the parameter label always references a multiple-object
	// heap region in order to handle the range of possible contexts
	// for a method call.  This means we need to make a special mapping
	// of argument->parameter regions in order to update the caller graph,
	// then remember which heap regions were used so we can ignore them
	// in the more general algorithm below that includes all heap regions
	// of the callee graph
	HashSet<Integer> calleeParameterIDs = new HashSet<Integer>();

	if( isStatic ) {
	    assert fc.numArgs()     == fm.numParameters();
	} else {
	    assert fc.numArgs() + 1 == fm.numParameters();
	}

	for( int a = 0; a < fc.numArgs(); ++a ) {
	    
	}

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
		
		// now we know that in the callee method's ownership graph
		// there is a heap region->heap region reference edge given
		// by the ownership-graph independent ID's:
		// idCallee->idChildCallee
		// 


		// at this point we know an edge in graph A exists
		// idA -> idChildA, does this exist in B?
	    } 
	}	
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


    /*
    // use this method to determine if two temp descriptors can possibly
    // access the same heap regions, which means there is a possible alias
    public boolean havePossibleAlias( TempDescriptor td1,
				      TempDescriptor td2 ) {
	

	return false;
    }
    */


    // for writing ownership graphs to dot files
    public void writeGraph( Descriptor methodDesc,
			    FlatNode   fn ) throws java.io.IOException {
	writeGraph(
		   methodDesc.getSymbol() +
		   methodDesc.getNum() +
		   fn.toString()
		   );
    }

    public void writeGraph( Descriptor methodDesc ) throws java.io.IOException {
	writeGraph( 
		   methodDesc.getSymbol() +
		   methodDesc.getNum() +
		   "COMPLETE"
		    );
    }

    private void writeGraph( String graphName ) throws java.io.IOException {
	// remove all non-word characters from the graph name so
	// the filename and identifier in dot don't cause errors
	graphName = graphName.replaceAll( "[\\W]", "" );

	BufferedWriter bw = new BufferedWriter( new FileWriter( graphName+".dot" ) );
	bw.write( "digraph "+graphName+" {\n" );

	// then visit every heap region node
	HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

	Set      s = id2hrn.entrySet();
	Iterator i = s.iterator();
	while( i.hasNext() ) {
	    Map.Entry      me  = (Map.Entry)      i.next();
	    HeapRegionNode hrn = (HeapRegionNode) me.getValue();
	    if( !visited.contains( hrn ) ) {
		traverseHeapRegionNodes( VISIT_HRN_WRITE_FULL, hrn, bw, null, visited );
	    }
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
	    
	    String attributes = "[";
	    
	    if( hrn.isSingleObject() ) {
		attributes += "shape=box";
	    } else {
		attributes += "shape=Msquare";
	    }

	    if( hrn.isFlagged() ) {
		attributes += ",style=filled,fillcolor=lightgrey";
	    }

	    attributes += ",label=\""           +
		          hrn.getDescription()  +
		          "\"]";

	    bw.write( "  " + hrn.toString() + attributes + ";\n" );
	    break;
	}


	// go back and let a compile flag control whether the light
	// gray "referencer" edges are written to dot files.  It makes
	// the graph cluttered but can be useful for debugging.
	/*
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
	*/

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
