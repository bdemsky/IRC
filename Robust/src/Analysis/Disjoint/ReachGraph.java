package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import Util.UtilAlgorithms;
import java.util.*;
import java.io.*;

public class ReachGraph {
		   
  protected static final TempDescriptor tdReturn    = new TempDescriptor( "_Return___" );
		   
  /*
  // some frequently used reachability constants
  protected static final ReachState rstateEmpty        = new ReachTupleSet().makeCanonical();
  protected static final ReachSet   rsetEmpty          = new ReachSet().makeCanonical();
  protected static final ReachSet   rsetWithEmptyState = new ReachSet( rstateEmpty ).makeCanonical();

  public Hashtable<Integer,        HeapRegionNode> id2hrn;
  public Hashtable<TempDescriptor, VariableNode  > td2vn;

  public HashSet<AllocSite> allocSites;
  */

  // use to disable improvements for comparison
  protected static final boolean DISABLE_STRONG_UPDATES = false;
  protected static final boolean DISABLE_GLOBAL_SWEEP   = false;

  protected static int      allocationDepth   = -1;
  protected static TypeUtil typeUtil          = null;
  protected static boolean  debugCallMap      = false;
  protected static int      debugCallMapCount = 0;
  protected static String   debugCallee       = null;
  protected static String   debugCaller       = null;



  public ReachGraph() {
    /*
    id2hrn = new Hashtable<Integer,        HeapRegionNode>();
    td2vn  = new Hashtable<TempDescriptor, VariableNode  >();

    allocSites = new HashSet<AllocSite>();
    */
  }

  /*
  // temp descriptors are globally unique and maps to
  // exactly one variable node, easy
  protected VariableNode getVariableNodeFromTemp( TempDescriptor td ) {
    assert td != null;

    if( !td2vn.containsKey( td ) ) {
      td2vn.put( td, new VariableNode( td ) );
    }

    return td2vn.get( td );
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
			     TypeDescriptor type,
			     AllocSite allocSite,
			     ReachSet alpha,
			     String description,
			     String globalIdentifier ) {

    boolean markForAnalysis = isFlagged;

    TypeDescriptor typeToUse = null;
    if( allocSite != null ) {
      typeToUse = allocSite.getType();
    } else {
      typeToUse = type;
    }

    if( allocSite != null && allocSite.getDisjointAnalysisId() != null ) {
      markForAnalysis = true;
    }

    if( id == null ) {
      id = DisjointAnalysis.generateUniqueHeapRegionNodeID();
    }

    if( alpha == null ) {
      if( markForAnalysis ) {
	alpha = new ReachSet(
			     new ReachTuple( id,
					     !isSingleObject,
					     ReachTuple.ARITY_ONE
					     ).makeCanonical()
			     ).makeCanonical();
      } else {
	alpha = new ReachSet(
			     new ReachState().makeCanonical()
			     ).makeCanonical();
      }
    }
    
    HeapRegionNode hrn = new HeapRegionNode( id,
					     isSingleObject,
					     markForAnalysis,
					     isNewSummary,
					     typeToUse,
					     allocSite,
					     alpha,
					     description,
					     globalIdentifier );
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
  protected void addRefEdge(RefSrcNode referencer,
                                  HeapRegionNode referencee,
                                  RefEdge edge) {
    assert referencer != null;
    assert referencee != null;
    assert edge       != null;
    assert edge.getSrc() == referencer;
    assert edge.getDst() == referencee;

    referencer.addReferencee(edge);
    referencee.addReferencer(edge);
  }

  protected void removeRefEdge(RefEdge e) {
    removeRefEdge(e.getSrc(),
			e.getDst(),
			e.getType(),
			e.getField() );
  }

  protected void removeRefEdge(RefSrcNode referencer,
                                     HeapRegionNode referencee,
                                     TypeDescriptor type,
				     String field) {
    assert referencer != null;
    assert referencee != null;
    
    RefEdge edge = referencer.getReferenceTo(referencee,
                                                   type,
						   field);
    assert edge != null;
    assert edge == referencee.getReferenceFrom(referencer,
                                               type,
					       field);
       
//    int oldTaint=edge.getTaintIdentifier();
//    if(referencer instanceof HeapRegionNode){
//    	depropagateTaintIdentifier((HeapRegionNode)referencer,oldTaint,new HashSet<HeapRegionNode>());
//    }

    referencer.removeReferencee(edge);
    referencee.removeReferencer(edge);
  }

  protected void clearRefEdgesFrom(RefSrcNode referencer,
                                         TypeDescriptor type,
					 String field,
                                         boolean removeAll) {
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
	
	removeRefEdge(referencer,
	                    referencee,
	                    edge.getType(),
			    edge.getField() );
      }
    }
  }

  protected void clearRefEdgesTo(HeapRegionNode referencee,
				       TypeDescriptor type,
				       String field,
                                       boolean removeAll) {
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

	removeRefEdge(referencer,
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
  //  The destination in an assignment statement is
  //  going to have new references.  The method of
  //  determining the references depends on the type
  //  of the FlatNode assignment and the predicates
  //  of the nodes and edges involved.
  //
  ////////////////////////////////////////////////////

  public void nullifyDeadVars( Set<TempDescriptor> liveIn ) {

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
      RefEdge  edgeY      = itrYhrn.next();
      HeapRegionNode referencee = edgeY.getDst();
      RefEdge  edgeNew    = edgeY.copy();

      if( !isSuperiorType( x.getType(), edgeY.getType() ) ) {
	impossibleEdges.add( edgeY );
	continue;
      }

      edgeNew.setSrc( lnX );
      
      edgeNew.setType( mostSpecificType( y.getType(),
					 tdCast, 
					 edgeY.getType(), 
					 referencee.getType() 
					 ) 
		       );

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
      RefEdge   edgeY = itrYhrn.next();
      HeapRegionNode  hrnY  = edgeY.getDst();
      ReachSet betaY = edgeY.getBeta();

      Iterator<RefEdge> itrHrnFhrn = hrnY.iteratorToReferencees();
      while( itrHrnFhrn.hasNext() ) {
	RefEdge   edgeHrn = itrHrnFhrn.next();
	HeapRegionNode  hrnHrn  = edgeHrn.getDst();
	ReachSet betaHrn = edgeHrn.getBeta();

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
	
	int newTaintIdentifier=getTaintIdentifierFromHRN(hrnHrn);
	edgeNew.setTaintIdentifier(newTaintIdentifier);
	
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
    HashSet<RefEdge>  edgesWithNewBeta  = new HashSet<RefEdge>();

    // note it is possible that the types of temps in the
    // flat node to analyze will reveal that some typed
    // edges in the reachability graph are impossible
    Set<RefEdge> impossibleEdges = new HashSet<RefEdge>();

    // first look for possible strong updates and remove those edges
    boolean strongUpdate = false;

    Iterator<RefEdge> itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      RefEdge edgeX = itrXhrn.next();
      HeapRegionNode hrnX = edgeX.getDst();

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
      RefEdge   edgeX = itrXhrn.next();
      HeapRegionNode  hrnX  = edgeX.getDst();
      ReachSet betaX = edgeX.getBeta();
      ReachSet R     = hrnX.getAlpha().intersection( edgeX.getBeta() );

      Iterator<RefEdge> itrYhrn = lnY.iteratorToReferencees();
      while( itrYhrn.hasNext() ) {
	RefEdge   edgeY = itrYhrn.next();
	HeapRegionNode  hrnY  = edgeY.getDst();
	ReachSet O     = edgeY.getBeta();

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
      RefEdge edgeX = itrXhrn.next();
      HeapRegionNode hrnX = edgeX.getDst();
      
      Iterator<RefEdge> itrYhrn = lnY.iteratorToReferencees();
      while( itrYhrn.hasNext() ) {
	RefEdge edgeY = itrYhrn.next();
	HeapRegionNode hrnY = edgeY.getDst();

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

	  if((!hrnX.isParameter() && hrnY.isParameter()) || ( hrnX.isParameter() && hrnY.isParameter())){
	    int newTaintIdentifier=getTaintIdentifierFromHRN(hrnY);
	    edgeExisting.unionTaintIdentifier(newTaintIdentifier);
	  }
	  // a new edge here cannot be reflexive, so existing will
	  // always be also not reflexive anymore
	  edgeExisting.setIsInitialParam( false );
	} else {
		
	  if((!hrnX.isParameter() && hrnY.isParameter()) || ( hrnX.isParameter() && hrnY.isParameter())){
	    int newTaintIdentifier=getTaintIdentifierFromHRN(hrnY);
	    edgeNew.setTaintIdentifier(newTaintIdentifier);
	  }
	  //currently, taint isn't propagated through the chain of refrences
	  //propagateTaintIdentifier(hrnX,newTaintIdentifier,new HashSet<HeapRegionNode>());
	  
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


  // the parameter model is to use a single-object heap region
  // for the primary parameter, and a multiple-object heap
  // region for the secondary objects reachable through the
  // primary object, if necessary
  public void assignTempEqualToParamAlloc( TempDescriptor td,
					   boolean isTask,
					   Integer paramIndex, FlatMethod fm ) {
    assert td != null;
    
    TypeDescriptor typeParam = td.getType();
    assert typeParam != null;

    // either the parameter is an array or a class to be in this method
    assert typeParam.isArray() || typeParam.isClass();

    // discover some info from the param type and use it below
    // to get parameter model as precise as we can
    boolean createSecondaryRegion = false;
    Set<FieldDescriptor> primary2primaryFields   = new HashSet<FieldDescriptor>();
    Set<FieldDescriptor> primary2secondaryFields = new HashSet<FieldDescriptor>();

    // there might be an element reference for array types
    if( typeParam.isArray() ) {
      // only bother with this if the dereferenced type can
      // affect reachability
      TypeDescriptor typeDeref = typeParam.dereference();
      if( !typeDeref.isImmutable() || typeDeref.isArray() ) {
	primary2secondaryFields.add( 
	  DisjointAnalysis.getArrayField( typeDeref )
				   );
	createSecondaryRegion = true;

	// also handle a special case where an array of objects
	// can point back to the array, which is an object!
	if( typeParam.toPrettyString().equals( "Object[]" ) &&
	    typeDeref.toPrettyString().equals( "Object" ) ) {

	  primary2primaryFields.add( 
	    DisjointAnalysis.getArrayField( typeDeref )
				   );
	}
      }
    }

    // there might be member references for class types
    if( typeParam.isClass() ) {
      ClassDescriptor cd = typeParam.getClassDesc();
      while( cd != null ) {

	Iterator fieldItr = cd.getFields();
	while( fieldItr.hasNext() ) {
	  
	  FieldDescriptor fd = (FieldDescriptor) fieldItr.next();
	  TypeDescriptor typeField = fd.getType();
	  assert typeField != null;	
	  
	  if( !typeField.isImmutable() || typeField.isArray() ) {
	    primary2secondaryFields.add( fd );
	    createSecondaryRegion = true;
	  }
	  
	  if( typeUtil.isSuperorType( typeField, typeParam ) ) {
	    primary2primaryFields.add( fd );
	  }
	}

	cd = cd.getSuperDesc();
      }
    }
    

    // now build everything we need
    VariableNode lnParam = getVariableNodeFromTemp( td );
    HeapRegionNode hrnPrimary = createNewHeapRegionNode( null,       // id or null to generate a new one 
							 true,	     // single object?			 	
							 false,      // summary?			 
							 false,      // flagged?			 
							 true,       // is a parameter?			 
							 typeParam,  // type				 
							 null,       // allocation site			 
							 null,       // reachability set                 
							 "param"+paramIndex+" obj",
							 generateUniqueIdentifier(fm,paramIndex,"P"));

    parameterTemps.add( td );
    parameterLabels.add( lnParam );


    // this is a non-program-accessible label that picks up beta
    // info to be used for fixing a caller of this method
    TempDescriptor tdParamQ = new TempDescriptor( td+qString );
    paramIndex2tdQ.put( paramIndex, tdParamQ );    
    VariableNode lnParamQ = getVariableNodeFromTemp( tdParamQ );

    outOfScopeTemps.add( tdParamQ );
    outOfScopeLabels.add( lnParamQ );

    // keep track of heap regions that were created for
    // parameter labels, the index of the parameter they
    // are for is important when resolving method calls
    Integer newPrimaryID = hrnPrimary.getID();
    assert !idPrimary2paramIndexSet.containsKey( newPrimaryID );
    Set<Integer> s = new HashSet<Integer>();
    s.add( paramIndex );
    idPrimary2paramIndexSet.put( newPrimaryID, s );
    paramIndex2idPrimary.put( paramIndex, newPrimaryID );
    
    ReachTuple ttPrimary = new ReachTuple( newPrimaryID,
					   false, // multi-object
					   ReachTuple.ARITY_ONE ).makeCanonical();    

        
    HeapRegionNode hrnSecondary   = null;
    Integer        newSecondaryID = null;
    ReachTuple     ttSecondary    = null;    
    TempDescriptor tdParamR       = null;
    VariableNode      lnParamR       = null;
 
    if( createSecondaryRegion ) {
      tdParamR = new TempDescriptor( td+rString );
      paramIndex2tdR.put( paramIndex, tdParamR );    
      lnParamR = getVariableNodeFromTemp( tdParamR );

      outOfScopeTemps.add( tdParamR );
      outOfScopeLabels.add( lnParamR );

      hrnSecondary = createNewHeapRegionNode( null,  // id or null to generate a new one  
					      false, // single object?			 
					      false, // summary?			 
					      false, // flagged?			 
					      true,  // is a parameter?			 
					      null,  // type				 
					      null,  // allocation site			 
					      null,  // reachability set                 
					      "param"+paramIndex+" reachable", 
					      generateUniqueIdentifier(fm,paramIndex,"S"));

      newSecondaryID = hrnSecondary.getID();
      assert !idSecondary2paramIndexSet.containsKey( newSecondaryID );
      Set<Integer> s2 = new HashSet<Integer>();
      s2.add( paramIndex );
      idSecondary2paramIndexSet.put( newSecondaryID, s2 );
      paramIndex2idSecondary.put( paramIndex, newSecondaryID );
            
      
      ttSecondary = new ReachTuple( newSecondaryID,
				    true, // multi-object
				    ReachTuple.ARITY_ONE ).makeCanonical();      
    }

    // use a beta that has everything and put it all over the
    // parameter model, then use a global sweep later to fix
    // it up, since parameters can have different shapes
    ReachState tts0 = new ReachTupleSet( ttPrimary ).makeCanonical();
    ReachSet betaSoup;
    if( createSecondaryRegion ) {
      ReachState tts1 = new ReachTupleSet( ttSecondary ).makeCanonical();
      ReachState tts2 = new ReachTupleSet( ttPrimary   ).makeCanonical().union( ttSecondary );   
      betaSoup = ReachSet.factory( tts0 ).union( tts1 ).union( tts2 );
    } else {
      betaSoup = ReachSet.factory( tts0 );
    }

    RefEdge edgeFromLabel =
      new RefEdge( lnParam,            // src
			 hrnPrimary,         // dst
			 typeParam,          // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    edgeFromLabel.tainedBy(paramIndex);
    addRefEdge( lnParam, hrnPrimary, edgeFromLabel );

    RefEdge edgeFromLabelQ =
      new RefEdge( lnParamQ,           // src
			 hrnPrimary,         // dst
			 null,               // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    edgeFromLabelQ.tainedBy(paramIndex);
    addRefEdge( lnParamQ, hrnPrimary, edgeFromLabelQ );
    
    RefEdge edgeSecondaryReflexive;
    if( createSecondaryRegion ) {
      edgeSecondaryReflexive =
	new RefEdge( hrnSecondary,    // src
			   hrnSecondary,    // dst
			   null,            // match all types
			   null,            // match all fields
			   true,            // special param initial
			   betaSoup );      // reachability
      addRefEdge( hrnSecondary, hrnSecondary, edgeSecondaryReflexive );

      RefEdge edgeSecondary2Primary =
	new RefEdge( hrnSecondary,    // src
			   hrnPrimary,      // dst
			   null,            // match all types
			   null,            // match all fields
			   true,            // special param initial
			   betaSoup );      // reachability
      addRefEdge( hrnSecondary, hrnPrimary, edgeSecondary2Primary );

      RefEdge edgeFromLabelR =
	new RefEdge( lnParamR,           // src
			   hrnSecondary,       // dst
			   null,               // type
			   null,               // field
			   false,              // special param initial (not needed on label->node)
			   betaSoup );         // reachability
      edgeFromLabelR.tainedBy(paramIndex);
      addRefEdge( lnParamR, hrnSecondary, edgeFromLabelR );
    }
    
    Iterator<FieldDescriptor> fieldItr = primary2primaryFields.iterator();
    while( fieldItr.hasNext() ) {
      FieldDescriptor fd = fieldItr.next();

      RefEdge edgePrimaryReflexive =
	new RefEdge( hrnPrimary,     // src
			   hrnPrimary,     // dst
			   fd.getType(),   // type
			   fd.getSymbol(), // field
			   true,           // special param initial
			   betaSoup );     // reachability
      addRefEdge( hrnPrimary, hrnPrimary, edgePrimaryReflexive );
    }

    fieldItr = primary2secondaryFields.iterator();
    while( fieldItr.hasNext() ) {
      FieldDescriptor fd = fieldItr.next();

      RefEdge edgePrimary2Secondary =
	new RefEdge( hrnPrimary,     // src
			   hrnSecondary,   // dst
			   fd.getType(),   // type
			   fd.getSymbol(), // field
			   true,           // special param initial
			   betaSoup );     // reachability      
      addRefEdge( hrnPrimary, hrnSecondary, edgePrimary2Secondary );
    }
  }


  public void makeAliasedParamHeapRegionNode(FlatMethod fm) {

    VariableNode lnBlob = getVariableNodeFromTemp( tdAliasBlob );

    outOfScopeTemps.add( tdAliasBlob );
    outOfScopeLabels.add( lnBlob );
    
    HeapRegionNode hrn = createNewHeapRegionNode( null,  // id or null to generate a new one 
						  false, // single object?			 
						  false, // summary?			 
						  false, // flagged?			 
						  true,	 // is a parameter?			 
						  null,	 // type				 
						  null,	 // allocation site			 
						  null,	 // reachability set                 
						  "aliasedParams", 
						  generateUniqueIdentifier(fm,0,"A"));

    
    ReachSet beta = new ReachSet( new ReachTuple( hrn.getID(),
								true,
								ReachTuple.ARITY_ONE).makeCanonical()
						).makeCanonical();
        
    RefEdge edgeFromLabel =
      new RefEdge( lnBlob, hrn, null, null, false, beta );

    RefEdge edgeReflexive =
      new RefEdge( hrn,    hrn, null, null, true,  beta );
    
    addRefEdge( lnBlob, hrn, edgeFromLabel );
    addRefEdge( hrn,    hrn, edgeReflexive );
  }


  public void assignTempEqualToAliasedParam( TempDescriptor tdParam,
					     Integer        paramIndex, FlatMethod fm ) {
    assert tdParam != null;

    TypeDescriptor typeParam = tdParam.getType();
    assert typeParam != null;

    VariableNode lnParam   = getVariableNodeFromTemp( tdParam );    
    VariableNode lnAliased = getVariableNodeFromTemp( tdAliasBlob );

    parameterTemps.add( tdParam );
    parameterLabels.add( lnParam );

    // this is a non-program-accessible label that picks up beta
    // info to be used for fixing a caller of this method
    TempDescriptor tdParamQ = new TempDescriptor( tdParam+qString );
    TempDescriptor tdParamR = new TempDescriptor( tdParam+rString );

    paramIndex2tdQ.put( paramIndex, tdParamQ );
    paramIndex2tdR.put( paramIndex, tdParamR );

    VariableNode lnParamQ = getVariableNodeFromTemp( tdParamQ );
    VariableNode lnParamR = getVariableNodeFromTemp( tdParamR );

    outOfScopeTemps.add( tdParamR );
    outOfScopeLabels.add( lnParamR );
    outOfScopeTemps.add( tdParamQ );
    outOfScopeLabels.add( lnParamQ );

    // the lnAliased should always only reference one node, and that
    // heap region node is the aliased param blob
    assert lnAliased.getNumReferencees() == 1;
    HeapRegionNode hrnAliasBlob = lnAliased.iteratorToReferencees().next().getDst();
    Integer idAliased = hrnAliasBlob.getID();

    
    ReachTuple ttAliased = new ReachTuple( idAliased,
					   true, // multi-object
					   ReachTuple.ARITY_ONE ).makeCanonical();         


    HeapRegionNode hrnPrimary = createNewHeapRegionNode( null,      // id or null to generate a new one 
							 true,	    // single object?			 
							 false,	    // summary?			 
							 false,     // flagged?			  
							 true,	    // is a parameter?			 
							 typeParam, // type				 
							 null,	    // allocation site			 
							 null,	    // reachability set                 
							 "param"+paramIndex+" obj",
							 generateUniqueIdentifier(fm, paramIndex.intValue(), "P"));

    Integer newPrimaryID = hrnPrimary.getID();
    assert !idPrimary2paramIndexSet.containsKey( newPrimaryID );
    Set<Integer> s1 = new HashSet<Integer>();
    s1.add( paramIndex );
    idPrimary2paramIndexSet.put( newPrimaryID, s1 );
    paramIndex2idPrimary.put( paramIndex, newPrimaryID );

    Set<Integer> s2 = idSecondary2paramIndexSet.get( idAliased );
    if( s2 == null ) {
      s2 = new HashSet<Integer>();
    }
    s2.add( paramIndex );
    idSecondary2paramIndexSet.put( idAliased, s2 );
    paramIndex2idSecondary.put( paramIndex, idAliased );
    

    
    ReachTuple ttPrimary = new ReachTuple( newPrimaryID,
					   false, // multi-object
					   ReachTuple.ARITY_ONE ).makeCanonical();   

    
    ReachState tts0 = new ReachTupleSet( ttPrimary ).makeCanonical();
    ReachState tts1 = new ReachTupleSet( ttAliased ).makeCanonical();
    ReachState tts2 = new ReachTupleSet( ttPrimary ).makeCanonical().union( ttAliased );   
    ReachSet betaSoup = ReachSet.factory( tts0 ).union( tts1 ).union( tts2 );


    RefEdge edgeFromLabel =
      new RefEdge( lnParam,            // src
			 hrnPrimary,         // dst
			 typeParam,          // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    edgeFromLabel.tainedBy(paramIndex);
    addRefEdge( lnParam, hrnPrimary, edgeFromLabel );

    RefEdge edgeFromLabelQ =
      new RefEdge( lnParamQ,           // src
			 hrnPrimary,         // dst
			 null,               // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    edgeFromLabelQ.tainedBy(paramIndex);
    addRefEdge( lnParamQ, hrnPrimary, edgeFromLabelQ );
    
    RefEdge edgeAliased2Primary =
      new RefEdge( hrnAliasBlob,    // src
			 hrnPrimary,      // dst
			 null,            // match all types
			 null,            // match all fields
			 true,            // special param initial
			 betaSoup );      // reachability
    addRefEdge( hrnAliasBlob, hrnPrimary, edgeAliased2Primary );    

    RefEdge edgeFromLabelR =
      new RefEdge( lnParamR,           // src
			 hrnAliasBlob,       // dst
			 null,               // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    edgeFromLabelR.tainedBy(paramIndex);
    addRefEdge( lnParamR, hrnAliasBlob, edgeFromLabelR );
  }


  public void addParam2ParamAliasEdges( FlatMethod fm,
					Set<Integer> aliasedParamIndices ) {

    VariableNode lnAliased = getVariableNodeFromTemp( tdAliasBlob );

    // the lnAliased should always only reference one node, and that
    // heap region node is the aliased param blob
    assert lnAliased.getNumReferencees() == 1;
    HeapRegionNode hrnAliasBlob = lnAliased.iteratorToReferencees().next().getDst();
    Integer idAliased = hrnAliasBlob.getID();

   
    ReachTuple ttAliased = new ReachTuple( idAliased,
					   true, // multi-object
					   ReachTuple.ARITY_ONE ).makeCanonical();


    Iterator<Integer> apItrI = aliasedParamIndices.iterator();
    while( apItrI.hasNext() ) {
      Integer i = apItrI.next();
      TempDescriptor tdParamI = fm.getParameter( i );
      TypeDescriptor typeI    = tdParamI.getType();
      VariableNode      lnParamI = getVariableNodeFromTemp( tdParamI );

      Integer        idPrimaryI =  paramIndex2idPrimary.get( i );
      assert         idPrimaryI != null;
      HeapRegionNode primaryI   =  id2hrn.get( idPrimaryI );
      assert         primaryI   != null;           
      
      ReachTuple ttPrimaryI = new ReachTuple( idPrimaryI,
					      false, // multi-object
					      ReachTuple.ARITY_ONE ).makeCanonical();
      
      ReachState ttsI  = new ReachTupleSet( ttPrimaryI ).makeCanonical();
      ReachState ttsA  = new ReachTupleSet( ttAliased  ).makeCanonical();
      ReachState ttsIA = new ReachTupleSet( ttPrimaryI ).makeCanonical().union( ttAliased );   
      ReachSet betaSoup = ReachSet.factory( ttsI ).union( ttsA ).union( ttsIA );


      // calculate whether fields of this aliased parameter are able to
      // reference its own primary object, the blob, or other parameter's
      // primary objects!
      Set<FieldDescriptor> primary2primaryFields   = new HashSet<FieldDescriptor>();
      Set<FieldDescriptor> primary2secondaryFields = new HashSet<FieldDescriptor>();
    
      // there might be an element reference for array types
      if( typeI.isArray() ) {
	// only bother with this if the dereferenced type can
	// affect reachability
	TypeDescriptor typeDeref = typeI.dereference();
	


	/////////////////////////////////////////////////////////////
	// NOTE! For the KMeans benchmark a parameter of type float
	// array, which has an immutable dereferenced type, is causing
	// this assertion to fail.  I'm commenting it out for now which
	// is safe, because it allows aliasing where no aliasing can occur,
	// so it can only get a worse-but-not-wrong answer.  FIX!
	/////////////////////////////////////////////////////////////
	// for this parameter to be aliased the following must be true
	//assert !typeDeref.isImmutable() || typeDeref.isArray();
	
	

	primary2secondaryFields.add( 
	  DisjointAnalysis.getArrayField( typeDeref )
				   );

	// also handle a special case where an array of objects
	// can point back to the array, which is an object!
	if( typeI    .toPrettyString().equals( "Object[]" ) &&
	    typeDeref.toPrettyString().equals( "Object" ) ) {
	  primary2primaryFields.add( 
	    DisjointAnalysis.getArrayField( typeDeref )
				   );
	}
      }
      
      // there might be member references for class types
      if( typeI.isClass() ) {
	ClassDescriptor cd = typeI.getClassDesc();
	while( cd != null ) {
	  
	  Iterator fieldItr = cd.getFields();
	  while( fieldItr.hasNext() ) {
	    
	    FieldDescriptor fd = (FieldDescriptor) fieldItr.next();
	    TypeDescriptor typeField = fd.getType();
	    assert typeField != null;	
	    
	    if( !typeField.isImmutable() || typeField.isArray() ) {
	      primary2secondaryFields.add( fd );
	    }
	    
	    if( typeUtil.isSuperorType( typeField, typeI ) ) {
	      primary2primaryFields.add( fd );
	    }	
	  }
	  
	  cd = cd.getSuperDesc();
	}
      }

      Iterator<FieldDescriptor> fieldItr = primary2primaryFields.iterator();
      while( fieldItr.hasNext() ) {
	FieldDescriptor fd = fieldItr.next();
	
	RefEdge edgePrimaryReflexive =
	  new RefEdge( primaryI,       // src
			     primaryI,       // dst
			     fd.getType(),   // type
			     fd.getSymbol(), // field
			     true,           // special param initial
			     betaSoup );     // reachability      
	addRefEdge( primaryI, primaryI, edgePrimaryReflexive );
      }

      fieldItr = primary2secondaryFields.iterator();
      while( fieldItr.hasNext() ) {
	FieldDescriptor fd = fieldItr.next();
	TypeDescriptor typeField = fd.getType();
	assert typeField != null;	
	
	RefEdge edgePrimary2Secondary =
	  new RefEdge( primaryI,       // src
			     hrnAliasBlob,   // dst
			     fd.getType(),   // type
			     fd.getSymbol(), // field
			     true,           // special param initial
			     betaSoup );     // reachability
	addRefEdge( primaryI, hrnAliasBlob, edgePrimary2Secondary );

	// ask whether these fields might match any of the other aliased
	// parameters and make those edges too
	Iterator<Integer> apItrJ = aliasedParamIndices.iterator();
	while( apItrJ.hasNext() ) {
	  Integer        j        = apItrJ.next();
	  TempDescriptor tdParamJ = fm.getParameter( j );
	  TypeDescriptor typeJ    = tdParamJ.getType();

	  if( !i.equals( j ) && typeUtil.isSuperorType( typeField, typeJ ) ) {

	    Integer idPrimaryJ = paramIndex2idPrimary.get( j );
	    assert idPrimaryJ != null;
	    HeapRegionNode primaryJ = id2hrn.get( idPrimaryJ );
	    assert primaryJ != null;	    

	    ReachTuple ttPrimaryJ = new ReachTuple( idPrimaryJ,
						    false, // multi-object
						    ReachTuple.ARITY_ONE ).makeCanonical();

	    ReachState ttsJ   = new ReachTupleSet( ttPrimaryJ ).makeCanonical();
	    ReachState ttsIJ  = ttsI.union( ttsJ );
	    ReachState ttsAJ  = ttsA.union( ttsJ );
	    ReachState ttsIAJ = ttsIA.union( ttsJ );
	    ReachSet betaSoupWJ = ReachSet.factory( ttsJ ).union( ttsIJ ).union( ttsAJ ).union( ttsIAJ );

	    RefEdge edgePrimaryI2PrimaryJ =
	      new RefEdge( primaryI,       // src
				 primaryJ,       // dst
				 fd.getType(),   // type
				 fd.getSymbol(), // field
				 true,           // special param initial
				 betaSoupWJ );   // reachability
	    addRefEdge( primaryI, primaryJ, edgePrimaryI2PrimaryJ );
	  }
	}	
      }    
      
      
      // look at whether aliased parameters i and j can
      // possibly be the same primary object, add edges
      Iterator<Integer> apItrJ = aliasedParamIndices.iterator();
      while( apItrJ.hasNext() ) {
	Integer        j        = apItrJ.next();
	TempDescriptor tdParamJ = fm.getParameter( j );
	TypeDescriptor typeJ    = tdParamJ.getType();
	VariableNode      lnParamJ = getVariableNodeFromTemp( tdParamJ );

	if( !i.equals( j ) && typeUtil.isSuperorType( typeI, typeJ ) ) {
	  	  	  
	  Integer idPrimaryJ = paramIndex2idPrimary.get( j );
	  assert idPrimaryJ != null;
	  HeapRegionNode primaryJ = id2hrn.get( idPrimaryJ );
	  assert primaryJ != null;
	  
	  RefEdge lnJ2PrimaryJ = lnParamJ.getReferenceTo( primaryJ,
								tdParamJ.getType(),	
								null );
	  assert lnJ2PrimaryJ != null;
	  
	  RefEdge lnI2PrimaryJ = lnJ2PrimaryJ.copy();
	  lnI2PrimaryJ.setSrc( lnParamI );
	  lnI2PrimaryJ.setType( tdParamI.getType() );
	  lnI2PrimaryJ.tainedBy(new Integer(j));
	  addRefEdge( lnParamI, primaryJ, lnI2PrimaryJ );
	}
      }
    }
  }

  public void prepareParamTokenMaps( FlatMethod fm ) {

    // always add the bogus mappings that are used to
    // rewrite "with respect to no parameter"
    paramTokenPrimary2paramIndex.put( bogusToken, bogusIndex );
    paramIndex2paramTokenPrimary.put( bogusIndex, bogusToken );

    paramTokenSecondary2paramIndex.put( bogusToken, bogusIndex );
    paramIndex2paramTokenSecondary.put( bogusIndex, bogusToken );
    paramTokenSecondaryPlus2paramIndex.put( bogusTokenPlus, bogusIndex );
    paramIndex2paramTokenSecondaryPlus.put( bogusIndex, bogusTokenPlus );
    paramTokenSecondaryStar2paramIndex.put( bogusTokenStar, bogusIndex );
    paramIndex2paramTokenSecondaryStar.put( bogusIndex, bogusTokenStar );

    for( int i = 0; i < fm.numParameters(); ++i ) {
      Integer paramIndex = new Integer( i );

      // immutable objects have no primary regions
      if( paramIndex2idPrimary.containsKey( paramIndex ) ) {
	Integer idPrimary = paramIndex2idPrimary.get( paramIndex );
	
	assert id2hrn.containsKey( idPrimary );
	HeapRegionNode hrnPrimary = id2hrn.get( idPrimary );
	
	ReachTuple p_i = new ReachTuple( hrnPrimary.getID(),
					 false, // multiple-object?
					 ReachTuple.ARITY_ONE ).makeCanonical();
	paramTokenPrimary2paramIndex.put( p_i, paramIndex );
	paramIndex2paramTokenPrimary.put( paramIndex, p_i );	
      }	
	
      // any parameter object, by type, may have no secondary region
      if( paramIndex2idSecondary.containsKey( paramIndex ) ) {
	Integer idSecondary = paramIndex2idSecondary.get( paramIndex );
	
	assert id2hrn.containsKey( idSecondary );
	HeapRegionNode hrnSecondary = id2hrn.get( idSecondary );
	
	ReachTuple s_i = new ReachTuple( hrnSecondary.getID(),
					 true, // multiple-object?
					 ReachTuple.ARITY_ONE ).makeCanonical();
	paramTokenSecondary2paramIndex.put( s_i, paramIndex );
	paramIndex2paramTokenSecondary.put( paramIndex, s_i );
	
	ReachTuple s_i_plus = new ReachTuple( hrnSecondary.getID(),
					      true, // multiple-object?
					      ReachTuple.ARITY_ONEORMORE ).makeCanonical();
	paramTokenSecondaryPlus2paramIndex.put( s_i_plus, paramIndex );
	paramIndex2paramTokenSecondaryPlus.put( paramIndex, s_i_plus );
	
	ReachTuple s_i_star = new ReachTuple( hrnSecondary.getID(),
					      true, // multiple-object?
					      ReachTuple.ARITY_ZEROORMORE ).makeCanonical();
	paramTokenSecondaryStar2paramIndex.put( s_i_star, paramIndex );
	paramIndex2paramTokenSecondaryStar.put( paramIndex, s_i_star );
      }
    }
  }



  public void assignReturnEqualToTemp(TempDescriptor x) {

    VariableNode lnR = getVariableNodeFromTemp(tdReturn);
    VariableNode lnX = getVariableNodeFromTemp(x);

    clearRefEdgesFrom(lnR, null, null, true);

    Iterator<RefEdge> itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      RefEdge edgeX       = itrXhrn.next();
      HeapRegionNode referencee = edgeX.getDst();
      RefEdge edgeNew    = edgeX.copy();
      edgeNew.setSrc(lnR);

      addRefEdge(lnR, referencee, edgeNew);
    }
  }


  public void assignTempEqualToNewAlloc(TempDescriptor x,
                                        AllocSite as) {
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
    TypeDescriptor type    = as.getType();
    RefEdge  edgeNew =
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
  public void age(AllocSite as) {

    // aging adds this allocation site to the graph's
    // list of sites that exist in the graph, or does
    // nothing if the site is already in the list
    allocSites.add(as);

    // get the summary node for the allocation site in the context
    // of this particular ownership graph
    HeapRegionNode hrnSummary = getSummaryNode(as);

    // merge oldest node into summary
    Integer idK  = as.getOldest();
    HeapRegionNode hrnK = id2hrn.get(idK);
    mergeIntoSummary(hrnK, hrnSummary);

    // move down the line of heap region nodes
    // clobbering the ith and transferring all references
    // to and from i-1 to node i.  Note that this clobbers
    // the oldest node (hrnK) that was just merged into
    // the summary
    for( int i = allocationDepth - 1; i > 0; --i ) {

      // move references from the i-1 oldest to the ith oldest
      Integer idIth     = as.getIthOldest(i);
      HeapRegionNode hrnI      = id2hrn.get(idIth);
      Integer idImin1th = as.getIthOldest(i - 1);
      HeapRegionNode hrnImin1  = id2hrn.get(idImin1th);

      transferOnto(hrnImin1, hrnI);
    }

    // as stated above, the newest node should have had its
    // references moved over to the second oldest, so we wipe newest
    // in preparation for being the new object to assign something to
    Integer id0th = as.getIthOldest(0);
    HeapRegionNode hrn0  = id2hrn.get(id0th);
    assert hrn0 != null;

    // clear all references in and out of newest node
    clearRefEdgesFrom(hrn0, null, null, true);
    clearRefEdgesTo(hrn0, null, null, true);


    // now tokens in reachability sets need to "age" also
    Iterator itrAllVariableNodes = td2vn.entrySet().iterator();
    while( itrAllVariableNodes.hasNext() ) {
      Map.Entry me = (Map.Entry)itrAllVariableNodes.next();
      VariableNode ln = (VariableNode) me.getValue();

      Iterator<RefEdge> itrEdges = ln.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTokens(as, itrEdges.next() );
      }
    }

    Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
    while( itrAllHRNodes.hasNext() ) {
      Map.Entry me       = (Map.Entry)itrAllHRNodes.next();
      HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

      ageTokens(as, hrnToAge);

      Iterator<RefEdge> itrEdges = hrnToAge.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTokens(as, itrEdges.next() );
      }
    }


    // after tokens have been aged, reset newest node's reachability
    if( hrn0.isFlagged() ) {
      hrn0.setAlpha(new ReachSet(
                      new ReachState(
                        new ReachTuple(hrn0).makeCanonical()
                        ).makeCanonical()
                      ).makeCanonical()
                    );
    } else {
      hrn0.setAlpha(new ReachSet(
                      new ReachState().makeCanonical()
                      ).makeCanonical()
                    );
    }
  }


  protected HeapRegionNode getSummaryNode(AllocSite as) {

    Integer idSummary  = as.getSummary();
    HeapRegionNode hrnSummary = id2hrn.get(idSummary);

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
      
      if(as.getFlag()){
    	  hasFlags=as.getFlag();
      }

      hrnSummary = createNewHeapRegionNode(idSummary,    // id or null to generate a new one 
                                           false,	 // single object?			 
                                           true,	 // summary?			 
                                           hasFlags,	 // flagged?			 
                                           false,	 // is a parameter?			 
					   as.getType(), // type				 
                                           as,		 // allocation site			 
                                           null,	 // reachability set                 
                                           as.toStringForDOT() + "\\nsummary",
                                           generateUniqueIdentifier(as,0,true));

      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
	Integer idIth = as.getIthOldest(i);
	assert !id2hrn.containsKey(idIth);
	createNewHeapRegionNode(idIth,        // id or null to generate a new one 
	                        true,	      // single object?			 
				false,	      // summary?			 
	                        hasFlags,     // flagged?			 
	                        false,	      // is a parameter?			 
				as.getType(), // type				 
	                        as,	      // allocation site			 
	                        null,	      // reachability set                 
	                        as.toStringForDOT() + "\\n" + i + " oldest",
	                        generateUniqueIdentifier(as,i,false));
      }
    }

    return hrnSummary;
  }


  protected HeapRegionNode getShadowSummaryNode(AllocSite as) {

    Integer idShadowSummary  = as.getSummaryShadow();
    HeapRegionNode hrnShadowSummary = id2hrn.get(idShadowSummary);

    if( hrnShadowSummary == null ) {

      boolean hasFlags = false;
      if( as.getType().isClass() ) {
	hasFlags = as.getType().getClassDesc().hasFlags();
      }

      hrnShadowSummary = createNewHeapRegionNode(idShadowSummary, // id or null to generate a new one 
                                                 false,        	  // single object?			 
						 true,		  // summary?			 
                                                 hasFlags,        // flagged?			                                     
                                                 false,		  // is a parameter?			 
						 as.getType(),	  // type				 
                                                 as,		  // allocation site			 
                                                 null,		  // reachability set                 
                                                 as + "\\n" + as.getType() + "\\nshadowSum",
                                                 "");

      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
	Integer idShadowIth = as.getIthOldestShadow(i);
	assert !id2hrn.containsKey(idShadowIth);
	createNewHeapRegionNode(idShadowIth,  // id or null to generate a new one 
	                        true,	      // single object?			 
				false,	      // summary?			 
	                        hasFlags,     // flagged?			 
	                        false,	      // is a parameter?			 
				as.getType(), // type				 
	                        as,	      // allocation site			 
	                        null,	      // reachability set                 
	                        as + "\\n" + as.getType() + "\\n" + i + " shadow",
	                        "");
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


  public Set<Integer> calculateAliasedParamSet( FlatCall fc,
						boolean isStatic,
						FlatMethod fm ) {

    Hashtable<Integer, VariableNode> paramIndex2ln =
      new Hashtable<Integer, VariableNode>();

    Hashtable<Integer, HashSet<HeapRegionNode> > paramIndex2reachableCallerNodes =
      new Hashtable<Integer, HashSet<HeapRegionNode> >();

    for( int i = 0; i < fm.numParameters(); ++i ) {
      Integer        paramIndex = new Integer( i );
      TempDescriptor tdParam    = fm.getParameter( i );
      TypeDescriptor typeParam  = tdParam.getType();

      if( typeParam.isImmutable() && !typeParam.isArray() ) {
	// don't bother with this primitive parameter, it
	// cannot affect reachability
	continue;
      }

      // now depending on whether the callee is static or not
      // we need to account for a "this" argument in order to
      // find the matching argument in the caller context
      TempDescriptor argTemp_i = fc.getArgMatchingParamIndex( fm, paramIndex );

      VariableNode argLabel_i = getVariableNodeFromTemp(argTemp_i);
      paramIndex2ln.put(paramIndex, argLabel_i);
    }

    Iterator lnArgItr = paramIndex2ln.entrySet().iterator();
    while( lnArgItr.hasNext() ) {
      Map.Entry me      = (Map.Entry)lnArgItr.next();
      Integer index     = (Integer)   me.getKey();
      VariableNode lnArg_i = (VariableNode) me.getValue();

      HashSet<HeapRegionNode> reachableNodes = new HashSet<HeapRegionNode>();
      HashSet<HeapRegionNode> todoNodes      = new HashSet<HeapRegionNode>();

      // to find all reachable nodes, start with label referencees
      Iterator<RefEdge> edgeArgItr = lnArg_i.iteratorToReferencees();
      while( edgeArgItr.hasNext() ) {
	RefEdge edge = edgeArgItr.next();
	todoNodes.add( edge.getDst() );
      }

      // then follow links until all reachable nodes have been found
      while( !todoNodes.isEmpty() ) {
	HeapRegionNode hrn = todoNodes.iterator().next();
	todoNodes.remove(hrn);
	reachableNodes.add(hrn);

	Iterator<RefEdge> edgeItr = hrn.iteratorToReferencees();
	while( edgeItr.hasNext() ) {
	  RefEdge edge = edgeItr.next();

	  if( !reachableNodes.contains(edge.getDst() ) ) {
	    todoNodes.add(edge.getDst() );
	  }
	}
      }

      // save for later
      paramIndex2reachableCallerNodes.put(index, reachableNodes);
    }

    Set<Integer> aliasedIndices = new HashSet<Integer>();

    // check for arguments that are aliased
    for( int i = 0; i < fm.numParameters(); ++i ) {
      for( int j = 0; j < i; ++j ) {	
	HashSet<HeapRegionNode> s1 = paramIndex2reachableCallerNodes.get( i );
	HashSet<HeapRegionNode> s2 = paramIndex2reachableCallerNodes.get( j );

	// some parameters are immutable or primitive, so skip em
	if( s1 == null || s2 == null ) {
	  continue;
	}

	Set<HeapRegionNode> intersection = new HashSet<HeapRegionNode>(s1);
	intersection.retainAll(s2);

	if( !intersection.isEmpty() ) {
	  aliasedIndices.add( new Integer( i ) );
	  aliasedIndices.add( new Integer( j ) );
	}
      }
    }

    return aliasedIndices;
  }


  private String makeMapKey( Integer i, Integer j, String field ) {
    return i+","+j+","+field;
  }

  private String makeMapKey( Integer i, String field ) {
    return i+","+field;
  }

  // these hashtables are used during the mapping procedure to say that
  // with respect to some argument i there is an edge placed into some
  // category for mapping with respect to another argument index j
  // so the key into the hashtable is i, the value is a two-element vector
  // that contains in 0 the edge and in 1 the Integer index j
  private void ensureEmptyEdgeIndexPair( Hashtable< Integer, Set<Vector> > edge_index_pairs,
					 Integer indexI ) {

    Set<Vector> ei = edge_index_pairs.get( indexI );
    if( ei == null ) { 
      ei = new HashSet<Vector>(); 
    }
    edge_index_pairs.put( indexI, ei );
  }

  private void addEdgeIndexPair( Hashtable< Integer, Set<Vector> > edge_index_pairs,
				 Integer indexI,
				 RefEdge edge,
				 Integer indexJ ) {
    
    Vector v = new Vector(); v.setSize( 2 );
    v.set( 0 , edge  );
    v.set( 1 , indexJ );
    Set<Vector> ei = edge_index_pairs.get( indexI );
    if( ei == null ) { 
      ei = new HashSet<Vector>(); 
    }
    ei.add( v );
    edge_index_pairs.put( indexI, ei );
  }

  private ReachSet funcScriptR( ReachSet rsIn, 
				       ReachGraph  ogCallee,
				       MethodContext   mc ) {

    ReachSet rsOut = new ReachSet( rsIn );

    Iterator itr = ogCallee.paramIndex2paramTokenPrimary.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry  me  = (Map.Entry)  itr.next();
      Integer    i   = (Integer)    me.getKey();
      ReachTuple p_i = (ReachTuple) me.getValue();
      ReachTuple s_i = ogCallee.paramIndex2paramTokenSecondary.get( i );

      // skip this if there is no secondary token or the parameter
      // is part of the aliasing context
      if( s_i == null || mc.getAliasedParamIndices().contains( i ) ) {
	continue;
      }

      rsOut = rsOut.removeTokenAIfTokenB( p_i, s_i );
    }

    return rsOut;
  }

  // detects strong updates to the primary parameter object and
  // effects the removal of old edges in the calling graph
  private void effectCalleeStrongUpdates( Integer paramIndex,
					  ReachGraph ogCallee,
					  HeapRegionNode hrnCaller
					  ) {
    Integer idPrimary = ogCallee.paramIndex2idPrimary.get( paramIndex );
    assert idPrimary != null;

    HeapRegionNode hrnPrimary = ogCallee.id2hrn.get( idPrimary );
    assert hrnPrimary != null;

    TypeDescriptor typeParam = hrnPrimary.getType();
    assert typeParam.isClass();
  
    Set<String> fieldNamesToRemove = new HashSet<String>();   

    ClassDescriptor cd = typeParam.getClassDesc();
    while( cd != null ) {

      Iterator fieldItr = cd.getFields();
      while( fieldItr.hasNext() ) {
	  
	FieldDescriptor fd = (FieldDescriptor) fieldItr.next();
	TypeDescriptor typeField = fd.getType();
	assert typeField != null;	
	  
	if( ogCallee.hasFieldBeenUpdated( hrnPrimary, fd.getSymbol() ) ) {
	  clearRefEdgesFrom( hrnCaller, fd.getType(), fd.getSymbol(), false );
	}
      }
      
      cd = cd.getSuperDesc();
    }
  }

  private boolean hasFieldBeenUpdated( HeapRegionNode hrnPrimary, String field ) {

    Iterator<RefEdge> itr = hrnPrimary.iteratorToReferencees();
    while( itr.hasNext() ) {
      RefEdge e = itr.next();
      if( e.fieldEquals( field ) && e.isInitialParam() ) {
	return false;
      }
    }

    return true;
  }

  // resolveMethodCall() is used to incorporate a callee graph's effects into
  // *this* graph, which is the caller.  This method can also be used, after
  // the entire analysis is complete, to perform parameter decomposition for 
  // a given call chain.
  public void resolveMethodCall(FlatCall       fc,        // call site in caller method
                                boolean        isStatic,  // whether it is a static method
                                FlatMethod     fm,        // the callee method (when virtual, can be many)
                                ReachGraph ogCallee,  // the callee's current ownership graph
				MethodContext  mc,        // the aliasing context for this call
				ParameterDecomposition pd // if this is not null, we're calling after analysis
				) {

    if( debugCallMap &&
	mc.getDescriptor().getSymbol().equals( debugCaller ) &&
	fm.getMethod().getSymbol().equals( debugCallee ) 
	) {

      try {
	writeGraph("debug1BeforeCall",
		      true,  // write labels (variables)
		      true,  // selectively hide intermediate temp vars
		      true,  // prune unreachable heap regions
		      false, // show back edges to confirm graph validity
		      false, // show parameter indices (unmaintained!)
		      true,  // hide subset reachability states
		      true); // hide edge taints

	ogCallee.writeGraph("debug0Callee",
		      true,  // write labels (variables)
		      true,  // selectively hide intermediate temp vars
		      true,  // prune unreachable heap regions
		      false, // show back edges to confirm graph validity
		      false, // show parameter indices (unmaintained!)
		      true,  // hide subset reachability states
		      true); // hide edge taints
      } catch( IOException e ) {}

      System.out.println( "  "+mc+" is calling "+fm );
    }



    // define rewrite rules and other structures to organize data by parameter/argument index
    Hashtable<Integer, ReachSet> paramIndex2rewriteH_p = new Hashtable<Integer, ReachSet>();
    Hashtable<Integer, ReachSet> paramIndex2rewriteH_s = new Hashtable<Integer, ReachSet>();
    
    Hashtable<String,  ReachSet> paramIndex2rewriteJ_p2p = new Hashtable<String,  ReachSet>(); // select( i, j, f )
    Hashtable<String,  ReachSet> paramIndex2rewriteJ_p2s = new Hashtable<String,  ReachSet>(); // select( i,    f )
    Hashtable<Integer, ReachSet> paramIndex2rewriteJ_s2p = new Hashtable<Integer, ReachSet>();
    Hashtable<Integer, ReachSet> paramIndex2rewriteJ_s2s = new Hashtable<Integer, ReachSet>();

    Hashtable<Integer, ReachSet> paramIndex2rewriteK_p  = new Hashtable<Integer, ReachSet>();
    Hashtable<Integer, ReachSet> paramIndex2rewriteK_p2 = new Hashtable<Integer, ReachSet>();
    Hashtable<Integer, ReachSet> paramIndex2rewriteK_s  = new Hashtable<Integer, ReachSet>();

    Hashtable<Integer, ReachSet> paramIndex2rewrite_d_p = new Hashtable<Integer, ReachSet>();
    Hashtable<Integer, ReachSet> paramIndex2rewrite_d_s = new Hashtable<Integer, ReachSet>();

    Hashtable<Integer, ReachSet> paramIndex2rewriteD = new Hashtable<Integer, ReachSet>();


    Hashtable<Integer, VariableNode> paramIndex2ln = new Hashtable<Integer, VariableNode>();


    paramIndex2rewriteH_p.put( bogusIndex, rsIdentity );
    paramIndex2rewriteH_s.put( bogusIndex, rsIdentity );    

    paramIndex2rewriteJ_p2p.put( bogusIndex.toString(), rsIdentity );
    paramIndex2rewriteJ_p2s.put( bogusIndex.toString(), rsIdentity );
    paramIndex2rewriteJ_s2p.put( bogusIndex,            rsIdentity );
    paramIndex2rewriteJ_s2s.put( bogusIndex,            rsIdentity );


    for( int i = 0; i < fm.numParameters(); ++i ) {
      Integer paramIndex = new Integer(i);

      if( !ogCallee.paramIndex2idPrimary.containsKey( paramIndex ) ) {
	// skip this immutable parameter
	continue;
      }
      
      // setup H (primary)
      Integer idPrimary = ogCallee.paramIndex2idPrimary.get( paramIndex );
      assert ogCallee.id2hrn.containsKey( idPrimary );
      HeapRegionNode hrnPrimary = ogCallee.id2hrn.get( idPrimary );
      assert hrnPrimary != null;
      paramIndex2rewriteH_p.put( paramIndex, toShadowTokens( ogCallee, hrnPrimary.getAlpha() ) );

      // setup J (primary->X)
      Iterator<RefEdge> p2xItr = hrnPrimary.iteratorToReferencees();
      while( p2xItr.hasNext() ) {
	RefEdge p2xEdge = p2xItr.next();

	// we only care about initial parameter edges here
	if( !p2xEdge.isInitialParam() ) { continue; }

	HeapRegionNode hrnDst = p2xEdge.getDst();

	if( ogCallee.idPrimary2paramIndexSet.containsKey( hrnDst.getID() ) ) {
	  Iterator<Integer> jItr = ogCallee.idPrimary2paramIndexSet.get( hrnDst.getID() ).iterator();
	  while( jItr.hasNext() ) {
	    Integer j = jItr.next();
	    paramIndex2rewriteJ_p2p.put( makeMapKey( i, j, p2xEdge.getField() ),
					 toShadowTokens( ogCallee, p2xEdge.getBeta() ) );
	  }

	} else {
	  assert ogCallee.idSecondary2paramIndexSet.containsKey( hrnDst.getID() );
	  paramIndex2rewriteJ_p2s.put( makeMapKey( i, p2xEdge.getField() ),
				       toShadowTokens( ogCallee, p2xEdge.getBeta() ) );
	}
      }

      // setup K (primary)
      TempDescriptor tdParamQ = ogCallee.paramIndex2tdQ.get( paramIndex );
      assert tdParamQ != null;
      VariableNode lnParamQ = ogCallee.td2vn.get( tdParamQ );
      assert lnParamQ != null;
      RefEdge edgeSpecialQ_i = lnParamQ.getReferenceTo( hrnPrimary, null, null );
      assert edgeSpecialQ_i != null;
      ReachSet qBeta = toShadowTokens( ogCallee, edgeSpecialQ_i.getBeta() );

      ReachTuple p_i = ogCallee.paramIndex2paramTokenPrimary  .get( paramIndex );
      ReachTuple s_i = ogCallee.paramIndex2paramTokenSecondary.get( paramIndex );

      ReachSet K_p  = new ReachSet().makeCanonical();
      ReachSet K_p2 = new ReachSet().makeCanonical();
      if( s_i == null ) {
	K_p = qBeta;
      } else {
	// sort qBeta into K_p1 and K_p2	
	Iterator<ReachState> ttsItr = qBeta.iterator();
	while( ttsItr.hasNext() ) {
	  ReachState tts = ttsItr.next();
	  if( s_i != null && tts.containsBoth( p_i, s_i ) ) {
	    K_p2 = K_p2.union( tts );
	  } else {
	    K_p = K_p.union( tts );
	  }
	}
      }
      paramIndex2rewriteK_p .put( paramIndex, K_p  );
      paramIndex2rewriteK_p2.put( paramIndex, K_p2 );


      // if there is a secondary node, compute the rest of the rewrite rules
      if( ogCallee.paramIndex2idSecondary.containsKey( paramIndex ) ) {

	// setup H (secondary)
	Integer idSecondary = ogCallee.paramIndex2idSecondary.get( paramIndex );
	assert ogCallee.id2hrn.containsKey( idSecondary );
	HeapRegionNode hrnSecondary = ogCallee.id2hrn.get( idSecondary );
	assert hrnSecondary != null;
	paramIndex2rewriteH_s.put( paramIndex, toShadowTokens( ogCallee, hrnSecondary.getAlpha() ) );

	// setup J (secondary->X)
	Iterator<RefEdge> s2xItr = hrnSecondary.iteratorToReferencees();
	while( s2xItr.hasNext() ) {
	  RefEdge s2xEdge = s2xItr.next();
	  
	  if( !s2xEdge.isInitialParam() ) { continue; }
	  
	  HeapRegionNode hrnDst = s2xEdge.getDst();
	  
	  if( ogCallee.idPrimary2paramIndexSet.containsKey( hrnDst.getID() ) ) {
	    Iterator<Integer> jItr = ogCallee.idPrimary2paramIndexSet.get( hrnDst.getID() ).iterator();
	    while( jItr.hasNext() ) {
	      Integer j = jItr.next();
	      paramIndex2rewriteJ_s2p.put( i, toShadowTokens( ogCallee, s2xEdge.getBeta() ) );
	    }
	    
	  } else {
	    assert ogCallee.idSecondary2paramIndexSet.containsKey( hrnDst.getID() );
	    paramIndex2rewriteJ_s2s.put( i, toShadowTokens( ogCallee, s2xEdge.getBeta() ) );
	  }
	}

	// setup K (secondary)
	TempDescriptor tdParamR = ogCallee.paramIndex2tdR.get( paramIndex );
	assert tdParamR != null;
	VariableNode lnParamR = ogCallee.td2vn.get( tdParamR );
	assert lnParamR != null;
	RefEdge edgeSpecialR_i = lnParamR.getReferenceTo( hrnSecondary, null, null );
	assert edgeSpecialR_i != null;
	paramIndex2rewriteK_s.put( paramIndex,
				   toShadowTokens( ogCallee, edgeSpecialR_i.getBeta() ) );	
      }
    

      // now depending on whether the callee is static or not
      // we need to account for a "this" argument in order to
      // find the matching argument in the caller context
      TempDescriptor argTemp_i = fc.getArgMatchingParamIndex( fm, paramIndex );

      // remember which caller arg label maps to param index
      VariableNode argLabel_i = getVariableNodeFromTemp( argTemp_i );
      paramIndex2ln.put( paramIndex, argLabel_i );

      // do a callee-effect strong update pre-pass here      
      if( argTemp_i.getType().isClass() ) {

	Iterator<RefEdge> edgeItr = argLabel_i.iteratorToReferencees();
	while( edgeItr.hasNext() ) {
	  RefEdge edge = edgeItr.next();
	  HeapRegionNode hrn = edge.getDst();

	  if( (hrn.getNumReferencers()                                == 1) || // case 1
	      (hrn.isSingleObject() && argLabel_i.getNumReferencees() == 1)    // case 2	     	     
	    ) {
	    if( !DISABLE_STRONG_UPDATES ) {
              effectCalleeStrongUpdates( paramIndex, ogCallee, hrn );
            }
	  }
	}
      }

      // then calculate the d and D rewrite rules
      ReachSet d_i_p = new ReachSet().makeCanonical();
      ReachSet d_i_s = new ReachSet().makeCanonical();
      Iterator<RefEdge> edgeItr = argLabel_i.iteratorToReferencees();
      while( edgeItr.hasNext() ) {
	RefEdge edge = edgeItr.next();

	d_i_p = d_i_p.union( edge.getBeta().intersection( edge.getDst().getAlpha() ) );
	d_i_s = d_i_s.union( edge.getBeta() );
      }
      paramIndex2rewrite_d_p.put( paramIndex, d_i_p );
      paramIndex2rewrite_d_s.put( paramIndex, d_i_s );

      // TODO: we should only do this when we need it, and then
      // memoize it for the rest of the mapping procedure
      ReachSet D_i = d_i_s.exhaustiveArityCombinations();
      paramIndex2rewriteD.put( paramIndex, D_i );
    }


    // with respect to each argument, map parameter effects into caller
    HashSet<HeapRegionNode> nodesWithNewAlpha = new HashSet<HeapRegionNode>();
    HashSet<RefEdge>  edgesWithNewBeta  = new HashSet<RefEdge>();

    Hashtable<Integer, Set<HeapRegionNode> > pi2dr =
      new Hashtable<Integer, Set<HeapRegionNode> >();

    Hashtable<Integer, Set<HeapRegionNode> > pi2r =
      new Hashtable<Integer, Set<HeapRegionNode> >();

    Set<HeapRegionNode> defParamObj = new HashSet<HeapRegionNode>();

    Iterator lnArgItr = paramIndex2ln.entrySet().iterator();
    while( lnArgItr.hasNext() ) {
      Map.Entry me      = (Map.Entry) lnArgItr.next();
      Integer   index   = (Integer)   me.getKey();
      VariableNode lnArg_i = (VariableNode) me.getValue();
      
      Set<HeapRegionNode> dr   = new HashSet<HeapRegionNode>();
      Set<HeapRegionNode> r    = new HashSet<HeapRegionNode>();
      Set<HeapRegionNode> todo = new HashSet<HeapRegionNode>();

      // find all reachable nodes starting with label referencees
      Iterator<RefEdge> edgeArgItr = lnArg_i.iteratorToReferencees();
      while( edgeArgItr.hasNext() ) {
	RefEdge edge = edgeArgItr.next();
	HeapRegionNode hrn = edge.getDst();

	dr.add( hrn );

	if( lnArg_i.getNumReferencees() == 1 && hrn.isSingleObject() ) {
	  defParamObj.add( hrn );
	}

	Iterator<RefEdge> edgeHrnItr = hrn.iteratorToReferencees();
	while( edgeHrnItr.hasNext() ) {
	  RefEdge edger = edgeHrnItr.next();
	  todo.add( edger.getDst() );
	}

	// then follow links until all reachable nodes have been found
	while( !todo.isEmpty() ) {
	  HeapRegionNode hrnr = todo.iterator().next();
	  todo.remove( hrnr );
	  
	  r.add( hrnr );
	  
	  Iterator<RefEdge> edgeItr = hrnr.iteratorToReferencees();
	  while( edgeItr.hasNext() ) {
	    RefEdge edger = edgeItr.next();
	    if( !r.contains( edger.getDst() ) ) {
	      todo.add( edger.getDst() );
	    }
	  }
	}

	if( hrn.isSingleObject() ) {
	  r.remove( hrn );
	}
      }

      pi2dr.put( index, dr );
      pi2r .put( index, r  );
    }

    assert defParamObj.size() <= fm.numParameters();

    // if we're in parameter decomposition mode, report some results here
    if( pd != null ) {
      Iterator mapItr;

      // report primary parameter object mappings
      mapItr = pi2dr.entrySet().iterator();
      while( mapItr.hasNext() ) {
	Map.Entry           me         = (Map.Entry)           mapItr.next();
	Integer             paramIndex = (Integer)             me.getKey();
	Set<HeapRegionNode> hrnAset    = (Set<HeapRegionNode>) me.getValue();

	Iterator<HeapRegionNode> hrnItr = hrnAset.iterator();
	while( hrnItr.hasNext() ) {
	  HeapRegionNode hrnA = hrnItr.next();
	  pd.mapRegionToParamObject( hrnA, paramIndex );
	}
      }

      // report parameter-reachable mappings
      mapItr = pi2r.entrySet().iterator();
      while( mapItr.hasNext() ) {
	Map.Entry           me         = (Map.Entry)           mapItr.next();
	Integer             paramIndex = (Integer)             me.getKey();
	Set<HeapRegionNode> hrnRset    = (Set<HeapRegionNode>) me.getValue();

	Iterator<HeapRegionNode> hrnItr = hrnRset.iterator();
	while( hrnItr.hasNext() ) {
	  HeapRegionNode hrnR = hrnItr.next();
	  pd.mapRegionToParamReachable( hrnR, paramIndex );
	}
      }

      // and we're done in this method for special param decomp mode
      return;
    }


    // now iterate over reachable nodes to rewrite their alpha, and
    // classify edges found for beta rewrite    
    Hashtable<ReachTuple, ReachSet> tokens2states = new Hashtable<ReachTuple, ReachSet>();

    Hashtable< Integer, Set<Vector> > edges_p2p   = new Hashtable< Integer, Set<Vector> >();
    Hashtable< Integer, Set<Vector> > edges_p2s   = new Hashtable< Integer, Set<Vector> >();
    Hashtable< Integer, Set<Vector> > edges_s2p   = new Hashtable< Integer, Set<Vector> >();
    Hashtable< Integer, Set<Vector> > edges_s2s   = new Hashtable< Integer, Set<Vector> >();
    Hashtable< Integer, Set<Vector> > edges_up_dr = new Hashtable< Integer, Set<Vector> >();
    Hashtable< Integer, Set<Vector> > edges_up_r  = new Hashtable< Integer, Set<Vector> >();

    // so again, with respect to some arg i...
    lnArgItr = paramIndex2ln.entrySet().iterator();
    while( lnArgItr.hasNext() ) {
      Map.Entry me      = (Map.Entry) lnArgItr.next();
      Integer   index   = (Integer)   me.getKey();
      VariableNode lnArg_i = (VariableNode) me.getValue();      

      ReachTuple p_i = ogCallee.paramIndex2paramTokenPrimary.get( index );
      ReachTuple s_i = ogCallee.paramIndex2paramTokenSecondary.get( index );
      assert p_i != null;      

      ensureEmptyEdgeIndexPair( edges_p2p,   index );
      ensureEmptyEdgeIndexPair( edges_p2s,   index );
      ensureEmptyEdgeIndexPair( edges_s2p,   index );
      ensureEmptyEdgeIndexPair( edges_s2s,   index );
      ensureEmptyEdgeIndexPair( edges_up_dr, index );
      ensureEmptyEdgeIndexPair( edges_up_r,  index );

      Set<HeapRegionNode> dr = pi2dr.get( index );
      Iterator<HeapRegionNode> hrnItr = dr.iterator();
      while( hrnItr.hasNext() ) {
	// this heap region is definitely an "a_i" or primary by virtue of being in dr
	HeapRegionNode hrn = hrnItr.next();

	tokens2states.clear();
	tokens2states.put( p_i, hrn.getAlpha() );

	rewriteCallerReachability( index,
				   hrn,
				   null,
				   paramIndex2rewriteH_p.get( index ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );

	nodesWithNewAlpha.add( hrn );

	// sort edges
	Iterator<RefEdge> edgeItr = hrn.iteratorToReferencers();
	while( edgeItr.hasNext() ) {
	  RefEdge edge = edgeItr.next();
	  RefSrcNode on   = edge.getSrc();

	  boolean edge_classified = false;


	  if( on instanceof HeapRegionNode ) {
	    // hrn0 may be "a_j" and/or "r_j" or even neither
	    HeapRegionNode hrn0 = (HeapRegionNode) on;

	    Iterator itr = pi2dr.entrySet().iterator();
	    while( itr.hasNext() ) {
	      Map.Entry           mo   = (Map.Entry)           itr.next();
	      Integer             pi   = (Integer)             mo.getKey();
	      Set<HeapRegionNode> dr_i = (Set<HeapRegionNode>) mo.getValue();

	      if( dr_i.contains( hrn0 ) ) {		
		addEdgeIndexPair( edges_p2p, pi, edge, index );
		edge_classified = true;
	      }			      
	    }

	    itr = pi2r.entrySet().iterator();
	    while( itr.hasNext() ) {
	      Map.Entry           mo  = (Map.Entry)           itr.next();
	      Integer             pi  = (Integer)             mo.getKey();
	      Set<HeapRegionNode> r_i = (Set<HeapRegionNode>) mo.getValue();

	      if( r_i.contains( hrn0 ) ) {
		addEdgeIndexPair( edges_s2p, pi, edge, index );
		edge_classified = true;
	      }			      
	    }
	  }

	  // all of these edges are upstream of directly reachable objects
	  if( !edge_classified ) {
	    addEdgeIndexPair( edges_up_dr, index, edge, index );
	  }
	}
      }


      Set<HeapRegionNode> r = pi2r.get( index );
      hrnItr = r.iterator();
      while( hrnItr.hasNext() ) {
	// this heap region is definitely an "r_i" or secondary by virtue of being in r
	HeapRegionNode hrn = hrnItr.next();
      
	if( paramIndex2rewriteH_s.containsKey( index ) ) {

	  tokens2states.clear();
	  tokens2states.put( p_i, new ReachSet().makeCanonical() );
	  tokens2states.put( s_i, hrn.getAlpha() );

	  rewriteCallerReachability( index,
				     hrn,
				     null,
				     paramIndex2rewriteH_s.get( index ),
				     tokens2states,
				     paramIndex2rewrite_d_p,
				     paramIndex2rewrite_d_s,
				     paramIndex2rewriteD,
				     ogCallee,
				     false,
				     null );
	
	  nodesWithNewAlpha.add( hrn );	
	}       

	// sort edges
	Iterator<RefEdge> edgeItr = hrn.iteratorToReferencers();
	while( edgeItr.hasNext() ) {
	  RefEdge edge = edgeItr.next();
	  RefSrcNode on   = edge.getSrc();

	  boolean edge_classified = false;

	  if( on instanceof HeapRegionNode ) {
	    // hrn0 may be "a_j" and/or "r_j" or even neither
	    HeapRegionNode hrn0 = (HeapRegionNode) on;

	    Iterator itr = pi2dr.entrySet().iterator();
	    while( itr.hasNext() ) {
	      Map.Entry           mo   = (Map.Entry)           itr.next();
	      Integer             pi   = (Integer)             mo.getKey();
	      Set<HeapRegionNode> dr_i = (Set<HeapRegionNode>) mo.getValue();

	      if( dr_i.contains( hrn0 ) ) {
		addEdgeIndexPair( edges_p2s, pi, edge, index );
		edge_classified = true;
	      }			      
	    }

	    itr = pi2r.entrySet().iterator();
	    while( itr.hasNext() ) {
	      Map.Entry           mo  = (Map.Entry)           itr.next();
	      Integer             pi  = (Integer)             mo.getKey();
	      Set<HeapRegionNode> r_i = (Set<HeapRegionNode>) mo.getValue();

	      if( r_i.contains( hrn0 ) ) {
		addEdgeIndexPair( edges_s2s, pi, edge, index );
		edge_classified = true;
	      }			      
	    }
	  }

	  // these edges are all upstream of some reachable node
	  if( !edge_classified ) {
	    addEdgeIndexPair( edges_up_r, index, edge, index );
	  }
	}
      }
    }


    // and again, with respect to some arg i...
    lnArgItr = paramIndex2ln.entrySet().iterator();
    while( lnArgItr.hasNext() ) {
      Map.Entry me      = (Map.Entry) lnArgItr.next();
      Integer   index   = (Integer)   me.getKey();
      VariableNode lnArg_i = (VariableNode) me.getValue();      


      // update reachable edges
      Iterator edgeItr = edges_p2p.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	RefEdge edge   = (RefEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_p2p.containsKey( makeMapKey( index, 
							   indexJ,
 							   edge.getField() ) ) ) {
	  continue;
	}

	ReachTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
	assert p_j != null;
       
	tokens2states.clear();
	tokens2states.put( p_j, edge.getBeta() );

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteJ_p2p.get( makeMapKey( index, 
									    indexJ, 
									    edge.getField() ) ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );
	
	edgesWithNewBeta.add( edge );
      }


      edgeItr = edges_p2s.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	RefEdge edge   = (RefEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_p2s.containsKey( makeMapKey( index, 
							      edge.getField() ) ) ) {
	  continue;
	}

	ReachTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
	assert s_j != null;

	tokens2states.clear();
	tokens2states.put( s_j, edge.getBeta() );

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteJ_p2s.get( makeMapKey( index,
									    edge.getField() ) ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );
	
	edgesWithNewBeta.add( edge );	
      }


      edgeItr = edges_s2p.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	RefEdge edge   = (RefEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_s2p.containsKey( index ) ) {
	  continue;
	}

	ReachTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
	assert p_j != null;

	tokens2states.clear();
	tokens2states.put( p_j, edge.getBeta() );

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteJ_s2p.get( index ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );

	edgesWithNewBeta.add( edge );
      }


      edgeItr = edges_s2s.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	RefEdge edge   = (RefEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_s2s.containsKey( index ) ) {
	  continue;
	}

	ReachTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
	assert s_j != null;

	tokens2states.clear();
	tokens2states.put( s_j, edge.getBeta() );

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteJ_s2s.get( index ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );

	edgesWithNewBeta.add( edge );
      }


      // update directly upstream edges
      Hashtable<RefEdge, ChangeSet> edgeUpstreamPlannedChanges =
        new Hashtable<RefEdge, ChangeSet>();
      
      HashSet<RefEdge> edgesDirectlyUpstream =
	new HashSet<RefEdge>();

      edgeItr = edges_up_dr.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	RefEdge edge   = (RefEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	edgesDirectlyUpstream.add( edge );

	ReachTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
	assert p_j != null;

	// start with K_p2 and p_j
	tokens2states.clear();
	tokens2states.put( p_j, edge.getBeta() );

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteK_p2.get( index ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   true,
				   edgeUpstreamPlannedChanges );

	// and add in s_j, if required, and do K_p
	ReachTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
	if( s_j != null ) {
	  tokens2states.put( s_j, edge.getBeta() );
	}

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteK_p.get( index ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   true,
				   edgeUpstreamPlannedChanges );	

	edgesWithNewBeta.add( edge );
      }

      propagateTokensOverEdges( edgesDirectlyUpstream,
				edgeUpstreamPlannedChanges,
				edgesWithNewBeta );
      

      // update upstream edges
      edgeUpstreamPlannedChanges =
        new Hashtable<RefEdge, ChangeSet>();

      HashSet<RefEdge> edgesUpstream =
	new HashSet<RefEdge>();

      edgeItr = edges_up_r.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	RefEdge edge   = (RefEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteK_s.containsKey( index ) ) {
	  continue;
	}

	edgesUpstream.add( edge );

	ReachTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
	assert p_j != null;

	ReachTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
	assert s_j != null;

	tokens2states.clear();
	tokens2states.put( p_j, rsWttsEmpty );
	tokens2states.put( s_j, edge.getBeta() );

	rewriteCallerReachability( index,
				   null,
				   edge,
				   paramIndex2rewriteK_s.get( index ),
				   tokens2states,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   true,
				   edgeUpstreamPlannedChanges );

	edgesWithNewBeta.add( edge );
      }

      propagateTokensOverEdges( edgesUpstream,
				edgeUpstreamPlannedChanges,
				edgesWithNewBeta );

    } // end effects per argument/parameter map


    // commit changes to alpha and beta
    Iterator<HeapRegionNode> nodeItr = nodesWithNewAlpha.iterator();
    while( nodeItr.hasNext() ) {
      nodeItr.next().applyAlphaNew();
    }

    Iterator<RefEdge> edgeItr = edgesWithNewBeta.iterator();
    while( edgeItr.hasNext() ) {
      edgeItr.next().applyBetaNew();
    }

    
    // verify the existence of allocation sites and their
    // shadows from the callee in the context of this caller graph
    // then map allocated nodes of callee onto the caller shadows
    // of them
    Hashtable<ReachTuple, ReachSet> tokens2statesEmpty = new Hashtable<ReachTuple, ReachSet>();

    Iterator<AllocSite> asItr = ogCallee.allocSites.iterator();
    while( asItr.hasNext() ) {
      AllocSite allocSite  = asItr.next();

      // grab the summary in the caller just to make sure
      // the allocation site has nodes in the caller
      HeapRegionNode hrnSummary = getSummaryNode( allocSite );

      // assert that the shadow nodes have no reference edges
      // because they're brand new to the graph, or last time
      // they were used they should have been cleared of edges
      HeapRegionNode hrnShadowSummary = getShadowSummaryNode( allocSite );
      assert hrnShadowSummary.getNumReferencers() == 0;
      assert hrnShadowSummary.getNumReferencees() == 0;

      // then bring g_ij onto g'_ij and rewrite
      HeapRegionNode hrnSummaryCallee = ogCallee.getSummaryNode( allocSite );
      hrnShadowSummary.setAlpha( toShadowTokens( ogCallee, hrnSummaryCallee.getAlpha() ) );

      // shadow nodes only are touched by a rewrite one time,
      // so rewrite and immediately commit--and they don't belong
      // to a particular parameter, so use a bogus param index
      // that pulls a self-rewrite out of H
      rewriteCallerReachability( bogusIndex,
				 hrnShadowSummary,
				 null,
				 funcScriptR( hrnShadowSummary.getAlpha(), ogCallee, mc ),
				 tokens2statesEmpty,
				 paramIndex2rewrite_d_p,
				 paramIndex2rewrite_d_s,
				 paramIndex2rewriteD,
				 ogCallee,
				 false,
				 null );

      hrnShadowSummary.applyAlphaNew();


      for( int i = 0; i < allocSite.getAllocationDepth(); ++i ) {
	Integer idIth = allocSite.getIthOldest(i);
	assert id2hrn.containsKey(idIth);
	HeapRegionNode hrnIth = id2hrn.get(idIth);

	Integer idShadowIth = -(allocSite.getIthOldest(i));
	assert id2hrn.containsKey(idShadowIth);
	HeapRegionNode hrnIthShadow = id2hrn.get(idShadowIth);
	assert hrnIthShadow.getNumReferencers() == 0;
	assert hrnIthShadow.getNumReferencees() == 0;

	assert ogCallee.id2hrn.containsKey(idIth);
	HeapRegionNode hrnIthCallee = ogCallee.id2hrn.get(idIth);
	hrnIthShadow.setAlpha(toShadowTokens(ogCallee, hrnIthCallee.getAlpha() ) );

	rewriteCallerReachability( bogusIndex,
				   hrnIthShadow,
				   null,
				   funcScriptR( hrnIthShadow.getAlpha(), ogCallee, mc ),
				   tokens2statesEmpty,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );

	hrnIthShadow.applyAlphaNew();
      }
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

      Iterator<RefEdge> heapRegionsItrCallee = hrnCallee.iteratorToReferencees();
      while( heapRegionsItrCallee.hasNext() ) {
	RefEdge  edgeCallee     = heapRegionsItrCallee.next();
	HeapRegionNode hrnChildCallee = edgeCallee.getDst();
	Integer        idChildCallee  = hrnChildCallee.getID();

	// only address this edge if it is not a special initial edge
	if( !edgeCallee.isInitialParam() ) {

	  // now we know that in the callee method's ownership graph
	  // there is a heap region->heap region reference edge given
	  // by heap region pointers:
	  // hrnCallee -> heapChildCallee
	  //
	  // or by the ownership-graph independent ID's:
	  // idCallee -> idChildCallee

	  // make the edge with src and dst so beta info is
	  // calculated once, then copy it for each new edge in caller

	  RefEdge edgeNewInCallerTemplate = new RefEdge( null,
								     null,
								     edgeCallee.getType(),
								     edgeCallee.getField(),
								     false,
								     funcScriptR( toShadowTokens( ogCallee,
												  edgeCallee.getBeta()
												  ),
										  ogCallee,
										  mc )
								     );

	  rewriteCallerReachability( bogusIndex,
				     null,
				     edgeNewInCallerTemplate,
				     edgeNewInCallerTemplate.getBeta(),
				     tokens2statesEmpty,
				     paramIndex2rewrite_d_p,
				     paramIndex2rewrite_d_s,
				     paramIndex2rewriteD,
				     ogCallee,
				     false,
				     null );

	  edgeNewInCallerTemplate.applyBetaNew();


	  // So now make a set of possible source heaps in the caller graph
	  // and a set of destination heaps in the caller graph, and make
	  // a reference edge in the caller for every possible (src,dst) pair
	  HashSet<HeapRegionNode> possibleCallerSrcs =
	    getHRNSetThatPossiblyMapToCalleeHRN( ogCallee,
						 (HeapRegionNode) edgeCallee.getSrc(),
						 pi2dr,
						 pi2r );

	  HashSet<HeapRegionNode> possibleCallerDsts =
	    getHRNSetThatPossiblyMapToCalleeHRN( ogCallee,
						 edgeCallee.getDst(),
						 pi2dr,
						 pi2r );

	  // make every possible pair of {srcSet} -> {dstSet} edges in the caller
	  Iterator srcItr = possibleCallerSrcs.iterator();
	  while( srcItr.hasNext() ) {
	    HeapRegionNode src = (HeapRegionNode) srcItr.next();
	    
	    if( !hasMatchingField( src, edgeCallee ) ) {
	      // prune this source node possibility
	      continue;
	    }

	    Iterator dstItr = possibleCallerDsts.iterator();
	    while( dstItr.hasNext() ) {
	      HeapRegionNode dst = (HeapRegionNode) dstItr.next();

	      if( !hasMatchingType( edgeCallee, dst ) ) {
		// prune
		continue;
	      }

	      
	      


	      // otherwise the caller src and dst pair can match the edge, so make it
	      TypeDescriptor tdNewEdge =
		mostSpecificType( edgeCallee.getType(),
				  hrnChildCallee.getType(),
				  dst.getType()
				  );	      

	      RefEdge edgeNewInCaller = edgeNewInCallerTemplate.copy();
	      edgeNewInCaller.setSrc( src );
	      edgeNewInCaller.setDst( dst );	     
	      edgeNewInCaller.setType( tdNewEdge );

	      
	      // handle taint info if callee created this edge
	      // added by eom
	      Set<Integer> pParamSet=idPrimary2paramIndexSet.get(dst.getID());
	      Set<Integer> sParamSet=idSecondary2paramIndexSet.get(dst.getID());
	      HashSet<Integer> paramSet=new  HashSet<Integer>();
	      if(pParamSet!=null){
	    	  paramSet.addAll(pParamSet);  
	      }
	      if(sParamSet!=null){
	    	  paramSet.addAll(sParamSet);  
	      }
	      Iterator<Integer> paramIter=paramSet.iterator();
	      int newTaintIdentifier=0;
	      while(paramIter.hasNext()){
	    	  Integer paramIdx=paramIter.next();
	    	  edgeNewInCaller.tainedBy(paramIdx);
	      }

	      RefEdge edgeExisting = src.getReferenceTo( dst, 
							       edgeNewInCaller.getType(),
							       edgeNewInCaller.getField() );
	      if( edgeExisting == null ) {
		// if this edge doesn't exist in the caller, create it
		addRefEdge( src, dst, edgeNewInCaller );

	      } else {
		// if it already exists, merge with it
		edgeExisting.setBeta( edgeExisting.getBeta().union( edgeNewInCaller.getBeta() ) );
	      }
	    }
	  }
	}
      }
    }



    // return value may need to be assigned in caller
    TempDescriptor returnTemp = fc.getReturnTemp();
    if( returnTemp != null && !returnTemp.getType().isImmutable() ) {

      VariableNode lnLhsCaller = getVariableNodeFromTemp( returnTemp );
      clearRefEdgesFrom( lnLhsCaller, null, null, true );

      VariableNode lnReturnCallee = ogCallee.getVariableNodeFromTemp( tdReturn );
      Iterator<RefEdge> edgeCalleeItr = lnReturnCallee.iteratorToReferencees();
      while( edgeCalleeItr.hasNext() ) {
	RefEdge  edgeCallee     = edgeCalleeItr.next();
	HeapRegionNode hrnChildCallee = edgeCallee.getDst();

	// some edge types are not possible return values when we can
	// see what type variable we are assigning it to
	if( !isSuperiorType( returnTemp.getType(), edgeCallee.getType() ) ) {
	  System.out.println( "*** NOT EXPECTING TO SEE THIS: Throwing out "+edgeCallee+" for return temp "+returnTemp );
	  // prune
	  continue;
	}	

	RefEdge edgeNewInCallerTemplate = new RefEdge( null,
								   null,
								   edgeCallee.getType(),
								   edgeCallee.getField(),
								   false,
								   funcScriptR( toShadowTokens(ogCallee,
											       edgeCallee.getBeta() ),
										ogCallee,
										mc )
								   );
	rewriteCallerReachability( bogusIndex,
				   null,
				   edgeNewInCallerTemplate,
				   edgeNewInCallerTemplate.getBeta(),
				   tokens2statesEmpty,
				   paramIndex2rewrite_d_p,
				   paramIndex2rewrite_d_s,
				   paramIndex2rewriteD,
				   ogCallee,
				   false,
				   null );

	edgeNewInCallerTemplate.applyBetaNew();


	HashSet<HeapRegionNode> assignCallerRhs =
	  getHRNSetThatPossiblyMapToCalleeHRN( ogCallee,
					       edgeCallee.getDst(),
					       pi2dr,
					       pi2r );

	Iterator<HeapRegionNode> itrHrn = assignCallerRhs.iterator();
	while( itrHrn.hasNext() ) {
	  HeapRegionNode hrnCaller = itrHrn.next();

	  // don't make edge in caller if it is disallowed by types
	  if( !isSuperiorType( returnTemp.getType(), hrnCaller.getType() ) ) {
	    // prune	   
	    continue;
	  }

	  if( !isSuperiorType( returnTemp.getType(), hrnChildCallee.getType() ) ) {
	    // prune	   
	    continue;
	  }

	  if( !isSuperiorType( edgeCallee.getType(), hrnCaller.getType() ) ) {
	    // prune
	    continue;
	  }
	  
	  TypeDescriptor tdNewEdge =
	    mostSpecificType( edgeCallee.getType(),
			      hrnChildCallee.getType(),
			      hrnCaller.getType()
			      );	      

	  // otherwise caller node can match callee edge, so make it
	  RefEdge edgeNewInCaller = edgeNewInCallerTemplate.copy();
	  edgeNewInCaller.setSrc( lnLhsCaller );
	  edgeNewInCaller.setDst( hrnCaller );
	  edgeNewInCaller.setType( tdNewEdge );

	  RefEdge edgeExisting = lnLhsCaller.getReferenceTo( hrnCaller, 
								   tdNewEdge,
								   edgeNewInCaller.getField() );
	  if( edgeExisting == null ) {

	    // if this edge doesn't exist in the caller, create it
	    addRefEdge( lnLhsCaller, hrnCaller, edgeNewInCaller );
	  } else {
	    // if it already exists, merge with it
	    edgeExisting.setBeta( edgeExisting.getBeta().union( edgeNewInCaller.getBeta() ) );
	  }
	}
      }
    }



    // merge the shadow nodes of allocation sites back down to normal capacity
    Iterator<AllocSite> allocItr = ogCallee.allocSites.iterator();
    while( allocItr.hasNext() ) {
      AllocSite as = allocItr.next();

      // first age each allocation site enough times to make room for the shadow nodes
      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
	age( as );
      }

      // then merge the shadow summary into the normal summary
      HeapRegionNode hrnSummary = getSummaryNode( as );
      assert hrnSummary != null;

      HeapRegionNode hrnSummaryShadow = getShadowSummaryNode( as );
      assert hrnSummaryShadow != null;

      mergeIntoSummary( hrnSummaryShadow, hrnSummary );

      // then clear off after merge
      clearRefEdgesFrom( hrnSummaryShadow, null, null, true );
      clearRefEdgesTo  ( hrnSummaryShadow, null, null, true );
      hrnSummaryShadow.setAlpha( new ReachSet().makeCanonical() );

      // then transplant shadow nodes onto the now clean normal nodes
      for( int i = 0; i < as.getAllocationDepth(); ++i ) {

	Integer        idIth        = as.getIthOldest( i );
	HeapRegionNode hrnIth       = id2hrn.get( idIth );
	Integer        idIthShadow  = as.getIthOldestShadow( i );
	HeapRegionNode hrnIthShadow = id2hrn.get( idIthShadow );

	transferOnto( hrnIthShadow, hrnIth );

	// clear off shadow nodes after transfer
	clearRefEdgesFrom( hrnIthShadow, null, null, true );
	clearRefEdgesTo  ( hrnIthShadow, null, null, true );
	hrnIthShadow.setAlpha( new ReachSet().makeCanonical() );
      }

      // finally, globally change shadow tokens into normal tokens
      Iterator itrAllVariableNodes = td2vn.entrySet().iterator();
      while( itrAllVariableNodes.hasNext() ) {
	Map.Entry me = (Map.Entry) itrAllVariableNodes.next();
	VariableNode ln = (VariableNode) me.getValue();

	Iterator<RefEdge> itrEdges = ln.iteratorToReferencees();
	while( itrEdges.hasNext() ) {
	  unshadowTokens( as, itrEdges.next() );
	}
      }

      Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
      while( itrAllHRNodes.hasNext() ) {
	Map.Entry      me       = (Map.Entry)      itrAllHRNodes.next();
	HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

	unshadowTokens( as, hrnToAge );

	Iterator<RefEdge> itrEdges = hrnToAge.iteratorToReferencees();
	while( itrEdges.hasNext() ) {
	  unshadowTokens( as, itrEdges.next() );
	}
      }
    }



    // improve reachability as much as possible
    if( !DISABLE_GLOBAL_SWEEP ) {
      globalSweep();
    }


    if( debugCallMap &&
	mc.getDescriptor().getSymbol().equals( debugCaller ) &&
	fm.getMethod().getSymbol().equals( debugCallee ) 
	) {
      
      try {
	writeGraph( "debug9endResolveCall",
		    true,  // write labels (variables)
		    true,  // selectively hide intermediate temp vars
		    true,  // prune unreachable heap regions
		    false, // show back edges to confirm graph validity
		    false, // show parameter indices (unmaintained!)
		    true,  // hide subset reachability states
		    true); // hide edge taints
      } catch( IOException e ) {}
      System.out.println( "  "+mc+" done calling "+fm );      
      ++x;
      if( x == debugCallMapCount ) {
	System.exit( 0 );   
      }
    }
  }

  static int x = 0;


  protected boolean hasMatchingField(HeapRegionNode src, RefEdge edge) {

    // if no type, then it's a match-everything region
    TypeDescriptor tdSrc = src.getType();    
    if( tdSrc == null ) {
      return true;
    }

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


  protected boolean hasMatchingType(RefEdge edge, HeapRegionNode dst) {
    
    // if the region has no type, matches everything
    TypeDescriptor tdDst = dst.getType();
    if( tdDst == null ) {
      return true;
    }
 
    // if the type is not a class or an array, don't
    // match because primitives are copied, no aliases
    ClassDescriptor cdDst = tdDst.getClassDesc();
    if( cdDst == null && !tdDst.isArray() ) {
      return false;
    }
 
    // if the edge type is null, it matches everything
    TypeDescriptor tdEdge = edge.getType();
    if( tdEdge == null ) {
      return true;
    }
 
    return typeUtil.isSuperorType(tdEdge, tdDst);
  }


  protected void unshadowTokens(AllocSite as, RefEdge edge) {
    edge.setBeta(edge.getBeta().unshadowTokens(as) );
  }

  protected void unshadowTokens(AllocSite as, HeapRegionNode hrn) {
    hrn.setAlpha(hrn.getAlpha().unshadowTokens(as) );
  }


  private ReachSet toShadowTokens(ReachGraph ogCallee,
                                         ReachSet rsIn) {

    ReachSet rsOut = new ReachSet(rsIn).makeCanonical();

    Iterator<AllocSite> allocItr = ogCallee.allocSites.iterator();
    while( allocItr.hasNext() ) {
      AllocSite as = allocItr.next();

      rsOut = rsOut.toShadowTokens(as);
    }

    return rsOut.makeCanonical();
  }


  private void rewriteCallerReachability(Integer paramIndex,
                                         HeapRegionNode hrn,
                                         RefEdge edge,
                                         ReachSet rules,
					 Hashtable<ReachTuple, ReachSet> tokens2states,
                                         Hashtable<Integer,    ReachSet> paramIndex2rewrite_d_p,
                                         Hashtable<Integer,    ReachSet> paramIndex2rewrite_d_s,
                                         Hashtable<Integer,    ReachSet> paramIndex2rewriteD,
					 ReachGraph ogCallee,
                                         boolean makeChangeSet,
                                         Hashtable<RefEdge, ChangeSet> edgePlannedChanges) {

    assert(hrn == null && edge != null) ||
          (hrn != null && edge == null);

    assert rules         != null;
    assert tokens2states != null;

    ReachSet callerReachabilityNew = new ReachSet().makeCanonical();

    // for initializing structures in this method
    ReachState ttsEmpty = new ReachTupleSet().makeCanonical();

    // use this to construct a change set if required; the idea is to
    // map every partially rewritten token tuple set to the set of
    // caller-context token tuple sets that were used to generate it
    Hashtable<ReachState, HashSet<ReachTupleSet> > rewritten2source =
      new Hashtable<ReachState, HashSet<ReachTupleSet> >();
    rewritten2source.put( ttsEmpty, new HashSet<ReachState>() );

    
    Iterator<ReachState> rulesItr = rules.iterator();
    while(rulesItr.hasNext()) {
      ReachState rule = rulesItr.next();

      ReachSet rewrittenRule = new ReachSet(ttsEmpty).makeCanonical();

      Iterator<ReachTuple> ruleItr = rule.iterator();
      while(ruleItr.hasNext()) {
	ReachTuple ttCallee = ruleItr.next();	

	// compute the possibilities for rewriting this callee token
	ReachSet ttCalleeRewrites = null;
	boolean         callerSourceUsed = false;

	if( tokens2states.containsKey( ttCallee ) ) {
	  callerSourceUsed = true;
	  ttCalleeRewrites = tokens2states.get( ttCallee );
	  assert ttCalleeRewrites != null;

	} else if( ogCallee.paramTokenPrimary2paramIndex.containsKey( ttCallee ) ) {
	  // use little d_p
	  Integer paramIndex_j = ogCallee.paramTokenPrimary2paramIndex.get( ttCallee );
	  assert  paramIndex_j != null;
	  ttCalleeRewrites = paramIndex2rewrite_d_p.get( paramIndex_j );
	  assert ttCalleeRewrites != null;

	} else if( ogCallee.paramTokenSecondary2paramIndex.containsKey( ttCallee ) ) {
	  // use little d_s
	  Integer paramIndex_j = ogCallee.paramTokenSecondary2paramIndex.get( ttCallee );
	  assert  paramIndex_j != null;
	  ttCalleeRewrites = paramIndex2rewrite_d_s.get( paramIndex_j );
	  assert ttCalleeRewrites != null;

	} else if( ogCallee.paramTokenSecondaryPlus2paramIndex.containsKey( ttCallee ) ) {
	  // worse, use big D
	  Integer paramIndex_j = ogCallee.paramTokenSecondaryPlus2paramIndex.get( ttCallee );
	  assert  paramIndex_j != null;
	  ttCalleeRewrites = paramIndex2rewriteD.get( paramIndex_j );
	  assert ttCalleeRewrites != null;

	} else if( ogCallee.paramTokenSecondaryStar2paramIndex.containsKey( ttCallee ) ) {
	  // worse, use big D
	  Integer paramIndex_j = ogCallee.paramTokenSecondaryStar2paramIndex.get( ttCallee );
	  assert  paramIndex_j != null;
	  ttCalleeRewrites = paramIndex2rewriteD.get( paramIndex_j );
	  assert ttCalleeRewrites != null;

	} else {
	  // otherwise there's no need for a rewrite, just pass this one on
	  ReachState ttsCaller = new ReachTupleSet( ttCallee ).makeCanonical();
	  ttCalleeRewrites = new ReachSet( ttsCaller ).makeCanonical();
	}

	// branch every version of the working rewritten rule with
	// the possibilities for rewriting the current callee token
	ReachSet rewrittenRuleWithTTCallee = new ReachSet().makeCanonical();

	Iterator<ReachState> rewrittenRuleItr = rewrittenRule.iterator();
	while( rewrittenRuleItr.hasNext() ) {
	  ReachState ttsRewritten = rewrittenRuleItr.next();

	  Iterator<ReachState> ttCalleeRewritesItr = ttCalleeRewrites.iterator();
	  while( ttCalleeRewritesItr.hasNext() ) {
	    ReachState ttsBranch = ttCalleeRewritesItr.next();

	    ReachState ttsRewrittenNext = ttsRewritten.unionUpArity( ttsBranch );

	    if( makeChangeSet ) {
	      // in order to keep the list of source token tuple sets
	      // start with the sets used to make the partially rewritten
	      // rule up to this point
	      HashSet<ReachState> sourceSets = rewritten2source.get( ttsRewritten );
	      assert sourceSets != null;

	      // make a shallow copy for possible modification
	      sourceSets = (HashSet<ReachState>) sourceSets.clone();

	      // if we used something from the caller to rewrite it, remember
	      if( callerSourceUsed ) {
		sourceSets.add( ttsBranch );
	      }

	      // set mapping for the further rewritten rule
	      rewritten2source.put( ttsRewrittenNext, sourceSets );
	    }

	    rewrittenRuleWithTTCallee =
	      rewrittenRuleWithTTCallee.union( ttsRewrittenNext );
	  }
	}

	// now the rewritten rule's possibilities have been extended by
	// rewriting the current callee token, remember result
	rewrittenRule = rewrittenRuleWithTTCallee;
      }

      // the rule has been entirely rewritten into the caller context
      // now, so add it to the new reachability information
      callerReachabilityNew =
        callerReachabilityNew.union( rewrittenRule );
    }

    if( makeChangeSet ) {
      ChangeSet callerChangeSet = new ChangeSet().makeCanonical();

      // each possibility for the final reachability should have a set of
      // caller sources mapped to it, use to create the change set
      Iterator<ReachState> callerReachabilityItr = callerReachabilityNew.iterator();
      while( callerReachabilityItr.hasNext() ) {
	ReachState ttsRewrittenFinal = callerReachabilityItr.next();
	HashSet<ReachState> sourceSets = rewritten2source.get( ttsRewrittenFinal );
	assert sourceSets != null;

	Iterator<ReachState> sourceSetsItr = sourceSets.iterator();
	while( sourceSetsItr.hasNext() ) {
	  ReachState ttsSource = sourceSetsItr.next();

	  callerChangeSet =
	    callerChangeSet.union( new ChangeTuple( ttsSource, ttsRewrittenFinal ) );
	}
      }

      assert edgePlannedChanges != null;
      edgePlannedChanges.put( edge, callerChangeSet );
    }

    if( hrn == null ) {
      edge.setBetaNew( edge.getBetaNew().union( callerReachabilityNew ) );
    } else {
      hrn.setAlphaNew( hrn.getAlphaNew().union( callerReachabilityNew ) );
    }
  }



  private HashSet<HeapRegionNode>
    getHRNSetThatPossiblyMapToCalleeHRN( ReachGraph ogCallee,
					 HeapRegionNode hrnCallee,
					 Hashtable<Integer, Set<HeapRegionNode> > pi2dr,
					 Hashtable<Integer, Set<HeapRegionNode> > pi2r
					 ) {
    
    HashSet<HeapRegionNode> possibleCallerHRNs = new HashSet<HeapRegionNode>();

    Set<Integer> paramIndicesCallee_p = ogCallee.idPrimary2paramIndexSet  .get( hrnCallee.getID() );
    Set<Integer> paramIndicesCallee_s = ogCallee.idSecondary2paramIndexSet.get( hrnCallee.getID() );

    if( paramIndicesCallee_p == null &&
	paramIndicesCallee_s == null ) {
      // this is a node allocated in the callee and it has
      // exactly one shadow node in the caller to map to
      AllocSite as = hrnCallee.getAllocSite();
      assert as != null;

      int age = as.getAgeCategory( hrnCallee.getID() );
      assert age != AllocSite.AGE_notInThisSite;

      Integer idCaller;
      if( age == AllocSite.AGE_summary ) {
	idCaller = as.getSummaryShadow();

      } else if( age == AllocSite.AGE_oldest ) {
	idCaller = as.getOldestShadow();

      } else {
	assert age == AllocSite.AGE_in_I;

	Integer I = as.getAge( hrnCallee.getID() );
	assert I != null;

	idCaller = as.getIthOldestShadow( I );
      }

      assert id2hrn.containsKey( idCaller );
      possibleCallerHRNs.add( id2hrn.get( idCaller ) );

      return possibleCallerHRNs;
    }

    // find out what primary objects this might be
    if( paramIndicesCallee_p != null ) {
      // this is a node that was created to represent a parameter
      // so it maps to some regions directly reachable from the arg labels
      Iterator<Integer> itrIndex = paramIndicesCallee_p.iterator();
      while( itrIndex.hasNext() ) {
	Integer paramIndexCallee = itrIndex.next();
	assert pi2dr.containsKey( paramIndexCallee );
	possibleCallerHRNs.addAll( pi2dr.get( paramIndexCallee ) );
      }
    }

    // find out what secondary objects this might be
    if( paramIndicesCallee_s != null ) {
      // this is a node that was created to represent objs reachable from
      // some parameter, so it maps to regions reachable from the arg labels
      Iterator<Integer> itrIndex = paramIndicesCallee_s.iterator();
      while( itrIndex.hasNext() ) {
	Integer paramIndexCallee = itrIndex.next();
	assert pi2r.containsKey( paramIndexCallee );
	possibleCallerHRNs.addAll( pi2r.get( paramIndexCallee ) );
      }
    }

    // TODO: is this true?
    // one of the two cases above should have put something in here
    //assert !possibleCallerHRNs.isEmpty();

    return possibleCallerHRNs;
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
      assert rsEmpty.equals( hrn.getAlphaNew() );

      Iterator<RefEdge> itrRers = hrn.iteratorToReferencers();
      while( itrRers.hasNext() ) {
	RefEdge edge = itrRers.next();
	assert rsEmpty.equals( edge.getBetaNew() );
      }      

      // calculate boldB for this flagged node
      if( hrn.isFlagged() || hrn.isParameter() ) {
	
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

	ReachState markedTokens = new ReachTupleSet().makeCanonical();

	Iterator<ReachTuple> ttItr = ttsOld.iterator();
	while( ttItr.hasNext() ) {
	  ReachTuple ttOld = ttItr.next();

	  // never remove the identity token from a flagged region
	  // because it is trivially satisfied
	  if( hrn.isFlagged() || hrn.isParameter() ) {	
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
	ReachState ttsPruned = new ReachTupleSet().makeCanonical();
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
  // in merge() and equals() methods the suffix A
  // represents the passed in graph and the suffix
  // B refers to the graph in this object
  // Merging means to take the incoming graph A and
  // merge it into B, so after the operation graph B
  // is the final result.
  ////////////////////////////////////////////////////
  public void merge(ReachGraph og) {

    if( og == null ) {
      return;
    }

    mergeRefSrcNodes(og);
    mergeRefEdges(og);
    mergeParamIndexMappings(og);
    mergeAllocSites(og);
    mergeAccessPaths(og);
    mergeTempAndLabelCategories(og);
  }


  protected void mergeRefSrcNodes(ReachGraph og) {
    Set sA = og.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA  = (Map.Entry)iA.next();
      Integer idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      // if this graph doesn't have a node the
      // incoming graph has, allocate it
      if( !id2hrn.containsKey(idA) ) {
	HeapRegionNode hrnB = hrnA.copy();
	id2hrn.put(idA, hrnB);

      } else {
	// otherwise this is a node present in both graphs
	// so make the new reachability set a union of the
	// nodes' reachability sets
	HeapRegionNode hrnB = id2hrn.get(idA);
	hrnB.setAlpha(hrnB.getAlpha().union(hrnA.getAlpha() ) );
      }
    }

    // now add any label nodes that are in graph B but
    // not in A
    sA = og.td2vn.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      VariableNode lnA = (VariableNode)      meA.getValue();

      // if the label doesn't exist in B, allocate and add it
      VariableNode lnB = getVariableNodeFromTemp(tdA);
    }
  }

  protected void mergeRefEdges(ReachGraph og) {

    // heap regions
    Set sA = og.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA  = (Map.Entry)iA.next();
      Integer idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      Iterator<RefEdge> heapRegionsItrA = hrnA.iteratorToReferencees();
      while( heapRegionsItrA.hasNext() ) {
	RefEdge edgeA     = heapRegionsItrA.next();
	HeapRegionNode hrnChildA = edgeA.getDst();
	Integer idChildA  = hrnChildA.getID();

	// at this point we know an edge in graph A exists
	// idA -> idChildA, does this exist in B?
	assert id2hrn.containsKey(idA);
	HeapRegionNode hrnB        = id2hrn.get(idA);
	RefEdge edgeToMerge = null;

	Iterator<RefEdge> heapRegionsItrB = hrnB.iteratorToReferencees();
	while( heapRegionsItrB.hasNext() &&
	       edgeToMerge == null          ) {

	  RefEdge edgeB     = heapRegionsItrB.next();
	  HeapRegionNode hrnChildB = edgeB.getDst();
	  Integer idChildB  = hrnChildB.getID();

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
	  assert id2hrn.containsKey(idChildA);
	  HeapRegionNode hrnChildB = id2hrn.get(idChildA);
	  edgeToMerge = edgeA.copy();
	  edgeToMerge.setSrc(hrnB);
	  edgeToMerge.setDst(hrnChildB);
	  addRefEdge(hrnB, hrnChildB, edgeToMerge);
	}
	// otherwise, the edge already existed in both graphs
	// so merge their reachability sets
	else {
	  // just replace this beta set with the union
	  assert edgeToMerge != null;
	  edgeToMerge.setBeta(
	    edgeToMerge.getBeta().union(edgeA.getBeta() )
	    );
	  	//TODO eom
	    edgeToMerge.unionTaintIdentifier(edgeA.getTaintIdentifier());
	  if( !edgeA.isInitialParam() ) {
	    edgeToMerge.setIsInitialParam(false);
	  }
	}
      }
    }

    // and then again with label nodes
    sA = og.td2vn.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      VariableNode lnA = (VariableNode)      meA.getValue();

      Iterator<RefEdge> heapRegionsItrA = lnA.iteratorToReferencees();
      while( heapRegionsItrA.hasNext() ) {
	RefEdge edgeA     = heapRegionsItrA.next();
	HeapRegionNode hrnChildA = edgeA.getDst();
	Integer idChildA  = hrnChildA.getID();

	// at this point we know an edge in graph A exists
	// tdA -> idChildA, does this exist in B?
	assert td2vn.containsKey(tdA);
	VariableNode lnB         = td2vn.get(tdA);
	RefEdge edgeToMerge = null;

	Iterator<RefEdge> heapRegionsItrB = lnB.iteratorToReferencees();
	while( heapRegionsItrB.hasNext() &&
	       edgeToMerge == null          ) {

	  RefEdge  edgeB     = heapRegionsItrB.next();
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
	  assert id2hrn.containsKey(idChildA);
	  HeapRegionNode hrnChildB = id2hrn.get(idChildA);
	  edgeToMerge = edgeA.copy();
	  edgeToMerge.setSrc(lnB);
	  edgeToMerge.setDst(hrnChildB);
	  addRefEdge(lnB, hrnChildB, edgeToMerge);
	}
	// otherwise, the edge already existed in both graphs
	// so merge their reachability sets
	else {
	  // just replace this beta set with the union
	  edgeToMerge.setBeta(
	    edgeToMerge.getBeta().union(edgeA.getBeta() )
	    );
	    edgeToMerge.unionTaintIdentifier(edgeA.getTaintIdentifier());
	  if( !edgeA.isInitialParam() ) {
	    edgeToMerge.setIsInitialParam(false);
	  }
	}
      }
    }
  }

  // you should only merge ownership graphs that have the
  // same number of parameters, or if one or both parameter
  // index tables are empty
  protected void mergeParamIndexMappings(ReachGraph og) {
    
    if( idPrimary2paramIndexSet.size() == 0 ) {

      idPrimary2paramIndexSet            = og.idPrimary2paramIndexSet;
      paramIndex2idPrimary               = og.paramIndex2idPrimary;

      idSecondary2paramIndexSet          = og.idSecondary2paramIndexSet;
      paramIndex2idSecondary             = og.paramIndex2idSecondary;

      paramIndex2tdQ                     = og.paramIndex2tdQ;
      paramIndex2tdR                     = og.paramIndex2tdR;

      paramTokenPrimary2paramIndex       = og.paramTokenPrimary2paramIndex;
      paramIndex2paramTokenPrimary       = og.paramIndex2paramTokenPrimary;      

      paramTokenSecondary2paramIndex     = og.paramTokenSecondary2paramIndex;    
      paramIndex2paramTokenSecondary     = og.paramIndex2paramTokenSecondary;    
      paramTokenSecondaryPlus2paramIndex = og.paramTokenSecondaryPlus2paramIndex;
      paramIndex2paramTokenSecondaryPlus = og.paramIndex2paramTokenSecondaryPlus;
      paramTokenSecondaryStar2paramIndex = og.paramTokenSecondaryStar2paramIndex;
      paramIndex2paramTokenSecondaryStar = og.paramIndex2paramTokenSecondaryStar;      

      return;
    }

    if( og.idPrimary2paramIndexSet.size() == 0 ) {

      og.idPrimary2paramIndexSet            = idPrimary2paramIndexSet;
      og.paramIndex2idPrimary               = paramIndex2idPrimary;
         
      og.idSecondary2paramIndexSet          = idSecondary2paramIndexSet;
      og.paramIndex2idSecondary             = paramIndex2idSecondary;
         
      og.paramIndex2tdQ                     = paramIndex2tdQ;
      og.paramIndex2tdR                     = paramIndex2tdR;
         
      og.paramTokenPrimary2paramIndex       = paramTokenPrimary2paramIndex;
      og.paramIndex2paramTokenPrimary       = paramIndex2paramTokenPrimary;      
         
      og.paramTokenSecondary2paramIndex     = paramTokenSecondary2paramIndex;    
      og.paramIndex2paramTokenSecondary     = paramIndex2paramTokenSecondary;    
      og.paramTokenSecondaryPlus2paramIndex = paramTokenSecondaryPlus2paramIndex;
      og.paramIndex2paramTokenSecondaryPlus = paramIndex2paramTokenSecondaryPlus;
      og.paramTokenSecondaryStar2paramIndex = paramTokenSecondaryStar2paramIndex;
      og.paramIndex2paramTokenSecondaryStar = paramIndex2paramTokenSecondaryStar;      

      return;
    }

    assert idPrimary2paramIndexSet.size()   == og.idPrimary2paramIndexSet.size();
    assert idSecondary2paramIndexSet.size() == og.idSecondary2paramIndexSet.size();
  }

  protected void mergeAllocSites(ReachGraph og) {
    allocSites.addAll(og.allocSites);
  }

  protected void mergeAccessPaths(ReachGraph og) {
    UtilAlgorithms.mergeHashtablesWithHashSetValues(temp2accessPaths,
						    og.temp2accessPaths);
  }

  protected void mergeTempAndLabelCategories(ReachGraph og) {
    outOfScopeTemps.addAll(og.outOfScopeTemps);
    outOfScopeLabels.addAll(og.outOfScopeLabels);
    parameterTemps.addAll(og.parameterTemps);
    parameterLabels.addAll(og.parameterLabels);
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
  public boolean equals(ReachGraph og) {

    if( og == null ) {
      return false;
    }

    if( !areHeapRegionNodesEqual(og) ) {
      return false;
    }

    if( !areVariableNodesEqual(og) ) {
      return false;
    }

    if( !areRefEdgesEqual(og) ) {
      return false;
    }

    if( !areParamIndexMappingsEqual(og) ) {
      return false;
    }

    if( !areAccessPathsEqual(og) ) {
      return false;
    }

    // if everything is equal up to this point,
    // assert that allocSites is also equal--
    // this data is redundant and kept for efficiency
    assert allocSites .equals(og.allocSites );
    assert outOfScopeTemps .equals(og.outOfScopeTemps );
    assert outOfScopeLabels.equals(og.outOfScopeLabels);
    assert parameterTemps  .equals(og.parameterTemps  );
    assert parameterLabels .equals(og.parameterLabels );

    return true;
  }

  protected boolean areHeapRegionNodesEqual(ReachGraph og) {

    if( !areallHRNinAalsoinBandequal(this, og) ) {
      return false;
    }

    if( !areallHRNinAalsoinBandequal(og, this) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallHRNinAalsoinBandequal(ReachGraph ogA,
                                                       ReachGraph ogB) {
    Set sA = ogA.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA  = (Map.Entry)iA.next();
      Integer idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      if( !ogB.id2hrn.containsKey(idA) ) {
	return false;
      }

      HeapRegionNode hrnB = ogB.id2hrn.get(idA);
      if( !hrnA.equalsIncludingAlpha(hrnB) ) {
	return false;
      }
    }

    return true;
  }


  protected boolean areVariableNodesEqual(ReachGraph og) {

    if( !areallLNinAalsoinBandequal(this, og) ) {
      return false;
    }

    if( !areallLNinAalsoinBandequal(og, this) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallLNinAalsoinBandequal(ReachGraph ogA,
                                                      ReachGraph ogB) {
    Set sA = ogA.td2vn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();

      if( !ogB.td2vn.containsKey(tdA) ) {
	return false;
      }
    }

    return true;
  }


  protected boolean areRefEdgesEqual(ReachGraph og) {
    if( !areallREinAandBequal(this, og) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallREinAandBequal(ReachGraph ogA,
                                                ReachGraph ogB) {

    // check all the heap region->heap region edges
    Set sA = ogA.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA  = (Map.Entry)iA.next();
      Integer idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      // we should have already checked that the same
      // heap regions exist in both graphs
      assert ogB.id2hrn.containsKey(idA);

      if( !areallREfromAequaltoB(ogA, hrnA, ogB) ) {
	return false;
      }

      // then check every edge in B for presence in A, starting
      // from the same parent HeapRegionNode
      HeapRegionNode hrnB = ogB.id2hrn.get(idA);

      if( !areallREfromAequaltoB(ogB, hrnB, ogA) ) {
	return false;
      }
    }

    // then check all the label->heap region edges
    sA = ogA.td2vn.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      VariableNode lnA = (VariableNode)      meA.getValue();

      // we should have already checked that the same
      // label nodes exist in both graphs
      assert ogB.td2vn.containsKey(tdA);

      if( !areallREfromAequaltoB(ogA, lnA, ogB) ) {
	return false;
      }

      // then check every edge in B for presence in A, starting
      // from the same parent VariableNode
      VariableNode lnB = ogB.td2vn.get(tdA);

      if( !areallREfromAequaltoB(ogB, lnB, ogA) ) {
	return false;
      }
    }

    return true;
  }


  static protected boolean areallREfromAequaltoB(ReachGraph ogA,
                                                 RefSrcNode onA,
                                                 ReachGraph ogB) {

    Iterator<RefEdge> itrA = onA.iteratorToReferencees();
    while( itrA.hasNext() ) {
      RefEdge edgeA     = itrA.next();
      HeapRegionNode hrnChildA = edgeA.getDst();
      Integer idChildA  = hrnChildA.getID();

      assert ogB.id2hrn.containsKey(idChildA);

      // at this point we know an edge in graph A exists
      // onA -> idChildA, does this exact edge exist in B?
      boolean edgeFound = false;

      RefSrcNode onB = null;
      if( onA instanceof HeapRegionNode ) {
	HeapRegionNode hrnA = (HeapRegionNode) onA;
	onB = ogB.id2hrn.get(hrnA.getID() );
      } else {
	VariableNode lnA = (VariableNode) onA;
	onB = ogB.td2vn.get(lnA.getTempDescriptor() );
      }

      Iterator<RefEdge> itrB = onB.iteratorToReferencees();
      while( itrB.hasNext() ) {
	RefEdge edgeB     = itrB.next();
	HeapRegionNode hrnChildB = edgeB.getDst();
	Integer idChildB  = hrnChildB.getID();

	if( idChildA.equals( idChildB ) &&
	    edgeA.typeAndFieldEquals( edgeB ) ) {

	  // there is an edge in the right place with the right field,
	  // but do they have the same attributes?
	  if( edgeA.getBeta().equals(edgeB.getBeta() ) ) {
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


  protected boolean areParamIndexMappingsEqual(ReachGraph og) {

    if( idPrimary2paramIndexSet.size() != og.idPrimary2paramIndexSet.size() ) {
      return false;
    }

    if( idSecondary2paramIndexSet.size() != og.idSecondary2paramIndexSet.size() ) {
      return false;
    }

    return true;
  }


  protected boolean areAccessPathsEqual(ReachGraph og) {
    return temp2accessPaths.equals( og.temp2accessPaths );
  }



  public Set<HeapRegionNode> hasPotentialAlias( HeapRegionNode hrn1, HeapRegionNode hrn2 ) {
    assert hrn1 != null;
    assert hrn2 != null;

    // then get the various tokens for these heap regions
    ReachTuple h1 = new ReachTuple(hrn1.getID(),
				   !hrn1.isSingleObject(),
                                   ReachTuple.ARITY_ONE).makeCanonical();

    ReachTuple h1plus = new ReachTuple(hrn1.getID(),
                                       !hrn1.isSingleObject(),
                                       ReachTuple.ARITY_ONEORMORE).makeCanonical();

    ReachTuple h1star = new ReachTuple(hrn1.getID(),
                                       !hrn1.isSingleObject(),
                                       ReachTuple.ARITY_ZEROORMORE).makeCanonical();

    ReachTuple h2 = new ReachTuple(hrn2.getID(),
				   !hrn2.isSingleObject(),
                                   ReachTuple.ARITY_ONE).makeCanonical();

    ReachTuple h2plus = new ReachTuple(hrn2.getID(),
                                       !hrn2.isSingleObject(),
                                       ReachTuple.ARITY_ONEORMORE).makeCanonical();

    ReachTuple h2star = new ReachTuple(hrn2.getID(),
                                       !hrn2.isSingleObject(),
                                       ReachTuple.ARITY_ZEROORMORE).makeCanonical();

    // then get the merged beta of all out-going edges from these heap regions
    ReachSet beta1 = new ReachSet().makeCanonical();
    Iterator<RefEdge> itrEdge = hrn1.iteratorToReferencees();
    while( itrEdge.hasNext() ) {
      RefEdge edge = itrEdge.next();
      beta1 = beta1.union( edge.getBeta() );
    }

    ReachSet beta2 = new ReachSet().makeCanonical();
    itrEdge = hrn2.iteratorToReferencees();
    while( itrEdge.hasNext() ) {
      RefEdge edge = itrEdge.next();
      beta2 = beta2.union( edge.getBeta() );
    }

    boolean aliasDetected = false;

    // only do this one if they are different tokens
    if( h1 != h2 &&
        beta1.containsTupleSetWithBoth(h1,     h2) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1plus, h2) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1star, h2) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1,     h2plus) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1plus, h2plus) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1star, h2plus) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1,     h2star) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1plus, h2star) ) {
      aliasDetected = true;
    }
    if( beta1.containsTupleSetWithBoth(h1star, h2star) ) {
      aliasDetected = true;
    }

    if( h1 != h2 &&
	beta2.containsTupleSetWithBoth(h1,     h2) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1plus, h2) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1star, h2) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1,     h2plus) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1plus, h2plus) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1star, h2plus) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1,     h2star) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1plus, h2star) ) {
      aliasDetected = true;
    }
    if( beta2.containsTupleSetWithBoth(h1star, h2star) ) {
      aliasDetected = true;
    }

    Set<HeapRegionNode> common = new HashSet<HeapRegionNode>();
    if( aliasDetected ) {
      common = findCommonReachableNodes( hrn1, hrn2 );
      if( !(DISABLE_STRONG_UPDATES || DISABLE_GLOBAL_SWEEP) ) {
        assert !common.isEmpty();
      }
    }

    return common;    
  }


  public Set<HeapRegionNode> hasPotentialAlias(Integer paramIndex1, Integer paramIndex2) {

    // get parameter 1's heap regions
    assert paramIndex2idPrimary.containsKey(paramIndex1);
    Integer idParamPri1 = paramIndex2idPrimary.get(paramIndex1);

    assert id2hrn.containsKey(idParamPri1);
    HeapRegionNode hrnParamPri1 = id2hrn.get(idParamPri1);
    assert hrnParamPri1 != null;

    HeapRegionNode hrnParamSec1 = null;
    if( paramIndex2idSecondary.containsKey(paramIndex1) ) {
      Integer idParamSec1 = paramIndex2idSecondary.get(paramIndex1);

      assert id2hrn.containsKey(idParamSec1);
      hrnParamSec1 = id2hrn.get(idParamSec1);
      assert hrnParamSec1 != null;
    }


    // get the other parameter
    assert paramIndex2idPrimary.containsKey(paramIndex2);
    Integer idParamPri2 = paramIndex2idPrimary.get(paramIndex2);

    assert id2hrn.containsKey(idParamPri2);
    HeapRegionNode hrnParamPri2 = id2hrn.get(idParamPri2);
    assert hrnParamPri2 != null;

    HeapRegionNode hrnParamSec2 = null;
    if( paramIndex2idSecondary.containsKey(paramIndex2) ) {
      Integer idParamSec2 = paramIndex2idSecondary.get(paramIndex2);

      assert id2hrn.containsKey(idParamSec2);
      hrnParamSec2 = id2hrn.get(idParamSec2);
      assert hrnParamSec2 != null;
    }

    Set<HeapRegionNode> common = new HashSet<HeapRegionNode>();
    common.addAll( hasPotentialAlias( hrnParamPri1, hrnParamPri2 ) );

    if( hrnParamSec1 != null ) {
	common.addAll( hasPotentialAlias( hrnParamSec1, hrnParamPri2 ) );
    }

    if( hrnParamSec2 != null ) {
	common.addAll( hasPotentialAlias( hrnParamSec2, hrnParamPri1 ) );
    }

    if( hrnParamSec1 != null && hrnParamSec2 != null ) {
	common.addAll( hasPotentialAlias( hrnParamSec1, hrnParamSec2 ) );
    }

    return common;
  }


  public Set<HeapRegionNode> hasPotentialAlias(Integer paramIndex, AllocSite as) {

    // get parameter's heap regions
    assert paramIndex2idPrimary.containsKey(paramIndex);
    Integer idParamPri = paramIndex2idPrimary.get(paramIndex);

    assert id2hrn.containsKey(idParamPri);
    HeapRegionNode hrnParamPri = id2hrn.get(idParamPri);
    assert hrnParamPri != null;

    HeapRegionNode hrnParamSec = null;
    if( paramIndex2idSecondary.containsKey(paramIndex) ) {
      Integer idParamSec = paramIndex2idSecondary.get(paramIndex);

      assert id2hrn.containsKey(idParamSec);
      hrnParamSec = id2hrn.get(idParamSec);
      assert hrnParamSec != null;
    }

    // get summary node
    assert id2hrn.containsKey( as.getSummary() );
    HeapRegionNode hrnSummary = id2hrn.get( as.getSummary() );
    assert hrnSummary != null;

    Set<HeapRegionNode> common = hasPotentialAlias( hrnParamPri, hrnSummary );
    
    if( hrnParamSec != null ) {
	common.addAll( hasPotentialAlias( hrnParamSec, hrnSummary ) );
    }

    // check for other nodes
    for( int i = 0; i < as.getAllocationDepth(); ++i ) {

      assert id2hrn.containsKey( as.getIthOldest( i ) );
      HeapRegionNode hrnIthOldest = id2hrn.get( as.getIthOldest( i ) );
      assert hrnIthOldest != null;

      common = hasPotentialAlias( hrnParamPri, hrnIthOldest );
    
      if( hrnParamSec != null ) {
	  common.addAll( hasPotentialAlias( hrnParamSec, hrnIthOldest ) );
      }
    }
    
    return common;
  }


  public Set<HeapRegionNode> hasPotentialAlias(AllocSite as1, AllocSite as2) {     

    // get summary node 1's alpha
    Integer idSum1 = as1.getSummary();
    assert id2hrn.containsKey(idSum1);
    HeapRegionNode hrnSum1 = id2hrn.get(idSum1);
    assert hrnSum1 != null;

    // get summary node 2's alpha
    Integer idSum2 = as2.getSummary();
    assert id2hrn.containsKey(idSum2);
    HeapRegionNode hrnSum2 = id2hrn.get(idSum2);
    assert hrnSum2 != null;

    Set<HeapRegionNode> common = hasPotentialAlias( hrnSum1, hrnSum2 );

    // check sum2 against alloc1 nodes
    for( int i = 0; i < as1.getAllocationDepth(); ++i ) {
      Integer idI1 = as1.getIthOldest(i);
      assert id2hrn.containsKey(idI1);
      HeapRegionNode hrnI1 = id2hrn.get(idI1);
      assert hrnI1 != null;

      common.addAll( hasPotentialAlias( hrnI1, hrnSum2 ) );
    }

    // check sum1 against alloc2 nodes
    for( int i = 0; i < as2.getAllocationDepth(); ++i ) {
      Integer idI2 = as2.getIthOldest(i);
      assert id2hrn.containsKey(idI2);
      HeapRegionNode hrnI2 = id2hrn.get(idI2);
      assert hrnI2 != null;

      common.addAll( hasPotentialAlias( hrnSum1, hrnI2 ) );

      // while we're at it, do an inner loop for alloc2 vs alloc1 nodes
      for( int j = 0; j < as1.getAllocationDepth(); ++j ) {
	Integer idI1 = as1.getIthOldest(j);

	// if these are the same site, don't look for the same token, no alias.
	// different tokens of the same site could alias together though
	if( idI1.equals( idI2 ) ) {
	  continue;
	}

	HeapRegionNode hrnI1 = id2hrn.get(idI1);

	common.addAll( hasPotentialAlias( hrnI1, hrnI2 ) );
      }
    }

    return common;
  }


  public Set<HeapRegionNode> findCommonReachableNodes( HeapRegionNode hrn1,
						       HeapRegionNode hrn2 ) {

    Set<HeapRegionNode> reachableNodes1 = new HashSet<HeapRegionNode>();
    Set<HeapRegionNode> reachableNodes2 = new HashSet<HeapRegionNode>();

    Set<HeapRegionNode> todoNodes1 = new HashSet<HeapRegionNode>();
    todoNodes1.add( hrn1 );

    Set<HeapRegionNode> todoNodes2 = new HashSet<HeapRegionNode>();   
    todoNodes2.add( hrn2 );

    // follow links until all reachable nodes have been found
    while( !todoNodes1.isEmpty() ) {
      HeapRegionNode hrn = todoNodes1.iterator().next();
      todoNodes1.remove( hrn );
      reachableNodes1.add(hrn);
      
      Iterator<RefEdge> edgeItr = hrn.iteratorToReferencees();
      while( edgeItr.hasNext() ) {
	RefEdge edge = edgeItr.next();
	
	if( !reachableNodes1.contains( edge.getDst() ) ) {
	  todoNodes1.add( edge.getDst() );
	}
      }
    }

    while( !todoNodes2.isEmpty() ) {
      HeapRegionNode hrn = todoNodes2.iterator().next();
      todoNodes2.remove( hrn );
      reachableNodes2.add(hrn);
      
      Iterator<RefEdge> edgeItr = hrn.iteratorToReferencees();
      while( edgeItr.hasNext() ) {
	RefEdge edge = edgeItr.next();
	
	if( !reachableNodes2.contains( edge.getDst() ) ) {
	  todoNodes2.add( edge.getDst() );
	}
      }
    }
    
    Set<HeapRegionNode> intersection = 
      new HashSet<HeapRegionNode>( reachableNodes1 );

    intersection.retainAll( reachableNodes2 );
  
    return intersection;
  }

  
  public void writeGraph(String graphName,
                         boolean writeLabels,
                         boolean labelSelect,
                         boolean pruneGarbage,
                         boolean writeReferencers,
                         boolean writeParamMappings,
                         boolean hideSubsetReachability,
			 boolean hideEdgeTaints
                         ) throws java.io.IOException {

    // remove all non-word characters from the graph name so
    // the filename and identifier in dot don't cause errors
    graphName = graphName.replaceAll("[\\W]", "");

    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName+".dot") );
    bw.write("digraph "+graphName+" {\n");

    HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();

    // then visit every heap region node
    Set s = id2hrn.entrySet();
    Iterator i = s.iterator();
    while( i.hasNext() ) {
      Map.Entry me  = (Map.Entry)i.next();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();      

      if( !pruneGarbage ||
          (hrn.isFlagged() && hrn.getID() > 0) ||
          hrn.getDescription().startsWith("param")
          ) {

	if( !visited.contains(hrn) ) {
	  traverseHeapRegionNodes(VISIT_HRN_WRITE_FULL,
	                          hrn,
	                          bw,
	                          null,
	                          visited,
	                          writeReferencers,
                                  hideSubsetReachability,
				  hideEdgeTaints);
	}
      }
    }

    bw.write("  graphTitle[label=\""+graphName+"\",shape=box];\n");
    }

    // then visit every label node, useful for debugging
    if( writeLabels ) {
      s = td2vn.entrySet();
      i = s.iterator();
      while( i.hasNext() ) {
	Map.Entry me = (Map.Entry)i.next();
	VariableNode ln = (VariableNode) me.getValue();

	if( labelSelect ) {
	  String labelStr = ln.getTempDescriptorString();
	  if( labelStr.startsWith("___temp") ||
	      labelStr.startsWith("___dst") ||
	      labelStr.startsWith("___srctmp") ||
	      labelStr.startsWith("___neverused") ||
	      labelStr.contains(qString) ||
	      labelStr.contains(rString) ||
	      labelStr.contains(blobString)
	      ) {
	    continue;
	  }
	}

	//bw.write("  "+ln.toString() + ";\n");

	Iterator<RefEdge> heapRegionsItr = ln.iteratorToReferencees();
	while( heapRegionsItr.hasNext() ) {
	  RefEdge edge = heapRegionsItr.next();
	  HeapRegionNode hrn  = edge.getDst();

	  if( pruneGarbage && !visited.contains(hrn) ) {
	    traverseHeapRegionNodes(VISIT_HRN_WRITE_FULL,
	                            hrn,
	                            bw,
	                            null,
	                            visited,
	                            writeReferencers,
                                    hideSubsetReachability,
				    hideEdgeTaints);
	  }

	  bw.write("  "        + ln.toString() +
	           " -> "      + hrn.toString() +
	           "[label=\"" + edge.toGraphEdgeString(hideSubsetReachability,
							hideEdgeTaints) +
	           "\",decorate];\n");
	}
      }
    }


    bw.write("}\n");
    bw.close();
  }

  protected void traverseHeapRegionNodes(int mode,
                                         HeapRegionNode hrn,
                                         BufferedWriter bw,
                                         TempDescriptor td,
                                         HashSet<HeapRegionNode> visited,
                                         boolean writeReferencers,
                                         boolean hideSubsetReachability,
					 boolean hideEdgeTaints
                                         ) throws java.io.IOException {

    if( visited.contains(hrn) ) {
      return;
    }
    visited.add(hrn);

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

      attributes += ",label=\"ID" +
                    hrn.getID()   +
                    "\\n";

      if( hrn.getType() != null ) {
        attributes += hrn.getType().toPrettyString() + "\\n";
      }
       
      attributes += hrn.getDescription() +
	            "\\n"                +
                    hrn.getAlphaString(hideSubsetReachability) +
                    "\"]";

      bw.write("  " + hrn.toString() + attributes + ";\n");
      break;
    }



    Iterator<RefEdge> childRegionsItr = hrn.iteratorToReferencees();
    while( childRegionsItr.hasNext() ) {
      RefEdge edge     = childRegionsItr.next();
      HeapRegionNode hrnChild = edge.getDst();

      switch( mode ) {
      case VISIT_HRN_WRITE_FULL:
	bw.write("  "        + hrn.toString() +
	         " -> "      + hrnChild.toString() +
	         "[label=\"" + edge.toGraphEdgeString(hideSubsetReachability,
						      hideEdgeTaints) +
	         "\",decorate];\n");
	break;
      }

      traverseHeapRegionNodes(mode,
                              hrnChild,
                              bw,
                              td,
                              visited,
                              writeReferencers,
                              hideSubsetReachability,
			      hideEdgeTaints);
    }
  }
  
  public int getTaintIdentifierFromHRN(HeapRegionNode hrn){
	  HashSet<RefEdge> referenceEdges=hrn.referencers;
	  Iterator<RefEdge> iter=referenceEdges.iterator();
	  
	  int taintIdentifier=0;
	  while(iter.hasNext()){
		  RefEdge edge=iter.next();
		  taintIdentifier=taintIdentifier | edge.getTaintIdentifier();		  
	  }
	  
	  return taintIdentifier;
	  
  }
  
  public void propagateTaintIdentifier(HeapRegionNode hrn, int newTaintIdentifier, HashSet<HeapRegionNode> visitedSet){
	  
	  HashSet<RefEdge> setEdge=hrn.referencers;
	  Iterator<RefEdge> iter=setEdge.iterator();
	  while(iter.hasNext()){
		  RefEdge edge= iter.next();
		  edge.unionTaintIdentifier(newTaintIdentifier);		  
		  if(edge.getSrc() instanceof HeapRegionNode){
			  
			  HeapRegionNode refHRN=(HeapRegionNode)edge.getSrc();
			  //check whether it is reflexive edge
			  if(!refHRN.equals(hrn) && !visitedSet.contains(refHRN)){
				  visitedSet.add(refHRN);
				  propagateTaintIdentifier((HeapRegionNode)edge.getSrc(),newTaintIdentifier,visitedSet);
			  }
			 
		  }
	  }	  
	  
  }
  
  public void depropagateTaintIdentifier(HeapRegionNode hrn, int newTaintIdentifier, HashSet<HeapRegionNode> visitedSet){
	  
	  HashSet<RefEdge> setEdge=hrn.referencers;
	  Iterator<RefEdge> iter=setEdge.iterator();
	  while(iter.hasNext()){
		  RefEdge edge= iter.next();
		  edge.minusTaintIdentifier(newTaintIdentifier);		  
		  if(edge.getSrc() instanceof HeapRegionNode){
			  
			  HeapRegionNode refHRN=(HeapRegionNode)edge.getSrc();
			  //check whether it is reflexive edge
			  if(!refHRN.equals(hrn) && !visitedSet.contains(refHRN)){
				  visitedSet.add(refHRN);
				  depropagateTaintIdentifier((HeapRegionNode)edge.getSrc(),newTaintIdentifier,visitedSet);
			  }
			 
		  }
	  }	  
	  
  }


  // in this analysis specifically:
  // we have a notion that a null type is the "match any" type,
  // so wrap calls to the utility methods that deal with null
  public TypeDescriptor mostSpecificType( TypeDescriptor td1,
					  TypeDescriptor td2 ) {
    if( td1 == null ) {
      return td2;
    }
    if( td2 == null ) {
      return td1;
    }
    if( td1.isNull() ) {
      return td2;
    }
    if( td2.isNull() ) {
      return td1;
    }
    return typeUtil.mostSpecific( td1, td2 );
  }
  
  public TypeDescriptor mostSpecificType( TypeDescriptor td1,
					  TypeDescriptor td2,
					  TypeDescriptor td3 ) {
    
    return mostSpecificType( td1, 
			     mostSpecificType( td2, td3 )
			     );
  }  
  
  public TypeDescriptor mostSpecificType( TypeDescriptor td1,
					  TypeDescriptor td2,
					  TypeDescriptor td3,
					  TypeDescriptor td4 ) {
    
    return mostSpecificType( mostSpecificType( td1, td2 ), 
			     mostSpecificType( td3, td4 )
			     );
  }  

  // remember, in this analysis a null type means "any type"
  public boolean isSuperiorType( TypeDescriptor possibleSuper,
				 TypeDescriptor possibleChild ) {
    if( possibleSuper == null ||
	possibleChild == null ) {
      return true;
    }

    if( possibleSuper.isNull() ||
	possibleChild.isNull() ) {
      return true;
    }

    return typeUtil.isSuperorType( possibleSuper, possibleChild );
  }

  public String generateUniqueIdentifier(FlatMethod fm, int paramIdx, String type){
	  
	  //type: A->aliapsed parameter heap region
	  // P -> primary paramter heap region
	  // S -> secondary paramter heap region
	
	  String identifier;
	  if(type.equals("A")){
		  //aliased param
		  identifier="FM"+fm.hashCode()+".A";
	  }else{
		  identifier="FM"+fm.hashCode()+"."+paramIdx+"."+type;
	  }
	  return identifier;
	  
  }
  
  public String generateUniqueIdentifier(AllocSite as, int age, boolean isSummary){
	  
	  String identifier;
	  
	  FlatNew fn=as.getFlatNew();
	  
	  if(isSummary){
		  identifier="FN"+fn.hashCode()+".S";
	  }else{
		  identifier="FN"+fn.hashCode()+"."+age;
	  }
	  
	  return identifier;
	  
  }  
*/
}
