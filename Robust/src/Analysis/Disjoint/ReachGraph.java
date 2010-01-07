package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import Util.UtilAlgorithms;
import java.util.*;
import java.io.*;

public class ReachGraph {
		   
  protected static final TempDescriptor tdReturn    = new TempDescriptor( "_Return___" );
		   
  // some frequently used reachability constants
  protected static final ReachState rstateEmpty        = new ReachState().makeCanonical();
  protected static final ReachSet   rsetEmpty          = new ReachSet().makeCanonical();
  protected static final ReachSet   rsetWithEmptyState = new ReachSet( rstateEmpty ).makeCanonical();

  public Hashtable<Integer,        HeapRegionNode> id2hrn;
  public Hashtable<TempDescriptor, VariableNode  > td2vn;

  public HashSet<AllocSite> allocSites;

  // this is kept to allow edges created from variables (a src and dst)
  // to know the access paths that allowed it, to prune edges when
  // mapping them back into the caller--an access path must appear
  public Hashtable< TempDescriptor, Set<AccessPath> > temp2accessPaths;
  

  // use to disable improvements for comparison
  protected static final boolean DISABLE_STRONG_UPDATES = false;
  protected static final boolean DISABLE_GLOBAL_SWEEP   = true;

  protected static int      allocationDepth   = -1;
  protected static TypeUtil typeUtil          = null;
  protected static boolean  debugCallMap      = false;
  protected static int      debugCallMapCount = 0;
  protected static String   debugCallee       = null;
  protected static String   debugCaller       = null;


  public ReachGraph() {
    id2hrn = new Hashtable<Integer,        HeapRegionNode>();
    td2vn  = new Hashtable<TempDescriptor, VariableNode  >();

    allocSites = new HashSet<AllocSite>();

    temp2accessPaths = 
      new Hashtable< TempDescriptor, Set<AccessPath> >();
  }

  
  // temp descriptors are globally unique and maps to
  // exactly one variable node, easy
  protected VariableNode getVariableNodeFromTemp( TempDescriptor td ) {
    assert td != null;

    if( !td2vn.containsKey( td ) ) {
      td2vn.put( td, new VariableNode( td ) );
    }

    return td2vn.get( td );
  }

  public boolean hasVariable( TempDescriptor td ) {
    return td2vn.containsKey( td );
  }


  // the reason for this method is to have the option
  // of creating new heap regions with specific IDs, or
  // duplicating heap regions with specific IDs (especially
  // in the merge() operation) or to create new heap
  // regions with a new unique ID
  protected HeapRegionNode
    createNewHeapRegionNode( Integer id,
			     boolean isSingleObject,
			     boolean isNewSummary,
			     boolean isFlagged,
                             boolean isClean,
                             boolean isOutOfContext,
			     TypeDescriptor type,
			     AllocSite allocSite,
                             ReachSet inherent,
			     ReachSet alpha,
			     String description
                             ) {

    boolean markForAnalysis = isFlagged;

    TypeDescriptor typeToUse = null;
    if( allocSite != null ) {
      typeToUse = allocSite.getType();
      allocSites.add( allocSite );
    } else {
      typeToUse = type;
    }

    if( allocSite != null && allocSite.getDisjointAnalysisId() != null ) {
      markForAnalysis = true;
    }

    if( id == null ) {
      id = DisjointAnalysis.generateUniqueHeapRegionNodeID();
    }

    if( inherent == null ) {
      if( markForAnalysis ) {
	inherent = new ReachSet(
                                new ReachTuple( id,
                                                !isSingleObject,
                                                ReachTuple.ARITY_ONE
                                                ).makeCanonical()
                                ).makeCanonical();
      } else {
	inherent = rsetWithEmptyState;
      }
    }

    if( alpha == null ) {
      alpha = inherent;
    }
    
    HeapRegionNode hrn = new HeapRegionNode( id,
					     isSingleObject,
					     markForAnalysis,
					     isNewSummary,
                                             isClean,
                                             isOutOfContext,
					     typeToUse,
					     allocSite,
                                             inherent,
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
  //  creating references between reachability nodes
  //  and handling the details of maintaining both
  //  list of referencers and referencees.
  //
  ////////////////////////////////////////////////
  protected void addRefEdge( RefSrcNode     referencer,
                             HeapRegionNode referencee,
                             RefEdge        edge ) {
    assert referencer != null;
    assert referencee != null;
    assert edge       != null;
    assert edge.getSrc() == referencer;
    assert edge.getDst() == referencee;

    referencer.addReferencee( edge );
    referencee.addReferencer( edge );
  }

  protected void removeRefEdge( RefEdge e ) {
    removeRefEdge( e.getSrc(),
                   e.getDst(),
                   e.getType(),
                   e.getField() );
  }

  protected void removeRefEdge( RefSrcNode     referencer,
                                HeapRegionNode referencee,
                                TypeDescriptor type,
                                String         field ) {
    assert referencer != null;
    assert referencee != null;
    
    RefEdge edge = referencer.getReferenceTo( referencee,
                                              type,
                                              field );
    assert edge != null;
    assert edge == referencee.getReferenceFrom( referencer,
                                                type,
                                                field );
       
    referencer.removeReferencee( edge );
    referencee.removeReferencer( edge );
  }

  protected void clearRefEdgesFrom( RefSrcNode     referencer,
                                    TypeDescriptor type,
                                    String         field,
                                    boolean        removeAll ) {
    assert referencer != null;

    // get a copy of the set to iterate over, otherwise
    // we will be trying to take apart the set as we
    // are iterating over it, which won't work
    Iterator<RefEdge> i = referencer.iteratorToReferenceesClone();
    while( i.hasNext() ) {
      RefEdge edge = i.next();

      if( removeAll                                          || 
	  (edge.typeEquals( type ) && edge.fieldEquals( field ))
        ){

	HeapRegionNode referencee = edge.getDst();
	
	removeRefEdge( referencer,
                       referencee,
                       edge.getType(),
                       edge.getField() );
      }
    }
  }

  protected void clearRefEdgesTo( HeapRegionNode referencee,
                                  TypeDescriptor type,
                                  String         field,
                                  boolean        removeAll ) {
    assert referencee != null;

    // get a copy of the set to iterate over, otherwise
    // we will be trying to take apart the set as we
    // are iterating over it, which won't work
    Iterator<RefEdge> i = referencee.iteratorToReferencersClone();
    while( i.hasNext() ) {
      RefEdge edge = i.next();

      if( removeAll                                          || 
	  (edge.typeEquals( type ) && edge.fieldEquals( field ))
        ){

	RefSrcNode referencer = edge.getSrc();

	removeRefEdge( referencer,
                       referencee,
                       edge.getType(),
                       edge.getField() );
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
  ////////////////////////////////////////////////////

  public void nullifyDeadVars( Set<TempDescriptor> liveIn ) {
    // THIS IS BUGGGY

    /*
    // make a set of the temps that are out of scope, don't
    // consider them when nullifying dead in-scope variables
    Set<TempDescriptor> outOfScope = new HashSet<TempDescriptor>();
    outOfScope.add( tdReturn );
    outOfScope.add( tdAliasBlob );
    outOfScope.addAll( paramIndex2tdQ.values() );
    outOfScope.addAll( paramIndex2tdR.values() );    
    
    Iterator varItr = td2vn.entrySet().iterator();
    while( varItr.hasNext() ) {
      Map.Entry      me = (Map.Entry)      varItr.next();
      TempDescriptor td = (TempDescriptor) me.getKey();
      VariableNode      ln = (VariableNode)      me.getValue();

      // if this variable is not out-of-scope or live
      // in graph, nullify its references to anything
      if( !outOfScope.contains( td ) &&
	  !liveIn.contains( td ) 
	  ) {
	clearRefEdgesFrom( ln, null, null, true );
      }
    }
    */
  }


  public void assignTempXEqualToTempY( TempDescriptor x,
				       TempDescriptor y ) {
    assignTempXEqualToCastedTempY( x, y, null );
  }

  public void assignTempXEqualToCastedTempY( TempDescriptor x,
					     TempDescriptor y,
					     TypeDescriptor tdCast ) {

    VariableNode lnX = getVariableNodeFromTemp( x );
    VariableNode lnY = getVariableNodeFromTemp( y );
    
    clearRefEdgesFrom( lnX, null, null, true );

    // note it is possible that the types of temps in the
    // flat node to analyze will reveal that some typed
    // edges in the reachability graph are impossible
    Set<RefEdge> impossibleEdges = new HashSet<RefEdge>();

    Iterator<RefEdge> itrYhrn = lnY.iteratorToReferencees();
    while( itrYhrn.hasNext() ) {
      RefEdge        edgeY      = itrYhrn.next();
      HeapRegionNode referencee = edgeY.getDst();
      RefEdge        edgeNew    = edgeY.copy();

      if( !isSuperiorType( x.getType(), edgeY.getType() ) ) {
	impossibleEdges.add( edgeY );
	continue;
      }

      edgeNew.setSrc( lnX );
      
      if( tdCast == null ) {
        edgeNew.setType( mostSpecificType( y.getType(),                           
                                           edgeY.getType(), 
                                           referencee.getType() 
                                           ) 
                         );
      } else {
        edgeNew.setType( mostSpecificType( y.getType(),
                                           edgeY.getType(), 
                                           referencee.getType(),
                                           tdCast
                                           ) 
                         );
      }

      edgeNew.setField( null );

      addRefEdge( lnX, referencee, edgeNew );
    }

    Iterator<RefEdge> itrImp = impossibleEdges.iterator();
    while( itrImp.hasNext() ) {
      RefEdge edgeImp = itrImp.next();
      removeRefEdge( edgeImp );
    }
  }


  public void assignTempXEqualToTempYFieldF( TempDescriptor  x,
					     TempDescriptor  y,
					     FieldDescriptor f ) {
    VariableNode lnX = getVariableNodeFromTemp( x );
    VariableNode lnY = getVariableNodeFromTemp( y );

    clearRefEdgesFrom( lnX, null, null, true );

    // note it is possible that the types of temps in the
    // flat node to analyze will reveal that some typed
    // edges in the reachability graph are impossible
    Set<RefEdge> impossibleEdges = new HashSet<RefEdge>();

    Iterator<RefEdge> itrYhrn = lnY.iteratorToReferencees();
    while( itrYhrn.hasNext() ) {
      RefEdge        edgeY = itrYhrn.next();
      HeapRegionNode hrnY  = edgeY.getDst();
      ReachSet       betaY = edgeY.getBeta();

      Iterator<RefEdge> itrHrnFhrn = hrnY.iteratorToReferencees();
      while( itrHrnFhrn.hasNext() ) {
	RefEdge        edgeHrn = itrHrnFhrn.next();
	HeapRegionNode hrnHrn  = edgeHrn.getDst();
	ReachSet       betaHrn = edgeHrn.getBeta();

	// prune edges that are not a matching field
	if( edgeHrn.getType() != null &&	    	    
	    !edgeHrn.getField().equals( f.getSymbol() )	    
	    ) {
	  continue;
	}

	// check for impossible edges
	if( !isSuperiorType( x.getType(), edgeHrn.getType() ) ) {
	  impossibleEdges.add( edgeHrn );
	  continue;
	}

	TypeDescriptor tdNewEdge =
	  mostSpecificType( edgeHrn.getType(), 
			    hrnHrn.getType() 
			    );       
	  
	RefEdge edgeNew = new RefEdge( lnX,
                                       hrnHrn,
                                       tdNewEdge,
                                       null,
                                       false,
                                       betaY.intersection( betaHrn )
                                       );
	
	addRefEdge( lnX, hrnHrn, edgeNew );	
      }
    }

    Iterator<RefEdge> itrImp = impossibleEdges.iterator();
    while( itrImp.hasNext() ) {
      RefEdge edgeImp = itrImp.next();
      removeRefEdge( edgeImp );
    }

    // anytime you might remove edges between heap regions
    // you must global sweep to clean up broken reachability
    if( !impossibleEdges.isEmpty() ) {
      if( !DISABLE_GLOBAL_SWEEP ) {
	globalSweep();
      }
    }
  }


  public void assignTempXFieldFEqualToTempY( TempDescriptor  x,
					     FieldDescriptor f,
					     TempDescriptor  y ) {

    VariableNode lnX = getVariableNodeFromTemp( x );
    VariableNode lnY = getVariableNodeFromTemp( y );

    HashSet<HeapRegionNode> nodesWithNewAlpha = new HashSet<HeapRegionNode>();
    HashSet<RefEdge>        edgesWithNewBeta  = new HashSet<RefEdge>();

    // note it is possible that the types of temps in the
    // flat node to analyze will reveal that some typed
    // edges in the reachability graph are impossible
    Set<RefEdge> impossibleEdges = new HashSet<RefEdge>();

    // first look for possible strong updates and remove those edges
    boolean strongUpdate = false;

    Iterator<RefEdge> itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      RefEdge        edgeX = itrXhrn.next();
      HeapRegionNode hrnX  = edgeX.getDst();

      // we can do a strong update here if one of two cases holds	
      if( f != null &&
	  f != DisjointAnalysis.getArrayField( f.getType() ) &&	    
	  (   (hrnX.getNumReferencers()                         == 1) || // case 1
	      (hrnX.isSingleObject() && lnX.getNumReferencees() == 1)    // case 2
	      )
	  ) {
        if( !DISABLE_STRONG_UPDATES ) {
          strongUpdate = true;
          clearRefEdgesFrom( hrnX, f.getType(), f.getSymbol(), false );
        }
      }
    }
    
    // then do all token propagation
    itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      RefEdge        edgeX = itrXhrn.next();
      HeapRegionNode hrnX  = edgeX.getDst();
      ReachSet       betaX = edgeX.getBeta();
      ReachSet       R     = hrnX.getAlpha().intersection( edgeX.getBeta() );

      Iterator<RefEdge> itrYhrn = lnY.iteratorToReferencees();
      while( itrYhrn.hasNext() ) {
	RefEdge        edgeY = itrYhrn.next();
	HeapRegionNode hrnY  = edgeY.getDst();
	ReachSet       O     = edgeY.getBeta();

	// check for impossible edges
	if( !isSuperiorType( f.getType(), edgeY.getType() ) ) {
	  impossibleEdges.add( edgeY );
	  continue;
	}

	// propagate tokens over nodes starting from hrnSrc, and it will
	// take care of propagating back up edges from any touched nodes
	ChangeSet Cy = O.unionUpArityToChangeSet( R );
	propagateTokensOverNodes( hrnY, Cy, nodesWithNewAlpha, edgesWithNewBeta );

	// then propagate back just up the edges from hrn
	ChangeSet Cx = R.unionUpArityToChangeSet(O);
  	HashSet<RefEdge> todoEdges = new HashSet<RefEdge>();

	Hashtable<RefEdge, ChangeSet> edgePlannedChanges =
	  new Hashtable<RefEdge, ChangeSet>();

	Iterator<RefEdge> referItr = hrnX.iteratorToReferencers();
	while( referItr.hasNext() ) {
	  RefEdge edgeUpstream = referItr.next();
	  todoEdges.add( edgeUpstream );
	  edgePlannedChanges.put( edgeUpstream, Cx );
	}

	propagateTokensOverEdges( todoEdges,
				  edgePlannedChanges,
				  edgesWithNewBeta );
      }
    }


    // apply the updates to reachability
    Iterator<HeapRegionNode> nodeItr = nodesWithNewAlpha.iterator();
    while( nodeItr.hasNext() ) {
      nodeItr.next().applyAlphaNew();
    }

    Iterator<RefEdge> edgeItr = edgesWithNewBeta.iterator();
    while( edgeItr.hasNext() ) {
      edgeItr.next().applyBetaNew();
    }


    // then go back through and add the new edges
    itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      RefEdge        edgeX = itrXhrn.next();
      HeapRegionNode hrnX  = edgeX.getDst();
      
      Iterator<RefEdge> itrYhrn = lnY.iteratorToReferencees();
      while( itrYhrn.hasNext() ) {
	RefEdge        edgeY = itrYhrn.next();
	HeapRegionNode hrnY  = edgeY.getDst();

	// skip impossible edges here, we already marked them
	// when computing reachability propagations above
	if( !isSuperiorType( f.getType(), edgeY.getType() ) ) {
	  continue;
	}
	
	// prepare the new reference edge hrnX.f -> hrnY
	TypeDescriptor tdNewEdge = 	
	  mostSpecificType( y.getType(),
			    edgeY.getType(), 
			    hrnY.getType()
			    );	

	RefEdge edgeNew = new RefEdge( hrnX,
                                       hrnY,
                                       tdNewEdge,
                                       f.getSymbol(),
                                       false,
                                       edgeY.getBeta().pruneBy( hrnX.getAlpha() )
                                       );

	// look to see if an edge with same field exists
	// and merge with it, otherwise just add the edge
	RefEdge edgeExisting = hrnX.getReferenceTo( hrnY, 
                                                    tdNewEdge,
                                                    f.getSymbol() );
	
	if( edgeExisting != null ) {
	  edgeExisting.setBeta(
			       edgeExisting.getBeta().union( edgeNew.getBeta() )
                               );
	  // we touched this edge in the current context
          // so dirty it
	  edgeExisting.setIsClean( false );
	
        } else {			  
	  addRefEdge( hrnX, hrnY, edgeNew );
	}
      }
    }

    Iterator<RefEdge> itrImp = impossibleEdges.iterator();
    while( itrImp.hasNext() ) {
      RefEdge edgeImp = itrImp.next();
      removeRefEdge( edgeImp );
    }

    // if there was a strong update, make sure to improve
    // reachability with a global sweep
    if( strongUpdate || !impossibleEdges.isEmpty() ) {    
      if( !DISABLE_GLOBAL_SWEEP ) {
        globalSweep();
      }
    }
  }


  public void assignReturnEqualToTemp( TempDescriptor x ) {

    VariableNode lnR = getVariableNodeFromTemp( tdReturn );
    VariableNode lnX = getVariableNodeFromTemp( x );

    clearRefEdgesFrom( lnR, null, null, true );

    Iterator<RefEdge> itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      RefEdge        edgeX      = itrXhrn.next();
      HeapRegionNode referencee = edgeX.getDst();
      RefEdge        edgeNew    = edgeX.copy();
      edgeNew.setSrc( lnR );

      addRefEdge( lnR, referencee, edgeNew );
    }
  }


  public void assignTempEqualToNewAlloc( TempDescriptor x,
                                         AllocSite      as ) {
    assert x  != null;
    assert as != null;

    age( as );

    // after the age operation the newest (or zero-ith oldest)
    // node associated with the allocation site should have
    // no references to it as if it were a newly allocated
    // heap region
    Integer        idNewest   = as.getIthOldest( 0 );
    HeapRegionNode hrnNewest  = id2hrn.get( idNewest );
    assert         hrnNewest != null;

    VariableNode lnX = getVariableNodeFromTemp( x );
    clearRefEdgesFrom( lnX, null, null, true );

    // make a new reference to allocated node
    TypeDescriptor type = as.getType();

    RefEdge edgeNew =
      new RefEdge( lnX,                  // source
                   hrnNewest,            // dest
                   type,                 // type
                   null,                 // field name
                   false,                // is initial param
                   hrnNewest.getAlpha()  // beta
                   );

    addRefEdge( lnX, hrnNewest, edgeNew );
  }


  // use the allocation site (unique to entire analysis) to
  // locate the heap region nodes in this reachability graph
  // that should be aged.  The process models the allocation
  // of new objects and collects all the oldest allocations
  // in a summary node to allow for a finite analysis
  //
  // There is an additional property of this method.  After
  // running it on a particular reachability graph (many graphs
  // may have heap regions related to the same allocation site)
  // the heap region node objects in this reachability graph will be
  // allocated.  Therefore, after aging a graph for an allocation
  // site, attempts to retrieve the heap region nodes using the
  // integer id's contained in the allocation site should always
  // return non-null heap regions.
  public void age( AllocSite as ) {

    // aging adds this allocation site to the graph's
    // list of sites that exist in the graph, or does
    // nothing if the site is already in the list
    allocSites.add( as );

    // get the summary node for the allocation site in the context
    // of this particular reachability graph
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
    clearRefEdgesFrom( hrn0, null, null, true );
    clearRefEdgesTo  ( hrn0, null, null, true );


    // now tokens in reachability sets need to "age" also
    Iterator itrAllVariableNodes = td2vn.entrySet().iterator();
    while( itrAllVariableNodes.hasNext() ) {
      Map.Entry    me = (Map.Entry)    itrAllVariableNodes.next();
      VariableNode ln = (VariableNode) me.getValue();

      Iterator<RefEdge> itrEdges = ln.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTokens(as, itrEdges.next() );
      }
    }

    Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
    while( itrAllHRNodes.hasNext() ) {
      Map.Entry      me       = (Map.Entry)      itrAllHRNodes.next();
      HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

      ageTokens( as, hrnToAge );

      Iterator<RefEdge> itrEdges = hrnToAge.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTokens( as, itrEdges.next() );
      }
    }


    // after tokens have been aged, reset newest node's reachability
    if( hrn0.isFlagged() ) {
      hrn0.setAlpha( new ReachSet(
                       new ReachState(
                         new ReachTuple( hrn0 ).makeCanonical()
                       ).makeCanonical()
                     ).makeCanonical()
                   );
    } else {
      hrn0.setAlpha( new ReachSet(
                       new ReachState().makeCanonical()
                     ).makeCanonical()
                   );
    }
  }


  protected HeapRegionNode getSummaryNode( AllocSite as ) {

    Integer        idSummary  = as.getSummary();
    HeapRegionNode hrnSummary = id2hrn.get( idSummary );

    // If this is null then we haven't touched this allocation site
    // in the context of the current reachability graph, so allocate
    // heap region nodes appropriate for the entire allocation site.
    // This should only happen once per reachability graph per allocation site,
    // and a particular integer id can be used to locate the heap region
    // in different reachability graphs that represents the same part of an
    // allocation site.
    if( hrnSummary == null ) {

      boolean hasFlags = false;
      if( as.getType().isClass() ) {
	hasFlags = as.getType().getClassDesc().hasFlags();
      }
      
      if( as.getFlag() ){
        hasFlags = as.getFlag();
      }

      String strDesc = as.toStringForDOT()+"\\nsummary";
      hrnSummary = 
        createNewHeapRegionNode( idSummary,    // id or null to generate a new one 
                                 false,        // single object?		 
                                 true,         // summary?	 
                                 hasFlags,     // flagged?
                                 false,        // clean?
                                 false,        // out-of-context?
                                 as.getType(), // type				 
                                 as,           // allocation site			 
                                 null,         // inherent reach
                                 null,         // current reach                 
                                 strDesc       // description
                                 );
                                 
      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
	Integer idIth = as.getIthOldest( i );
	assert !id2hrn.containsKey( idIth );
        strDesc = as.toStringForDOT()+"\\n"+i+" oldest";
	createNewHeapRegionNode( idIth,        // id or null to generate a new one 
                                 true,	       // single object?			 
                                 false,	       // summary?			 
                                 hasFlags,     // flagged?			 
                                 false,        // clean?
                                 false,        // out-of-context?
                                 as.getType(), // type				 
                                 as,	       // allocation site			 
                                 null,         // inherent reach
                                 null,	       // current reach
                                 strDesc       // description
                                 );
      }
    }

    return hrnSummary;
  }


  protected HeapRegionNode getShadowSummaryNode( AllocSite as ) {

    Integer        idShadowSummary  = as.getSummaryShadow();
    HeapRegionNode hrnShadowSummary = id2hrn.get( idShadowSummary );

    if( hrnShadowSummary == null ) {

      boolean hasFlags = false;
      if( as.getType().isClass() ) {
	hasFlags = as.getType().getClassDesc().hasFlags();
      }

      if( as.getFlag() ){
        hasFlags = as.getFlag();
      }

      String strDesc = as+"\\n"+as.getType()+"\\nshadowSum";
      hrnShadowSummary = 
        createNewHeapRegionNode( idShadowSummary, // id or null to generate a new one 
                                 false,           // single object?			 
                                 true,		  // summary?			 
                                 hasFlags,        // flagged?	                            
                                 false,           // clean?
                                 false,           // out-of-context?
                                 as.getType(),    // type				 
                                 as,		  // allocation site			 
                                 null,            // inherent reach
                                 null,		  // current reach
                                 strDesc          // description
                                 );

      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
	Integer idShadowIth = as.getIthOldestShadow( i );
	assert !id2hrn.containsKey( idShadowIth );
        strDesc = as+"\\n"+as.getType()+"\\n"+i+" shadow";
	createNewHeapRegionNode( idShadowIth,  // id or null to generate a new one 
                                 true,	       // single object?			 
                                 false,	       // summary?			 
                                 hasFlags,     // flagged?	
                                 false,        // clean?
                                 false,        // out-of-context?
                                 as.getType(), // type				 
                                 as,	       // allocation site			 
                                 null,         // inherent reach
                                 null,	       // current reach                 
                                 strDesc       // description
                                 );
      }
    }

    return hrnShadowSummary;
  }


  protected void mergeIntoSummary(HeapRegionNode hrn, HeapRegionNode hrnSummary) {
    assert hrnSummary.isNewSummary();

    // transfer references _from_ hrn over to hrnSummary
    Iterator<RefEdge> itrReferencee = hrn.iteratorToReferencees();
    while( itrReferencee.hasNext() ) {
      RefEdge edge       = itrReferencee.next();
      RefEdge edgeMerged = edge.copy();
      edgeMerged.setSrc(hrnSummary);

      HeapRegionNode hrnReferencee = edge.getDst();
      RefEdge edgeSummary   = hrnSummary.getReferenceTo(hrnReferencee, 
							      edge.getType(),
							      edge.getField() );

      if( edgeSummary == null ) {
	// the merge is trivial, nothing to be done
      } else {
	// otherwise an edge from the referencer to hrnSummary exists already
	// and the edge referencer->hrn should be merged with it
	edgeMerged.setBeta(edgeMerged.getBeta().union(edgeSummary.getBeta() ) );
      }

      addRefEdge(hrnSummary, hrnReferencee, edgeMerged);
    }

    // next transfer references _to_ hrn over to hrnSummary
    Iterator<RefEdge> itrReferencer = hrn.iteratorToReferencers();
    while( itrReferencer.hasNext() ) {
      RefEdge edge         = itrReferencer.next();
      RefEdge edgeMerged   = edge.copy();
      edgeMerged.setDst(hrnSummary);

      RefSrcNode onReferencer = edge.getSrc();
      RefEdge edgeSummary  = onReferencer.getReferenceTo(hrnSummary, 
							       edge.getType(),
							       edge.getField() );

      if( edgeSummary == null ) {
	// the merge is trivial, nothing to be done
      } else {
	// otherwise an edge from the referencer to alpha_S exists already
	// and the edge referencer->alpha_K should be merged with it
	edgeMerged.setBeta(edgeMerged.getBeta().union(edgeSummary.getBeta() ) );
      }

      addRefEdge(onReferencer, hrnSummary, edgeMerged);
    }

    // then merge hrn reachability into hrnSummary
    hrnSummary.setAlpha(hrnSummary.getAlpha().union(hrn.getAlpha() ) );
  }


  protected void transferOnto(HeapRegionNode hrnA, HeapRegionNode hrnB) {

    // clear references in and out of node b
    clearRefEdgesFrom(hrnB, null, null, true);
    clearRefEdgesTo(hrnB, null, null, true);

    // copy each edge in and out of A to B
    Iterator<RefEdge> itrReferencee = hrnA.iteratorToReferencees();
    while( itrReferencee.hasNext() ) {
      RefEdge edge          = itrReferencee.next();
      HeapRegionNode hrnReferencee = edge.getDst();
      RefEdge edgeNew       = edge.copy();
      edgeNew.setSrc(hrnB);

      addRefEdge(hrnB, hrnReferencee, edgeNew);
    }

    Iterator<RefEdge> itrReferencer = hrnA.iteratorToReferencers();
    while( itrReferencer.hasNext() ) {
      RefEdge edge         = itrReferencer.next();
      RefSrcNode onReferencer = edge.getSrc();
      RefEdge edgeNew      = edge.copy();
      edgeNew.setDst(hrnB);

      addRefEdge(onReferencer, hrnB, edgeNew);
    }

    // replace hrnB reachability with hrnA's
    hrnB.setAlpha(hrnA.getAlpha() );
  }


  protected void ageTokens(AllocSite as, RefEdge edge) {
    edge.setBeta(edge.getBeta().ageTokens(as) );
  }

  protected void ageTokens(AllocSite as, HeapRegionNode hrn) {
    hrn.setAlpha(hrn.getAlpha().ageTokens(as) );
  }



  protected void propagateTokensOverNodes(HeapRegionNode nPrime,
                                          ChangeSet c0,
                                          HashSet<HeapRegionNode> nodesWithNewAlpha,
                                          HashSet<RefEdge>  edgesWithNewBeta) {

    HashSet<HeapRegionNode> todoNodes
      = new HashSet<HeapRegionNode>();
    todoNodes.add(nPrime);

    HashSet<RefEdge> todoEdges
      = new HashSet<RefEdge>();

    Hashtable<HeapRegionNode, ChangeSet> nodePlannedChanges
      = new Hashtable<HeapRegionNode, ChangeSet>();
    nodePlannedChanges.put(nPrime, c0);

    Hashtable<RefEdge, ChangeSet> edgePlannedChanges
      = new Hashtable<RefEdge, ChangeSet>();

    // first propagate change sets everywhere they can go
    while( !todoNodes.isEmpty() ) {
      HeapRegionNode n = todoNodes.iterator().next();
      ChangeSet C = nodePlannedChanges.get(n);

      Iterator<RefEdge> referItr = n.iteratorToReferencers();
      while( referItr.hasNext() ) {
	RefEdge edge = referItr.next();
	todoEdges.add(edge);

	if( !edgePlannedChanges.containsKey(edge) ) {
	  edgePlannedChanges.put(edge, new ChangeSet().makeCanonical() );
	}

	edgePlannedChanges.put(edge, edgePlannedChanges.get(edge).union(C) );
      }

      Iterator<RefEdge> refeeItr = n.iteratorToReferencees();
      while( refeeItr.hasNext() ) {
	RefEdge edgeF = refeeItr.next();
	HeapRegionNode m     = edgeF.getDst();

	ChangeSet changesToPass = new ChangeSet().makeCanonical();

	Iterator<ChangeTuple> itrCprime = C.iterator();
	while( itrCprime.hasNext() ) {
	  ChangeTuple c = itrCprime.next();
	  if( edgeF.getBeta().contains( c.getSetToMatch() ) ) {
	    changesToPass = changesToPass.union(c);
	  }
	}

	if( !changesToPass.isEmpty() ) {
	  if( !nodePlannedChanges.containsKey(m) ) {
	    nodePlannedChanges.put(m, new ChangeSet().makeCanonical() );
	  }

	  ChangeSet currentChanges = nodePlannedChanges.get(m);

	  if( !changesToPass.isSubset(currentChanges) ) {

	    nodePlannedChanges.put(m, currentChanges.union(changesToPass) );
	    todoNodes.add(m);
	  }
	}
      }

      todoNodes.remove(n);
    }

    // then apply all of the changes for each node at once
    Iterator itrMap = nodePlannedChanges.entrySet().iterator();
    while( itrMap.hasNext() ) {
      Map.Entry      me = (Map.Entry)      itrMap.next();
      HeapRegionNode n  = (HeapRegionNode) me.getKey();
      ChangeSet C  = (ChangeSet) me.getValue();

      // this propagation step is with respect to one change,
      // so we capture the full change from the old alpha:
      ReachSet localDelta = n.getAlpha().applyChangeSet( C, true );

      // but this propagation may be only one of many concurrent
      // possible changes, so keep a running union with the node's
      // partially updated new alpha set
      n.setAlphaNew( n.getAlphaNew().union( localDelta ) );

      nodesWithNewAlpha.add( n );
    }

    propagateTokensOverEdges(todoEdges, edgePlannedChanges, edgesWithNewBeta);
  }


  protected void propagateTokensOverEdges(
    HashSet<RefEdge>                   todoEdges,
    Hashtable<RefEdge, ChangeSet> edgePlannedChanges,
    HashSet<RefEdge>                   edgesWithNewBeta) {

    // first propagate all change tuples everywhere they can go
    while( !todoEdges.isEmpty() ) {
      RefEdge edgeE = todoEdges.iterator().next();
      todoEdges.remove(edgeE);

      if( !edgePlannedChanges.containsKey(edgeE) ) {
	edgePlannedChanges.put(edgeE, new ChangeSet().makeCanonical() );
      }

      ChangeSet C = edgePlannedChanges.get(edgeE);

      ChangeSet changesToPass = new ChangeSet().makeCanonical();

      Iterator<ChangeTuple> itrC = C.iterator();
      while( itrC.hasNext() ) {
	ChangeTuple c = itrC.next();
	if( edgeE.getBeta().contains( c.getSetToMatch() ) ) {
	  changesToPass = changesToPass.union(c);
	}
      }

      RefSrcNode onSrc = edgeE.getSrc();

      if( !changesToPass.isEmpty() && onSrc instanceof HeapRegionNode ) {
	HeapRegionNode n = (HeapRegionNode) onSrc;

	Iterator<RefEdge> referItr = n.iteratorToReferencers();
	while( referItr.hasNext() ) {
	  RefEdge edgeF = referItr.next();

	  if( !edgePlannedChanges.containsKey(edgeF) ) {
	    edgePlannedChanges.put(edgeF, new ChangeSet().makeCanonical() );
	  }

	  ChangeSet currentChanges = edgePlannedChanges.get(edgeF);

	  if( !changesToPass.isSubset(currentChanges) ) {
	    todoEdges.add(edgeF);
	    edgePlannedChanges.put(edgeF, currentChanges.union(changesToPass) );
	  }
	}
      }
    }

    // then apply all of the changes for each edge at once
    Iterator itrMap = edgePlannedChanges.entrySet().iterator();
    while( itrMap.hasNext() ) {
      Map.Entry      me = (Map.Entry)      itrMap.next();
      RefEdge  e  = (RefEdge)  me.getKey();
      ChangeSet C  = (ChangeSet) me.getValue();

      // this propagation step is with respect to one change,
      // so we capture the full change from the old beta:
      ReachSet localDelta = e.getBeta().applyChangeSet( C, true );

      // but this propagation may be only one of many concurrent
      // possible changes, so keep a running union with the edge's
      // partially updated new beta set
      e.setBetaNew( e.getBetaNew().union( localDelta  ) );
      
      edgesWithNewBeta.add( e );
    }
  }



  // use this method to make a new reach graph that is
  // what heap the FlatMethod callee from the FlatCall 
  // would start with reaching from its arguments in
  // this reach graph
  public ReachGraph makeCalleeView( FlatCall   fc,
                                    FlatMethod fm ) {

    // the callee view is a new graph: DON'T MODIFY
    // *THIS* graph
    ReachGraph rg = new ReachGraph();

    // track what parts of this graph have already been
    // added to callee view, variables not needed.
    // Note that we need this because when we traverse
    // this caller graph for each parameter we may find
    // nodes and edges more than once (which the per-param
    // "visit" sets won't show) and we only want to create
    // an element in the new callee view one time
    Set callerNodesCopiedToCallee = new HashSet<HeapRegionNode>();
    Set callerEdgesCopiedToCallee = new HashSet<RefEdge>();


    // a conservative starting point is to take the 
    // mechanically-reachable-from-arguments graph
    // as opposed to using reachability information
    // to prune the graph further
    for( int i = 0; i < fm.numParameters(); ++i ) {

      // for each parameter index, get the symbol in the
      // caller view and callee view
      
      // argument defined here is the symbol in the caller
      TempDescriptor tdArg = fc.getArgMatchingParamIndex( fm, i );

      // parameter defined here is the symbol in the callee
      TempDescriptor tdParam = fm.getParameter( i );

      // use these two VariableNode objects to translate
      // between caller and callee--its easy to compare
      // a HeapRegionNode across callee and caller because
      // they will have the same heap region ID
      VariableNode vnCaller = this.getVariableNodeFromTemp( tdArg );
      VariableNode vnCallee = rg.getVariableNodeFromTemp( tdParam );
      
      // now traverse the caller view using the argument to
      // build the callee view which has the parameter symbol
      Set<RefSrcNode> toVisitInCaller = new HashSet<RefSrcNode>();
      Set<RefSrcNode> visitedInCaller = new HashSet<RefSrcNode>();
      toVisitInCaller.add( vnCaller );

      while( !toVisitInCaller.isEmpty() ) {
        RefSrcNode rsnCaller = toVisitInCaller.iterator().next();
        RefSrcNode rsnCallee;

        toVisitInCaller.remove( rsnCaller );
        visitedInCaller.add( rsnCaller );
        
        // FIRST - setup the source end of an edge

        if( rsnCaller == vnCaller ) {
          // if the caller node is the param symbol, we
          // have to do this translation for the callee
          rsnCallee = vnCallee;
        } else {
          // otherwise the callee-view node is a heap
          // region with the same ID, that may or may
          // not have been created already
          assert rsnCaller instanceof HeapRegionNode;          

          HeapRegionNode hrnSrcCaller = (HeapRegionNode) rsnCaller;
          if( !callerNodesCopiedToCallee.contains( rsnCaller ) ) {
            rsnCallee = 
              rg.createNewHeapRegionNode( hrnSrcCaller.getID(),
                                          hrnSrcCaller.isSingleObject(),
                                          hrnSrcCaller.isNewSummary(),
                                          hrnSrcCaller.isFlagged(),
                                          true,  // clean?
                                          false, // out-of-context?
                                          hrnSrcCaller.getType(),
                                          hrnSrcCaller.getAllocSite(),
                                          toShadowTokens( this, hrnSrcCaller.getInherent() ),
                                          toShadowTokens( this, hrnSrcCaller.getAlpha() ),
                                          hrnSrcCaller.getDescription()
                                          );
            callerNodesCopiedToCallee.add( rsnCaller );
          } else {
            rsnCallee = rg.id2hrn.get( hrnSrcCaller.getID() );
          }
        }

        // SECOND - go over all edges from that source

        Iterator<RefEdge> itrRefEdges = rsnCaller.iteratorToReferencees();
        while( itrRefEdges.hasNext() ) {
          RefEdge        reCaller  = itrRefEdges.next();
          HeapRegionNode hrnCaller = reCaller.getDst();
          HeapRegionNode hrnCallee;

          // THIRD - setup destination ends of edges

          if( !callerNodesCopiedToCallee.contains( hrnCaller ) ) {
            hrnCallee = 
              rg.createNewHeapRegionNode( hrnCaller.getID(),
                                          hrnCaller.isSingleObject(),
                                          hrnCaller.isNewSummary(),
                                          hrnCaller.isFlagged(),
                                          true,  // clean?
                                          false, // out-of-context?
                                          hrnCaller.getType(),
                                          hrnCaller.getAllocSite(),
                                          toShadowTokens( this, hrnCaller.getInherent() ),
                                          toShadowTokens( this, hrnCaller.getAlpha() ),
                                          hrnCaller.getDescription()
                                          );
            callerNodesCopiedToCallee.add( hrnCaller );
          } else {
            hrnCallee = rg.id2hrn.get( hrnCaller.getID() );
          }

          // FOURTH - copy edge over if needed
          if( !callerEdgesCopiedToCallee.contains( reCaller ) ) {
            rg.addRefEdge( rsnCallee,
                           hrnCallee,
                           new RefEdge( rsnCallee,
                                        hrnCallee,
                                        reCaller.getType(),
                                        reCaller.getField(),
                                        true, // clean?
                                        toShadowTokens( this, reCaller.getBeta() )
                                        )
                           );              
            callerEdgesCopiedToCallee.add( reCaller );
          }
          
          // keep traversing nodes reachable from param i
          // that we haven't visited yet
          if( !visitedInCaller.contains( hrnCaller ) ) {
            toVisitInCaller.add( hrnCaller );
          }
          
        } // end edge iteration        
      } // end visiting heap nodes in caller
    } // end iterating over parameters as starting points


    // find the set of edges in this graph with source
    // out-of-context (not in nodes copied) and have a
    // destination in context (one of nodes copied) as
    // a starting point for building out-of-context nodes
    Iterator<HeapRegionNode> itrInContext =
      callerNodesCopiedToCallee.iterator();
    while( itrInContext.hasNext() ) {
      HeapRegionNode hrnCallerAndInContext = itrInContext.next();
      
      Iterator<RefEdge> itrMightCross =
        hrnCallerAndInContext.iteratorToReferencers();
      while( itrMightCross.hasNext() ) {
        RefEdge edgeMightCross = itrMightCross.next();

        // we're only interested in edges with a source
        // 1) out-of-context and 2) is a heap region
        if( callerNodesCopiedToCallee.contains( edgeMightCross.getSrc() ) ||
            !(edgeMightCross.getSrc() instanceof HeapRegionNode)
            ) { 
          // then just skip
          continue;
        }

        HeapRegionNode hrnCallerAndOutContext = 
          (HeapRegionNode)edgeMightCross.getSrc();

        // we found a reference that crosses from out-of-context
        // to in-context, so build a special out-of-context node
        // for the callee IHM and its reference edge
        HeapRegionNode hrnCalleeAndOutContext =
          rg.createNewHeapRegionNode( null,  // ID
                                      false, // single object?
                                      false, // new summary?
                                      false, // flagged?
                                      true,  // clean?
                                      true,  // out-of-context?
                                      hrnCallerAndOutContext.getType(),
                                      null,  // alloc site, shouldn't be used
                                      toShadowTokens( this, hrnCallerAndOutContext.getAlpha() ), // inherent
                                      toShadowTokens( this, hrnCallerAndOutContext.getAlpha() ), // alpha
                                      "out-of-context"
                                      );
       
        HeapRegionNode hrnCalleeAndInContext = 
          rg.id2hrn.get( hrnCallerAndInContext.getID() );

        rg.addRefEdge( hrnCalleeAndOutContext,
                       hrnCalleeAndInContext,
                       new RefEdge( hrnCalleeAndOutContext,
                                    hrnCalleeAndInContext,
                                    edgeMightCross.getType(),
                                    edgeMightCross.getField(),
                                    true, // clean?
                                    toShadowTokens( this, edgeMightCross.getBeta() )
                                    )
                       );                              
      }
    }    

    /*
    try {
      rg.writeGraph( "calleeview", true, true, true, false, true, true );
    } catch( IOException e ) {}

    if( fc.getMethod().getSymbol().equals( "f1" ) ) {
      System.exit( 0 );
    }
    */

    return rg;
  }  

  public void resolveMethodCall( FlatCall   fc,        
                                 FlatMethod fm,        
                                 ReachGraph rgCallee
                                 ) {
    
  } 

  
  protected void unshadowTokens( AllocSite as, 
                                 RefEdge   edge 
                                 ) {
    edge.setBeta( edge.getBeta().unshadowTokens( as ) );
  }

  protected void unshadowTokens( AllocSite      as, 
                                 HeapRegionNode hrn 
                                 ) {
    hrn.setAlpha( hrn.getAlpha().unshadowTokens( as ) );
  }


  private ReachSet toShadowTokens( ReachGraph rg,
                                   ReachSet   rsIn 
                                   ) {
    ReachSet rsOut = new ReachSet( rsIn ).makeCanonical();

    Iterator<AllocSite> allocItr = rg.allocSites.iterator();
    while( allocItr.hasNext() ) {
      AllocSite as = allocItr.next();
      rsOut = rsOut.toShadowTokens( as );
    }

    return rsOut.makeCanonical();
  }



  ////////////////////////////////////////////////////
  //
  //  This global sweep is an optional step to prune
  //  reachability sets that are not internally
  //  consistent with the global graph.  It should be
  //  invoked after strong updates or method calls.
  //
  ////////////////////////////////////////////////////
  public void globalSweep() {

    // boldB is part of the phase 1 sweep
    Hashtable< Integer, Hashtable<RefEdge, ReachSet> > boldB =
      new Hashtable< Integer, Hashtable<RefEdge, ReachSet> >();    

    // visit every heap region to initialize alphaNew and calculate boldB
    Set hrns = id2hrn.entrySet();
    Iterator itrHrns = hrns.iterator();
    while( itrHrns.hasNext() ) {
      Map.Entry me = (Map.Entry)itrHrns.next();
      Integer token = (Integer) me.getKey();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();
    
      // assert that this node and incoming edges have clean alphaNew
      // and betaNew sets, respectively
      assert rstateEmpty.equals( hrn.getAlphaNew() );

      Iterator<RefEdge> itrRers = hrn.iteratorToReferencers();
      while( itrRers.hasNext() ) {
	RefEdge edge = itrRers.next();
	assert rstateEmpty.equals( edge.getBetaNew() );
      }      

      // calculate boldB for this flagged node
      if( hrn.isFlagged() ) {
	
	Hashtable<RefEdge, ReachSet> boldB_f =
	  new Hashtable<RefEdge, ReachSet>();
	
	Set<RefEdge> workSetEdges = new HashSet<RefEdge>();

	// initial boldB_f constraints
	Iterator<RefEdge> itrRees = hrn.iteratorToReferencees();
	while( itrRees.hasNext() ) {
	  RefEdge edge = itrRees.next();

	  assert !boldB.containsKey( edge );
	  boldB_f.put( edge, edge.getBeta() );

	  assert !workSetEdges.contains( edge );
	  workSetEdges.add( edge );
	}      	

	// enforce the boldB_f constraint at edges until we reach a fixed point
	while( !workSetEdges.isEmpty() ) {
	  RefEdge edge = workSetEdges.iterator().next();
	  workSetEdges.remove( edge );	 
	  
	  Iterator<RefEdge> itrPrime = edge.getDst().iteratorToReferencees();
	  while( itrPrime.hasNext() ) {
	    RefEdge edgePrime = itrPrime.next();	    

	    ReachSet prevResult   = boldB_f.get( edgePrime );
	    ReachSet intersection = boldB_f.get( edge ).intersection( edgePrime.getBeta() );
	    	    
	    if( prevResult == null || 
		prevResult.union( intersection ).size() > prevResult.size() ) {
	      
	      if( prevResult == null ) {
		boldB_f.put( edgePrime, edgePrime.getBeta().union( intersection ) );
	      } else {
		boldB_f.put( edgePrime, prevResult         .union( intersection ) );
	      }
	      workSetEdges.add( edgePrime );	
	    }
	  }
	}
	
       	boldB.put( token, boldB_f );
      }      
    }


    // use boldB to prune tokens from alpha states that are impossible
    // and propagate the differences backwards across edges
    HashSet<RefEdge> edgesForPropagation = new HashSet<RefEdge>();

    Hashtable<RefEdge, ChangeSet> edgePlannedChanges =
      new Hashtable<RefEdge, ChangeSet>();

    hrns = id2hrn.entrySet();
    itrHrns = hrns.iterator();
    while( itrHrns.hasNext() ) {
      Map.Entry me = (Map.Entry)itrHrns.next();
      Integer token = (Integer) me.getKey();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();

      // never remove the identity token from a flagged region
      // because it is trivially satisfied
      ReachTuple ttException = new ReachTuple( token, 
					       !hrn.isSingleObject(), 
					       ReachTuple.ARITY_ONE ).makeCanonical();

      ChangeSet cts = new ChangeSet().makeCanonical();

      // mark tokens for removal
      Iterator<ReachState> stateItr = hrn.getAlpha().iterator();
      while( stateItr.hasNext() ) {
	ReachState ttsOld = stateItr.next();

	ReachState markedTokens = new ReachState().makeCanonical();

	Iterator<ReachTuple> ttItr = ttsOld.iterator();
	while( ttItr.hasNext() ) {
	  ReachTuple ttOld = ttItr.next();

	  // never remove the identity token from a flagged region
	  // because it is trivially satisfied
	  if( hrn.isFlagged() ) {	
	    if( ttOld == ttException ) {
	      continue;
	    }
	  }

	  // does boldB_ttOld allow this token?
	  boolean foundState = false;
	  Iterator<RefEdge> incidentEdgeItr = hrn.iteratorToReferencers();
	  while( incidentEdgeItr.hasNext() ) {
	    RefEdge incidentEdge = incidentEdgeItr.next();

	    // if it isn't allowed, mark for removal
	    Integer idOld = ttOld.getToken();
	    assert id2hrn.containsKey( idOld );
	    Hashtable<RefEdge, ReachSet> B = boldB.get( idOld );	    
	    ReachSet boldB_ttOld_incident = B.get( incidentEdge );// B is NULL!	    
	    if( boldB_ttOld_incident != null &&
		boldB_ttOld_incident.contains( ttsOld ) ) {
	      foundState = true;
	    }
	  }

	  if( !foundState ) {
	    markedTokens = markedTokens.add( ttOld );	  
	  }
	}

	// if there is nothing marked, just move on
	if( markedTokens.isEmpty() ) {
	  hrn.setAlphaNew( hrn.getAlphaNew().union( ttsOld ) );
	  continue;
	}

	// remove all marked tokens and establish a change set that should
	// propagate backwards over edges from this node
	ReachState ttsPruned = new ReachState().makeCanonical();
	ttItr = ttsOld.iterator();
	while( ttItr.hasNext() ) {
	  ReachTuple ttOld = ttItr.next();

	  if( !markedTokens.containsTuple( ttOld ) ) {
	    ttsPruned = ttsPruned.union( ttOld );
	  }
	}
	assert !ttsOld.equals( ttsPruned );

	hrn.setAlphaNew( hrn.getAlphaNew().union( ttsPruned ) );
	ChangeTuple ct = new ChangeTuple( ttsOld, ttsPruned ).makeCanonical();
	cts = cts.union( ct );
      }

      // throw change tuple set on all incident edges
      if( !cts.isEmpty() ) {
	Iterator<RefEdge> incidentEdgeItr = hrn.iteratorToReferencers();
	while( incidentEdgeItr.hasNext() ) {
	  RefEdge incidentEdge = incidentEdgeItr.next();
	  	  
	  edgesForPropagation.add( incidentEdge );

	  if( edgePlannedChanges.get( incidentEdge ) == null ) {
	    edgePlannedChanges.put( incidentEdge, cts );
	  } else {	    
	    edgePlannedChanges.put( 
	      incidentEdge, 
	      edgePlannedChanges.get( incidentEdge ).union( cts ) 
				  );
	  }
	}
      }
    }
    
    HashSet<RefEdge> edgesUpdated = new HashSet<RefEdge>();

    propagateTokensOverEdges( edgesForPropagation,
			      edgePlannedChanges,
			      edgesUpdated );

    // at the end of the 1st phase reference edges have
    // beta, betaNew that correspond to beta and betaR
    //
    // commit beta<-betaNew, so beta=betaR and betaNew
    // will represent the beta' calculation in 2nd phase
    //
    // commit alpha<-alphaNew because it won't change
    HashSet<RefEdge> res = new HashSet<RefEdge>();

    Iterator<HeapRegionNode> nodeItr = id2hrn.values().iterator();
    while( nodeItr.hasNext() ) {
      HeapRegionNode hrn = nodeItr.next();
      hrn.applyAlphaNew();
      Iterator<RefEdge> itrRes = hrn.iteratorToReferencers();
      while( itrRes.hasNext() ) {
	res.add( itrRes.next() );
      }
    }


    // 2nd phase    
    Iterator<RefEdge> edgeItr = res.iterator();
    while( edgeItr.hasNext() ) {
      RefEdge edge = edgeItr.next();
      HeapRegionNode hrn = edge.getDst();

      // commit results of last phase
      if( edgesUpdated.contains( edge ) ) {
	edge.applyBetaNew();
      }

      // compute intial condition of 2nd phase
      edge.setBetaNew( edge.getBeta().intersection( hrn.getAlpha() ) );      
    }
        
    // every edge in the graph is the initial workset
    Set<RefEdge> edgeWorkSet = (Set) res.clone();
    while( !edgeWorkSet.isEmpty() ) {
      RefEdge edgePrime = edgeWorkSet.iterator().next();
      edgeWorkSet.remove( edgePrime );

      RefSrcNode on = edgePrime.getSrc();
      if( !(on instanceof HeapRegionNode) ) {
	continue;
      }
      HeapRegionNode hrn = (HeapRegionNode) on;

      Iterator<RefEdge> itrEdge = hrn.iteratorToReferencers();
      while( itrEdge.hasNext() ) {
	RefEdge edge = itrEdge.next();	    

	ReachSet prevResult = edge.getBetaNew();
	assert prevResult != null;

	ReachSet intersection = edge.getBeta().intersection( edgePrime.getBetaNew() );
		    
	if( prevResult.union( intersection ).size() > prevResult.size() ) {	  
	  edge.setBetaNew( prevResult.union( intersection ) );
	  edgeWorkSet.add( edge );
	}	
      }      
    }

    // commit beta' (beta<-betaNew)
    edgeItr = res.iterator();
    while( edgeItr.hasNext() ) {
      edgeItr.next().applyBetaNew();
    } 
  }  



  ////////////////////////////////////////////////////
  // high-level merge operations
  ////////////////////////////////////////////////////
  public void merge_sameMethodContext( ReachGraph rg ) {
    // when merging two graphs that abstract the heap
    // of the same method context, we just call the
    // basic merge operation
    merge( rg );
  }

  public void merge_diffMethodContext( ReachGraph rg ) {
    // when merging graphs for abstract heaps in
    // different method contexts we should:
    // 1) age the allocation sites?
    merge( rg );
  }

  ////////////////////////////////////////////////////
  // in merge() and equals() methods the suffix A
  // represents the passed in graph and the suffix
  // B refers to the graph in this object
  // Merging means to take the incoming graph A and
  // merge it into B, so after the operation graph B
  // is the final result.
  ////////////////////////////////////////////////////
  protected void merge( ReachGraph rg ) {

    if( rg == null ) {
      return;
    }

    mergeNodes      ( rg );
    mergeRefEdges   ( rg );
    mergeAllocSites ( rg );
    mergeAccessPaths( rg );
  }
  
  protected void mergeNodes( ReachGraph rg ) {

    // start with heap region nodes
    Set      sA = rg.id2hrn.entrySet();
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

        // if hrnB is already dirty or hrnA is dirty,
        // the hrnB should end up dirty
        if( !hrnA.isClean() ) {
          hrnB.setIsClean( false );
        }
      }
    }

    // now add any variable nodes that are in graph B but
    // not in A
    sA = rg.td2vn.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA = (Map.Entry)      iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      VariableNode   lnA = (VariableNode)   meA.getValue();

      // if the variable doesn't exist in B, allocate and add it
      VariableNode lnB = getVariableNodeFromTemp( tdA );
    }
  }

  protected void mergeRefEdges( ReachGraph rg ) {

    // between heap regions
    Set      sA = rg.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA  = (Map.Entry)      iA.next();
      Integer        idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      Iterator<RefEdge> heapRegionsItrA = hrnA.iteratorToReferencees();
      while( heapRegionsItrA.hasNext() ) {
	RefEdge        edgeA     = heapRegionsItrA.next();
	HeapRegionNode hrnChildA = edgeA.getDst();
	Integer        idChildA  = hrnChildA.getID();

	// at this point we know an edge in graph A exists
	// idA -> idChildA, does this exist in B?
	assert id2hrn.containsKey( idA );
	HeapRegionNode hrnB        = id2hrn.get( idA );
	RefEdge        edgeToMerge = null;

	Iterator<RefEdge> heapRegionsItrB = hrnB.iteratorToReferencees();
	while( heapRegionsItrB.hasNext() &&
	       edgeToMerge == null          ) {

	  RefEdge        edgeB     = heapRegionsItrB.next();
	  HeapRegionNode hrnChildB = edgeB.getDst();
	  Integer        idChildB  = hrnChildB.getID();

	  // don't use the RefEdge.equals() here because
	  // we're talking about existence between graphs,
          // not intragraph equal
	  if( idChildB.equals( idChildA ) &&
	      edgeB.typeAndFieldEquals( edgeA ) ) {

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
	  addRefEdge( hrnB, hrnChildB, edgeToMerge );
	}
	// otherwise, the edge already existed in both graphs
	// so merge their reachability sets
	else {
	  // just replace this beta set with the union
	  assert edgeToMerge != null;
	  edgeToMerge.setBeta(
	    edgeToMerge.getBeta().union( edgeA.getBeta() )
	    );
	  if( !edgeA.isClean() ) {
	    edgeToMerge.setIsClean( false );
	  }
	}
      }
    }

    // and then again from variable nodes
    sA = rg.td2vn.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA = (Map.Entry)      iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      VariableNode   vnA = (VariableNode)   meA.getValue();

      Iterator<RefEdge> heapRegionsItrA = vnA.iteratorToReferencees();
      while( heapRegionsItrA.hasNext() ) {
	RefEdge        edgeA     = heapRegionsItrA.next();
	HeapRegionNode hrnChildA = edgeA.getDst();
	Integer        idChildA  = hrnChildA.getID();

	// at this point we know an edge in graph A exists
	// tdA -> idChildA, does this exist in B?
	assert td2vn.containsKey( tdA );
	VariableNode vnB         = td2vn.get( tdA );
	RefEdge      edgeToMerge = null;

	Iterator<RefEdge> heapRegionsItrB = vnB.iteratorToReferencees();
	while( heapRegionsItrB.hasNext() &&
	       edgeToMerge == null          ) {

	  RefEdge        edgeB     = heapRegionsItrB.next();
	  HeapRegionNode hrnChildB = edgeB.getDst();
	  Integer        idChildB  = hrnChildB.getID();

	  // don't use the RefEdge.equals() here because
	  // we're talking about existence between graphs
	  if( idChildB.equals( idChildA ) &&
	      edgeB.typeAndFieldEquals( edgeA ) ) {

	    edgeToMerge = edgeB;
	  }
	}

	// if the edge from A was not found in B,
	// add it to B.
	if( edgeToMerge == null ) {
	  assert id2hrn.containsKey( idChildA );
	  HeapRegionNode hrnChildB = id2hrn.get( idChildA );
	  edgeToMerge = edgeA.copy();
	  edgeToMerge.setSrc( vnB );
	  edgeToMerge.setDst( hrnChildB );
	  addRefEdge( vnB, hrnChildB, edgeToMerge );
	}
	// otherwise, the edge already existed in both graphs
	// so merge their reachability sets
	else {
	  // just replace this beta set with the union
	  edgeToMerge.setBeta(
	    edgeToMerge.getBeta().union( edgeA.getBeta() )
	    );
	  if( !edgeA.isClean() ) {
	    edgeToMerge.setIsClean( false );
	  }
	}
      }
    }
  }

  protected void mergeAllocSites( ReachGraph rg ) {
    allocSites.addAll( rg.allocSites );
  }

  protected void mergeAccessPaths( ReachGraph rg ) {
    UtilAlgorithms.mergeHashtablesWithHashSetValues( temp2accessPaths,
                                                     rg.temp2accessPaths );
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
  public boolean equals( ReachGraph rg ) {

    if( rg == null ) {
      return false;
    }
    
    if( !areHeapRegionNodesEqual( rg ) ) {
      return false;
    }

    if( !areVariableNodesEqual( rg ) ) {
      return false;
    }

    if( !areRefEdgesEqual( rg ) ) {
      return false;
    }

    if( !areAccessPathsEqual( rg ) ) {
      return false;
    }

    // if everything is equal up to this point,
    // assert that allocSites is also equal--
    // this data is redundant but kept for efficiency
    assert allocSites.equals( rg.allocSites );

    return true;
  }

  
  protected boolean areHeapRegionNodesEqual( ReachGraph rg ) {

    if( !areallHRNinAalsoinBandequal( this, rg ) ) {
      return false;
    }

    if( !areallHRNinAalsoinBandequal( rg, this ) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallHRNinAalsoinBandequal( ReachGraph rgA,
                                                        ReachGraph rgB ) {
    Set      sA = rgA.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA  = (Map.Entry)      iA.next();
      Integer        idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      if( !rgB.id2hrn.containsKey( idA ) ) {
	return false;
      }

      HeapRegionNode hrnB = rgB.id2hrn.get( idA );
      if( !hrnA.equalsIncludingAlpha( hrnB ) ) {
	return false;
      }
    }
    
    return true;
  }
  

  protected boolean areVariableNodesEqual( ReachGraph rg ) {

    if( !areallVNinAalsoinBandequal( this, rg ) ) {
      return false;
    }

    if( !areallVNinAalsoinBandequal( rg, this ) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallVNinAalsoinBandequal( ReachGraph rgA,
                                                       ReachGraph rgB ) {
    Set      sA = rgA.td2vn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA = (Map.Entry)      iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();

      if( !rgB.td2vn.containsKey( tdA ) ) {
	return false;
      }
    }

    return true;
  }


  protected boolean areRefEdgesEqual( ReachGraph rg ) {
    if( !areallREinAandBequal( this, rg ) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallREinAandBequal( ReachGraph rgA,
                                                 ReachGraph rgB ) {

    // check all the heap region->heap region edges
    Set      sA = rgA.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA  = (Map.Entry)      iA.next();
      Integer        idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      // we should have already checked that the same
      // heap regions exist in both graphs
      assert rgB.id2hrn.containsKey( idA );

      if( !areallREfromAequaltoB( rgA, hrnA, rgB ) ) {
	return false;
      }

      // then check every edge in B for presence in A, starting
      // from the same parent HeapRegionNode
      HeapRegionNode hrnB = rgB.id2hrn.get( idA );

      if( !areallREfromAequaltoB( rgB, hrnB, rgA ) ) {
	return false;
      }
    }

    // then check all the variable->heap region edges
    sA = rgA.td2vn.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry      meA = (Map.Entry)      iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      VariableNode   vnA = (VariableNode)   meA.getValue();

      // we should have already checked that the same
      // label nodes exist in both graphs
      assert rgB.td2vn.containsKey( tdA );

      if( !areallREfromAequaltoB( rgA, vnA, rgB ) ) {
	return false;
      }

      // then check every edge in B for presence in A, starting
      // from the same parent VariableNode
      VariableNode vnB = rgB.td2vn.get( tdA );

      if( !areallREfromAequaltoB( rgB, vnB, rgA ) ) {
	return false;
      }
    }

    return true;
  }


  static protected boolean areallREfromAequaltoB( ReachGraph rgA,
                                                  RefSrcNode rnA,
                                                  ReachGraph rgB ) {

    Iterator<RefEdge> itrA = rnA.iteratorToReferencees();
    while( itrA.hasNext() ) {
      RefEdge        edgeA     = itrA.next();
      HeapRegionNode hrnChildA = edgeA.getDst();
      Integer        idChildA  = hrnChildA.getID();

      assert rgB.id2hrn.containsKey( idChildA );

      // at this point we know an edge in graph A exists
      // rnA -> idChildA, does this exact edge exist in B?
      boolean edgeFound = false;

      RefSrcNode rnB = null;
      if( rnA instanceof HeapRegionNode ) {
	HeapRegionNode hrnA = (HeapRegionNode) rnA;
	rnB = rgB.id2hrn.get( hrnA.getID() );
      } else {
	VariableNode vnA = (VariableNode) rnA;
	rnB = rgB.td2vn.get( vnA.getTempDescriptor() );
      }

      Iterator<RefEdge> itrB = rnB.iteratorToReferencees();
      while( itrB.hasNext() ) {
	RefEdge        edgeB     = itrB.next();
	HeapRegionNode hrnChildB = edgeB.getDst();
	Integer        idChildB  = hrnChildB.getID();

	if( idChildA.equals( idChildB ) &&
	    edgeA.typeAndFieldEquals( edgeB ) ) {

	  // there is an edge in the right place with the right field,
	  // but do they have the same attributes?
	  if( edgeA.getBeta().equals( edgeB.getBeta() ) ) {
	    edgeFound = true;
	  }
	}
      }
      
      if( !edgeFound ) {
	return false;
      }
    }

    return true;
  }


  protected boolean areAccessPathsEqual( ReachGraph rg ) {
    return temp2accessPaths.equals( rg.temp2accessPaths );
  }



  // this analysis no longer has the "match anything"
  // type which was represented by null
  protected TypeDescriptor mostSpecificType( TypeDescriptor td1,
                                             TypeDescriptor td2 ) {
    assert td1 != null;
    assert td2 != null;

    if( td1.isNull() ) {
      return td2;
    }
    if( td2.isNull() ) {
      return td1;
    }
    return typeUtil.mostSpecific( td1, td2 );
  }
  
  protected TypeDescriptor mostSpecificType( TypeDescriptor td1,
                                             TypeDescriptor td2,
                                             TypeDescriptor td3 ) {
    
    return mostSpecificType( td1, 
			     mostSpecificType( td2, td3 )
			     );
  }  
  
  protected TypeDescriptor mostSpecificType( TypeDescriptor td1,
                                             TypeDescriptor td2,
                                             TypeDescriptor td3,
                                             TypeDescriptor td4 ) {
    
    return mostSpecificType( mostSpecificType( td1, td2 ), 
			     mostSpecificType( td3, td4 )
			     );
  }  

  protected boolean isSuperiorType( TypeDescriptor possibleSuper,
                                    TypeDescriptor possibleChild ) {
    assert possibleSuper != null;
    assert possibleChild != null;
    
    if( possibleSuper.isNull() ||
	possibleChild.isNull() ) {
      return true;
    }

    return typeUtil.isSuperorType( possibleSuper, possibleChild );
  }


  protected boolean hasMatchingField( HeapRegionNode src, 
                                      RefEdge        edge ) {

    TypeDescriptor tdSrc = src.getType();    
    assert tdSrc != null;

    if( tdSrc.isArray() ) {
      TypeDescriptor td = edge.getType();
      assert td != null;

      TypeDescriptor tdSrcDeref = tdSrc.dereference();
      assert tdSrcDeref != null;

      if( !typeUtil.isSuperorType( tdSrcDeref, td ) ) {
	return false;
      }

      return edge.getField().equals( DisjointAnalysis.arrayElementFieldName );
    }

    // if it's not a class, it doesn't have any fields to match
    if( !tdSrc.isClass() ) {
      return false;
    }

    ClassDescriptor cd = tdSrc.getClassDesc();
    while( cd != null ) {      
      Iterator fieldItr = cd.getFields();

      while( fieldItr.hasNext() ) {	
	FieldDescriptor fd = (FieldDescriptor) fieldItr.next();

	if( fd.getType().equals( edge.getType() ) &&
	    fd.getSymbol().equals( edge.getField() ) ) {
	  return true;
	}
      }
      
      cd = cd.getSuperDesc();
    }
    
    // otherwise it is a class with fields
    // but we didn't find a match
    return false;
  }

  protected boolean hasMatchingType( RefEdge        edge, 
                                     HeapRegionNode dst  ) {
    
    // if the region has no type, matches everything
    TypeDescriptor tdDst = dst.getType();
    assert tdDst != null;
 
    // if the type is not a class or an array, don't
    // match because primitives are copied, no aliases
    ClassDescriptor cdDst = tdDst.getClassDesc();
    if( cdDst == null && !tdDst.isArray() ) {
      return false;
    }
 
    // if the edge type is null, it matches everything
    TypeDescriptor tdEdge = edge.getType();
    assert tdEdge != null;
 
    return typeUtil.isSuperorType( tdEdge, tdDst );
  }
  

  public void writeGraph( String graphName,
                          boolean writeLabels,
                          boolean labelSelect,
                          boolean pruneGarbage,
                          boolean writeReferencers,
                          boolean hideSubsetReachability,
                          boolean hideEdgeTaints
                          ) throws java.io.IOException {
    
    // remove all non-word characters from the graph name so
    // the filename and identifier in dot don't cause errors
    graphName = graphName.replaceAll( "[\\W]", "" );

    BufferedWriter bw = 
      new BufferedWriter( new FileWriter( graphName+".dot" ) );

    bw.write( "digraph "+graphName+" {\n" );

    Set<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

    // then visit every heap region node
    Set      s = id2hrn.entrySet();
    Iterator i = s.iterator();
    while( i.hasNext() ) {
      Map.Entry      me  = (Map.Entry)      i.next();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();      

      // only visit nodes worth writing out--for instance
      // not every node at an allocation is referenced
      // (think of it as garbage-collected), etc.
      if( !pruneGarbage                              ||
          (hrn.isFlagged() && hrn.getID() > 0)       ||
          hrn.getDescription().startsWith( "param" ) ||
          hrn.isOutOfContext()
          ) {

	if( !visited.contains( hrn ) ) {
	  traverseHeapRegionNodes( hrn,
                                   bw,
                                   null,
                                   visited,
                                   writeReferencers,
                                   hideSubsetReachability,
                                   hideEdgeTaints );
	}
      }
    }

    bw.write( "  graphTitle[label=\""+graphName+"\",shape=box];\n" );
  

    // then visit every label node, useful for debugging
    if( writeLabels ) {
      s = td2vn.entrySet();
      i = s.iterator();
      while( i.hasNext() ) {
        Map.Entry    me = (Map.Entry)    i.next();
        VariableNode vn = (VariableNode) me.getValue();
        
        if( labelSelect ) {
          String labelStr = vn.getTempDescriptorString();
          if( labelStr.startsWith("___temp") ||
              labelStr.startsWith("___dst") ||
              labelStr.startsWith("___srctmp") ||
              labelStr.startsWith("___neverused")
              ) {
            continue;
          }
        }

        //bw.write("  "+vn.toString() + ";\n");
        
        Iterator<RefEdge> heapRegionsItr = vn.iteratorToReferencees();
        while( heapRegionsItr.hasNext() ) {
          RefEdge        edge = heapRegionsItr.next();
          HeapRegionNode hrn  = edge.getDst();
          
          if( pruneGarbage && !visited.contains( hrn ) ) {
            traverseHeapRegionNodes( hrn,
                                     bw,
                                     null,
                                     visited,
                                     writeReferencers,
                                     hideSubsetReachability,
                                     hideEdgeTaints );
          }
          
          bw.write( "  "        + vn.toString() +
                    " -> "      + hrn.toString() +
                    "[label=\"" + edge.toGraphEdgeString( hideSubsetReachability ) +
                    "\",decorate];\n" );
        }
      }
    }
    
    bw.write( "}\n" );
    bw.close();
  }

  protected void traverseHeapRegionNodes( HeapRegionNode hrn,
                                          BufferedWriter bw,
                                          TempDescriptor td,
                                          Set<HeapRegionNode> visited,
                                          boolean writeReferencers,
                                          boolean hideSubsetReachability,
                                          boolean hideEdgeTaints
                                          ) throws java.io.IOException {

    if( visited.contains( hrn ) ) {
      return;
    }
    visited.add( hrn );

    String attributes = "[";

    if( hrn.isSingleObject() ) {
      attributes += "shape=box";
    } else {
      attributes += "shape=Msquare";
    }

    if( hrn.isFlagged() ) {
      attributes += ",style=filled,fillcolor=lightgrey";
    }

    attributes += ",label=\"ID" +
      hrn.getID()   +
      "\\n";

    if( hrn.getType() != null ) {
      attributes += hrn.getType().toPrettyString() + "\\n";
    }
       
    attributes += hrn.getDescription() +
      "\\n"                +
      hrn.getAlphaString( hideSubsetReachability ) +
      "\"]";

    bw.write( "  "+hrn.toString()+attributes+";\n" );


    Iterator<RefEdge> childRegionsItr = hrn.iteratorToReferencees();
    while( childRegionsItr.hasNext() ) {
      RefEdge        edge     = childRegionsItr.next();
      HeapRegionNode hrnChild = edge.getDst();

      bw.write( "  "       +hrn.toString()+
                " -> "     +hrnChild.toString()+
                "[label=\""+edge.toGraphEdgeString( hideSubsetReachability )+
                "\",decorate];\n");

      traverseHeapRegionNodes( hrnChild,
                               bw,
                               td,
                               visited,
                               writeReferencers,
                               hideSubsetReachability,
                               hideEdgeTaints );
    }
  }  

}
