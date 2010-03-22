package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import Util.UtilAlgorithms;
import java.util.*;
import java.io.*;

public class ReachGraph {

  // use to disable improvements for comparison
  protected static final boolean DISABLE_STRONG_UPDATES = false;
  protected static final boolean DISABLE_GLOBAL_SWEEP   = false;
		   
  // a special out-of-scope temp
  protected static final TempDescriptor tdReturn = new TempDescriptor( "_Return___" );
		   
  // some frequently used reachability constants
  protected static final ReachState rstateEmpty        = ReachState.factory();
  protected static final ReachSet   rsetEmpty          = ReachSet.factory();
  protected static final ReachSet   rsetWithEmptyState = ReachSet.factory( rstateEmpty );

  // predicate constants
  protected static final ExistPred    predTrue   = ExistPred.factory(); // if no args, true
  protected static final ExistPredSet predsEmpty = ExistPredSet.factory();
  protected static final ExistPredSet predsTrue  = ExistPredSet.factory( predTrue );


  // from DisjointAnalysis for convenience
  protected static int      allocationDepth   = -1;
  protected static TypeUtil typeUtil          = null;


  // variable and heap region nodes indexed by unique ID
  public Hashtable<Integer,        HeapRegionNode> id2hrn;
  public Hashtable<TempDescriptor, VariableNode  > td2vn;

  // convenient set of alloc sites for all heap regions
  // present in the graph without having to search
  public HashSet<AllocSite> allocSites;  

  public ReachGraph() {
    id2hrn     = new Hashtable<Integer,        HeapRegionNode>();
    td2vn      = new Hashtable<TempDescriptor, VariableNode  >();
    allocSites = new HashSet<AllocSite>();
  }

  
  // temp descriptors are globally unique and map to
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


  // this suite of methods can be used to assert a
  // very important property of ReachGraph objects:
  // some element, HeapRegionNode, RefEdge etc.
  // should be referenced by at most ONE ReachGraph!!
  // If a heap region or edge or variable should be
  // in another graph, make a new object with
  // equivalent properties for a new graph
  public boolean belongsToThis( RefSrcNode rsn ) {
    if( rsn instanceof VariableNode ) {
      VariableNode vn = (VariableNode) rsn;
      return this.td2vn.get( vn.getTempDescriptor() ) == vn;
    }
    HeapRegionNode hrn = (HeapRegionNode) rsn;
    return this.id2hrn.get( hrn.getID() ) == hrn;
  }



  // the reason for this method is to have the option
  // of creating new heap regions with specific IDs, or
  // duplicating heap regions with specific IDs (especially
  // in the merge() operation) or to create new heap
  // regions with a new unique ID
  protected HeapRegionNode
    createNewHeapRegionNode( Integer        id,
			     boolean        isSingleObject,
			     boolean        isNewSummary,
			     boolean        isFlagged,
                             boolean        isOutOfContext,
			     TypeDescriptor type,
			     AllocSite      allocSite,
                             ReachSet       inherent,
			     ReachSet       alpha,
                             ExistPredSet   preds,
			     String         description
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
	inherent = 
          ReachSet.factory(
                           ReachState.factory(
                                              ReachTuple.factory( id,
                                                                  !isSingleObject,
                                                                  ReachTuple.ARITY_ONE,
                                                                  false // out-of-context
                                                                  )
                                              )
                           );
      } else {
	inherent = rsetWithEmptyState;
      }
    }

    if( alpha == null ) {
      alpha = inherent;
    }

    assert preds != null;
    
    HeapRegionNode hrn = new HeapRegionNode( id,
					     isSingleObject,
					     markForAnalysis,
					     isNewSummary,
                                             isOutOfContext,
					     typeToUse,
					     allocSite,
                                             inherent,
					     alpha,
                                             preds,
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
    assert belongsToThis( referencer );
    assert belongsToThis( referencee );

    // edges are getting added twice to graphs now, the
    // kind that should have abstract facts merged--use
    // this check to prevent that
    assert referencer.getReferenceTo( referencee,
                                      edge.getType(),
                                      edge.getField()
                                      ) == null;

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

  protected void clearNonVarRefEdgesTo( HeapRegionNode referencee ) {
    assert referencee != null;

    // get a copy of the set to iterate over, otherwise
    // we will be trying to take apart the set as we
    // are iterating over it, which won't work
    Iterator<RefEdge> i = referencee.iteratorToReferencersClone();
    while( i.hasNext() ) {
      RefEdge edge = i.next();
      RefSrcNode referencer = edge.getSrc();
      if( !(referencer instanceof VariableNode) ) {
	removeRefEdge( referencer,
                       referencee,
                       edge.getType(),
                       edge.getField() );
      }
    }
  }

  // this is a common operation in many transfer functions: we want
  // to add an edge, but if there is already such an edge we should
  // merge the properties of the existing and the new edges
  protected void addEdgeOrMergeWithExisting( RefEdge edgeNew ) {

    RefSrcNode src = edgeNew.getSrc();
    assert belongsToThis( src );

    HeapRegionNode dst = edgeNew.getDst();
    assert belongsToThis( dst );

    // look to see if an edge with same field exists
    // and merge with it, otherwise just add the edge
    RefEdge edgeExisting = src.getReferenceTo( dst, 
                                               edgeNew.getType(),
                                               edgeNew.getField() 
                                               );
	
    if( edgeExisting != null ) {
      edgeExisting.setBeta(
                           Canonical.unionORpreds( edgeExisting.getBeta(),
                                                   edgeNew.getBeta()
                                                   )
                           );
      edgeExisting.setPreds(
                            Canonical.join( edgeExisting.getPreds(),
                                            edgeNew.getPreds()
                                            )
                            );
	
    } else {			  
      addRefEdge( src, dst, edgeNew );
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
                                       Canonical.intersection( betaY, betaHrn ),
                                       predsTrue
                                       );

        addEdgeOrMergeWithExisting( edgeNew );
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
      ReachSet       R     = Canonical.intersection( hrnX.getAlpha(),
                                                     edgeX.getBeta() 
                                                     );

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
	ChangeSet Cy = Canonical.unionUpArityToChangeSet( O, R );
	propagateTokensOverNodes( hrnY, Cy, nodesWithNewAlpha, edgesWithNewBeta );

	// then propagate back just up the edges from hrn
	ChangeSet Cx = Canonical.unionUpArityToChangeSet( R, O );
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
                                       Canonical.pruneBy( edgeY.getBeta(),
                                                          hrnX.getAlpha() 
                                                          ),
                                       predsTrue
                                       );

        addEdgeOrMergeWithExisting( edgeNew );
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
                   hrnNewest.getAlpha(), // beta
                   predsTrue             // predicates
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

    // keep track of allocation sites that are represented 
    // in this graph for efficiency with other operations
    allocSites.add( as );

    // if there is a k-th oldest node, it merges into
    // the summary node
    Integer idK = as.getOldest();
    if( id2hrn.containsKey( idK ) ) {
      HeapRegionNode hrnK = id2hrn.get( idK );

      // retrieve the summary node, or make it
      // from scratch
      HeapRegionNode hrnSummary = getSummaryNode( as, false );      
      
      mergeIntoSummary( hrnK, hrnSummary );
    }

    // move down the line of heap region nodes
    // clobbering the ith and transferring all references
    // to and from i-1 to node i.
    for( int i = allocationDepth - 1; i > 0; --i ) {

      // only do the transfer if the i-1 node exists
      Integer idImin1th = as.getIthOldest( i - 1 );
      if( id2hrn.containsKey( idImin1th ) ) {
        HeapRegionNode hrnImin1 = id2hrn.get( idImin1th );
        if( hrnImin1.isWiped() ) {
          // there is no info on this node, just skip
          continue;
        }

        // either retrieve or make target of transfer
        HeapRegionNode hrnI = getIthNode( as, i, false );

        transferOnto( hrnImin1, hrnI );
      }

    }

    // as stated above, the newest node should have had its
    // references moved over to the second oldest, so we wipe newest
    // in preparation for being the new object to assign something to
    HeapRegionNode hrn0 = getIthNode( as, 0, false );
    wipeOut( hrn0, true );

    // now tokens in reachability sets need to "age" also
    Iterator itrAllVariableNodes = td2vn.entrySet().iterator();
    while( itrAllVariableNodes.hasNext() ) {
      Map.Entry    me = (Map.Entry)    itrAllVariableNodes.next();
      VariableNode ln = (VariableNode) me.getValue();

      Iterator<RefEdge> itrEdges = ln.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTuplesFrom( as, itrEdges.next() );
      }
    }

    Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
    while( itrAllHRNodes.hasNext() ) {
      Map.Entry      me       = (Map.Entry)      itrAllHRNodes.next();
      HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

      ageTuplesFrom( as, hrnToAge );

      Iterator<RefEdge> itrEdges = hrnToAge.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTuplesFrom( as, itrEdges.next() );
      }
    }


    // after tokens have been aged, reset newest node's reachability
    // and a brand new node has a "true" predicate
    hrn0.setAlpha( hrn0.getInherent() );
    hrn0.setPreds( predsTrue );
  }


  // either retrieve or create the needed heap region node
  protected HeapRegionNode getSummaryNode( AllocSite as, 
                                           boolean   shadow ) {

    Integer idSummary;
    if( shadow ) {
      idSummary = as.getSummaryShadow();
    } else {
      idSummary = as.getSummary();
    }

    HeapRegionNode hrnSummary = id2hrn.get( idSummary );

    if( hrnSummary == null ) {

      boolean hasFlags = false;
      if( as.getType().isClass() ) {
	hasFlags = as.getType().getClassDesc().hasFlags();
      }
      
      if( as.getFlag() ){
        hasFlags = as.getFlag();
      }

      String strDesc = as.toStringForDOT()+"\\nsummary";
      if( shadow ) {
        strDesc += " shadow";
      }

      hrnSummary = 
        createNewHeapRegionNode( idSummary,    // id or null to generate a new one 
                                 false,        // single object?		 
                                 true,         // summary?	 
                                 hasFlags,     // flagged?
                                 false,        // out-of-context?
                                 as.getType(), // type				 
                                 as,           // allocation site			 
                                 null,         // inherent reach
                                 null,         // current reach                 
                                 predsEmpty,   // predicates
                                 strDesc       // description
                                 );                                
    }
  
    return hrnSummary;
  }

  // either retrieve or create the needed heap region node
  protected HeapRegionNode getIthNode( AllocSite as, 
                                       Integer   i, 
                                       boolean   shadow ) {

    Integer idIth;
    if( shadow ) {
      idIth = as.getIthOldestShadow( i );
    } else {
      idIth = as.getIthOldest( i );
    }
    
    HeapRegionNode hrnIth = id2hrn.get( idIth );
    
    if( hrnIth == null ) {

      boolean hasFlags = false;
      if( as.getType().isClass() ) {
        hasFlags = as.getType().getClassDesc().hasFlags();
      }
      
      if( as.getFlag() ){
        hasFlags = as.getFlag();
      }

      String strDesc = as.toStringForDOT()+"\\n"+i+" oldest";
      if( shadow ) {
        strDesc += " shadow";
      }

      hrnIth = createNewHeapRegionNode( idIth,        // id or null to generate a new one 
                                        true,	      // single object?			 
                                        false,	      // summary?			 
                                        hasFlags,     // flagged?			 
                                        false,        // out-of-context?
                                        as.getType(), // type				 
                                        as,	      // allocation site			 
                                        null,         // inherent reach
                                        null,	      // current reach
                                        predsEmpty,   // predicates
                                        strDesc       // description
                                        );
    }

    return hrnIth;
  }


  protected void mergeIntoSummary( HeapRegionNode hrn, 
                                   HeapRegionNode hrnSummary ) {
    assert hrnSummary.isNewSummary();

    // assert that these nodes belong to THIS graph
    assert belongsToThis( hrn );
    assert belongsToThis( hrnSummary );

    assert hrn != hrnSummary;

    // transfer references _from_ hrn over to hrnSummary
    Iterator<RefEdge> itrReferencee = hrn.iteratorToReferencees();
    while( itrReferencee.hasNext() ) {
      RefEdge edge       = itrReferencee.next();
      RefEdge edgeMerged = edge.copy();
      edgeMerged.setSrc( hrnSummary );

      HeapRegionNode hrnReferencee = edge.getDst();
      RefEdge        edgeSummary   = 
        hrnSummary.getReferenceTo( hrnReferencee, 
                                   edge.getType(),
                                   edge.getField() 
                                   );
      
      if( edgeSummary == null ) {
	// the merge is trivial, nothing to be done
        addRefEdge( hrnSummary, hrnReferencee, edgeMerged );

      } else {
	// otherwise an edge from the referencer to hrnSummary exists already
	// and the edge referencer->hrn should be merged with it
	edgeSummary.setBeta( 
                            Canonical.unionORpreds( edgeMerged.getBeta(),
                                             edgeSummary.getBeta() 
                                             ) 
                             );
        edgeSummary.setPreds( 
                             Canonical.join( edgeMerged.getPreds(),
                                             edgeSummary.getPreds() 
                                             )
                              );
      }
    }

    // next transfer references _to_ hrn over to hrnSummary
    Iterator<RefEdge> itrReferencer = hrn.iteratorToReferencers();
    while( itrReferencer.hasNext() ) {
      RefEdge edge         = itrReferencer.next();
      RefEdge edgeMerged   = edge.copy();
      edgeMerged.setDst( hrnSummary );

      RefSrcNode onReferencer = edge.getSrc();
      RefEdge    edgeSummary  =
        onReferencer.getReferenceTo( hrnSummary, 
                                     edge.getType(),
                                     edge.getField() 
                                     );

      if( edgeSummary == null ) {
	// the merge is trivial, nothing to be done
        addRefEdge( onReferencer, hrnSummary, edgeMerged );

      } else {
	// otherwise an edge from the referencer to alpha_S exists already
	// and the edge referencer->alpha_K should be merged with it
	edgeSummary.setBeta( 
                            Canonical.unionORpreds( edgeMerged.getBeta(),
                                             edgeSummary.getBeta() 
                                             ) 
                             );
        edgeSummary.setPreds( 
                             Canonical.join( edgeMerged.getPreds(),
                                             edgeSummary.getPreds() 
                                             )
                              );
      }
    }

    // then merge hrn reachability into hrnSummary
    hrnSummary.setAlpha( 
                        Canonical.unionORpreds( hrnSummary.getAlpha(),
                                         hrn.getAlpha() 
                                         )
                         );

    hrnSummary.setPreds(
                        Canonical.join( hrnSummary.getPreds(),
                                        hrn.getPreds()
                                        )
                        );
    
    // and afterward, this node is gone
    wipeOut( hrn, true );
  }


  protected void transferOnto( HeapRegionNode hrnA, 
                               HeapRegionNode hrnB ) {

    assert belongsToThis( hrnA );
    assert belongsToThis( hrnB );
    assert hrnA != hrnB;

    // clear references in and out of node b?
    assert hrnB.isWiped();

    // copy each: (edge in and out of A) to B
    Iterator<RefEdge> itrReferencee = hrnA.iteratorToReferencees();
    while( itrReferencee.hasNext() ) {
      RefEdge        edge          = itrReferencee.next();
      HeapRegionNode hrnReferencee = edge.getDst();
      RefEdge        edgeNew       = edge.copy();
      edgeNew.setSrc( hrnB );
      edgeNew.setDst( hrnReferencee );

      addRefEdge( hrnB, hrnReferencee, edgeNew );
    }

    Iterator<RefEdge> itrReferencer = hrnA.iteratorToReferencers();
    while( itrReferencer.hasNext() ) {
      RefEdge    edge          = itrReferencer.next();
      RefSrcNode rsnReferencer = edge.getSrc();
      RefEdge    edgeNew       = edge.copy();
      edgeNew.setSrc( rsnReferencer );
      edgeNew.setDst( hrnB );

      addRefEdge( rsnReferencer, hrnB, edgeNew );
    }

    // replace hrnB reachability and preds with hrnA's
    hrnB.setAlpha( hrnA.getAlpha() );
    hrnB.setPreds( hrnA.getPreds() );

    // after transfer, wipe out source
    wipeOut( hrnA, true );
  }


  // the purpose of this method is to conceptually "wipe out"
  // a heap region from the graph--purposefully not called REMOVE
  // because the node is still hanging around in the graph, just
  // not mechanically connected or have any reach or predicate
  // information on it anymore--lots of ops can use this
  protected void wipeOut( HeapRegionNode hrn,
                          boolean        wipeVariableReferences ) {

    assert belongsToThis( hrn );

    clearRefEdgesFrom( hrn, null, null, true );

    if( wipeVariableReferences ) {
      clearRefEdgesTo( hrn, null, null, true );
    } else {
      clearNonVarRefEdgesTo( hrn );
    }

    hrn.setAlpha( rsetEmpty );
    hrn.setPreds( predsEmpty );
  }


  protected void ageTuplesFrom( AllocSite as, RefEdge edge ) {
    edge.setBeta( 
                 Canonical.ageTuplesFrom( edge.getBeta(),
                                          as
                                          )
                  );
  }

  protected void ageTuplesFrom( AllocSite as, HeapRegionNode hrn ) {
    hrn.setAlpha( 
                 Canonical.ageTuplesFrom( hrn.getAlpha(),
                                          as
                                          )
                  );
  }



  protected void propagateTokensOverNodes( HeapRegionNode          nPrime,
                                           ChangeSet               c0,
                                           HashSet<HeapRegionNode> nodesWithNewAlpha,
                                           HashSet<RefEdge>        edgesWithNewBeta ) {

    HashSet<HeapRegionNode> todoNodes
      = new HashSet<HeapRegionNode>();
    todoNodes.add( nPrime );
    
    HashSet<RefEdge> todoEdges
      = new HashSet<RefEdge>();
    
    Hashtable<HeapRegionNode, ChangeSet> nodePlannedChanges
      = new Hashtable<HeapRegionNode, ChangeSet>();
    nodePlannedChanges.put( nPrime, c0 );

    Hashtable<RefEdge, ChangeSet> edgePlannedChanges
      = new Hashtable<RefEdge, ChangeSet>();

    // first propagate change sets everywhere they can go
    while( !todoNodes.isEmpty() ) {
      HeapRegionNode n = todoNodes.iterator().next();
      ChangeSet      C = nodePlannedChanges.get( n );

      Iterator<RefEdge> referItr = n.iteratorToReferencers();
      while( referItr.hasNext() ) {
	RefEdge edge = referItr.next();
	todoEdges.add( edge );

	if( !edgePlannedChanges.containsKey( edge ) ) {
	  edgePlannedChanges.put( edge, 
                                  ChangeSet.factory()
                                  );
	}

	edgePlannedChanges.put( edge, 
                                Canonical.union( edgePlannedChanges.get( edge ),
                                                 C
                                                 )
                                );
      }

      Iterator<RefEdge> refeeItr = n.iteratorToReferencees();
      while( refeeItr.hasNext() ) {
	RefEdge        edgeF = refeeItr.next();
	HeapRegionNode m     = edgeF.getDst();

	ChangeSet changesToPass = ChangeSet.factory();

	Iterator<ChangeTuple> itrCprime = C.iterator();
	while( itrCprime.hasNext() ) {
	  ChangeTuple c = itrCprime.next();
	  if( edgeF.getBeta().containsIgnorePreds( c.getStateToMatch() ) 
              != null
              ) {
	    changesToPass = Canonical.add( changesToPass, c );
	  }
	}

	if( !changesToPass.isEmpty() ) {
	  if( !nodePlannedChanges.containsKey( m ) ) {
	    nodePlannedChanges.put( m, ChangeSet.factory() );
	  }

	  ChangeSet currentChanges = nodePlannedChanges.get( m );

	  if( !changesToPass.isSubset( currentChanges ) ) {

	    nodePlannedChanges.put( m, 
                                    Canonical.union( currentChanges,
                                                     changesToPass
                                                     )
                                    );
	    todoNodes.add( m );
	  }
	}
      }

      todoNodes.remove( n );
    }

    // then apply all of the changes for each node at once
    Iterator itrMap = nodePlannedChanges.entrySet().iterator();
    while( itrMap.hasNext() ) {
      Map.Entry      me = (Map.Entry)      itrMap.next();
      HeapRegionNode n  = (HeapRegionNode) me.getKey();
      ChangeSet      C  = (ChangeSet)      me.getValue();

      // this propagation step is with respect to one change,
      // so we capture the full change from the old alpha:
      ReachSet localDelta = Canonical.applyChangeSet( n.getAlpha(),
                                                      C,
                                                      true 
                                                      );
      // but this propagation may be only one of many concurrent
      // possible changes, so keep a running union with the node's
      // partially updated new alpha set
      n.setAlphaNew( Canonical.unionORpreds( n.getAlphaNew(),
                                      localDelta 
                                      )
                     );

      nodesWithNewAlpha.add( n );
    }

    propagateTokensOverEdges( todoEdges, 
                              edgePlannedChanges, 
                              edgesWithNewBeta
                              );
  }


  protected void propagateTokensOverEdges( HashSet  <RefEdge>            todoEdges,
                                           Hashtable<RefEdge, ChangeSet> edgePlannedChanges,
                                           HashSet  <RefEdge>            edgesWithNewBeta ) {
    
    // first propagate all change tuples everywhere they can go
    while( !todoEdges.isEmpty() ) {
      RefEdge edgeE = todoEdges.iterator().next();
      todoEdges.remove( edgeE );

      if( !edgePlannedChanges.containsKey( edgeE ) ) {
	edgePlannedChanges.put( edgeE, 
                                ChangeSet.factory()
                                );
      }

      ChangeSet C = edgePlannedChanges.get( edgeE );

      ChangeSet changesToPass = ChangeSet.factory();

      Iterator<ChangeTuple> itrC = C.iterator();
      while( itrC.hasNext() ) {
	ChangeTuple c = itrC.next();
	if( edgeE.getBeta().containsIgnorePreds( c.getStateToMatch() ) 
            != null
            ) {
	  changesToPass = Canonical.add( changesToPass, c );
	}
      }

      RefSrcNode rsn = edgeE.getSrc();

      if( !changesToPass.isEmpty() && rsn instanceof HeapRegionNode ) {
	HeapRegionNode n = (HeapRegionNode) rsn;

	Iterator<RefEdge> referItr = n.iteratorToReferencers();
	while( referItr.hasNext() ) {
	  RefEdge edgeF = referItr.next();

	  if( !edgePlannedChanges.containsKey( edgeF ) ) {
	    edgePlannedChanges.put( edgeF,
                                    ChangeSet.factory()
                                    );
	  }

	  ChangeSet currentChanges = edgePlannedChanges.get( edgeF );

	  if( !changesToPass.isSubset( currentChanges ) ) {
	    todoEdges.add( edgeF );
	    edgePlannedChanges.put( edgeF,
                                    Canonical.union( currentChanges,
                                                     changesToPass
                                                     )
                                    );
	  }
	}
      }
    }

    // then apply all of the changes for each edge at once
    Iterator itrMap = edgePlannedChanges.entrySet().iterator();
    while( itrMap.hasNext() ) {
      Map.Entry me = (Map.Entry) itrMap.next();
      RefEdge   e  = (RefEdge)   me.getKey();
      ChangeSet C  = (ChangeSet) me.getValue();

      // this propagation step is with respect to one change,
      // so we capture the full change from the old beta:
      ReachSet localDelta =
        Canonical.applyChangeSet( e.getBeta(),
                                  C,
                                  true 
                                  );

      // but this propagation may be only one of many concurrent
      // possible changes, so keep a running union with the edge's
      // partially updated new beta set
      e.setBetaNew( Canonical.unionORpreds( e.getBetaNew(),
                                     localDelta  
                                     )
                    );
      
      edgesWithNewBeta.add( e );
    }
  }


  // used in makeCalleeView below to decide if there is
  // already an appropriate out-of-context edge in a callee
  // view graph for merging, or null if a new one will be added
  protected RefEdge
    getOutOfContextReferenceTo( HeapRegionNode hrn,
                                TypeDescriptor srcType,
                                TypeDescriptor refType,
                                String         refField ) {
    assert belongsToThis( hrn );

    HeapRegionNode hrnInContext = id2hrn.get( hrn.getID() );
    if( hrnInContext == null ) {
      return null;
    }

    Iterator<RefEdge> refItr = hrnInContext.iteratorToReferencers();
    while( refItr.hasNext() ) {
      RefEdge re = refItr.next();

      assert belongsToThis( re.getSrc() );
      assert belongsToThis( re.getDst() );

      if( !(re.getSrc() instanceof HeapRegionNode) ) {
        continue;
      }

      HeapRegionNode hrnSrc = (HeapRegionNode) re.getSrc();
      if( !hrnSrc.isOutOfContext() ) {
        continue;
      }
      
      if( srcType == null ) {
        if( hrnSrc.getType() != null ) {
          continue;
        }
      } else {
        if( !srcType.equals( hrnSrc.getType() ) ) {
          continue;
        }
      }

      if( !re.typeEquals( refType ) ) {
        continue;
      }

      if( !re.fieldEquals( refField ) ) {
        continue;
      }

      // tada!  We found it!
      return re;
    }
    
    return null;
  }

  // used below to convert a ReachSet to its callee-context
  // equivalent with respect to allocation sites in this graph
  protected ReachSet toCalleeContext( ReachSet        rs,
                                      ExistPredSet    preds,
                                      Set<ReachTuple> oocTuples
                                      ) {
    ReachSet out = ReachSet.factory();
   
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState stateCaller = itr.next();
    
      ReachState stateCallee = stateCaller;

      Iterator<AllocSite> asItr = allocSites.iterator();
      while( asItr.hasNext() ) {
        AllocSite as = asItr.next();

        ReachState stateNew = ReachState.factory();
        Iterator<ReachTuple> rtItr = stateCallee.iterator();
        while( rtItr.hasNext() ) {
          ReachTuple rt = rtItr.next();

          // only translate this tuple if it is
          // in the out-callee-context bag
          if( !oocTuples.contains( rt ) ) {
            stateNew = Canonical.add( stateNew, rt );
            continue;
          }

          int age = as.getAgeCategory( rt.getHrnID() );
          
          // this is the current mapping, where 0, 1, 2S were allocated
          // in the current context, 0?, 1? and 2S? were allocated in a
          // previous context, and we're translating to a future context
          //
          // 0    -> 0?
          // 1    -> 1?
          // 2S   -> 2S?
          // 2S*  -> 2S?*
          //
          // 0?   -> 2S?
          // 1?   -> 2S?
          // 2S?  -> 2S?
          // 2S?* -> 2S?*
      
          if( age == AllocSite.AGE_notInThisSite ) {
            // things not from the site just go back in
            stateNew = Canonical.add( stateNew, rt );

          } else if( age == AllocSite.AGE_summary ||
                     rt.isOutOfContext()
                     ) {
            // the in-context summary and all existing out-of-context
            // stuff all become
            stateNew = Canonical.add( stateNew,
                                      ReachTuple.factory( as.getSummary(),
                                                          true, // multi
                                                          rt.getArity(),
                                                          true  // out-of-context
                                                          )
                                      );
          } else {
            // otherwise everything else just goes to an out-of-context
            // version, everything else the same
            Integer I = as.getAge( rt.getHrnID() );
            assert I != null;

            assert !rt.isMultiObject();

            stateNew = Canonical.add( stateNew,
                                      ReachTuple.factory( rt.getHrnID(),
                                                          rt.isMultiObject(),
                                                          rt.getArity(),
                                                          true  // out-of-context
                                                          )
                                      );        
          }
        }
        
        stateCallee = stateNew;
      }
      
      // attach the passed in preds
      stateCallee = Canonical.attach( stateCallee,
                                      preds );

      out = Canonical.add( out,
                           stateCallee
                           );

    }
    assert out.isCanonical();
    return out;
  }

  // used below to convert a ReachSet to its caller-context
  // equivalent with respect to allocation sites in this graph
  protected ReachSet 
    toCallerContext( ReachSet                            rs,
                     Hashtable<ReachState, ExistPredSet> calleeStatesSatisfied 
                     ) {
    ReachSet out = ReachSet.factory();

    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState stateCallee = itr.next();

      if( calleeStatesSatisfied.containsKey( stateCallee ) ) {

        // starting from one callee state...
        ReachSet rsCaller = ReachSet.factory( stateCallee );

        // possibly branch it into many states, which any
        // allocation site might do, so lots of derived states
        Iterator<AllocSite> asItr = allocSites.iterator();
        while( asItr.hasNext() ) {
          AllocSite as = asItr.next();
          rsCaller = Canonical.toCallerContext( rsCaller, as );
        }     
        
        // then before adding each derived, now caller-context
        // states to the output, attach the appropriate pred
        // based on the source callee state
        Iterator<ReachState> stateItr = rsCaller.iterator();
        while( stateItr.hasNext() ) {
          ReachState stateCaller = stateItr.next();
          stateCaller = Canonical.attach( stateCaller,
                                          calleeStatesSatisfied.get( stateCallee )
                                          );        
          out = Canonical.add( out,
                               stateCaller
                               );
        }
      } 
    }    

    assert out.isCanonical();
    return out;
  }

  // used below to convert a ReachSet to an equivalent
  // version with shadow IDs merged into unshadowed IDs
  protected ReachSet unshadow( ReachSet rs ) {
    ReachSet out = rs;
    Iterator<AllocSite> asItr = allocSites.iterator();
    while( asItr.hasNext() ) {
      AllocSite as = asItr.next();
      out = Canonical.unshadow( out, as );
    }
    assert out.isCanonical();
    return out;
  }


  // use this method to make a new reach graph that is
  // what heap the FlatMethod callee from the FlatCall 
  // would start with reaching from its arguments in
  // this reach graph
  public ReachGraph 
    makeCalleeView( FlatCall     fc,
                    FlatMethod   fmCallee,
                    Set<Integer> callerNodeIDsCopiedToCallee,
                    boolean      writeDebugDOTs
                    ) {


    // first traverse this context to find nodes and edges
    //  that will be callee-reachable
    Set<HeapRegionNode> reachableCallerNodes =
      new HashSet<HeapRegionNode>();

    // caller edges between callee-reachable nodes
    Set<RefEdge> reachableCallerEdges =
      new HashSet<RefEdge>();

    // caller edges from arg vars, and the matching param index
    // because these become a special edge in callee
    Hashtable<RefEdge, Integer> reachableCallerArgEdges2paramIndex =
      new Hashtable<RefEdge, Integer>();

    // caller edges from local vars or callee-unreachable nodes
    // (out-of-context sources) to callee-reachable nodes
    Set<RefEdge> oocCallerEdges =
      new HashSet<RefEdge>();


    for( int i = 0; i < fmCallee.numParameters(); ++i ) {

      TempDescriptor tdArg = fc.getArgMatchingParamIndex( fmCallee, i );
      VariableNode vnArgCaller = this.getVariableNodeFromTemp( tdArg );

      Set<RefSrcNode> toVisitInCaller = new HashSet<RefSrcNode>();
      Set<RefSrcNode> visitedInCaller = new HashSet<RefSrcNode>();

      toVisitInCaller.add( vnArgCaller );
      
      while( !toVisitInCaller.isEmpty() ) {
        RefSrcNode rsnCaller = toVisitInCaller.iterator().next();
        toVisitInCaller.remove( rsnCaller );
        visitedInCaller.add( rsnCaller );

        Iterator<RefEdge> itrRefEdges = rsnCaller.iteratorToReferencees();
        while( itrRefEdges.hasNext() ) {
          RefEdge        reCaller  = itrRefEdges.next();
          HeapRegionNode hrnCaller = reCaller.getDst();

          callerNodeIDsCopiedToCallee.add( hrnCaller.getID() );
          reachableCallerNodes.add( hrnCaller );

          if( reCaller.getSrc() instanceof HeapRegionNode ) {
            reachableCallerEdges.add( reCaller );
          } else {
            if( rsnCaller.equals( vnArgCaller ) ) {
              reachableCallerArgEdges2paramIndex.put( reCaller, i );
            } else {
              oocCallerEdges.add( reCaller );
            }
          }

          if( !visitedInCaller.contains( hrnCaller ) ) {
            toVisitInCaller.add( hrnCaller );
          }
          
        } // end edge iteration
      } // end visiting heap nodes in caller
    } // end iterating over parameters as starting points


    // now collect out-of-context reach tuples and 
    // more out-of-context edges
    Set<ReachTuple> oocTuples = new HashSet<ReachTuple>();

    Iterator<Integer> itrInContext = 
      callerNodeIDsCopiedToCallee.iterator();
    while( itrInContext.hasNext() ) {
      Integer        hrnID                 = itrInContext.next();
      HeapRegionNode hrnCallerAndInContext = id2hrn.get( hrnID );
      
      Iterator<RefEdge> itrMightCross =
        hrnCallerAndInContext.iteratorToReferencers();
      while( itrMightCross.hasNext() ) {
        RefEdge edgeMightCross = itrMightCross.next();        

        RefSrcNode rsnCallerAndOutContext =
          edgeMightCross.getSrc();
        
        if( rsnCallerAndOutContext instanceof VariableNode ) {
          // variables do not have out-of-context reach states,
          // so jump out now
          oocCallerEdges.add( edgeMightCross );
          continue;
        }
          
        HeapRegionNode hrnCallerAndOutContext = 
          (HeapRegionNode) rsnCallerAndOutContext;

        // is this source node out-of-context?
        if( callerNodeIDsCopiedToCallee.contains( hrnCallerAndOutContext.getID() ) ) {
          // no, skip this edge
          continue;
        }

        // okay, we got one
        oocCallerEdges.add( edgeMightCross );

        // add all reach tuples on the node to list
        // of things that are out-of-context: insight
        // if this node is reachable from someting that WAS
        // in-context, then this node should already be in-context
        Iterator<ReachState> stateItr = hrnCallerAndOutContext.getAlpha().iterator();
        while( stateItr.hasNext() ) {
          ReachState state = stateItr.next();

          Iterator<ReachTuple> rtItr = state.iterator();
          while( rtItr.hasNext() ) {
            ReachTuple rt = rtItr.next();

            oocTuples.add( rt );
          }
        }
      }
    }


    // the callee view is a new graph: DON'T MODIFY *THIS* graph
    ReachGraph rg = new ReachGraph();

    // add nodes to callee graph
    Iterator<HeapRegionNode> hrnItr = reachableCallerNodes.iterator();
    while( hrnItr.hasNext() ) {
      HeapRegionNode hrnCaller = hrnItr.next();

      assert callerNodeIDsCopiedToCallee.contains( hrnCaller.getID() );
      assert !rg.id2hrn.containsKey( hrnCaller.getID() );
            
      ExistPred    pred  = ExistPred.factory( hrnCaller.getID(), null );
      ExistPredSet preds = ExistPredSet.factory( pred );
      
      rg.createNewHeapRegionNode( hrnCaller.getID(),
                                  hrnCaller.isSingleObject(),
                                  hrnCaller.isNewSummary(),
                                  hrnCaller.isFlagged(),
                                  false, // out-of-context?
                                  hrnCaller.getType(),
                                  hrnCaller.getAllocSite(),
                                  toCalleeContext( hrnCaller.getInherent(),
                                                   preds,
                                                   oocTuples 
                                                   ),
                                  toCalleeContext( hrnCaller.getAlpha(),
                                                   preds,
                                                   oocTuples 
                                                   ),
                                  preds,
                                  hrnCaller.getDescription()
                                  );
    }

    // add param edges to callee graph
    Iterator argEdges = 
      reachableCallerArgEdges2paramIndex.entrySet().iterator();
    while( argEdges.hasNext() ) {
      Map.Entry me    = (Map.Entry) argEdges.next();
      RefEdge   reArg = (RefEdge)   me.getKey();
      Integer   index = (Integer)   me.getValue();
      
      TempDescriptor arg = fmCallee.getParameter( index );
      
      VariableNode vnCallee = 
        rg.getVariableNodeFromTemp( arg );
      
      HeapRegionNode hrnDstCaller = reArg.getDst();
      HeapRegionNode hrnDstCallee = rg.id2hrn.get( hrnDstCaller.getID() );
      assert hrnDstCallee != null;
      
      ExistPred pred =
        ExistPred.factory( arg,
                           null, 
                           hrnDstCallee.getID(),
                           reArg.getType(),
                           reArg.getField(),
                           null,
                           true,  // out-of-callee-context
                           false  // out-of-caller-context
                           );
      
      ExistPredSet preds = 
        ExistPredSet.factory( pred );
      
      RefEdge reCallee = 
        new RefEdge( vnCallee,
                     hrnDstCallee,
                     reArg.getType(),
                     reArg.getField(),
                     toCalleeContext( reArg.getBeta(),
                                      preds,
                                      oocTuples
                                      ),
                     preds
                     );
      
      rg.addRefEdge( vnCallee,
                     hrnDstCallee,
                     reCallee
                     );      
    }

    // add in-context edges to callee graph
    Iterator<RefEdge> reItr = reachableCallerEdges.iterator();
    while( reItr.hasNext() ) {
      RefEdge    reCaller  = reItr.next();
      RefSrcNode rsnCaller = reCaller.getSrc();
      assert rsnCaller instanceof HeapRegionNode;
      HeapRegionNode hrnSrcCaller = (HeapRegionNode) rsnCaller;
      HeapRegionNode hrnDstCaller = reCaller.getDst();

      HeapRegionNode hrnSrcCallee = rg.id2hrn.get( hrnSrcCaller.getID() );
      HeapRegionNode hrnDstCallee = rg.id2hrn.get( hrnDstCaller.getID() );
      assert hrnSrcCallee != null;
      assert hrnDstCallee != null;

      ExistPred pred =
        ExistPred.factory( null, 
                           hrnSrcCallee.getID(), 
                           hrnDstCallee.getID(),
                           reCaller.getType(),
                           reCaller.getField(),
                           null,
                           false, // out-of-callee-context
                           false  // out-of-caller-context
                           );
      
      ExistPredSet preds = 
        ExistPredSet.factory( pred );
      
      RefEdge reCallee = 
        new RefEdge( hrnSrcCallee,
                     hrnDstCallee,
                     reCaller.getType(),
                     reCaller.getField(),
                     toCalleeContext( reCaller.getBeta(),
                                      preds,
                                      oocTuples 
                                      ),
                     preds
                     );
      
      rg.addRefEdge( hrnSrcCallee,
                     hrnDstCallee,
                     reCallee
                     );        
    }

    // add out-of-context edges to callee graph
    reItr = oocCallerEdges.iterator();
    while( reItr.hasNext() ) {
      RefEdge        reCaller     = reItr.next();
      RefSrcNode     rsnCaller    = reCaller.getSrc();
      HeapRegionNode hrnDstCaller = reCaller.getDst();
      HeapRegionNode hrnDstCallee = rg.id2hrn.get( hrnDstCaller.getID() );
      assert hrnDstCallee != null;

      TypeDescriptor oocNodeType;
      ReachSet       oocReach;
      TempDescriptor oocPredSrcTemp = null;
      Integer        oocPredSrcID   = null;
      boolean        outOfCalleeContext;
      boolean        outOfCallerContext;

      if( rsnCaller instanceof VariableNode ) {
        VariableNode vnCaller = (VariableNode) rsnCaller;
        oocNodeType    = null;
        oocReach       = rsetEmpty;
        oocPredSrcTemp = vnCaller.getTempDescriptor();
        outOfCalleeContext = true;
        outOfCallerContext = false;

      } else {
        HeapRegionNode hrnSrcCaller = (HeapRegionNode) rsnCaller;
        assert !callerNodeIDsCopiedToCallee.contains( hrnSrcCaller.getID() );
        oocNodeType  = hrnSrcCaller.getType();
        oocReach     = hrnSrcCaller.getAlpha(); 
        oocPredSrcID = hrnSrcCaller.getID();
        if( hrnSrcCaller.isOutOfContext() ) {
          outOfCalleeContext = false;
          outOfCallerContext = true;
        } else {
          outOfCalleeContext = true;
          outOfCallerContext = false;
        }
      }

      ExistPred pred =
        ExistPred.factory( oocPredSrcTemp, 
                           oocPredSrcID, 
                           hrnDstCallee.getID(),
                           reCaller.getType(),
                           reCaller.getField(),
                           null,
                           outOfCalleeContext,
                           outOfCallerContext
                           );

      ExistPredSet preds = 
        ExistPredSet.factory( pred );
        
      RefEdge oocEdgeExisting =
        rg.getOutOfContextReferenceTo( hrnDstCallee,
                                       oocNodeType,
                                       reCaller.getType(),
                                       reCaller.getField()
                                       );

      if( oocEdgeExisting == null ) {          
          // for consistency, map one out-of-context "identifier"
          // to one heap region node id, otherwise no convergence
        String oocid = "oocid"+
          fmCallee+
          hrnDstCallee.getIDString()+
          oocNodeType+
          reCaller.getType()+
          reCaller.getField();
          
        Integer oocHrnID = oocid2hrnid.get( oocid );

        HeapRegionNode hrnCalleeAndOutContext;

        if( oocHrnID == null ) {

          hrnCalleeAndOutContext =
            rg.createNewHeapRegionNode( null,  // ID
                                        false, // single object?
                                        false, // new summary?
                                        false, // flagged?
                                        true,  // out-of-context?
                                        oocNodeType,
                                        null,  // alloc site, shouldn't be used
                                        toCalleeContext( oocReach,               
                                                         preds,
                                                         oocTuples
                                                         ),
                                        toCalleeContext( oocReach,
                                                         preds,
                                                         oocTuples
                                                         ),
                                        preds,
                                        "out-of-context"
                                        );       
          
          oocid2hrnid.put( oocid, hrnCalleeAndOutContext.getID() );
          
        } else {

          // the mapping already exists, so see if node is there
          hrnCalleeAndOutContext = rg.id2hrn.get( oocHrnID );

          if( hrnCalleeAndOutContext == null ) {
            // nope, make it
            hrnCalleeAndOutContext =
              rg.createNewHeapRegionNode( oocHrnID,  // ID
                                          false, // single object?
                                          false, // new summary?
                                          false, // flagged?
                                          true,  // out-of-context?
                                          oocNodeType,
                                          null,  // alloc site, shouldn't be used
                                          toCalleeContext( oocReach,
                                                           preds,
                                                           oocTuples
                                                           ),
                                          toCalleeContext( oocReach,
                                                           preds,
                                                           oocTuples
                                                           ),
                                          preds,
                                          "out-of-context"
                                          );       
          } else {
            // otherwise it is there, so merge reachability
            hrnCalleeAndOutContext.setAlpha( Canonical.unionORpreds( hrnCalleeAndOutContext.getAlpha(),
                                                                     toCalleeContext( oocReach,
                                                                                      preds,
                                                                                      oocTuples
                                                                                      )
                                                                     )
                                             );
          }
        }

        rg.addRefEdge( hrnCalleeAndOutContext,
                       hrnDstCallee,
                       new RefEdge( hrnCalleeAndOutContext,
                                    hrnDstCallee,
                                    reCaller.getType(),
                                    reCaller.getField(),
                                    toCalleeContext( reCaller.getBeta(),
                                                     preds,
                                                     oocTuples
                                                     ),
                                    preds
                                    )
                       );              
        
        } else {
        // the out-of-context edge already exists
        oocEdgeExisting.setBeta( Canonical.unionORpreds( oocEdgeExisting.getBeta(),
                                                         toCalleeContext( reCaller.getBeta(),
                                                                          preds,
                                                                          oocTuples
                                                                          )
                                                  )
                                 );         
          
        oocEdgeExisting.setPreds( Canonical.join( oocEdgeExisting.getPreds(),
                                                  reCaller.getPreds()
                                                  )
                                  );          

        HeapRegionNode hrnCalleeAndOutContext =
          (HeapRegionNode) oocEdgeExisting.getSrc();
        hrnCalleeAndOutContext.setAlpha( Canonical.unionORpreds( hrnCalleeAndOutContext.getAlpha(),
                                                                 toCalleeContext( oocReach,
                                                                                  preds,
                                                                                  oocTuples
                                                                                  )
                                                                 )
                                         );
        
        
      }                
    }


    if( writeDebugDOTs ) {    
      try {
        rg.writeGraph( "calleeview", 
                       resolveMethodDebugDOTwriteLabels,    
                       resolveMethodDebugDOTselectTemps,    
                       resolveMethodDebugDOTpruneGarbage,   
                       resolveMethodDebugDOThideSubsetReach,
                       resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }

    return rg;
  }  

  private static Hashtable<String, Integer> oocid2hrnid = 
    new Hashtable<String, Integer>();


  // useful since many graphs writes in the method call debug code
  private static boolean resolveMethodDebugDOTwriteLabels     = true;
  private static boolean resolveMethodDebugDOTselectTemps     = true;
  private static boolean resolveMethodDebugDOTpruneGarbage    = true;
  private static boolean resolveMethodDebugDOThideSubsetReach = false;
  private static boolean resolveMethodDebugDOThideEdgeTaints  = true;



  public void 
    resolveMethodCall( FlatCall     fc,        
                       FlatMethod   fmCallee,        
                       ReachGraph   rgCallee,
                       Set<Integer> callerNodeIDsCopiedToCallee,
                       boolean      writeDebugDOTs
                       ) {


    if( writeDebugDOTs ) {
      try {
        rgCallee.writeGraph( "callee", 
                       resolveMethodDebugDOTwriteLabels,    
                       resolveMethodDebugDOTselectTemps,    
                       resolveMethodDebugDOTpruneGarbage,   
                       resolveMethodDebugDOThideSubsetReach,
                       resolveMethodDebugDOThideEdgeTaints );

        writeGraph( "caller00In",  
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints,
                    callerNodeIDsCopiedToCallee );
      } catch( IOException e ) {}
    }


    // method call transfer function steps:
    // 1. Use current callee-reachable heap (CRH) to test callee 
    //    predicates and mark what will be coming in.
    // 2. Wipe CRH out of caller.
    // 3. Transplant marked callee parts in:
    //    a) bring in nodes
    //    b) bring in callee -> callee edges
    //    c) resolve out-of-context -> callee edges
    //    d) assign return value
    // 4. Collapse shadow nodes down
    // 5. Global sweep it.



    // 1. mark what callee elements have satisfied predicates
    Hashtable<HeapRegionNode, ExistPredSet> calleeNodesSatisfied =
      new Hashtable<HeapRegionNode, ExistPredSet>();
    
    Hashtable<RefEdge, ExistPredSet> calleeEdgesSatisfied =
      new Hashtable<RefEdge, ExistPredSet>();

    Hashtable<ReachState, ExistPredSet> calleeStatesSatisfied =
      new Hashtable<ReachState, ExistPredSet>();

    Hashtable< RefEdge, Set<RefSrcNode> > calleeEdges2oocCallerSrcMatches =
      new Hashtable< RefEdge, Set<RefSrcNode> >();

    Iterator meItr = rgCallee.id2hrn.entrySet().iterator();
    while( meItr.hasNext() ) {
      Map.Entry      me        = (Map.Entry)      meItr.next();
      Integer        id        = (Integer)        me.getKey();
      HeapRegionNode hrnCallee = (HeapRegionNode) me.getValue();

      // if a callee element's predicates are satisfied then a set
      // of CALLER predicates is returned: they are the predicates
      // that the callee element moved into the caller context
      // should have, and it is inefficient to find this again later
      ExistPredSet predsIfSatis = 
        hrnCallee.getPreds().isSatisfiedBy( this,
                                            callerNodeIDsCopiedToCallee
                                            );
      if( predsIfSatis != null ) {
        calleeNodesSatisfied.put( hrnCallee, predsIfSatis );
      } else {
        // otherwise don't bother looking at edges to this node
        continue;
      }
      
      // since the node is coming over, find out which reach
      // states on it should come over, too
      Iterator<ReachState> stateItr = hrnCallee.getAlpha().iterator();
      while( stateItr.hasNext() ) {
        ReachState stateCallee = stateItr.next();

        predsIfSatis = 
          stateCallee.getPreds().isSatisfiedBy( this,
                                                callerNodeIDsCopiedToCallee
                                                );
        if( predsIfSatis != null ) {
          calleeStatesSatisfied.put( stateCallee, predsIfSatis );
        } 
      }

      // then look at edges to the node
      Iterator<RefEdge> reItr = hrnCallee.iteratorToReferencers();
      while( reItr.hasNext() ) {
        RefEdge    reCallee  = reItr.next();
        RefSrcNode rsnCallee = reCallee.getSrc();

        // (caller local variables to in-context heap regions)
        // have an (out-of-context heap region -> in-context heap region)
        // abstraction in the callEE, so its true we never need to
        // look at a (var node -> heap region) edge in callee to bring
        // those over for the call site transfer.  What about (param var->heap region)
        // edges in callee? They are dealt with below this loop.
        // So, yes, at this point skip (var->region) edges in callee
        if( rsnCallee instanceof VariableNode ) {
          continue;
        }        

        // first see if the source is out-of-context, and only
        // proceed with this edge if we find some caller-context
        // matches
        HeapRegionNode hrnSrcCallee = (HeapRegionNode) rsnCallee;
        boolean matchedOutOfContext = false;

        if( hrnSrcCallee.isOutOfContext() ) {          

          assert !calleeEdges2oocCallerSrcMatches.containsKey( reCallee );
          Set<RefSrcNode> rsnCallers = new HashSet<RefSrcNode>();            

          HeapRegionNode hrnDstCaller = this.id2hrn.get( hrnCallee.getID() );
          Iterator<RefEdge> reDstItr = hrnDstCaller.iteratorToReferencers();
          while( reDstItr.hasNext() ) {
            // the edge and field (either possibly null) must match
            RefEdge reCaller = reDstItr.next();

            if( !reCaller.typeEquals ( reCallee.getType()  ) ||
                !reCaller.fieldEquals( reCallee.getField() ) 
                ) {
              continue;
            }
            
            RefSrcNode rsnCaller = reCaller.getSrc();
            if( rsnCaller instanceof VariableNode ) {
              // a variable node matches an OOC region with null type
              if( hrnSrcCallee.getType() != null ) {
                continue;
              }

            } else {
              // otherwise types should match
              HeapRegionNode hrnCallerSrc = (HeapRegionNode) rsnCaller;
              if( hrnSrcCallee.getType() == null ) {
                if( hrnCallerSrc.getType() != null ) {
                  continue;
                }
              } else {
                if( !hrnSrcCallee.getType().equals( hrnCallerSrc.getType() ) ) {
                  continue;
                }
              }
            }

            rsnCallers.add( rsnCaller );
            matchedOutOfContext = true;
          }

          if( !rsnCallers.isEmpty() ) {
            calleeEdges2oocCallerSrcMatches.put( reCallee, rsnCallers );
          }
        }

        if( hrnSrcCallee.isOutOfContext() &&
            !matchedOutOfContext ) {
          continue;
        }
        
        predsIfSatis = 
          reCallee.getPreds().isSatisfiedBy( this,
                                             callerNodeIDsCopiedToCallee
                                             );
        if( predsIfSatis != null ) {
          calleeEdgesSatisfied.put( reCallee, predsIfSatis );

          // since the edge is coming over, find out which reach
          // states on it should come over, too
          stateItr = reCallee.getBeta().iterator();
          while( stateItr.hasNext() ) {
            ReachState stateCallee = stateItr.next();
            
            predsIfSatis = 
              stateCallee.getPreds().isSatisfiedBy( this,
                                                    callerNodeIDsCopiedToCallee
                                                    );
            if( predsIfSatis != null ) {
              calleeStatesSatisfied.put( stateCallee, predsIfSatis );
            } 
          }
        }        
      }
    }

    // test param -> HRN edges, also
    for( int i = 0; i < fmCallee.numParameters(); ++i ) {

      // parameter defined here is the symbol in the callee
      TempDescriptor tdParam  = fmCallee.getParameter( i );
      VariableNode   vnCallee = rgCallee.getVariableNodeFromTemp( tdParam );

      Iterator<RefEdge> reItr = vnCallee.iteratorToReferencees();
      while( reItr.hasNext() ) {
        RefEdge reCallee = reItr.next();
        
        ExistPredSet ifDst = 
          reCallee.getDst().getPreds().isSatisfiedBy( this,
                                                      callerNodeIDsCopiedToCallee
                                                      );
        if( ifDst == null ) {
          continue;
        }
        
        ExistPredSet predsIfSatis = 
          reCallee.getPreds().isSatisfiedBy( this,
                                             callerNodeIDsCopiedToCallee
                                             );
        if( predsIfSatis != null ) {
          calleeEdgesSatisfied.put( reCallee, predsIfSatis );

          // since the edge is coming over, find out which reach
          // states on it should come over, too
          Iterator<ReachState> stateItr = reCallee.getBeta().iterator();
          while( stateItr.hasNext() ) {
            ReachState stateCallee = stateItr.next();
            
            predsIfSatis = 
              stateCallee.getPreds().isSatisfiedBy( this,
                                                    callerNodeIDsCopiedToCallee
                                                    );
            if( predsIfSatis != null ) {
              calleeStatesSatisfied.put( stateCallee, predsIfSatis );
            } 
          }

        }        
      }
    }




    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller20BeforeWipe", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }


    // 2. predicates tested, ok to wipe out caller part
    Iterator<Integer> hrnItr = callerNodeIDsCopiedToCallee.iterator();
    while( hrnItr.hasNext() ) {
      Integer        hrnID     = hrnItr.next();
      HeapRegionNode hrnCaller = id2hrn.get( hrnID );
      assert hrnCaller != null;

      // when clearing off nodes, also eliminate variable
      // references
      wipeOut( hrnCaller, true );
    }



    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller30BeforeAddingNodes", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }


    // 3. callee elements with satisfied preds come in, note that
    //    the mapping of elements satisfied to preds is like this:
    //    A callee element EE has preds EEp that are satisfied by
    //    some caller element ER.  We bring EE into the caller
    //    context as ERee with the preds of ER, namely ERp, which
    //    in the following algorithm is the value in the mapping

    // 3.a) nodes
    Iterator satisItr = calleeNodesSatisfied.entrySet().iterator();
    while( satisItr.hasNext() ) {
      Map.Entry      me        = (Map.Entry)      satisItr.next();
      HeapRegionNode hrnCallee = (HeapRegionNode) me.getKey();
      ExistPredSet   preds     = (ExistPredSet)   me.getValue();

      // TODO: I think its true that the current implementation uses
      // the type of the OOC region and the predicates OF THE EDGE from
      // it to link everything up in caller context, so that's why we're
      // skipping this... maybe that's a sillier way to do it?
      if( hrnCallee.isOutOfContext() ) {
        continue;
      }

      AllocSite as = hrnCallee.getAllocSite();  
      allocSites.add( as );

      Integer hrnIDshadow = as.getShadowIDfromID( hrnCallee.getID() );

      HeapRegionNode hrnCaller = id2hrn.get( hrnIDshadow );
      if( hrnCaller == null ) {
        hrnCaller =
          createNewHeapRegionNode( hrnIDshadow,                // id or null to generate a new one 
                                   hrnCallee.isSingleObject(), // single object?		 
                                   hrnCallee.isNewSummary(),   // summary?	 
                                   hrnCallee.isFlagged(),      // flagged?
                                   false,                      // out-of-context?
                                   hrnCallee.getType(),        // type				 
                                   hrnCallee.getAllocSite(),   // allocation site			 
                                   toCallerContext( hrnCallee.getInherent(),
                                                    calleeStatesSatisfied  ),    // inherent reach
                                   null,                       // current reach                 
                                   predsEmpty,                 // predicates
                                   hrnCallee.getDescription()  // description
                                   );                                        
      } else {
        assert hrnCaller.isWiped();
      }

      hrnCaller.setAlpha( toCallerContext( hrnCallee.getAlpha(),
                                           calleeStatesSatisfied 
                                           )
                          );

      hrnCaller.setPreds( preds );
    }



    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller31BeforeAddingEdges", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }


    // set these up during the next procedure so after
    // the caller has all of its nodes and edges put
    // back together we can propagate the callee's
    // reach changes backwards into the caller graph
    HashSet<RefEdge> edgesForPropagation = new HashSet<RefEdge>();

    Hashtable<RefEdge, ChangeSet> edgePlannedChanges =
      new Hashtable<RefEdge, ChangeSet>();


    // 3.b) callee -> callee edges AND out-of-context -> callee
    satisItr = calleeEdgesSatisfied.entrySet().iterator();
    while( satisItr.hasNext() ) {
      Map.Entry    me       = (Map.Entry)    satisItr.next();
      RefEdge      reCallee = (RefEdge)      me.getKey();
      ExistPredSet preds    = (ExistPredSet) me.getValue();

      HeapRegionNode hrnDstCallee = reCallee.getDst();
      AllocSite      asDst        = hrnDstCallee.getAllocSite();
      allocSites.add( asDst );

      Integer hrnIDDstShadow = 
        asDst.getShadowIDfromID( hrnDstCallee.getID() );
      
      HeapRegionNode hrnDstCaller = id2hrn.get( hrnIDDstShadow );
      assert hrnDstCaller != null;
      
      
      RefSrcNode rsnCallee = reCallee.getSrc();

      Set<RefSrcNode> rsnCallers =
        new HashSet<RefSrcNode>();
      
      Set<RefSrcNode> oocCallers = 
        calleeEdges2oocCallerSrcMatches.get( reCallee );

      boolean oocEdges = false;
      
      if( oocCallers == null ) {
        // there are no out-of-context matches, so it's
        // either a param/arg var or one in-context heap region
        if( rsnCallee instanceof VariableNode ) {
          // variable -> node in the callee should only
          // come into the caller if its from a param var
          VariableNode   vnCallee = (VariableNode) rsnCallee;
          TempDescriptor tdParam  = vnCallee.getTempDescriptor();
          TempDescriptor tdArg    = fc.getArgMatchingParam( fmCallee,
                                                            tdParam );
          if( tdArg == null ) {
            // this means the variable isn't a parameter, its local
            // to the callee so we ignore it in call site transfer
            // shouldn't this NEVER HAPPEN?
            assert false;
          }
          rsnCallers.add( this.getVariableNodeFromTemp( tdArg ) );
          oocEdges = true;

        } else {
          // otherwise source is in context, one region
          HeapRegionNode hrnSrcCallee = (HeapRegionNode) rsnCallee;

          // translate an in-context node to shadow
          AllocSite asSrc = hrnSrcCallee.getAllocSite();
          allocSites.add( asSrc );
          
          Integer hrnIDSrcShadow = 
            asSrc.getShadowIDfromID( hrnSrcCallee.getID() );

          HeapRegionNode hrnSrcCallerShadow =
            this.id2hrn.get( hrnIDSrcShadow );
          
          if( hrnSrcCallerShadow == null ) {
            hrnSrcCallerShadow =
              createNewHeapRegionNode( hrnIDSrcShadow,                // id or null to generate a new one 
                                       hrnSrcCallee.isSingleObject(), // single object?		 
                                       hrnSrcCallee.isNewSummary(),   // summary?	 
                                       hrnSrcCallee.isFlagged(),      // flagged?
                                       false,                         // out-of-context?
                                       hrnSrcCallee.getType(),        // type				 
                                       hrnSrcCallee.getAllocSite(),   // allocation site			 
                                       toCallerContext( hrnSrcCallee.getInherent(),
                                                        calleeStatesSatisfied ),    // inherent reach
                                       toCallerContext( hrnSrcCallee.getAlpha(),
                                                        calleeStatesSatisfied ),       // current reach                 
                                       predsEmpty,                    // predicates
                                       hrnSrcCallee.getDescription()  // description
                                       );                                        
          }
          
          rsnCallers.add( hrnSrcCallerShadow );
        }

      } else {
        // otherwise we have a set of out-of-context srcs
        // that should NOT be translated to shadow nodes
        assert !oocCallers.isEmpty();
        rsnCallers.addAll( oocCallers );
        oocEdges = true;
      }

      // now make all caller edges we've identified from
      // this callee edge with a satisfied predicate
      assert !rsnCallers.isEmpty();
      Iterator<RefSrcNode> rsnItr = rsnCallers.iterator();
      while( rsnItr.hasNext() ) {
        RefSrcNode rsnCaller = rsnItr.next();
        
        RefEdge reCaller = new RefEdge( rsnCaller,
                                        hrnDstCaller,
                                        reCallee.getType(),
                                        reCallee.getField(),
                                        toCallerContext( reCallee.getBeta(),
                                                         calleeStatesSatisfied ),
                                        preds
                                        );

        ChangeSet cs = ChangeSet.factory();
        Iterator<ReachState> rsItr = reCaller.getBeta().iterator();
        while( rsItr.hasNext() ) {
          ReachState   state          = rsItr.next();
          ExistPredSet predsPreCallee = state.getPreds();

          if( state.isEmpty() ) {
            continue;
          }

          Iterator<ExistPred> predItr = predsPreCallee.iterator();
          while( predItr.hasNext() ) {            
            ExistPred pred = predItr.next();
            ReachState old = pred.ne_state;

            if( old == null ) {
              old = rstateEmpty;
            }

            cs = Canonical.add( cs,
                                ChangeTuple.factory( old,
                                                     state
                                                     )
                                );
          }
        }
        

        // look to see if an edge with same field exists
        // and merge with it, otherwise just add the edge
        RefEdge edgeExisting = rsnCaller.getReferenceTo( hrnDstCaller,
                                                         reCallee.getType(),
                                                         reCallee.getField()
                                                         );	
        if( edgeExisting != null ) {
          edgeExisting.setBeta(
                               Canonical.unionORpreds( edgeExisting.getBeta(),
                                                reCaller.getBeta()
                                                )
                               );
          edgeExisting.setPreds(
                                Canonical.join( edgeExisting.getPreds(),
                                                reCaller.getPreds()
                                                )
                                );

          // for reach propagation
          if( !cs.isEmpty() ) {
            edgePlannedChanges.put( 
                                   edgeExisting, 
                                   Canonical.union( edgePlannedChanges.get( edgeExisting ),
                                                    cs
                                                    ) 
                                    );
          }
          
        } else {			  
          addRefEdge( rsnCaller, hrnDstCaller, reCaller );	

          // for reach propagation
          if( !cs.isEmpty() ) {
            edgesForPropagation.add( reCaller );
            assert !edgePlannedChanges.containsKey( reCaller );
            edgePlannedChanges.put( reCaller, cs );
          }
        }
      }
    }





    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller35BeforeAssignReturnValue", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }



    // TODO: WAIT! THIS SHOULD BE MERGED INTO OTHER PARTS, BECAUSE
    // AS IT IS WE'RE NOT VERIFYING PREDICATES OF RETURN VALUE
    // EDGES, JUST BRINGING THEM ALL!  It'll work for now, over approximation
    
    // 3.d) handle return value assignment if needed
    TempDescriptor returnTemp = fc.getReturnTemp();
    if( returnTemp != null && !returnTemp.getType().isImmutable() ) {

      VariableNode vnLhsCaller = getVariableNodeFromTemp( returnTemp );
      clearRefEdgesFrom( vnLhsCaller, null, null, true );

      VariableNode vnReturnCallee = rgCallee.getVariableNodeFromTemp( tdReturn );
      Iterator<RefEdge> reCalleeItr = vnReturnCallee.iteratorToReferencees();
      while( reCalleeItr.hasNext() ) {
	RefEdge        reCallee     = reCalleeItr.next();
	HeapRegionNode hrnDstCallee = reCallee.getDst();

	// some edge types are not possible return values when we can
	// see what type variable we are assigning it to
	if( !isSuperiorType( returnTemp.getType(), reCallee.getType() ) ) {
	  System.out.println( "*** NOT EXPECTING TO SEE THIS: Throwing out "+
                              reCallee+" for return temp "+returnTemp );
	  // prune
	  continue;
	}	

        AllocSite asDst = hrnDstCallee.getAllocSite();
        allocSites.add( asDst );

        Integer hrnIDDstShadow = asDst.getShadowIDfromID( hrnDstCallee.getID() );

        HeapRegionNode hrnDstCaller = id2hrn.get( hrnIDDstShadow );
        if( hrnDstCaller == null ) {
          hrnDstCaller =
            createNewHeapRegionNode( hrnIDDstShadow,                // id or null to generate a new one 
                                     hrnDstCallee.isSingleObject(), // single object?		 
                                     hrnDstCallee.isNewSummary(),   // summary?	 
                                     hrnDstCallee.isFlagged(),      // flagged?
                                     false,                         // out-of-context?
                                     hrnDstCallee.getType(),        // type				 
                                     hrnDstCallee.getAllocSite(),   // allocation site			 
                                     toCallerContext( hrnDstCallee.getInherent(),
                                                      calleeStatesSatisfied  ),    // inherent reach
                                     toCallerContext( hrnDstCallee.getAlpha(),
                                                      calleeStatesSatisfied  ),    // current reach                 
                                     predsTrue,                     // predicates
                                     hrnDstCallee.getDescription()  // description
                                     );                                        
        }

        TypeDescriptor tdNewEdge =
          mostSpecificType( reCallee.getType(),
                            hrnDstCallee.getType(),
                            hrnDstCaller.getType()
                            );	      

        RefEdge reCaller = new RefEdge( vnLhsCaller,
                                        hrnDstCaller,
                                        tdNewEdge,
                                        null,
                                        toCallerContext( reCallee.getBeta(),
                                                         calleeStatesSatisfied ),
                                        predsTrue
                                        );

        addRefEdge( vnLhsCaller, hrnDstCaller, reCaller );
      }
    }



    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller38propagateReach", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }

    // propagate callee reachability changes to the rest
    // of the caller graph edges
    HashSet<RefEdge> edgesUpdated = new HashSet<RefEdge>();
  
    propagateTokensOverEdges( edgesForPropagation, // source edges
                              edgePlannedChanges,  // map src edge to change set
                              edgesUpdated );      // list of updated edges
    
    // commit beta' (beta<-betaNew)
    Iterator<RefEdge> edgeItr = edgesUpdated.iterator();
    while( edgeItr.hasNext() ) {
      edgeItr.next().applyBetaNew();
    }






    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller40BeforeShadowMerge", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }
    

    // 4) merge shadow nodes so alloc sites are back to k
    Iterator<AllocSite> asItr = rgCallee.allocSites.iterator();
    while( asItr.hasNext() ) {
      // for each allocation site do the following to merge
      // shadow nodes (newest from callee) with any existing
      // look for the newest normal and newest shadow "slot"
      // not being used, transfer normal to shadow.  Keep
      // doing this until there are no more normal nodes, or
      // no empty shadow slots: then merge all remaining normal
      // nodes into the shadow summary.  Finally, convert all
      // shadow to their normal versions.
      AllocSite as = asItr.next();
      int ageNorm = 0;
      int ageShad = 0;
      while( ageNorm < allocationDepth &&
             ageShad < allocationDepth ) {

        // first, are there any normal nodes left?
        Integer        idNorm  = as.getIthOldest( ageNorm );
        HeapRegionNode hrnNorm = id2hrn.get( idNorm );
        if( hrnNorm == null ) {
          // no, this age of normal node not in the caller graph
          ageNorm++;
          continue;
        }

        // yes, a normal node exists, is there an empty shadow
        // "slot" to transfer it onto?
        HeapRegionNode hrnShad = getIthNode( as, ageShad, true );        
        if( !hrnShad.isWiped() ) {
          // no, this age of shadow node is not empty
          ageShad++;
          continue;
        }
 
        // yes, this shadow node is empty
        transferOnto( hrnNorm, hrnShad );
        ageNorm++;
        ageShad++;
      }

      // now, while there are still normal nodes but no shadow
      // slots, merge normal nodes into the shadow summary
      while( ageNorm < allocationDepth ) {

        // first, are there any normal nodes left?
        Integer        idNorm  = as.getIthOldest( ageNorm );
        HeapRegionNode hrnNorm = id2hrn.get( idNorm );
        if( hrnNorm == null ) {
          // no, this age of normal node not in the caller graph
          ageNorm++;
          continue;
        }

        // yes, a normal node exists, so get the shadow summary
        HeapRegionNode summShad = getSummaryNode( as, true );
        mergeIntoSummary( hrnNorm, summShad );
        ageNorm++;
      }

      // if there is a normal summary, merge it into shadow summary
      Integer        idNorm   = as.getSummary();
      HeapRegionNode summNorm = id2hrn.get( idNorm );
      if( summNorm != null ) {
        HeapRegionNode summShad = getSummaryNode( as, true );
        mergeIntoSummary( summNorm, summShad );
      }
      
      // finally, flip all existing shadow nodes onto the normal
      for( int i = 0; i < allocationDepth; ++i ) {
        Integer        idShad  = as.getIthOldestShadow( i );
        HeapRegionNode hrnShad = id2hrn.get( idShad );
        if( hrnShad != null ) {
          // flip it
          HeapRegionNode hrnNorm = getIthNode( as, i, false );
          assert hrnNorm.isWiped();
          transferOnto( hrnShad, hrnNorm );
        }
      }
      
      Integer        idShad   = as.getSummaryShadow();
      HeapRegionNode summShad = id2hrn.get( idShad );
      if( summShad != null ) {
        summNorm = getSummaryNode( as, false );
        transferOnto( summShad, summNorm );
      }      
    }


    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller45BeforeUnshadow", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }
    
    
    Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
    while( itrAllHRNodes.hasNext() ) {
      Map.Entry      me  = (Map.Entry)      itrAllHRNodes.next();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();
      
      hrn.setAlpha( unshadow( hrn.getAlpha() ) );
      
      Iterator<RefEdge> itrEdges = hrn.iteratorToReferencers();
      while( itrEdges.hasNext() ) {
        RefEdge re = itrEdges.next();
        re.setBeta( unshadow( re.getBeta() ) );
      }
    }
    


    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller50BeforeGlobalSweep", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }


    // 5.
    if( !DISABLE_GLOBAL_SWEEP ) {
      globalSweep();
    }
    


    if( writeDebugDOTs ) {
      try {
        writeGraph( "caller90AfterTransfer", 
                    resolveMethodDebugDOTwriteLabels,    
                    resolveMethodDebugDOTselectTemps,    
                    resolveMethodDebugDOTpruneGarbage,   
                    resolveMethodDebugDOThideSubsetReach,
                    resolveMethodDebugDOThideEdgeTaints );
      } catch( IOException e ) {}
    }
  } 

  

  ////////////////////////////////////////////////////
  //
  //  Abstract garbage collection simply removes
  //  heap region nodes that are not mechanically
  //  reachable from a root set.  This step is
  //  essential for testing node and edge existence
  //  predicates efficiently
  //
  ////////////////////////////////////////////////////
  public void abstractGarbageCollect( Set<TempDescriptor> liveSet ) {

    // calculate a root set, will be different for Java
    // version of analysis versus Bamboo version
    Set<RefSrcNode> toVisit = new HashSet<RefSrcNode>();

    // visit every variable in graph while building root
    // set, and do iterating on a copy, so we can remove
    // dead variables while we're at this
    Iterator makeCopyItr = td2vn.entrySet().iterator();
    Set      entrysCopy  = new HashSet();
    while( makeCopyItr.hasNext() ) {
      entrysCopy.add( makeCopyItr.next() );
    }
    
    Iterator eItr = entrysCopy.iterator();
    while( eItr.hasNext() ) {
      Map.Entry      me = (Map.Entry)      eItr.next();
      TempDescriptor td = (TempDescriptor) me.getKey();
      VariableNode   vn = (VariableNode)   me.getValue();

      if( liveSet.contains( td ) ) {
        toVisit.add( vn );

      } else {
        // dead var, remove completely from graph
        td2vn.remove( td );
        clearRefEdgesFrom( vn, null, null, true );
      }
    }

    // everything visited in a traversal is
    // considered abstractly live
    Set<RefSrcNode> visited = new HashSet<RefSrcNode>();
    
    while( !toVisit.isEmpty() ) {
      RefSrcNode rsn = toVisit.iterator().next();
      toVisit.remove( rsn );
      visited.add( rsn );
      
      Iterator<RefEdge> hrnItr = rsn.iteratorToReferencees();
      while( hrnItr.hasNext() ) {
        RefEdge        edge = hrnItr.next();
        HeapRegionNode hrn  = edge.getDst();
        
        if( !visited.contains( hrn ) ) {
          toVisit.add( hrn );
        }
      }
    }

    // get a copy of the set to iterate over because
    // we're going to monkey with the graph when we
    // identify a garbage node
    Set<HeapRegionNode> hrnAllPrior = new HashSet<HeapRegionNode>();
    Iterator<HeapRegionNode> hrnItr = id2hrn.values().iterator();
    while( hrnItr.hasNext() ) {
      hrnAllPrior.add( hrnItr.next() );
    }

    Iterator<HeapRegionNode> hrnAllItr = hrnAllPrior.iterator();
    while( hrnAllItr.hasNext() ) {
      HeapRegionNode hrn = hrnAllItr.next();

      if( !visited.contains( hrn ) ) {

        // heap region nodes are compared across ReachGraph
        // objects by their integer ID, so when discarding
        // garbage nodes we must also discard entries in
        // the ID -> heap region hashtable.
        id2hrn.remove( hrn.getID() );

        // RefEdge objects are two-way linked between
        // nodes, so when a node is identified as garbage,
        // actively clear references to and from it so
        // live nodes won't have dangling RefEdge's
        wipeOut( hrn, true );

        // if we just removed the last node from an allocation
        // site, it should be taken out of the ReachGraph's list
        AllocSite as = hrn.getAllocSite();
        if( !hasNodesOf( as ) ) {
          allocSites.remove( as );
        }
      }
    }
  }

  protected boolean hasNodesOf( AllocSite as ) {
    if( id2hrn.containsKey( as.getSummary() ) ) {
      return true;
    }

    for( int i = 0; i < allocationDepth; ++i ) {
      if( id2hrn.containsKey( as.getIthOldest( i ) ) ) {
        return true;
      }      
    }
    return false;
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
    // it has an in-context table and an out-of-context table
    Hashtable< Integer, Hashtable<RefEdge, ReachSet> > boldBic =
      new Hashtable< Integer, Hashtable<RefEdge, ReachSet> >();    

    Hashtable< Integer, Hashtable<RefEdge, ReachSet> > boldBooc =
      new Hashtable< Integer, Hashtable<RefEdge, ReachSet> >();    

    // visit every heap region to initialize alphaNew and betaNew,
    // and make a map of every hrnID to the source nodes it should
    // propagate forward from.  In-context flagged hrnID's propagate
    // from only the in-context node they name, but out-of-context
    // ID's may propagate from several out-of-context nodes
    Hashtable< Integer, Set<HeapRegionNode> > icID2srcs = 
      new Hashtable< Integer, Set<HeapRegionNode> >();

    Hashtable< Integer, Set<HeapRegionNode> > oocID2srcs =
      new Hashtable< Integer, Set<HeapRegionNode> >();


    Iterator itrHrns = id2hrn.entrySet().iterator();
    while( itrHrns.hasNext() ) {
      Map.Entry      me    = (Map.Entry)      itrHrns.next();
      Integer        hrnID = (Integer)        me.getKey();
      HeapRegionNode hrn   = (HeapRegionNode) me.getValue();
    
      // assert that this node and incoming edges have clean alphaNew
      // and betaNew sets, respectively
      assert rsetEmpty.equals( hrn.getAlphaNew() );

      Iterator<RefEdge> itrRers = hrn.iteratorToReferencers();
      while( itrRers.hasNext() ) {
	RefEdge edge = itrRers.next();
	assert rsetEmpty.equals( edge.getBetaNew() );
      }      

      // calculate boldB for this flagged node, or out-of-context node
      if( hrn.isFlagged() ) {
        assert !hrn.isOutOfContext();
        assert !icID2srcs.containsKey( hrn.getID() );
        Set<HeapRegionNode> srcs = new HashSet<HeapRegionNode>();
        srcs.add( hrn );
        icID2srcs.put( hrn.getID(), srcs );
      }

      if( hrn.isOutOfContext() ) {
	assert !hrn.isFlagged();

        Iterator<ReachState> stateItr = hrn.getAlpha().iterator();
        while( stateItr.hasNext() ) {
          ReachState state = stateItr.next();

          Iterator<ReachTuple> rtItr = state.iterator();
          while( rtItr.hasNext() ) {
            ReachTuple rt = rtItr.next();
            assert rt.isOutOfContext();

            Set<HeapRegionNode> srcs = oocID2srcs.get( rt.getHrnID() );
            if( srcs == null ) {
              srcs = new HashSet<HeapRegionNode>();
            }
            srcs.add( hrn );
            oocID2srcs.put( rt.getHrnID(), srcs );
          }
        }
      }
    }

    // calculate boldB for all hrnIDs identified by the above
    // node traversal, propagating from every source
    while( !icID2srcs.isEmpty() || !oocID2srcs.isEmpty() ) {

      Integer             hrnID;
      Set<HeapRegionNode> srcs;
      boolean             inContext;

      if( !icID2srcs.isEmpty() ) {
        Map.Entry me = (Map.Entry) icID2srcs.entrySet().iterator().next();
        hrnID = (Integer)             me.getKey();
        srcs  = (Set<HeapRegionNode>) me.getValue();
        inContext = true;
        icID2srcs.remove( hrnID );

      } else {
        assert !oocID2srcs.isEmpty();

        Map.Entry me = (Map.Entry) oocID2srcs.entrySet().iterator().next();
        hrnID = (Integer)             me.getKey();
        srcs  = (Set<HeapRegionNode>) me.getValue();
        inContext = false;
        oocID2srcs.remove( hrnID );
      }


      Hashtable<RefEdge, ReachSet> boldB_f =
        new Hashtable<RefEdge, ReachSet>();
	
      Set<RefEdge> workSetEdges = new HashSet<RefEdge>();

      Iterator<HeapRegionNode> hrnItr = srcs.iterator();
      while( hrnItr.hasNext() ) {
        HeapRegionNode hrn = hrnItr.next();

        assert workSetEdges.isEmpty();
        
        // initial boldB_f constraints
        Iterator<RefEdge> itrRees = hrn.iteratorToReferencees();
        while( itrRees.hasNext() ) {
          RefEdge edge = itrRees.next();
          
          assert !boldB_f.containsKey( edge );
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
            ReachSet intersection = Canonical.intersection( boldB_f.get( edge ),
                                                            edgePrime.getBeta()
                                                            );
          
            if( prevResult == null || 
                Canonical.unionORpreds( prevResult,
                                        intersection ).size() 
                > prevResult.size() 
                ) {
            
              if( prevResult == null ) {
                boldB_f.put( edgePrime, 
                             Canonical.unionORpreds( edgePrime.getBeta(),
                                                     intersection 
                                                     )
                             );
              } else {
                boldB_f.put( edgePrime, 
                             Canonical.unionORpreds( prevResult,
                                                     intersection 
                                                     )
                             );
              }
              workSetEdges.add( edgePrime );	
            }
          }
        }
      }
      
      if( inContext ) {
        boldBic.put( hrnID, boldB_f );
      } else {
        boldBooc.put( hrnID, boldB_f );
      }
    }


    // use boldB to prune hrnIDs from alpha states that are impossible
    // and propagate the differences backwards across edges
    HashSet<RefEdge> edgesForPropagation = new HashSet<RefEdge>();

    Hashtable<RefEdge, ChangeSet> edgePlannedChanges =
      new Hashtable<RefEdge, ChangeSet>();


    itrHrns = id2hrn.entrySet().iterator();
    while( itrHrns.hasNext() ) {
      Map.Entry      me    = (Map.Entry)      itrHrns.next();
      Integer        hrnID = (Integer)        me.getKey();
      HeapRegionNode hrn   = (HeapRegionNode) me.getValue();
      
      // out-of-context nodes don't participate in the 
      // global sweep, they serve as sources for the pass
      // performed above
      if( hrn.isOutOfContext() ) {
        continue;
      }

      // the inherent states of a region are the exception
      // to removal as the global sweep prunes
      ReachTuple rtException = ReachTuple.factory( hrnID,
                                                   !hrn.isSingleObject(),    
                                                   ReachTuple.ARITY_ONE,
                                                   false // out-of-context
                                                   );

      ChangeSet cts = ChangeSet.factory();

      // mark hrnIDs for removal
      Iterator<ReachState> stateItr = hrn.getAlpha().iterator();
      while( stateItr.hasNext() ) {
	ReachState stateOld = stateItr.next();

	ReachState markedHrnIDs = ReachState.factory();

	Iterator<ReachTuple> rtItr = stateOld.iterator();
	while( rtItr.hasNext() ) {
	  ReachTuple rtOld = rtItr.next();

	  // never remove the inherent hrnID from a flagged region
	  // because it is trivially satisfied
	  if( hrn.isFlagged() ) {	
	    if( rtOld == rtException ) {
	      continue;
	    }
	  }

	  // does boldB allow this hrnID?
	  boolean foundState = false;
	  Iterator<RefEdge> incidentEdgeItr = hrn.iteratorToReferencers();
	  while( incidentEdgeItr.hasNext() ) {
	    RefEdge incidentEdge = incidentEdgeItr.next();

            Hashtable<RefEdge, ReachSet> B; 
            if( rtOld.isOutOfContext() ) {
              B = boldBooc.get( rtOld.getHrnID() ); 
            } else {
              assert id2hrn.containsKey( rtOld.getHrnID() );
              B = boldBic.get( rtOld.getHrnID() ); 
            }

            if( B != null ) {            
              ReachSet boldB_rtOld_incident = B.get( incidentEdge );
              if( boldB_rtOld_incident != null &&
                  boldB_rtOld_incident.containsIgnorePreds( stateOld ) != null
                  ) {
                foundState = true;
              }
            }
	  }
          
	  if( !foundState ) {
	    markedHrnIDs = Canonical.add( markedHrnIDs, rtOld );	  
	  }
	}

	// if there is nothing marked, just move on
	if( markedHrnIDs.isEmpty() ) {
	  hrn.setAlphaNew( Canonical.add( hrn.getAlphaNew(),
                                          stateOld
                                          )
                           );
	  continue;
	}

	// remove all marked hrnIDs and establish a change set that should
	// propagate backwards over edges from this node
	ReachState statePruned = ReachState.factory();
	rtItr = stateOld.iterator();
	while( rtItr.hasNext() ) {
	  ReachTuple rtOld = rtItr.next();

	  if( !markedHrnIDs.containsTuple( rtOld ) ) {
	    statePruned = Canonical.add( statePruned, rtOld );
	  }
	}
	assert !stateOld.equals( statePruned );

	hrn.setAlphaNew( Canonical.add( hrn.getAlphaNew(),
                                        statePruned
                                        )
                         );
	ChangeTuple ct = ChangeTuple.factory( stateOld,
                                              statePruned
                                              );
	cts = Canonical.add( cts, ct );
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
                                   Canonical.union( edgePlannedChanges.get( incidentEdge ),
                                                    cts
                                                    ) 
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

      // as mentioned above, out-of-context nodes only serve
      // as sources of reach states for the sweep, not part
      // of the changes
      if( hrn.isOutOfContext() ) {
        assert hrn.getAlphaNew().equals( rsetEmpty );
      } else {
        hrn.applyAlphaNew();
      }

      Iterator<RefEdge> itrRes = hrn.iteratorToReferencers();
      while( itrRes.hasNext() ) {
	res.add( itrRes.next() );
      }
    }


    // 2nd phase    
    Iterator<RefEdge> edgeItr = res.iterator();
    while( edgeItr.hasNext() ) {
      RefEdge        edge = edgeItr.next();
      HeapRegionNode hrn  = edge.getDst();

      // commit results of last phase
      if( edgesUpdated.contains( edge ) ) {
	edge.applyBetaNew();
      }

      // compute intial condition of 2nd phase
      edge.setBetaNew( Canonical.intersection( edge.getBeta(),
                                               hrn.getAlpha() 
                                               )
                       );
    }
        
    // every edge in the graph is the initial workset
    Set<RefEdge> edgeWorkSet = (Set) res.clone();
    while( !edgeWorkSet.isEmpty() ) {
      RefEdge edgePrime = edgeWorkSet.iterator().next();
      edgeWorkSet.remove( edgePrime );

      RefSrcNode rsn = edgePrime.getSrc();
      if( !(rsn instanceof HeapRegionNode) ) {
	continue;
      }
      HeapRegionNode hrn = (HeapRegionNode) rsn;

      Iterator<RefEdge> itrEdge = hrn.iteratorToReferencers();
      while( itrEdge.hasNext() ) {
	RefEdge edge = itrEdge.next();	    

	ReachSet prevResult = edge.getBetaNew();
	assert prevResult != null;

	ReachSet intersection = 
          Canonical.intersection( edge.getBeta(),
                                  edgePrime.getBetaNew() 
                                  );
		    
	if( Canonical.unionORpreds( prevResult,
                                    intersection
                                    ).size() 
            > prevResult.size() 
            ) {
          
	  edge.setBetaNew( 
                          Canonical.unionORpreds( prevResult,
                                                  intersection 
                                                  )
                           );
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

    mergeNodes     ( rg );
    mergeRefEdges  ( rg );
    mergeAllocSites( rg );
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
	hrnB.setAlpha( Canonical.unionORpreds( hrnB.getAlpha(),
                                        hrnA.getAlpha() 
                                        )
                       );

        hrnB.setPreds( Canonical.join( hrnB.getPreds(),
                                       hrnA.getPreds()
                                       )
                       );
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
                              Canonical.unionORpreds( edgeToMerge.getBeta(),
                                               edgeA.getBeta() 
                                               )
                              );
          edgeToMerge.setPreds(
                               Canonical.join( edgeToMerge.getPreds(),
                                               edgeA.getPreds()
                                               )
                               );
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
	  edgeToMerge.setBeta( Canonical.unionORpreds( edgeToMerge.getBeta(),
                                                edgeA.getBeta()
                                                )
                               );
          edgeToMerge.setPreds( Canonical.join( edgeToMerge.getPreds(),
                                                edgeA.getPreds()
                                                )
                                );
	}
      }
    }
  }

  protected void mergeAllocSites( ReachGraph rg ) {
    allocSites.addAll( rg.allocSites );
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
      if( !hrnA.equalsIncludingAlphaAndPreds( hrnB ) ) {
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
	  if( edgeA.getBeta().equals( edgeB.getBeta() ) &&
              edgeA.equalsPreds( edgeB )
              ) {
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
  


  public void writeGraph( String  graphName,
                          boolean writeLabels,
                          boolean labelSelect,
                          boolean pruneGarbage,
                          boolean hideSubsetReachability,
                          boolean hideEdgeTaints
                          ) throws java.io.IOException {
    writeGraph( graphName,
                writeLabels,
                labelSelect,
                pruneGarbage,
                hideSubsetReachability,
                hideEdgeTaints,
                null );
  }

  public void writeGraph( String       graphName,
                          boolean      writeLabels,
                          boolean      labelSelect,
                          boolean      pruneGarbage,
                          boolean      hideSubsetReachability,
                          boolean      hideEdgeTaints,
                          Set<Integer> callerNodeIDsCopiedToCallee
                          ) throws java.io.IOException {
    
    // remove all non-word characters from the graph name so
    // the filename and identifier in dot don't cause errors
    graphName = graphName.replaceAll( "[\\W]", "" );

    BufferedWriter bw = 
      new BufferedWriter( new FileWriter( graphName+".dot" ) );

    bw.write( "digraph "+graphName+" {\n" );


    // this is an optional step to form the callee-reachable
    // "cut-out" into a DOT cluster for visualization
    if( callerNodeIDsCopiedToCallee != null ) {

      bw.write( "  subgraph cluster0 {\n" );
      bw.write( "    color=blue;\n" );

      Iterator i = id2hrn.entrySet().iterator();
      while( i.hasNext() ) {
        Map.Entry      me  = (Map.Entry)      i.next();
        HeapRegionNode hrn = (HeapRegionNode) me.getValue();      
        
        if( callerNodeIDsCopiedToCallee.contains( hrn.getID() ) ) {
          bw.write( "    "+hrn.toString()+
                    hrn.toStringDOT( hideSubsetReachability )+
                    ";\n" );
          
        }
      }

      bw.write( "  }\n" );
    }


    Set<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

    // then visit every heap region node    
    Iterator i = id2hrn.entrySet().iterator();
    while( i.hasNext() ) {
      Map.Entry      me  = (Map.Entry)      i.next();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();      

      // only visit nodes worth writing out--for instance
      // not every node at an allocation is referenced
      // (think of it as garbage-collected), etc.
      if( !pruneGarbage        ||
          hrn.isOutOfContext()
          ) {

	if( !visited.contains( hrn ) ) {
	  traverseHeapRegionNodes( hrn,
                                   bw,
                                   null,
                                   visited,
                                   hideSubsetReachability,
                                   hideEdgeTaints,
                                   callerNodeIDsCopiedToCallee );
	}
      }
    }

    bw.write( "  graphTitle[label=\""+graphName+"\",shape=box];\n" );
  

    // then visit every label node, useful for debugging
    if( writeLabels ) {
      i = td2vn.entrySet().iterator();
      while( i.hasNext() ) {
        Map.Entry    me = (Map.Entry)    i.next();
        VariableNode vn = (VariableNode) me.getValue();
        
        if( labelSelect ) {
          String labelStr = vn.getTempDescriptorString();
          if( labelStr.startsWith( "___temp" )     ||
              labelStr.startsWith( "___dst" )      ||
              labelStr.startsWith( "___srctmp" )   ||
              labelStr.startsWith( "___neverused" )
              ) {
            continue;
          }
        }
        
        Iterator<RefEdge> heapRegionsItr = vn.iteratorToReferencees();
        while( heapRegionsItr.hasNext() ) {
          RefEdge        edge = heapRegionsItr.next();
          HeapRegionNode hrn  = edge.getDst();
          
          if( !visited.contains( hrn ) ) {
            traverseHeapRegionNodes( hrn,
                                     bw,
                                     null,
                                     visited,
                                     hideSubsetReachability,
                                     hideEdgeTaints,
                                     callerNodeIDsCopiedToCallee );
          }
          
          bw.write( "  "+vn.toString()+
                    " -> "+hrn.toString()+
                    edge.toStringDOT( hideSubsetReachability, "" )+
                    ";\n" );
        }
      }
    }
    
    bw.write( "}\n" );
    bw.close();
  }

  protected void traverseHeapRegionNodes( HeapRegionNode      hrn,
                                          BufferedWriter      bw,
                                          TempDescriptor      td,
                                          Set<HeapRegionNode> visited,
                                          boolean             hideSubsetReachability,
                                          boolean             hideEdgeTaints,
                                          Set<Integer>        callerNodeIDsCopiedToCallee
                                          ) throws java.io.IOException {

    if( visited.contains( hrn ) ) {
      return;
    }
    visited.add( hrn );

    // if we're drawing the callee-view subgraph, only
    // write out the node info if it hasn't already been
    // written
    if( callerNodeIDsCopiedToCallee == null ||
        !callerNodeIDsCopiedToCallee.contains( hrn.getID() ) 
        ) {
      bw.write( "  "+hrn.toString()+
                hrn.toStringDOT( hideSubsetReachability )+
                ";\n" );
    }

    Iterator<RefEdge> childRegionsItr = hrn.iteratorToReferencees();
    while( childRegionsItr.hasNext() ) {
      RefEdge        edge     = childRegionsItr.next();
      HeapRegionNode hrnChild = edge.getDst();

      if( callerNodeIDsCopiedToCallee != null &&
          (edge.getSrc() instanceof HeapRegionNode) ) {
        HeapRegionNode hrnSrc = (HeapRegionNode) edge.getSrc();
        if( callerNodeIDsCopiedToCallee.contains( hrnSrc.getID()        ) &&
            callerNodeIDsCopiedToCallee.contains( edge.getDst().getID() )
            ) {
          bw.write( "  "+hrn.toString()+
                    " -> "+hrnChild.toString()+
                    edge.toStringDOT( hideSubsetReachability, ",color=blue" )+
                    ";\n");
        } else if( !callerNodeIDsCopiedToCallee.contains( hrnSrc.getID()       ) &&
                   callerNodeIDsCopiedToCallee.contains( edge.getDst().getID() )
                   ) {
          bw.write( "  "+hrn.toString()+
                    " -> "+hrnChild.toString()+
                    edge.toStringDOT( hideSubsetReachability, ",color=blue,style=dashed" )+
                    ";\n");
        } else {
          bw.write( "  "+hrn.toString()+
                    " -> "+hrnChild.toString()+
                    edge.toStringDOT( hideSubsetReachability, "" )+
                    ";\n");
        }
      } else {
        bw.write( "  "+hrn.toString()+
                  " -> "+hrnChild.toString()+
                  edge.toStringDOT( hideSubsetReachability, "" )+
                  ";\n");
      }
      
      traverseHeapRegionNodes( hrnChild,
                               bw,
                               td,
                               visited,
                               hideSubsetReachability,
                               hideEdgeTaints,
                               callerNodeIDsCopiedToCallee );
    }
  }  
  
	public Set<HeapRegionNode> findCommonReachableNodes(HeapRegionNode hrn1,
			HeapRegionNode hrn2) {

		Set<HeapRegionNode> reachableNodes1 = new HashSet<HeapRegionNode>();
		Set<HeapRegionNode> reachableNodes2 = new HashSet<HeapRegionNode>();

		Set<HeapRegionNode> todoNodes1 = new HashSet<HeapRegionNode>();
		todoNodes1.add(hrn1);

		Set<HeapRegionNode> todoNodes2 = new HashSet<HeapRegionNode>();
		todoNodes2.add(hrn2);

		// follow links until all reachable nodes have been found
		while (!todoNodes1.isEmpty()) {
			HeapRegionNode hrn = todoNodes1.iterator().next();
			todoNodes1.remove(hrn);
			reachableNodes1.add(hrn);

			Iterator<RefEdge> edgeItr = hrn.iteratorToReferencees();
			while (edgeItr.hasNext()) {
				RefEdge edge = edgeItr.next();

				if (!reachableNodes1.contains(edge.getDst())) {
					todoNodes1.add(edge.getDst());
				}
			}
		}

		while (!todoNodes2.isEmpty()) {
			HeapRegionNode hrn = todoNodes2.iterator().next();
			todoNodes2.remove(hrn);
			reachableNodes2.add(hrn);

			Iterator<RefEdge> edgeItr = hrn.iteratorToReferencees();
			while (edgeItr.hasNext()) {
				RefEdge edge = edgeItr.next();

				if (!reachableNodes2.contains(edge.getDst())) {
					todoNodes2.add(edge.getDst());
				}
			}
		}

		Set<HeapRegionNode> intersection =
                  new HashSet<HeapRegionNode>( reachableNodes1 );

		intersection.retainAll( reachableNodes2 );

		return intersection;
	}
	 
	public Set<HeapRegionNode> mayReachSharedObjects(HeapRegionNode hrn1,
			HeapRegionNode hrn2) {
		assert hrn1 != null;
		assert hrn2 != null;

		// then get the various tokens for these heap regions
		ReachTuple h1 = ReachTuple.factory(hrn1.getID(),
				!hrn1.isSingleObject(), ReachTuple.ARITY_ONE, false);

		int arity;
		if(hrn1.isSingleObject){
			arity=ReachTuple.ARITY_ONE;
		}else{
			arity=ReachTuple.ARITY_ZEROORMORE;
		}
		ReachTuple h1star = ReachTuple.factory(hrn1.getID(), !hrn1
				.isSingleObject(), arity, false);

		ReachTuple h2 = ReachTuple.factory(hrn2.getID(),
				!hrn2.isSingleObject(), ReachTuple.ARITY_ONE, false);

		if(hrn2.isSingleObject){
			arity=ReachTuple.ARITY_ONE;
		}else{
			arity=ReachTuple.ARITY_ZEROORMORE;
		}
		
		ReachTuple h2star = ReachTuple.factory(hrn2.getID(), !hrn2
				.isSingleObject(), arity, false);

		// then get the merged beta of all out-going edges from these heap
		// regions

		ReachSet beta1 = ReachSet.factory();
		Iterator<RefEdge> itrEdge = hrn1.iteratorToReferencees();
		while (itrEdge.hasNext()) {
			RefEdge edge = itrEdge.next();
			beta1 = Canonical.unionORpreds(beta1, edge.getBeta());
		}

		ReachSet beta2 = ReachSet.factory();
		itrEdge = hrn2.iteratorToReferencees();
		while (itrEdge.hasNext()) {
			RefEdge edge = itrEdge.next();
			beta2 = Canonical.unionORpreds(beta2, edge.getBeta());
		}

		boolean aliasDetected = false;

		// only do this one if they are different tokens
		if (h1 != h2 && beta1.containsStateWithBoth(h1, h2)) {
			aliasDetected = true;
		}
//		if (beta1.containsStateWithBoth(h1plus, h2)) {
//			aliasDetected = true;
//		}
		if (beta1.containsStateWithBoth(h1star, h2)) {
			aliasDetected = true;
		}
//		if (beta1.containsStateWithBoth(h1, h2plus)) {
//			aliasDetected = true;
//		}
//		if (beta1.containsStateWithBoth(h1plus, h2plus)) {
//			aliasDetected = true;
//		}
//		if (beta1.containsStateWithBoth(h1star, h2plus)) {
//			aliasDetected = true;
//		}
		if (beta1.containsStateWithBoth(h1, h2star)) {
			aliasDetected = true;
		}
//		if (beta1.containsStateWithBoth(h1plus, h2star)) {
//			aliasDetected = true;
//		}
		if (beta1.containsStateWithBoth(h1star, h2star)) {
			aliasDetected = true;
		}

		if (h1 != h2 && beta2.containsStateWithBoth(h1, h2)) {
			aliasDetected = true;
		}
//		if (beta2.containsStateWithBoth(h1plus, h2)) {
//			aliasDetected = true;
//		}
		if (beta2.containsStateWithBoth(h1star, h2)) {
			aliasDetected = true;
		}
//		if (beta2.containsStateWithBoth(h1, h2plus)) {
//			aliasDetected = true;
//		}
//		if (beta2.containsStateWithBoth(h1plus, h2plus)) {
//			aliasDetected = true;
//		}
//		if (beta2.containsStateWithBoth(h1star, h2plus)) {
//			aliasDetected = true;
//		}
		if (beta2.containsStateWithBoth(h1, h2star)) {
			aliasDetected = true;
		}
//		if (beta2.containsStateWithBoth(h1plus, h2star)) {
//			aliasDetected = true;
//		}
		if (beta2.containsStateWithBoth(h1star, h2star)) {
			aliasDetected = true;
		}

		Set<HeapRegionNode> common = new HashSet<HeapRegionNode>();
		if (aliasDetected) {
			common = findCommonReachableNodes(hrn1, hrn2);
			if (!(DISABLE_STRONG_UPDATES || DISABLE_GLOBAL_SWEEP)) {
				assert !common.isEmpty();
			}
		}

		return common;
	}

	public Set<HeapRegionNode> mayReachSharedObjects(FlatMethod fm,
			Integer paramIndex1, Integer paramIndex2) {

		// get parameter's heap regions
		TempDescriptor paramTemp1 = fm.getParameter(paramIndex1.intValue());
		VariableNode argVar1 = getVariableNodeFromTemp(paramTemp1);
		RefEdge argEdge1 = argVar1.iteratorToReferencees().next();
		HeapRegionNode hrnParam1 = argEdge1.getDst();

		TempDescriptor paramTemp2 = fm.getParameter(paramIndex2.intValue());
		VariableNode argVar2 = getVariableNodeFromTemp(paramTemp2);
		RefEdge argEdge2 = argVar2.iteratorToReferencees().next();
		HeapRegionNode hrnParam2 = argEdge2.getDst();

		Set<HeapRegionNode> common = new HashSet<HeapRegionNode>();
		common.addAll(mayReachSharedObjects(hrnParam1, hrnParam2));

		return common;
	}

	public Set<HeapRegionNode> mayReachSharedObjects(FlatMethod fm,
			Integer paramIndex, AllocSite as) {

		// get parameter's heap regions
		TempDescriptor paramTemp = fm.getParameter(paramIndex.intValue());
		VariableNode argVar = getVariableNodeFromTemp(paramTemp);
		RefEdge argEdge = argVar.iteratorToReferencees().next();
		HeapRegionNode hrnParam = argEdge.getDst();

		// get summary node
		HeapRegionNode hrnSummary=null;
		if(id2hrn.containsKey(as.getSummary())){
			// if summary node doesn't exist, ignore this case
			hrnSummary = id2hrn.get(as.getSummary());
			assert hrnSummary != null;
		}

		Set<HeapRegionNode> common  = new HashSet<HeapRegionNode>();
		if(hrnSummary!=null){
			common.addAll( mayReachSharedObjects(hrnParam, hrnSummary) );
		}

		// check for other nodes
		for (int i = 0; i < as.getAllocationDepth(); ++i) {

			assert id2hrn.containsKey(as.getIthOldest(i));
			HeapRegionNode hrnIthOldest = id2hrn.get(as.getIthOldest(i));
			assert hrnIthOldest != null;

			common.addAll(mayReachSharedObjects(hrnParam, hrnIthOldest));

		}

		return common;
	}

	public Set<HeapRegionNode> mayReachSharedObjects(AllocSite as1,
			AllocSite as2) {

		// get summary node 1's alpha
		Integer idSum1 = as1.getSummary();
		HeapRegionNode hrnSum1=null;
		if(id2hrn.containsKey(idSum1)){
			hrnSum1 = id2hrn.get(idSum1);
		}

		// get summary node 2's alpha
		Integer idSum2 = as2.getSummary();
		HeapRegionNode hrnSum2=null;
		if(id2hrn.containsKey(idSum2)){
			hrnSum2 = id2hrn.get(idSum2);
		}
		
		Set<HeapRegionNode> common = new HashSet<HeapRegionNode>();
		if(hrnSum1!=null && hrnSum2!=null){
			common.addAll(mayReachSharedObjects(hrnSum1, hrnSum2));
		}

		// check sum2 against alloc1 nodes
		if(hrnSum2!=null){
		for (int i = 0; i < as1.getAllocationDepth(); ++i) {
			Integer idI1 = as1.getIthOldest(i);
			assert id2hrn.containsKey(idI1);
			HeapRegionNode hrnI1 = id2hrn.get(idI1);
			assert hrnI1 != null;
			common.addAll(mayReachSharedObjects(hrnI1, hrnSum2));
		}
		}

		// check sum1 against alloc2 nodes
		for (int i = 0; i < as2.getAllocationDepth(); ++i) {
			Integer idI2 = as2.getIthOldest(i);
			assert id2hrn.containsKey(idI2);
			HeapRegionNode hrnI2 = id2hrn.get(idI2);
			assert hrnI2 != null;

			if(hrnSum1!=null){
				common.addAll(mayReachSharedObjects(hrnSum1, hrnI2));
			}

			// while we're at it, do an inner loop for alloc2 vs alloc1 nodes
			for (int j = 0; j < as1.getAllocationDepth(); ++j) {
				Integer idI1 = as1.getIthOldest(j);

				// if these are the same site, don't look for the same token, no
				// alias.
				// different tokens of the same site could alias together though
				if (idI1.equals(idI2)) {
					continue;
				}

				HeapRegionNode hrnI1 = id2hrn.get(idI1);

				common.addAll(mayReachSharedObjects(hrnI1, hrnI2));
			}
		}

		return common;
	}
  
}
