package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class OwnershipGraph {

  private int allocationDepth;
  private TypeUtil typeUtil;

  // there was already one other very similar reason
  // for traversing heap nodes that is no longer needed
  // instead of writing a new heap region visitor, use
  // the existing method with a new mode to describe what
  // actions to take during the traversal
  protected static final int VISIT_HRN_WRITE_FULL = 0;

  protected static final String qString    = new String( "Q_spec_" );
  protected static final String rString    = new String( "R_spec_" );
  protected static final String blobString = new String( "_AliasBlob___" );
		   
  protected static final TempDescriptor tdReturn    = new TempDescriptor( "_Return___" );
  protected static final TempDescriptor tdAliasBlob = new TempDescriptor( blobString );
		   
  protected static final TokenTupleSet   ttsEmpty    = new TokenTupleSet().makeCanonical();
  protected static final ReachabilitySet rsEmpty     = new ReachabilitySet().makeCanonical();
  protected static final ReachabilitySet rsWttsEmpty = new ReachabilitySet( ttsEmpty ).makeCanonical();

  // add a bogus entry with the identity rule for easy rewrite
  // of new callee nodes and edges, doesn't belong to any parameter
  protected static final int bogusParamIndexInt     = -2;
  protected static final Integer bogusID            = new Integer( bogusParamIndexInt );
  protected static final Integer bogusIndex         = new Integer( bogusParamIndexInt );
  protected static final TokenTuple bogusToken      = new TokenTuple( bogusID, true, TokenTuple.ARITY_ONE        ).makeCanonical();
  protected static final TokenTuple bogusTokenPlus  = new TokenTuple( bogusID, true, TokenTuple.ARITY_ONEORMORE  ).makeCanonical();
  protected static final TokenTuple bogusTokenStar  = new TokenTuple( bogusID, true, TokenTuple.ARITY_ZEROORMORE ).makeCanonical();
  protected static final ReachabilitySet rsIdentity =
    new ReachabilitySet( new TokenTupleSet( bogusToken ).makeCanonical() ).makeCanonical();


  public Hashtable<Integer,        HeapRegionNode> id2hrn;
  public Hashtable<TempDescriptor, LabelNode     > td2ln;

  public Hashtable<Integer,        Set<Integer>  > idPrimary2paramIndexSet;
  public Hashtable<Integer,        Integer       > paramIndex2idPrimary;

  public Hashtable<Integer,        Set<Integer>  > idSecondary2paramIndexSet;
  public Hashtable<Integer,        Integer       > paramIndex2idSecondary;

  public Hashtable<Integer,        TempDescriptor> paramIndex2tdQ;
  public Hashtable<Integer,        TempDescriptor> paramIndex2tdR;


  public HashSet<AllocationSite> allocationSites;


  public Hashtable<TokenTuple, Integer> paramTokenPrimary2paramIndex;
  public Hashtable<Integer, TokenTuple> paramIndex2paramTokenPrimary;

  public Hashtable<TokenTuple, Integer> paramTokenSecondary2paramIndex;
  public Hashtable<Integer, TokenTuple> paramIndex2paramTokenSecondary;
  public Hashtable<TokenTuple, Integer> paramTokenSecondaryPlus2paramIndex;
  public Hashtable<Integer, TokenTuple> paramIndex2paramTokenSecondaryPlus;
  public Hashtable<TokenTuple, Integer> paramTokenSecondaryStar2paramIndex;
  public Hashtable<Integer, TokenTuple> paramIndex2paramTokenSecondaryStar;


  public HeapRegionNode hrnNull;


  public OwnershipGraph(int allocationDepth, TypeUtil typeUtil) {
    this.allocationDepth = allocationDepth;
    this.typeUtil        = typeUtil;

    id2hrn                    = new Hashtable<Integer,        HeapRegionNode>();
    td2ln                     = new Hashtable<TempDescriptor, LabelNode     >();
    idPrimary2paramIndexSet   = new Hashtable<Integer,        Set<Integer>  >();
    paramIndex2idPrimary      = new Hashtable<Integer,        Integer       >();
    idSecondary2paramIndexSet = new Hashtable<Integer,        Set<Integer>  >();    
    paramIndex2idSecondary    = new Hashtable<Integer,        Integer       >();
    paramIndex2tdQ            = new Hashtable<Integer,        TempDescriptor>();
    paramIndex2tdR            = new Hashtable<Integer,        TempDescriptor>();

    paramTokenPrimary2paramIndex     = new Hashtable<TokenTuple,     Integer       >();
    paramIndex2paramTokenPrimary     = new Hashtable<Integer,        TokenTuple    >();

    paramTokenSecondary2paramIndex     = new Hashtable<TokenTuple,     Integer       >();
    paramIndex2paramTokenSecondary     = new Hashtable<Integer,        TokenTuple    >();
    paramTokenSecondaryPlus2paramIndex = new Hashtable<TokenTuple,     Integer       >();
    paramIndex2paramTokenSecondaryPlus = new Hashtable<Integer,        TokenTuple    >();
    paramTokenSecondaryStar2paramIndex = new Hashtable<TokenTuple,     Integer       >();
    paramIndex2paramTokenSecondaryStar = new Hashtable<Integer,        TokenTuple    >();

    allocationSites = new HashSet <AllocationSite>();

    hrnNull = createNewHeapRegionNode( OwnershipAnalysis.nullRegionID,
				       false,
				       false,
				       false,
				       false,
				       null,
				       null,
				       null,
				       "null" );
  }


  // label nodes are much easier to deal with than
  // heap region nodes.  Whenever there is a request
  // for the label node that is associated with a
  // temp descriptor we can either find it or make a
  // new one and return it.  This is because temp
  // descriptors are globally unique and every label
  // node is mapped to exactly one temp descriptor.
  protected LabelNode getLabelNodeFromTemp(TempDescriptor td) {
    assert td != null;

    if( !td2ln.containsKey(td) ) {
      td2ln.put(td, new LabelNode(td) );
    }

    return td2ln.get(td);
  }


  // the reason for this method is to have the option
  // creating new heap regions with specific IDs, or
  // duplicating heap regions with specific IDs (especially
  // in the merge() operation) or to create new heap
  // regions with a new unique ID.
  protected HeapRegionNode
  createNewHeapRegionNode(Integer id,
                          boolean isSingleObject,
                          boolean isNewSummary,
			  boolean isFlagged,
                          boolean isParameter,
			  TypeDescriptor type,
                          AllocationSite allocSite,
                          ReachabilitySet alpha,
                          String description) {

    boolean markForAnalysis = isFlagged || isParameter;

    TypeDescriptor typeToUse = null;
    if( allocSite != null ) {
      typeToUse = allocSite.getType();
    } else {
      typeToUse = type;
    }

    if( allocSite != null && allocSite.getDisjointId() != null ) {
      markForAnalysis = true;
    }

    if( id == null ) {
      id = OwnershipAnalysis.generateUniqueHeapRegionNodeID();
    }

    if( alpha == null ) {
      if( markForAnalysis ) {
	alpha = new ReachabilitySet(
	  new TokenTuple(id,
	                 !isSingleObject,
	                 TokenTuple.ARITY_ONE
	                 ).makeCanonical()
	  ).makeCanonical();
      } else {
	alpha = new ReachabilitySet(
	  new TokenTupleSet().makeCanonical()
	  ).makeCanonical();
      }
    }

    HeapRegionNode hrn = new HeapRegionNode(id,
                                            isSingleObject,
                                            markForAnalysis,
					    isParameter,
                                            isNewSummary,
					    typeToUse,
                                            allocSite,
                                            alpha,
                                            description);
    id2hrn.put(id, hrn);
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
  protected void addReferenceEdge(OwnershipNode referencer,
                                  HeapRegionNode referencee,
                                  ReferenceEdge edge) {
    assert referencer != null;
    assert referencee != null;
    assert edge       != null;
    assert edge.getSrc() == referencer;
    assert edge.getDst() == referencee;

    referencer.addReferencee(edge);
    referencee.addReferencer(edge);
  }

  protected void removeReferenceEdge(OwnershipNode referencer,
                                     HeapRegionNode referencee,
                                     TypeDescriptor type,
				     String field) {
    assert referencer != null;
    assert referencee != null;
    
    ReferenceEdge edge = referencer.getReferenceTo(referencee,
                                                   type,
						   field);
    assert edge != null;
    assert edge == referencee.getReferenceFrom(referencer,
                                               type,
					       field);

    referencer.removeReferencee(edge);
    referencee.removeReferencer(edge);
  }

  protected void clearReferenceEdgesFrom(OwnershipNode referencer,
                                         TypeDescriptor type,
					 String field,
                                         boolean removeAll) {
    assert referencer != null;

    // get a copy of the set to iterate over, otherwise
    // we will be trying to take apart the set as we
    // are iterating over it, which won't work
    Iterator<ReferenceEdge> i = referencer.iteratorToReferenceesClone();
    while( i.hasNext() ) {
      ReferenceEdge edge = i.next();

      if( removeAll                                          || 
	  (edge.typeEquals( type ) && edge.fieldEquals( field ))
	  //(type  != null && edge.getType() .equals( type  )) ||
	  //(field != null && edge.getField().equals( field ))   
        ){

	HeapRegionNode referencee = edge.getDst();
	
	removeReferenceEdge(referencer,
	                    referencee,
	                    edge.getType(),
			    edge.getField() );
      }
    }
  }

  protected void clearReferenceEdgesTo(HeapRegionNode referencee,
				       TypeDescriptor type,
				       String field,
                                       boolean removeAll) {
    assert referencee != null;

    // get a copy of the set to iterate over, otherwise
    // we will be trying to take apart the set as we
    // are iterating over it, which won't work
    Iterator<ReferenceEdge> i = referencee.iteratorToReferencersClone();
    while( i.hasNext() ) {
      ReferenceEdge edge = i.next();

      if( removeAll                                          || 
	  (edge.typeEquals( type ) && edge.fieldEquals( field ))
	  //(type  != null && edge.getType() .equals( type  )) ||
	  //(field != null && edge.getField().equals( field ))   
        ){

	OwnershipNode referencer = edge.getSrc();

	removeReferenceEdge(referencer,
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
  public void assignTempXEqualToTempY(TempDescriptor x,
                                      TempDescriptor y) {

    LabelNode lnX = getLabelNodeFromTemp(x);
    LabelNode lnY = getLabelNodeFromTemp(y);

    clearReferenceEdgesFrom(lnX, null, null, true);

    Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
    while( itrYhrn.hasNext() ) {
      ReferenceEdge  edgeY      = itrYhrn.next();
      HeapRegionNode referencee = edgeY.getDst();
      ReferenceEdge  edgeNew    = edgeY.copy();
      edgeNew.setSrc(lnX);

      addReferenceEdge(lnX, referencee, edgeNew);
    }
  }


  public void assignTempXEqualToNull(TempDescriptor x) {

    LabelNode lnX = getLabelNodeFromTemp(x);

    clearReferenceEdgesFrom(lnX, null, null, true);

    ReferenceEdge edgeNew = new ReferenceEdge(lnX,
					      hrnNull,
					      null,
					      null,
					      false,
					      null);

    addReferenceEdge(lnX, hrnNull, edgeNew);
  }


  public void assignTypedTempXEqualToTempY(TempDescriptor x,
					   TempDescriptor y,
					   TypeDescriptor type) {

    LabelNode lnX = getLabelNodeFromTemp(x);
    LabelNode lnY = getLabelNodeFromTemp(y);
    
    clearReferenceEdgesFrom(lnX, null, null, true);

    Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
    while( itrYhrn.hasNext() ) {
      ReferenceEdge  edgeY      = itrYhrn.next();
      HeapRegionNode referencee = edgeY.getDst();
      ReferenceEdge  edgeNew    = edgeY.copy();
      edgeNew.setSrc( lnX );
      edgeNew.setType( type );
      edgeNew.setField( null );

      addReferenceEdge(lnX, referencee, edgeNew);
    }
  }


  public void assignTempXEqualToTempYFieldF(TempDescriptor x,
                                            TempDescriptor y,
                                            FieldDescriptor f) {
    LabelNode lnX = getLabelNodeFromTemp(x);
    LabelNode lnY = getLabelNodeFromTemp(y);

    clearReferenceEdgesFrom(lnX, null, null, true);

    Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
    while( itrYhrn.hasNext() ) {
      ReferenceEdge   edgeY = itrYhrn.next();
      HeapRegionNode  hrnY  = edgeY.getDst();
      ReachabilitySet betaY = edgeY.getBeta();

      // skip the null region, load statement is not
      // meaningful for this region
      if( hrnY == hrnNull ) {
	continue;
      }

      Iterator<ReferenceEdge> itrHrnFhrn = hrnY.iteratorToReferencees();
      while( itrHrnFhrn.hasNext() ) {
	ReferenceEdge   edgeHrn = itrHrnFhrn.next();
	HeapRegionNode  hrnHrn  = edgeHrn.getDst();
	ReachabilitySet betaHrn = edgeHrn.getBeta();

	if( edgeHrn.getType() == null ||	    
	    (edgeHrn.getType() .equals( f.getType()   ) &&
	     edgeHrn.getField().equals( f.getSymbol() )    )
	  ) {

	  ReferenceEdge edgeNew = new ReferenceEdge(lnX,
	                                            hrnHrn,
	                                            f.getType(),
						    null,
	                                            false,
	                                            betaY.intersection(betaHrn) );

	  addReferenceEdge(lnX, hrnHrn, edgeNew);
	}
      }
    }
  }


  public void assignTempXFieldFEqualToTempY(TempDescriptor x,
                                            FieldDescriptor f,
                                            TempDescriptor y) {
    LabelNode lnX = getLabelNodeFromTemp(x);
    LabelNode lnY = getLabelNodeFromTemp(y);

    HashSet<HeapRegionNode> nodesWithNewAlpha = new HashSet<HeapRegionNode>();
    HashSet<ReferenceEdge>  edgesWithNewBeta  = new HashSet<ReferenceEdge>();

    // first look for possible strong updates and remove those edges
    boolean strongUpdate = false;

    Iterator<ReferenceEdge> itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      ReferenceEdge edgeX = itrXhrn.next();
      HeapRegionNode hrnX = edgeX.getDst();

      // if we are looking at the null region, skip
      if( hrnX == hrnNull ) {
	continue;
      }

      // we can do a strong update here if one of two cases holds	
      if( f != null &&
	  f != OwnershipAnalysis.getArrayField( f.getType() ) &&	    
	  (   (hrnX.getNumReferencers()                         == 1) || // case 1
	      (hrnX.isSingleObject() && lnX.getNumReferencees() == 1)    // case 2
	      )
	  ) {
	strongUpdate = true;
	clearReferenceEdgesFrom( hrnX, f.getType(), f.getSymbol(), false );
      }
    }
    
    // then do all token propagation
    itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      ReferenceEdge edgeX = itrXhrn.next();
      HeapRegionNode hrnX = edgeX.getDst();
      ReachabilitySet betaX = edgeX.getBeta();

      // if we are looking at the null region, skip
      if( hrnX == hrnNull ) {
	continue;
      }

      ReachabilitySet R = hrnX.getAlpha().intersection(edgeX.getBeta() );

      Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
      while( itrYhrn.hasNext() ) {
	ReferenceEdge edgeY = itrYhrn.next();
	HeapRegionNode hrnY = edgeY.getDst();
	ReachabilitySet O = edgeY.getBeta();


	// propagate tokens over nodes starting from hrnSrc, and it will
	// take care of propagating back up edges from any touched nodes
	ChangeTupleSet Cy = O.unionUpArityToChangeSet(R);
	propagateTokensOverNodes(hrnY, Cy, nodesWithNewAlpha, edgesWithNewBeta);


	// then propagate back just up the edges from hrn
	ChangeTupleSet Cx = R.unionUpArityToChangeSet(O);
  	HashSet<ReferenceEdge> todoEdges = new HashSet<ReferenceEdge>();

	Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges =
	  new Hashtable<ReferenceEdge, ChangeTupleSet>();

	Iterator<ReferenceEdge> referItr = hrnX.iteratorToReferencers();
	while( referItr.hasNext() ) {
	  ReferenceEdge edgeUpstream = referItr.next();
	  todoEdges.add(edgeUpstream);
	  edgePlannedChanges.put(edgeUpstream, Cx);
	}

	propagateTokensOverEdges(todoEdges,
	                         edgePlannedChanges,
	                         edgesWithNewBeta);
      }
    }


    // apply the updates to reachability
    Iterator<HeapRegionNode> nodeItr = nodesWithNewAlpha.iterator();
    while( nodeItr.hasNext() ) {
      nodeItr.next().applyAlphaNew();
    }

    Iterator<ReferenceEdge> edgeItr = edgesWithNewBeta.iterator();
    while( edgeItr.hasNext() ) {
      edgeItr.next().applyBetaNew();
    }


    // then go back through and add the new edges
    itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      ReferenceEdge edgeX = itrXhrn.next();
      HeapRegionNode hrnX = edgeX.getDst();

      // if we are looking at the null region, skip
      if( hrnX == hrnNull ) {
	continue;
      }

      Iterator<ReferenceEdge> itrYhrn = lnY.iteratorToReferencees();
      while( itrYhrn.hasNext() ) {
	ReferenceEdge edgeY = itrYhrn.next();
	HeapRegionNode hrnY = edgeY.getDst();

	// prepare the new reference edge hrnX.f -> hrnY
	ReferenceEdge edgeNew = new ReferenceEdge(hrnX,
	                                          hrnY,
	                                          f.getType(),
						  f.getSymbol(),
	                                          false,
	                                          edgeY.getBeta().pruneBy( hrnX.getAlpha() )
	                                          );

	// look to see if an edge with same field exists
	// and merge with it, otherwise just add the edge
	ReferenceEdge edgeExisting = hrnX.getReferenceTo( hrnY, 
							  f.getType(),
							  f.getSymbol() );
	
	if( edgeExisting != null ) {
	  edgeExisting.setBeta(
			       edgeExisting.getBeta().union( edgeNew.getBeta() )
			      );
	  // a new edge here cannot be reflexive, so existing will
	  // always be also not reflexive anymore
	  edgeExisting.setIsInitialParam( false );
	} else {
	  addReferenceEdge( hrnX, hrnY, edgeNew );
	}
      }
    }

    // if there was a strong update, make sure to improve
    // reachability with a global sweep
    if( strongUpdate ) {      
      globalSweep();
    }
  }




  // the parameter model is to use a single-object heap region
  // for the primary parameter, and a multiple-object heap
  // region for the secondary objects reachable through the
  // primary object, if necessary
  public void assignTempEqualToParamAlloc( TempDescriptor td,
					   boolean isTask,
					   Integer paramIndex ) {
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
	  OwnershipAnalysis.getArrayField( typeDeref )
				   );
	createSecondaryRegion = true;

	// also handle a special case where an array of objects
	// can point back to the array, which is an object!
	if( typeParam.toPrettyString().equals( "Object[]" ) &&
	    typeDeref.toPrettyString().equals( "Object" ) ) {

	  primary2primaryFields.add( 
	    OwnershipAnalysis.getArrayField( typeDeref )
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
    LabelNode lnParam = getLabelNodeFromTemp( td );
    HeapRegionNode hrnPrimary = createNewHeapRegionNode( null,       // id or null to generate a new one 
							 true,	     // single object?			 	
							 false,      // summary?			 
							 false,      // flagged?			 
							 true,       // is a parameter?			 
							 typeParam,  // type				 
							 null,       // allocation site			 
							 null,       // reachability set                 
							 "param"+paramIndex+" obj" );

    // this is a non-program-accessible label that picks up beta
    // info to be used for fixing a caller of this method
    TempDescriptor tdParamQ = new TempDescriptor( td+qString );
    paramIndex2tdQ.put( paramIndex, tdParamQ );    
    LabelNode lnParamQ = getLabelNodeFromTemp( tdParamQ );

    // keep track of heap regions that were created for
    // parameter labels, the index of the parameter they
    // are for is important when resolving method calls
    Integer newPrimaryID = hrnPrimary.getID();
    assert !idPrimary2paramIndexSet.containsKey( newPrimaryID );
    Set<Integer> s = new HashSet<Integer>();
    s.add( paramIndex );
    idPrimary2paramIndexSet.put( newPrimaryID, s );
    paramIndex2idPrimary.put( paramIndex, newPrimaryID );

    
    TokenTuple ttPrimary = new TokenTuple( newPrimaryID,
					   false, // multi-object
					   TokenTuple.ARITY_ONE ).makeCanonical();    
    //TokenTuple ttPrimary = new TokenTuple( hrnPrimary ).makeCanonical();

        
    HeapRegionNode hrnSecondary   = null;
    Integer        newSecondaryID = null;
    TokenTuple     ttSecondary    = null;    
    TempDescriptor tdParamR       = null;
    LabelNode      lnParamR       = null;
 
    if( createSecondaryRegion ) {
      tdParamR = new TempDescriptor( td+rString );
      paramIndex2tdR.put( paramIndex, tdParamR );    
      lnParamR = getLabelNodeFromTemp( tdParamR );

      hrnSecondary = createNewHeapRegionNode( null,  // id or null to generate a new one  
					      false, // single object?			 
					      false, // summary?			 
					      false, // flagged?			 
					      true,  // is a parameter?			 
					      null,  // type				 
					      null,  // allocation site			 
					      null,  // reachability set                 
					      "param"+paramIndex+" reachable" );

      newSecondaryID = hrnSecondary.getID();
      assert !idSecondary2paramIndexSet.containsKey( newSecondaryID );
      Set<Integer> s2 = new HashSet<Integer>();
      s2.add( paramIndex );
      idSecondary2paramIndexSet.put( newSecondaryID, s2 );
      paramIndex2idSecondary.put( paramIndex, newSecondaryID );
            
      
      ttSecondary = new TokenTuple( newSecondaryID,
				    true, // multi-object
				    TokenTuple.ARITY_ONE ).makeCanonical();      
      //ttSecondary = new TokenTuple( hrnSecondary ).makeCanonical();
    }

    // use a beta that has everything and put it all over the
    // parameter model, then use a global sweep later to fix
    // it up, since parameters can have different shapes
    TokenTupleSet tts0 = new TokenTupleSet( ttPrimary ).makeCanonical();
    ReachabilitySet betaSoup;
    if( createSecondaryRegion ) {
      TokenTupleSet tts1 = new TokenTupleSet( ttSecondary ).makeCanonical();
      TokenTupleSet tts2 = new TokenTupleSet( ttPrimary   ).makeCanonical().union( ttSecondary );   
      betaSoup = ReachabilitySet.factory( tts0 ).union( tts1 ).union( tts2 );
    } else {
      betaSoup = ReachabilitySet.factory( tts0 );
    }

    ReferenceEdge edgeFromLabel =
      new ReferenceEdge( lnParam,            // src
			 hrnPrimary,         // dst
			 typeParam,          // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    addReferenceEdge( lnParam, hrnPrimary, edgeFromLabel );

    ReferenceEdge edgeFromLabelQ =
      new ReferenceEdge( lnParamQ,           // src
			 hrnPrimary,         // dst
			 null,               // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    addReferenceEdge( lnParamQ, hrnPrimary, edgeFromLabelQ );
    
    ReferenceEdge edgeSecondaryReflexive;
    if( createSecondaryRegion ) {
      edgeSecondaryReflexive =
	new ReferenceEdge( hrnSecondary,    // src
			   hrnSecondary,    // dst
			   null,            // match all types
			   null,            // match all fields
			   true,            // special param initial
			   betaSoup );      // reachability
      addReferenceEdge( hrnSecondary, hrnSecondary, edgeSecondaryReflexive );

      ReferenceEdge edgeSecondary2Primary =
	new ReferenceEdge( hrnSecondary,    // src
			   hrnPrimary,      // dst
			   null,            // match all types
			   null,            // match all fields
			   true,            // special param initial
			   betaSoup );      // reachability
      addReferenceEdge( hrnSecondary, hrnPrimary, edgeSecondary2Primary );

      ReferenceEdge edgeFromLabelR =
	new ReferenceEdge( lnParamR,           // src
			   hrnSecondary,       // dst
			   null,               // type
			   null,               // field
			   false,              // special param initial (not needed on label->node)
			   betaSoup );         // reachability
      addReferenceEdge( lnParamR, hrnSecondary, edgeFromLabelR );
    }
    
    Iterator<FieldDescriptor> fieldItr = primary2primaryFields.iterator();
    while( fieldItr.hasNext() ) {
      FieldDescriptor fd = fieldItr.next();

      ReferenceEdge edgePrimaryReflexive =
	new ReferenceEdge( hrnPrimary,     // src
			   hrnPrimary,     // dst
			   fd.getType(),   // type
			   fd.getSymbol(), // field
			   true,           // special param initial
			   betaSoup );     // reachability      
      addReferenceEdge( hrnPrimary, hrnPrimary, edgePrimaryReflexive );
    }

    fieldItr = primary2secondaryFields.iterator();
    while( fieldItr.hasNext() ) {
      FieldDescriptor fd = fieldItr.next();

      ReferenceEdge edgePrimary2Secondary =
	new ReferenceEdge( hrnPrimary,     // src
			   hrnSecondary,   // dst
			   fd.getType(),   // type
			   fd.getSymbol(), // field
			   true,           // special param initial
			   betaSoup );     // reachability      
      addReferenceEdge( hrnPrimary, hrnSecondary, edgePrimary2Secondary );
    }
  }


  public void makeAliasedParamHeapRegionNode() {

    LabelNode lnBlob = getLabelNodeFromTemp( tdAliasBlob );
    HeapRegionNode hrn = createNewHeapRegionNode( null,  // id or null to generate a new one 
						  false, // single object?			 
						  false, // summary?			 
						  false, // flagged?			 
						  true,	 // is a parameter?			 
						  null,	 // type				 
						  null,	 // allocation site			 
						  null,	 // reachability set                 
						  "aliasedParams" );

    
    ReachabilitySet beta = new ReachabilitySet( new TokenTuple( hrn.getID(),
								true,
								TokenTuple.ARITY_ONE).makeCanonical()
						).makeCanonical();
    
    //ReachabilitySet beta = new ReachabilitySet( new TokenTuple( hrn ).makeCanonical()
    //						).makeCanonical();
    
    ReferenceEdge edgeFromLabel =
      new ReferenceEdge( lnBlob, hrn, null, null, false, beta );

    ReferenceEdge edgeReflexive =
      new ReferenceEdge( hrn,    hrn, null, null, true,  beta );

    addReferenceEdge( lnBlob, hrn, edgeFromLabel );
    addReferenceEdge( hrn,    hrn, edgeReflexive );
  }


  public void assignTempEqualToAliasedParam( TempDescriptor tdParam,
					     Integer        paramIndex ) {
    assert tdParam != null;

    TypeDescriptor typeParam = tdParam.getType();
    assert typeParam != null;

    LabelNode lnParam   = getLabelNodeFromTemp( tdParam );    
    LabelNode lnAliased = getLabelNodeFromTemp( tdAliasBlob );

    // this is a non-program-accessible label that picks up beta
    // info to be used for fixing a caller of this method
    TempDescriptor tdParamQ = new TempDescriptor( tdParam+qString );
    TempDescriptor tdParamR = new TempDescriptor( tdParam+rString );

    paramIndex2tdQ.put( paramIndex, tdParamQ );
    paramIndex2tdR.put( paramIndex, tdParamR );

    LabelNode lnParamQ = getLabelNodeFromTemp( tdParamQ );
    LabelNode lnParamR = getLabelNodeFromTemp( tdParamR );

    // the lnAliased should always only reference one node, and that
    // heap region node is the aliased param blob
    assert lnAliased.getNumReferencees() == 1;
    HeapRegionNode hrnAliasBlob = lnAliased.iteratorToReferencees().next().getDst();
    Integer idAliased = hrnAliasBlob.getID();

    
    TokenTuple ttAliased = new TokenTuple( idAliased,
					   true, // multi-object
					   TokenTuple.ARITY_ONE ).makeCanonical();         
    //TokenTuple ttAliased = new TokenTuple( hrnAliasBlob ).makeCanonical();     


    HeapRegionNode hrnPrimary = createNewHeapRegionNode( null,      // id or null to generate a new one 
							 true,	    // single object?			 
							 false,	    // summary?			 
							 false,     // flagged?			  
							 true,	    // is a parameter?			 
							 typeParam, // type				 
							 null,	    // allocation site			 
							 null,	    // reachability set                 
							 "param"+paramIndex+" obj" );

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
    

    
    TokenTuple ttPrimary = new TokenTuple( newPrimaryID,
					   false, // multi-object
					   TokenTuple.ARITY_ONE ).makeCanonical();   
    //TokenTuple ttPrimary = new TokenTuple( hrnPrimary ).makeCanonical();


    
    TokenTupleSet tts0 = new TokenTupleSet( ttPrimary ).makeCanonical();
    TokenTupleSet tts1 = new TokenTupleSet( ttAliased ).makeCanonical();
    TokenTupleSet tts2 = new TokenTupleSet( ttPrimary ).makeCanonical().union( ttAliased );   
    ReachabilitySet betaSoup = ReachabilitySet.factory( tts0 ).union( tts1 ).union( tts2 );


    ReferenceEdge edgeFromLabel =
      new ReferenceEdge( lnParam,            // src
			 hrnPrimary,         // dst
			 typeParam,          // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    addReferenceEdge( lnParam, hrnPrimary, edgeFromLabel );

    ReferenceEdge edgeFromLabelQ =
      new ReferenceEdge( lnParamQ,           // src
			 hrnPrimary,         // dst
			 null,               // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    addReferenceEdge( lnParamQ, hrnPrimary, edgeFromLabelQ );
    
    ReferenceEdge edgeAliased2Primary =
      new ReferenceEdge( hrnAliasBlob,    // src
			 hrnPrimary,      // dst
			 null,            // match all types
			 null,            // match all fields
			 true,            // special param initial
			 betaSoup );      // reachability
    addReferenceEdge( hrnAliasBlob, hrnPrimary, edgeAliased2Primary );    

    ReferenceEdge edgeFromLabelR =
      new ReferenceEdge( lnParamR,           // src
			 hrnAliasBlob,       // dst
			 null,               // type
			 null,               // field
			 false,              // special param initial (not needed on label->node)
			 betaSoup );         // reachability
    addReferenceEdge( lnParamR, hrnAliasBlob, edgeFromLabelR );
  }


  public void addParam2ParamAliasEdges( FlatMethod fm,
					Set<Integer> aliasedParamIndices ) {

    LabelNode lnAliased = getLabelNodeFromTemp( tdAliasBlob );

    // the lnAliased should always only reference one node, and that
    // heap region node is the aliased param blob
    assert lnAliased.getNumReferencees() == 1;
    HeapRegionNode hrnAliasBlob = lnAliased.iteratorToReferencees().next().getDst();
    Integer idAliased = hrnAliasBlob.getID();

   
    TokenTuple ttAliased = new TokenTuple( idAliased,
					   true, // multi-object
					   TokenTuple.ARITY_ONE ).makeCanonical();      
    //TokenTuple ttAliased = new TokenTuple( hrnAliasBlob ).makeCanonical();


    Iterator<Integer> apItrI = aliasedParamIndices.iterator();
    while( apItrI.hasNext() ) {
      Integer i = apItrI.next();
      TempDescriptor tdParamI = fm.getParameter( i );
      TypeDescriptor typeI    = tdParamI.getType();
      LabelNode      lnParamI = getLabelNodeFromTemp( tdParamI );

      Integer idPrimaryI = paramIndex2idPrimary.get( i );
      assert idPrimaryI != null;
      HeapRegionNode primaryI = id2hrn.get( idPrimaryI );
      assert primaryI != null;           
      
      TokenTuple ttPrimaryI = new TokenTuple( idPrimaryI,
					      false, // multi-object
					      TokenTuple.ARITY_ONE ).makeCanonical();
      
      TokenTupleSet ttsI  = new TokenTupleSet( ttPrimaryI ).makeCanonical();
      TokenTupleSet ttsA  = new TokenTupleSet( ttAliased  ).makeCanonical();
      TokenTupleSet ttsIA = new TokenTupleSet( ttPrimaryI ).makeCanonical().union( ttAliased );   
      ReachabilitySet betaSoup = ReachabilitySet.factory( ttsI ).union( ttsA ).union( ttsIA );


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
	
	// for this parameter to be aliased the following must be true
	assert !typeDeref.isImmutable() || typeDeref.isArray();
	
	primary2secondaryFields.add( 
	  OwnershipAnalysis.getArrayField( typeDeref )
				   );

	// also handle a special case where an array of objects
	// can point back to the array, which is an object!
	if( typeI    .toPrettyString().equals( "Object[]" ) &&
	    typeDeref.toPrettyString().equals( "Object" ) ) {
	  primary2primaryFields.add( 
	    OwnershipAnalysis.getArrayField( typeDeref )
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
	
	ReferenceEdge edgePrimaryReflexive =
	  new ReferenceEdge( primaryI,       // src
			     primaryI,       // dst
			     fd.getType(),   // type
			     fd.getSymbol(), // field
			     true,           // special param initial
			     betaSoup );     // reachability      
	addReferenceEdge( primaryI, primaryI, edgePrimaryReflexive );
      }

      fieldItr = primary2secondaryFields.iterator();
      while( fieldItr.hasNext() ) {
	FieldDescriptor fd = fieldItr.next();
	TypeDescriptor typeField = fd.getType();
	assert typeField != null;	
	
	ReferenceEdge edgePrimary2Secondary =
	  new ReferenceEdge( primaryI,       // src
			     hrnAliasBlob,   // dst
			     fd.getType(),   // type
			     fd.getSymbol(), // field
			     true,           // special param initial
			     betaSoup );     // reachability
	addReferenceEdge( primaryI, hrnAliasBlob, edgePrimary2Secondary );

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

	    TokenTuple ttPrimaryJ = new TokenTuple( idPrimaryJ,
						    false, // multi-object
						    TokenTuple.ARITY_ONE ).makeCanonical();

	    TokenTupleSet ttsJ   = new TokenTupleSet( ttPrimaryJ ).makeCanonical();
	    TokenTupleSet ttsIJ  = ttsI.union( ttsJ );
	    TokenTupleSet ttsAJ  = ttsA.union( ttsJ );
	    TokenTupleSet ttsIAJ = ttsIA.union( ttsJ );
	    ReachabilitySet betaSoupWJ = ReachabilitySet.factory( ttsJ ).union( ttsIJ ).union( ttsAJ ).union( ttsIAJ );

	    ReferenceEdge edgePrimaryI2PrimaryJ =
	      new ReferenceEdge( primaryI,       // src
				 primaryJ,       // dst
				 fd.getType(),   // type
				 fd.getSymbol(), // field
				 true,           // special param initial
				 betaSoupWJ );   // reachability
	    addReferenceEdge( primaryI, primaryJ, edgePrimaryI2PrimaryJ );
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
	LabelNode      lnParamJ = getLabelNodeFromTemp( tdParamJ );

	if( !i.equals( j ) && typeUtil.isSuperorType( typeI, typeJ ) ) {
	  	  	  
	  Integer idPrimaryJ = paramIndex2idPrimary.get( j );
	  assert idPrimaryJ != null;
	  HeapRegionNode primaryJ = id2hrn.get( idPrimaryJ );
	  assert primaryJ != null;
	  
	  ReferenceEdge lnJ2PrimaryJ = lnParamJ.getReferenceTo( primaryJ,
								tdParamJ.getType(),	
								null );
	  assert lnJ2PrimaryJ != null;
	  
	  ReferenceEdge lnI2PrimaryJ = lnJ2PrimaryJ.copy();
	  lnI2PrimaryJ.setSrc( lnParamI );
	  lnI2PrimaryJ.setType( tdParamI.getType() );
	  addReferenceEdge( lnParamI, primaryJ, lnI2PrimaryJ );
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
	
	TokenTuple p_i = new TokenTuple( hrnPrimary.getID(),
					 false, // multiple-object?
					 TokenTuple.ARITY_ONE ).makeCanonical();
	paramTokenPrimary2paramIndex.put( p_i, paramIndex );
	paramIndex2paramTokenPrimary.put( paramIndex, p_i );	
      }	
	
      // any parameter object, by type, may have no secondary region
      if( paramIndex2idSecondary.containsKey( paramIndex ) ) {
	Integer idSecondary = paramIndex2idSecondary.get( paramIndex );
	
	assert id2hrn.containsKey( idSecondary );
	HeapRegionNode hrnSecondary = id2hrn.get( idSecondary );
	
	TokenTuple s_i = new TokenTuple( hrnSecondary.getID(),
					 true, // multiple-object?
					 TokenTuple.ARITY_ONE ).makeCanonical();
	paramTokenSecondary2paramIndex.put( s_i, paramIndex );
	paramIndex2paramTokenSecondary.put( paramIndex, s_i );
	
	TokenTuple s_i_plus = new TokenTuple( hrnSecondary.getID(),
					      true, // multiple-object?
					      TokenTuple.ARITY_ONEORMORE ).makeCanonical();
	paramTokenSecondaryPlus2paramIndex.put( s_i_plus, paramIndex );
	paramIndex2paramTokenSecondaryPlus.put( paramIndex, s_i_plus );
	
	TokenTuple s_i_star = new TokenTuple( hrnSecondary.getID(),
					      true, // multiple-object?
					      TokenTuple.ARITY_ZEROORMORE ).makeCanonical();
	paramTokenSecondaryStar2paramIndex.put( s_i_star, paramIndex );
	paramIndex2paramTokenSecondaryStar.put( paramIndex, s_i_star );
      }
    }
  }



  public void assignReturnEqualToTemp(TempDescriptor x) {

    LabelNode lnR = getLabelNodeFromTemp(tdReturn);
    LabelNode lnX = getLabelNodeFromTemp(x);

    clearReferenceEdgesFrom(lnR, null, null, true);

    Iterator<ReferenceEdge> itrXhrn = lnX.iteratorToReferencees();
    while( itrXhrn.hasNext() ) {
      ReferenceEdge edgeX       = itrXhrn.next();
      HeapRegionNode referencee = edgeX.getDst();
      ReferenceEdge edgeNew    = edgeX.copy();
      edgeNew.setSrc(lnR);

      addReferenceEdge(lnR, referencee, edgeNew);
    }
  }


  public void assignTempEqualToNewAlloc(TempDescriptor x,
                                        AllocationSite as) {
    assert x  != null;
    assert as != null;

    age(as);

    // after the age operation the newest (or zero-ith oldest)
    // node associated with the allocation site should have
    // no references to it as if it were a newly allocated
    // heap region, so make a reference to it to complete
    // this operation

    Integer idNewest  = as.getIthOldest(0);
    HeapRegionNode hrnNewest = id2hrn.get(idNewest);
    assert hrnNewest != null;

    LabelNode lnX = getLabelNodeFromTemp(x);
    clearReferenceEdgesFrom(lnX, null, null, true);

    ReferenceEdge edgeNew =
      new ReferenceEdge(lnX, hrnNewest, null, null, false, hrnNewest.getAlpha() );

    addReferenceEdge(lnX, hrnNewest, edgeNew);
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
  public void age(AllocationSite as) {

    // aging adds this allocation site to the graph's
    // list of sites that exist in the graph, or does
    // nothing if the site is already in the list
    allocationSites.add(as);

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
    clearReferenceEdgesFrom(hrn0, null, null, true);
    clearReferenceEdgesTo(hrn0, null, null, true);


    // now tokens in reachability sets need to "age" also
    Iterator itrAllLabelNodes = td2ln.entrySet().iterator();
    while( itrAllLabelNodes.hasNext() ) {
      Map.Entry me = (Map.Entry)itrAllLabelNodes.next();
      LabelNode ln = (LabelNode) me.getValue();

      Iterator<ReferenceEdge> itrEdges = ln.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTokens(as, itrEdges.next() );
      }
    }

    Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
    while( itrAllHRNodes.hasNext() ) {
      Map.Entry me       = (Map.Entry)itrAllHRNodes.next();
      HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

      ageTokens(as, hrnToAge);

      Iterator<ReferenceEdge> itrEdges = hrnToAge.iteratorToReferencees();
      while( itrEdges.hasNext() ) {
	ageTokens(as, itrEdges.next() );
      }
    }


    // after tokens have been aged, reset newest node's reachability
    if( hrn0.isFlagged() ) {
      hrn0.setAlpha(new ReachabilitySet(
                      new TokenTupleSet(
                        new TokenTuple(hrn0).makeCanonical()
                        ).makeCanonical()
                      ).makeCanonical()
                    );
    } else {
      hrn0.setAlpha(new ReachabilitySet(
                      new TokenTupleSet().makeCanonical()
                      ).makeCanonical()
                    );
    }
  }


  protected HeapRegionNode getSummaryNode(AllocationSite as) {

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

      hrnSummary = createNewHeapRegionNode(idSummary,    // id or null to generate a new one 
                                           false,	 // single object?			 
                                           true,	 // summary?			 
                                           hasFlags,	 // flagged?			 
                                           false,	 // is a parameter?			 
					   as.getType(), // type				 
                                           as,		 // allocation site			 
                                           null,	 // reachability set                 
                                           as.toStringForDOT() + "\\nsummary");

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
	                        as.toStringForDOT() + "\\n" + i + " oldest");
      }
    }

    return hrnSummary;
  }


  protected HeapRegionNode getShadowSummaryNode(AllocationSite as) {

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
                                                 as + "\\n" + as.getType() + "\\nshadowSum");

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
	                        as + "\\n" + as.getType() + "\\n" + i + " shadow");
      }
    }

    return hrnShadowSummary;
  }


  protected void mergeIntoSummary(HeapRegionNode hrn, HeapRegionNode hrnSummary) {
    assert hrnSummary.isNewSummary();

    // transfer references _from_ hrn over to hrnSummary
    Iterator<ReferenceEdge> itrReferencee = hrn.iteratorToReferencees();
    while( itrReferencee.hasNext() ) {
      ReferenceEdge edge       = itrReferencee.next();
      ReferenceEdge edgeMerged = edge.copy();
      edgeMerged.setSrc(hrnSummary);

      HeapRegionNode hrnReferencee = edge.getDst();
      ReferenceEdge edgeSummary   = hrnSummary.getReferenceTo(hrnReferencee, 
							      edge.getType(),
							      edge.getField() );

      if( edgeSummary == null ) {
	// the merge is trivial, nothing to be done
      } else {
	// otherwise an edge from the referencer to hrnSummary exists already
	// and the edge referencer->hrn should be merged with it
	edgeMerged.setBeta(edgeMerged.getBeta().union(edgeSummary.getBeta() ) );
      }

      addReferenceEdge(hrnSummary, hrnReferencee, edgeMerged);
    }

    // next transfer references _to_ hrn over to hrnSummary
    Iterator<ReferenceEdge> itrReferencer = hrn.iteratorToReferencers();
    while( itrReferencer.hasNext() ) {
      ReferenceEdge edge         = itrReferencer.next();
      ReferenceEdge edgeMerged   = edge.copy();
      edgeMerged.setDst(hrnSummary);

      OwnershipNode onReferencer = edge.getSrc();
      ReferenceEdge edgeSummary  = onReferencer.getReferenceTo(hrnSummary, 
							       edge.getType(),
							       edge.getField() );

      if( edgeSummary == null ) {
	// the merge is trivial, nothing to be done
      } else {
	// otherwise an edge from the referencer to alpha_S exists already
	// and the edge referencer->alpha_K should be merged with it
	edgeMerged.setBeta(edgeMerged.getBeta().union(edgeSummary.getBeta() ) );
      }

      addReferenceEdge(onReferencer, hrnSummary, edgeMerged);
    }

    // then merge hrn reachability into hrnSummary
    hrnSummary.setAlpha(hrnSummary.getAlpha().union(hrn.getAlpha() ) );
  }


  protected void transferOnto(HeapRegionNode hrnA, HeapRegionNode hrnB) {

    // clear references in and out of node b
    clearReferenceEdgesFrom(hrnB, null, null, true);
    clearReferenceEdgesTo(hrnB, null, null, true);

    // copy each edge in and out of A to B
    Iterator<ReferenceEdge> itrReferencee = hrnA.iteratorToReferencees();
    while( itrReferencee.hasNext() ) {
      ReferenceEdge edge          = itrReferencee.next();
      HeapRegionNode hrnReferencee = edge.getDst();
      ReferenceEdge edgeNew       = edge.copy();
      edgeNew.setSrc(hrnB);

      addReferenceEdge(hrnB, hrnReferencee, edgeNew);
    }

    Iterator<ReferenceEdge> itrReferencer = hrnA.iteratorToReferencers();
    while( itrReferencer.hasNext() ) {
      ReferenceEdge edge         = itrReferencer.next();
      OwnershipNode onReferencer = edge.getSrc();
      ReferenceEdge edgeNew      = edge.copy();
      edgeNew.setDst(hrnB);

      addReferenceEdge(onReferencer, hrnB, edgeNew);
    }

    // replace hrnB reachability with hrnA's
    hrnB.setAlpha(hrnA.getAlpha() );
  }


  protected void ageTokens(AllocationSite as, ReferenceEdge edge) {
    edge.setBeta(edge.getBeta().ageTokens(as) );
  }

  protected void ageTokens(AllocationSite as, HeapRegionNode hrn) {
    hrn.setAlpha(hrn.getAlpha().ageTokens(as) );
  }



  protected void propagateTokensOverNodes(HeapRegionNode nPrime,
                                          ChangeTupleSet c0,
                                          HashSet<HeapRegionNode> nodesWithNewAlpha,
                                          HashSet<ReferenceEdge>  edgesWithNewBeta) {

    HashSet<HeapRegionNode> todoNodes
      = new HashSet<HeapRegionNode>();
    todoNodes.add(nPrime);

    HashSet<ReferenceEdge> todoEdges
      = new HashSet<ReferenceEdge>();

    Hashtable<HeapRegionNode, ChangeTupleSet> nodePlannedChanges
      = new Hashtable<HeapRegionNode, ChangeTupleSet>();
    nodePlannedChanges.put(nPrime, c0);

    Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges
      = new Hashtable<ReferenceEdge, ChangeTupleSet>();

    // first propagate change sets everywhere they can go
    while( !todoNodes.isEmpty() ) {
      HeapRegionNode n = todoNodes.iterator().next();
      ChangeTupleSet C = nodePlannedChanges.get(n);

      Iterator<ReferenceEdge> referItr = n.iteratorToReferencers();
      while( referItr.hasNext() ) {
	ReferenceEdge edge = referItr.next();
	todoEdges.add(edge);

	if( !edgePlannedChanges.containsKey(edge) ) {
	  edgePlannedChanges.put(edge, new ChangeTupleSet().makeCanonical() );
	}

	edgePlannedChanges.put(edge, edgePlannedChanges.get(edge).union(C) );
      }

      Iterator<ReferenceEdge> refeeItr = n.iteratorToReferencees();
      while( refeeItr.hasNext() ) {
	ReferenceEdge edgeF = refeeItr.next();
	HeapRegionNode m     = edgeF.getDst();

	ChangeTupleSet changesToPass = new ChangeTupleSet().makeCanonical();

	Iterator<ChangeTuple> itrCprime = C.iterator();
	while( itrCprime.hasNext() ) {
	  ChangeTuple c = itrCprime.next();
	  if( edgeF.getBeta().contains( c.getSetToMatch() ) ) {
	    changesToPass = changesToPass.union(c);
	  }
	}

	if( !changesToPass.isEmpty() ) {
	  if( !nodePlannedChanges.containsKey(m) ) {
	    nodePlannedChanges.put(m, new ChangeTupleSet().makeCanonical() );
	  }

	  ChangeTupleSet currentChanges = nodePlannedChanges.get(m);

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
      ChangeTupleSet C  = (ChangeTupleSet) me.getValue();

      n.setAlphaNew( n.getAlpha().applyChangeSet( C, true ) );
      nodesWithNewAlpha.add( n );
    }

    propagateTokensOverEdges(todoEdges, edgePlannedChanges, edgesWithNewBeta);
  }


  protected void propagateTokensOverEdges(
    HashSet<ReferenceEdge>                   todoEdges,
    Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges,
    HashSet<ReferenceEdge>                   edgesWithNewBeta) {

    // first propagate all change tuples everywhere they can go
    while( !todoEdges.isEmpty() ) {
      ReferenceEdge edgeE = todoEdges.iterator().next();
      todoEdges.remove(edgeE);

      if( !edgePlannedChanges.containsKey(edgeE) ) {
	edgePlannedChanges.put(edgeE, new ChangeTupleSet().makeCanonical() );
      }

      ChangeTupleSet C = edgePlannedChanges.get(edgeE);

      ChangeTupleSet changesToPass = new ChangeTupleSet().makeCanonical();

      Iterator<ChangeTuple> itrC = C.iterator();
      while( itrC.hasNext() ) {
	ChangeTuple c = itrC.next();
	if( edgeE.getBeta().contains( c.getSetToMatch() ) ) {
	  changesToPass = changesToPass.union(c);
	}
      }

      OwnershipNode onSrc = edgeE.getSrc();

      if( !changesToPass.isEmpty() && onSrc instanceof HeapRegionNode ) {
	HeapRegionNode n = (HeapRegionNode) onSrc;

	Iterator<ReferenceEdge> referItr = n.iteratorToReferencers();
	while( referItr.hasNext() ) {
	  ReferenceEdge edgeF = referItr.next();

	  if( !edgePlannedChanges.containsKey(edgeF) ) {
	    edgePlannedChanges.put(edgeF, new ChangeTupleSet().makeCanonical() );
	  }

	  ChangeTupleSet currentChanges = edgePlannedChanges.get(edgeF);

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
      ReferenceEdge  e  = (ReferenceEdge)  me.getKey();
      ChangeTupleSet C  = (ChangeTupleSet) me.getValue();

      e.setBetaNew( e.getBetaNew().union( e.getBeta().applyChangeSet( C, true ) ) );
      edgesWithNewBeta.add( e );
    }
  }


  public Set<Integer> calculateAliasedParamSet( FlatCall fc,
						boolean isStatic,
						FlatMethod fm ) {

    Hashtable<Integer, LabelNode> paramIndex2ln =
      new Hashtable<Integer, LabelNode>();

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
      TempDescriptor argTemp_i;
      if( isStatic ) {
	argTemp_i = fc.getArg(paramIndex);
      } else {
	if( paramIndex.equals(0) ) {
	  argTemp_i = fc.getThis();
	} else {
	  argTemp_i = fc.getArg(paramIndex - 1);
	}
      }

      // in non-static methods there is a "this" pointer
      // that should be taken into account
      if( isStatic ) {
	assert fc.numArgs()     == fm.numParameters();
      } else {
	assert fc.numArgs() + 1 == fm.numParameters();
      }

      LabelNode argLabel_i = getLabelNodeFromTemp(argTemp_i);
      paramIndex2ln.put(paramIndex, argLabel_i);
    }

    Iterator lnArgItr = paramIndex2ln.entrySet().iterator();
    while( lnArgItr.hasNext() ) {
      Map.Entry me      = (Map.Entry)lnArgItr.next();
      Integer index     = (Integer)   me.getKey();
      LabelNode lnArg_i = (LabelNode) me.getValue();

      HashSet<HeapRegionNode> reachableNodes = new HashSet<HeapRegionNode>();
      HashSet<HeapRegionNode> todoNodes      = new HashSet<HeapRegionNode>();

      // to find all reachable nodes, start with label referencees
      Iterator<ReferenceEdge> edgeArgItr = lnArg_i.iteratorToReferencees();
      while( edgeArgItr.hasNext() ) {
	ReferenceEdge edge = edgeArgItr.next();
	todoNodes.add( edge.getDst() );
      }

      // then follow links until all reachable nodes have been found
      while( !todoNodes.isEmpty() ) {
	HeapRegionNode hrn = todoNodes.iterator().next();
	todoNodes.remove(hrn);
	reachableNodes.add(hrn);

	Iterator<ReferenceEdge> edgeItr = hrn.iteratorToReferencees();
	while( edgeItr.hasNext() ) {
	  ReferenceEdge edge = edgeItr.next();

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
				 ReferenceEdge edge,
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

  private ReachabilitySet funcScriptR( ReachabilitySet rsIn, 
				       OwnershipGraph  ogCallee,
				       MethodContext   mc ) {

    ReachabilitySet rsOut = new ReachabilitySet( rsIn );

    Iterator itr = ogCallee.paramIndex2paramTokenPrimary.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry  me  = (Map.Entry)  itr.next();
      Integer    i   = (Integer)    me.getKey();
      TokenTuple p_i = (TokenTuple) me.getValue();
      TokenTuple s_i = ogCallee.paramIndex2paramTokenSecondary.get( i );

      // skip this if there is no secondary token or the parameter
      // is part of the aliasing context
      if( s_i == null || mc.getAliasedParamIndices().contains( i ) ) {
	continue;
      }

      rsOut = rsOut.removeTokenAIfTokenB( p_i, s_i );
    }

    return rsOut;
  }

  private void effectCalleeStrongUpdates( Integer paramIndex,
					  OwnershipGraph ogCallee,
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
	  clearReferenceEdgesFrom( hrnCaller, fd.getType(), fd.getSymbol(), false );
	}
      }
      
      cd = cd.getSuperDesc();
    }
  }

  private boolean hasFieldBeenUpdated( HeapRegionNode hrnPrimary, String field ) {

    Iterator<ReferenceEdge> itr = hrnPrimary.iteratorToReferencees();
    while( itr.hasNext() ) {
      ReferenceEdge e = itr.next();
      if( e.fieldEquals( field ) && e.isInitialParam() ) {
	return false;
      }
    }

    return true;
  }

  public void resolveMethodCall(FlatCall fc,
                                boolean isStatic,
                                FlatMethod fm,
                                OwnershipGraph ogCallee,
				MethodContext mc
				) {

    String debugCaller = "foo";
    String debugCallee = "bar";
    //String debugCaller = "StandardEngine";
    //String debugCaller = "register_by_type";
    //String debugCaller = "register_by_type_front";
    //String debugCaller = "addFirst";
    //String debugCallee = "LinkedListElement";

    if( mc.getDescriptor().getSymbol().equals( debugCaller ) &&
	fm.getMethod().getSymbol().equals( debugCallee ) ) {

      try {
	writeGraph( "debug1BeforeCall", true, true, true, false, false );
	ogCallee.writeGraph( "debug0Callee", true, true, true, false, false );
      } catch( IOException e ) {}

      System.out.println( "  "+mc+" is calling "+fm );
    }


    // define rewrite rules and other structures to organize data by parameter/argument index
    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteH_p = new Hashtable<Integer, ReachabilitySet>();
    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteH_s = new Hashtable<Integer, ReachabilitySet>();
    
    Hashtable<String,  ReachabilitySet> paramIndex2rewriteJ_p2p = new Hashtable<String,  ReachabilitySet>(); // select( i, j, f )
    Hashtable<String,  ReachabilitySet> paramIndex2rewriteJ_p2s = new Hashtable<String,  ReachabilitySet>(); // select( i,    f )
    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteJ_s2p = new Hashtable<Integer, ReachabilitySet>();
    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteJ_s2s = new Hashtable<Integer, ReachabilitySet>();

    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteK_p  = new Hashtable<Integer, ReachabilitySet>();
    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteK_p2 = new Hashtable<Integer, ReachabilitySet>();
    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteK_s  = new Hashtable<Integer, ReachabilitySet>();

    Hashtable<Integer, ReachabilitySet> paramIndex2rewrite_d_p = new Hashtable<Integer, ReachabilitySet>();
    Hashtable<Integer, ReachabilitySet> paramIndex2rewrite_d_s = new Hashtable<Integer, ReachabilitySet>();

    Hashtable<Integer, ReachabilitySet> paramIndex2rewriteD = new Hashtable<Integer, ReachabilitySet>();


    Hashtable<Integer, LabelNode> paramIndex2ln = new Hashtable<Integer, LabelNode>();


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
      Iterator<ReferenceEdge> p2xItr = hrnPrimary.iteratorToReferencees();
      while( p2xItr.hasNext() ) {
	ReferenceEdge p2xEdge = p2xItr.next();

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
      LabelNode lnParamQ = ogCallee.td2ln.get( tdParamQ );
      assert lnParamQ != null;
      ReferenceEdge edgeSpecialQ_i = lnParamQ.getReferenceTo( hrnPrimary, null, null );
      assert edgeSpecialQ_i != null;
      ReachabilitySet qBeta = toShadowTokens( ogCallee, edgeSpecialQ_i.getBeta() );

      TokenTuple p_i = ogCallee.paramIndex2paramTokenPrimary  .get( paramIndex );
      TokenTuple s_i = ogCallee.paramIndex2paramTokenSecondary.get( paramIndex );

      ReachabilitySet K_p  = new ReachabilitySet().makeCanonical();
      ReachabilitySet K_p2 = new ReachabilitySet().makeCanonical();
      if( s_i == null ) {
	K_p = qBeta;
      } else {
	// sort qBeta into K_p1 and K_p2	
	Iterator<TokenTupleSet> ttsItr = qBeta.iterator();
	while( ttsItr.hasNext() ) {
	  TokenTupleSet tts = ttsItr.next();
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
	Iterator<ReferenceEdge> s2xItr = hrnSecondary.iteratorToReferencees();
	while( s2xItr.hasNext() ) {
	  ReferenceEdge s2xEdge = s2xItr.next();
	  
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
	LabelNode lnParamR = ogCallee.td2ln.get( tdParamR );
	assert lnParamR != null;
	ReferenceEdge edgeSpecialR_i = lnParamR.getReferenceTo( hrnSecondary, null, null );
	assert edgeSpecialR_i != null;
	paramIndex2rewriteK_s.put( paramIndex,
				   toShadowTokens( ogCallee, edgeSpecialR_i.getBeta() ) );	
      }
    

      // now depending on whether the callee is static or not
      // we need to account for a "this" argument in order to
      // find the matching argument in the caller context
      TempDescriptor argTemp_i;
      if( isStatic ) {
	argTemp_i = fc.getArg( paramIndex );
      } else {
	if( paramIndex.equals( 0 ) ) {
	  argTemp_i = fc.getThis();
	} else {
	  argTemp_i = fc.getArg( paramIndex - 1 );
	}
      }

      // in non-static methods there is a "this" pointer
      // that should be taken into account
      if( isStatic ) {
	assert fc.numArgs()     == fm.numParameters();
      } else {
	assert fc.numArgs() + 1 == fm.numParameters();
      }

      // remember which caller arg label maps to param index
      LabelNode argLabel_i = getLabelNodeFromTemp( argTemp_i );
      paramIndex2ln.put( paramIndex, argLabel_i );

      // do a callee-effect strong update pre-pass here      
      if( argTemp_i.getType().isClass() ) {

	Iterator<ReferenceEdge> edgeItr = argLabel_i.iteratorToReferencees();
	while( edgeItr.hasNext() ) {
	  ReferenceEdge edge = edgeItr.next();
	  HeapRegionNode hrn = edge.getDst();

	  if( (hrn.getNumReferencers()                                == 1) || // case 1
	      (hrn.isSingleObject() && argLabel_i.getNumReferencees() == 1)    // case 2	     	     
	    ) {
	    
	    effectCalleeStrongUpdates( paramIndex, ogCallee, hrn );
	  }
	}
      }

      // then calculate the d and D rewrite rules
      ReachabilitySet d_i_p = new ReachabilitySet().makeCanonical();
      ReachabilitySet d_i_s = new ReachabilitySet().makeCanonical();
      Iterator<ReferenceEdge> edgeItr = argLabel_i.iteratorToReferencees();
      while( edgeItr.hasNext() ) {
	ReferenceEdge edge = edgeItr.next();

	d_i_p = d_i_p.union( edge.getBeta().intersection( edge.getDst().getAlpha() ) );
	d_i_s = d_i_s.union( edge.getBeta() );
      }
      paramIndex2rewrite_d_p.put( paramIndex, d_i_p );
      paramIndex2rewrite_d_s.put( paramIndex, d_i_s );

      // TODO: we should only do this when we need it, and then
      // memoize it for the rest of the mapping procedure
      ReachabilitySet D_i = d_i_s.exhaustiveArityCombinations();
      paramIndex2rewriteD.put( paramIndex, D_i );
    }


    // with respect to each argument, map parameter effects into caller
    HashSet<HeapRegionNode> nodesWithNewAlpha = new HashSet<HeapRegionNode>();
    HashSet<ReferenceEdge>  edgesWithNewBeta  = new HashSet<ReferenceEdge>();

    Hashtable<Integer, Set<HeapRegionNode> > pi2dr =
      new Hashtable<Integer, Set<HeapRegionNode> >();

    Hashtable<Integer, Set<HeapRegionNode> > pi2r =
      new Hashtable<Integer, Set<HeapRegionNode> >();

    Set<HeapRegionNode> defParamObj = new HashSet<HeapRegionNode>();

    Iterator lnArgItr = paramIndex2ln.entrySet().iterator();
    while( lnArgItr.hasNext() ) {
      Map.Entry me      = (Map.Entry) lnArgItr.next();
      Integer   index   = (Integer)   me.getKey();
      LabelNode lnArg_i = (LabelNode) me.getValue();
      
      Set<HeapRegionNode> dr   = new HashSet<HeapRegionNode>();
      Set<HeapRegionNode> r    = new HashSet<HeapRegionNode>();
      Set<HeapRegionNode> todo = new HashSet<HeapRegionNode>();

      // find all reachable nodes starting with label referencees
      Iterator<ReferenceEdge> edgeArgItr = lnArg_i.iteratorToReferencees();
      while( edgeArgItr.hasNext() ) {
	ReferenceEdge edge = edgeArgItr.next();
	HeapRegionNode hrn = edge.getDst();

	dr.add( hrn );

	if( lnArg_i.getNumReferencees() == 1 && hrn.isSingleObject() ) {
	  defParamObj.add( hrn );
	}

	Iterator<ReferenceEdge> edgeHrnItr = hrn.iteratorToReferencees();
	while( edgeHrnItr.hasNext() ) {
	  ReferenceEdge edger = edgeHrnItr.next();
	  todo.add( edger.getDst() );
	}

	// then follow links until all reachable nodes have been found
	while( !todo.isEmpty() ) {
	  HeapRegionNode hrnr = todo.iterator().next();
	  todo.remove( hrnr );
	  
	  r.add( hrnr );
	  
	  Iterator<ReferenceEdge> edgeItr = hrnr.iteratorToReferencees();
	  while( edgeItr.hasNext() ) {
	    ReferenceEdge edger = edgeItr.next();
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


    // now iterate over reachable nodes to rewrite their alpha, and
    // classify edges found for beta rewrite    
    Hashtable<TokenTuple, ReachabilitySet> tokens2states = new Hashtable<TokenTuple, ReachabilitySet>();

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
      LabelNode lnArg_i = (LabelNode) me.getValue();      

      TokenTuple p_i = ogCallee.paramIndex2paramTokenPrimary.get( index );
      TokenTuple s_i = ogCallee.paramIndex2paramTokenSecondary.get( index );
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
	Iterator<ReferenceEdge> edgeItr = hrn.iteratorToReferencers();
	while( edgeItr.hasNext() ) {
	  ReferenceEdge edge = edgeItr.next();
	  OwnershipNode on   = edge.getSrc();

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
	  tokens2states.put( p_i, new ReachabilitySet().makeCanonical() );
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
	Iterator<ReferenceEdge> edgeItr = hrn.iteratorToReferencers();
	while( edgeItr.hasNext() ) {
	  ReferenceEdge edge = edgeItr.next();
	  OwnershipNode on   = edge.getSrc();

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
      LabelNode lnArg_i = (LabelNode) me.getValue();      


      // update reachable edges
      Iterator edgeItr = edges_p2p.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	ReferenceEdge edge   = (ReferenceEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_p2p.containsKey( makeMapKey( index, 
							   indexJ,
 							   edge.getField() ) ) ) {
	  continue;
	}

	TokenTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
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
	ReferenceEdge edge   = (ReferenceEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_p2s.containsKey( makeMapKey( index, 
							      edge.getField() ) ) ) {
	  continue;
	}

	TokenTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
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
	ReferenceEdge edge   = (ReferenceEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_s2p.containsKey( index ) ) {
	  continue;
	}

	TokenTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
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
	ReferenceEdge edge   = (ReferenceEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteJ_s2s.containsKey( index ) ) {
	  continue;
	}

	TokenTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
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
      Hashtable<ReferenceEdge, ChangeTupleSet> edgeUpstreamPlannedChanges =
        new Hashtable<ReferenceEdge, ChangeTupleSet>();
      
      HashSet<ReferenceEdge> edgesDirectlyUpstream =
	new HashSet<ReferenceEdge>();

      edgeItr = edges_up_dr.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	ReferenceEdge edge   = (ReferenceEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	edgesDirectlyUpstream.add( edge );

	TokenTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
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
	TokenTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
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
        new Hashtable<ReferenceEdge, ChangeTupleSet>();

      HashSet<ReferenceEdge> edgesUpstream =
	new HashSet<ReferenceEdge>();

      edgeItr = edges_up_r.get( index ).iterator();
      while( edgeItr.hasNext() ) {
	Vector        mo     = (Vector)        edgeItr.next();
	ReferenceEdge edge   = (ReferenceEdge) mo.get( 0 );
	Integer       indexJ = (Integer)       mo.get( 1 );

	if( !paramIndex2rewriteK_s.containsKey( index ) ) {
	  continue;
	}

	edgesUpstream.add( edge );

	TokenTuple p_j = ogCallee.paramIndex2paramTokenPrimary.get( indexJ );
	assert p_j != null;

	TokenTuple s_j = ogCallee.paramIndex2paramTokenSecondary.get( indexJ );
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

    Iterator<ReferenceEdge> edgeItr = edgesWithNewBeta.iterator();
    while( edgeItr.hasNext() ) {
      edgeItr.next().applyBetaNew();
    }

    
    // verify the existence of allocation sites and their
    // shadows from the callee in the context of this caller graph
    // then map allocated nodes of callee onto the caller shadows
    // of them
    Hashtable<TokenTuple, ReachabilitySet> tokens2statesEmpty = new Hashtable<TokenTuple, ReachabilitySet>();

    Iterator<AllocationSite> asItr = ogCallee.allocationSites.iterator();
    while( asItr.hasNext() ) {
      AllocationSite allocSite  = asItr.next();

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

      Iterator<ReferenceEdge> heapRegionsItrCallee = hrnCallee.iteratorToReferencees();
      while( heapRegionsItrCallee.hasNext() ) {
	ReferenceEdge  edgeCallee     = heapRegionsItrCallee.next();
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

	  ReferenceEdge edgeNewInCallerTemplate = new ReferenceEdge( null,
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
	      ReferenceEdge edgeNewInCaller = edgeNewInCallerTemplate.copy();
	      edgeNewInCaller.setSrc( src );
	      edgeNewInCaller.setDst( dst );	      

	      ReferenceEdge edgeExisting = src.getReferenceTo( dst, 
							       edgeNewInCaller.getType(),
							       edgeNewInCaller.getField() );
	      if( edgeExisting == null ) {
		// if this edge doesn't exist in the caller, create it
		addReferenceEdge( src, dst, edgeNewInCaller );

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

      LabelNode lnLhsCaller = getLabelNodeFromTemp( returnTemp );
      clearReferenceEdgesFrom( lnLhsCaller, null, null, true );

      LabelNode lnReturnCallee = ogCallee.getLabelNodeFromTemp( tdReturn );
      Iterator<ReferenceEdge> edgeCalleeItr = lnReturnCallee.iteratorToReferencees();
      while( edgeCalleeItr.hasNext() ) {
	ReferenceEdge edgeCallee = edgeCalleeItr.next();

	ReferenceEdge edgeNewInCallerTemplate = new ReferenceEdge( null,
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

	  if( !hasMatchingType( edgeCallee, hrnCaller ) ) {
	    // prune
	    continue;
	  }

	  // otherwise caller node can match callee edge, so make it
	  ReferenceEdge edgeNewInCaller = edgeNewInCallerTemplate.copy();
	  edgeNewInCaller.setSrc( lnLhsCaller );
	  edgeNewInCaller.setDst( hrnCaller );

	  ReferenceEdge edgeExisting = lnLhsCaller.getReferenceTo( hrnCaller, 
								   edgeNewInCaller.getType(),
								   edgeNewInCaller.getField() );
	  if( edgeExisting == null ) {

	    // if this edge doesn't exist in the caller, create it
	    addReferenceEdge( lnLhsCaller, hrnCaller, edgeNewInCaller );
	  } else {
	    // if it already exists, merge with it
	    edgeExisting.setBeta( edgeExisting.getBeta().union( edgeNewInCaller.getBeta() ) );
	  }
	}
      }
    }


    if( mc.getDescriptor().getSymbol().equals( debugCaller ) &&
	fm.getMethod().getSymbol().equals( debugCallee ) ) {
      try {
	writeGraph( "debug7JustBeforeMergeToKCapacity", true, true, true, false, false );
      } catch( IOException e ) {}
    }



    // merge the shadow nodes of allocation sites back down to normal capacity
    Iterator<AllocationSite> allocItr = ogCallee.allocationSites.iterator();
    while( allocItr.hasNext() ) {
      AllocationSite as = allocItr.next();

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
      clearReferenceEdgesFrom( hrnSummaryShadow, null, null, true );
      clearReferenceEdgesTo  ( hrnSummaryShadow, null, null, true );
      hrnSummaryShadow.setAlpha( new ReachabilitySet().makeCanonical() );

      // then transplant shadow nodes onto the now clean normal nodes
      for( int i = 0; i < as.getAllocationDepth(); ++i ) {

	Integer        idIth        = as.getIthOldest( i );
	HeapRegionNode hrnIth       = id2hrn.get( idIth );
	Integer        idIthShadow  = as.getIthOldestShadow( i );
	HeapRegionNode hrnIthShadow = id2hrn.get( idIthShadow );

	transferOnto( hrnIthShadow, hrnIth );

	// clear off shadow nodes after transfer
	clearReferenceEdgesFrom( hrnIthShadow, null, null, true );
	clearReferenceEdgesTo  ( hrnIthShadow, null, null, true );
	hrnIthShadow.setAlpha( new ReachabilitySet().makeCanonical() );
      }

      // finally, globally change shadow tokens into normal tokens
      Iterator itrAllLabelNodes = td2ln.entrySet().iterator();
      while( itrAllLabelNodes.hasNext() ) {
	Map.Entry me = (Map.Entry) itrAllLabelNodes.next();
	LabelNode ln = (LabelNode) me.getValue();

	Iterator<ReferenceEdge> itrEdges = ln.iteratorToReferencees();
	while( itrEdges.hasNext() ) {
	  unshadowTokens( as, itrEdges.next() );
	}
      }

      Iterator itrAllHRNodes = id2hrn.entrySet().iterator();
      while( itrAllHRNodes.hasNext() ) {
	Map.Entry      me       = (Map.Entry)      itrAllHRNodes.next();
	HeapRegionNode hrnToAge = (HeapRegionNode) me.getValue();

	unshadowTokens( as, hrnToAge );

	Iterator<ReferenceEdge> itrEdges = hrnToAge.iteratorToReferencees();
	while( itrEdges.hasNext() ) {
	  unshadowTokens( as, itrEdges.next() );
	}
      }
    }


    if( mc.getDescriptor().getSymbol().equals( debugCaller ) &&
	fm.getMethod().getSymbol().equals( debugCallee ) ) {
      try {
	writeGraph( "debug8JustBeforeSweep", true, true, true, false, false );
      } catch( IOException e ) {}
    }


    // improve reachability as much as possible
    globalSweep();



    if( mc.getDescriptor().getSymbol().equals( debugCaller ) &&
	fm.getMethod().getSymbol().equals( debugCallee ) ) {
      try {
	writeGraph( "debug9endResolveCall", true, true, true, false, false );
      } catch( IOException e ) {}
      System.out.println( "  "+mc+" done calling "+fm );      
      ++x;
      if( x > 2 ) {
	System.exit( -1 );   
      }
    }
  }

  static int x = 0;


  protected boolean hasMatchingField(HeapRegionNode src, ReferenceEdge edge) {

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

      return edge.getField().equals( OwnershipAnalysis.arrayElementFieldName );
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


  protected boolean hasMatchingType(ReferenceEdge edge, HeapRegionNode dst) {
   
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



  protected void unshadowTokens(AllocationSite as, ReferenceEdge edge) {
    edge.setBeta(edge.getBeta().unshadowTokens(as) );
  }

  protected void unshadowTokens(AllocationSite as, HeapRegionNode hrn) {
    hrn.setAlpha(hrn.getAlpha().unshadowTokens(as) );
  }


  private ReachabilitySet toShadowTokens(OwnershipGraph ogCallee,
                                         ReachabilitySet rsIn) {

    ReachabilitySet rsOut = new ReachabilitySet(rsIn).makeCanonical();

    Iterator<AllocationSite> allocItr = ogCallee.allocationSites.iterator();
    while( allocItr.hasNext() ) {
      AllocationSite as = allocItr.next();

      rsOut = rsOut.toShadowTokens(as);
    }

    return rsOut.makeCanonical();
  }


  private void rewriteCallerReachability(Integer paramIndex,
                                         HeapRegionNode hrn,
                                         ReferenceEdge edge,
                                         ReachabilitySet rules,
					 Hashtable<TokenTuple, ReachabilitySet> tokens2states,
                                         Hashtable<Integer,    ReachabilitySet> paramIndex2rewrite_d_p,
                                         Hashtable<Integer,    ReachabilitySet> paramIndex2rewrite_d_s,
                                         Hashtable<Integer,    ReachabilitySet> paramIndex2rewriteD,
					 OwnershipGraph ogCallee,
                                         boolean makeChangeSet,
                                         Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges) {

    assert(hrn == null && edge != null) ||
          (hrn != null && edge == null);

    assert rules         != null;
    assert tokens2states != null;

    ReachabilitySet callerReachabilityNew = new ReachabilitySet().makeCanonical();

    // for initializing structures in this method
    TokenTupleSet ttsEmpty = new TokenTupleSet().makeCanonical();

    // use this to construct a change set if required; the idea is to
    // map every partially rewritten token tuple set to the set of
    // caller-context token tuple sets that were used to generate it
    Hashtable<TokenTupleSet, HashSet<TokenTupleSet> > rewritten2source =
      new Hashtable<TokenTupleSet, HashSet<TokenTupleSet> >();
    rewritten2source.put( ttsEmpty, new HashSet<TokenTupleSet>() );

    
    Iterator<TokenTupleSet> rulesItr = rules.iterator();
    while(rulesItr.hasNext()) {
      TokenTupleSet rule = rulesItr.next();

      ReachabilitySet rewrittenRule = new ReachabilitySet(ttsEmpty).makeCanonical();

      Iterator<TokenTuple> ruleItr = rule.iterator();
      while(ruleItr.hasNext()) {
	TokenTuple ttCallee = ruleItr.next();	

	// compute the possibilities for rewriting this callee token
	ReachabilitySet ttCalleeRewrites = null;
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
	  TokenTupleSet ttsCaller = new TokenTupleSet( ttCallee ).makeCanonical();
	  ttCalleeRewrites = new ReachabilitySet( ttsCaller ).makeCanonical();
	}

	// branch every version of the working rewritten rule with
	// the possibilities for rewriting the current callee token
	ReachabilitySet rewrittenRuleWithTTCallee = new ReachabilitySet().makeCanonical();

	Iterator<TokenTupleSet> rewrittenRuleItr = rewrittenRule.iterator();
	while( rewrittenRuleItr.hasNext() ) {
	  TokenTupleSet ttsRewritten = rewrittenRuleItr.next();

	  Iterator<TokenTupleSet> ttCalleeRewritesItr = ttCalleeRewrites.iterator();
	  while( ttCalleeRewritesItr.hasNext() ) {
	    TokenTupleSet ttsBranch = ttCalleeRewritesItr.next();

	    TokenTupleSet ttsRewrittenNext = ttsRewritten.unionUpArity( ttsBranch );

	    if( makeChangeSet ) {
	      // in order to keep the list of source token tuple sets
	      // start with the sets used to make the partially rewritten
	      // rule up to this point
	      HashSet<TokenTupleSet> sourceSets = rewritten2source.get( ttsRewritten );
	      assert sourceSets != null;

	      // make a shallow copy for possible modification
	      sourceSets = (HashSet<TokenTupleSet>) sourceSets.clone();

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
      ChangeTupleSet callerChangeSet = new ChangeTupleSet().makeCanonical();

      // each possibility for the final reachability should have a set of
      // caller sources mapped to it, use to create the change set
      Iterator<TokenTupleSet> callerReachabilityItr = callerReachabilityNew.iterator();
      while( callerReachabilityItr.hasNext() ) {
	TokenTupleSet ttsRewrittenFinal = callerReachabilityItr.next();
	HashSet<TokenTupleSet> sourceSets = rewritten2source.get( ttsRewrittenFinal );
	assert sourceSets != null;

	Iterator<TokenTupleSet> sourceSetsItr = sourceSets.iterator();
	while( sourceSetsItr.hasNext() ) {
	  TokenTupleSet ttsSource = sourceSetsItr.next();

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
    getHRNSetThatPossiblyMapToCalleeHRN( OwnershipGraph ogCallee,
					 HeapRegionNode hrnCallee,
					 Hashtable<Integer, Set<HeapRegionNode> > pi2dr,
					 Hashtable<Integer, Set<HeapRegionNode> > pi2r
					 ) {
    
    HashSet<HeapRegionNode> possibleCallerHRNs = new HashSet<HeapRegionNode>();

    if( hrnCallee == ogCallee.hrnNull ) {
      // this is the null heap region
      possibleCallerHRNs.add( id2hrn.get( hrnCallee.getID() ) );
      return possibleCallerHRNs;
    }

    Set<Integer> paramIndicesCallee_p = ogCallee.idPrimary2paramIndexSet  .get( hrnCallee.getID() );
    Set<Integer> paramIndicesCallee_s = ogCallee.idSecondary2paramIndexSet.get( hrnCallee.getID() );

    if( paramIndicesCallee_p == null &&
	paramIndicesCallee_s == null ) {
      // this is a node allocated in the callee and it has
      // exactly one shadow node in the caller to map to
      AllocationSite as = hrnCallee.getAllocationSite();
      assert as != null;

      int age = as.getAgeCategory( hrnCallee.getID() );
      assert age != AllocationSite.AGE_notInThisSite;

      Integer idCaller;
      if( age == AllocationSite.AGE_summary ) {
	idCaller = as.getSummaryShadow();

      } else if( age == AllocationSite.AGE_oldest ) {
	idCaller = as.getOldestShadow();

      } else {
	assert age == AllocationSite.AGE_in_I;

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
    Hashtable< Integer, Hashtable<ReferenceEdge, ReachabilitySet> > boldB =
      new Hashtable< Integer, Hashtable<ReferenceEdge, ReachabilitySet> >();    

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

      Iterator<ReferenceEdge> itrRers = hrn.iteratorToReferencers();
      while( itrRers.hasNext() ) {
	ReferenceEdge edge = itrRers.next();
	assert rsEmpty.equals( edge.getBetaNew() );
      }      

      // calculate boldB for this flagged node
      if( hrn.isFlagged() || hrn.isParameter() ) {
	
	Hashtable<ReferenceEdge, ReachabilitySet> boldB_f =
	  new Hashtable<ReferenceEdge, ReachabilitySet>();
	
	Set<ReferenceEdge> workSetEdges = new HashSet<ReferenceEdge>();

	// initial boldB_f constraints
	Iterator<ReferenceEdge> itrRees = hrn.iteratorToReferencees();
	while( itrRees.hasNext() ) {
	  ReferenceEdge edge = itrRees.next();

	  assert !boldB.containsKey( edge );
	  boldB_f.put( edge, edge.getBeta() );

	  assert !workSetEdges.contains( edge );
	  workSetEdges.add( edge );
	}      	

	// enforce the boldB_f constraint at edges until we reach a fixed point
	while( !workSetEdges.isEmpty() ) {
	  ReferenceEdge edge = workSetEdges.iterator().next();
	  workSetEdges.remove( edge );	 
	  
	  Iterator<ReferenceEdge> itrPrime = edge.getDst().iteratorToReferencees();
	  while( itrPrime.hasNext() ) {
	    ReferenceEdge edgePrime = itrPrime.next();	    

	    ReachabilitySet prevResult   = boldB_f.get( edgePrime );
	    ReachabilitySet intersection = boldB_f.get( edge ).intersection( edgePrime.getBeta() );
	    	    
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
    HashSet<ReferenceEdge> edgesForPropagation = new HashSet<ReferenceEdge>();

    Hashtable<ReferenceEdge, ChangeTupleSet> edgePlannedChanges =
      new Hashtable<ReferenceEdge, ChangeTupleSet>();

    hrns = id2hrn.entrySet();
    itrHrns = hrns.iterator();
    while( itrHrns.hasNext() ) {
      Map.Entry me = (Map.Entry)itrHrns.next();
      Integer token = (Integer) me.getKey();
      HeapRegionNode hrn = (HeapRegionNode) me.getValue();

      // never remove the identity token from a flagged region
      // because it is trivially satisfied
      TokenTuple ttException = new TokenTuple( token, 
					       !hrn.isSingleObject(), 
					       TokenTuple.ARITY_ONE ).makeCanonical();

      ChangeTupleSet cts = new ChangeTupleSet().makeCanonical();

      // mark tokens for removal
      Iterator<TokenTupleSet> stateItr = hrn.getAlpha().iterator();
      while( stateItr.hasNext() ) {
	TokenTupleSet ttsOld = stateItr.next();

	TokenTupleSet markedTokens = new TokenTupleSet().makeCanonical();

	Iterator<TokenTuple> ttItr = ttsOld.iterator();
	while( ttItr.hasNext() ) {
	  TokenTuple ttOld = ttItr.next();

	  // never remove the identity token from a flagged region
	  // because it is trivially satisfied
	  if( hrn.isFlagged() || hrn.isParameter() ) {	
	    if( ttOld == ttException ) {
	      continue;
	    }
	  }

	  // does boldB_ttOld allow this token?
	  boolean foundState = false;
	  Iterator<ReferenceEdge> incidentEdgeItr = hrn.iteratorToReferencers();
	  while( incidentEdgeItr.hasNext() ) {
	    ReferenceEdge incidentEdge = incidentEdgeItr.next();

	    // if it isn't allowed, mark for removal
	    Integer idOld = ttOld.getToken();
	    assert id2hrn.containsKey( idOld );
	    Hashtable<ReferenceEdge, ReachabilitySet> B = boldB.get( idOld );	    
	    ReachabilitySet boldB_ttOld_incident = B.get( incidentEdge );// B is NULL!	    
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
	TokenTupleSet ttsPruned = new TokenTupleSet().makeCanonical();
	ttItr = ttsOld.iterator();
	while( ttItr.hasNext() ) {
	  TokenTuple ttOld = ttItr.next();

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
	Iterator<ReferenceEdge> incidentEdgeItr = hrn.iteratorToReferencers();
	while( incidentEdgeItr.hasNext() ) {
	  ReferenceEdge incidentEdge = incidentEdgeItr.next();
	  	  
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
    
    HashSet<ReferenceEdge> edgesUpdated = new HashSet<ReferenceEdge>();

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
    HashSet<ReferenceEdge> res = new HashSet<ReferenceEdge>();

    Iterator<HeapRegionNode> nodeItr = id2hrn.values().iterator();
    while( nodeItr.hasNext() ) {
      HeapRegionNode hrn = nodeItr.next();
      hrn.applyAlphaNew();
      Iterator<ReferenceEdge> itrRes = hrn.iteratorToReferencers();
      while( itrRes.hasNext() ) {
	res.add( itrRes.next() );
      }
    }


    // 2nd phase    
    Iterator<ReferenceEdge> edgeItr = res.iterator();
    while( edgeItr.hasNext() ) {
      ReferenceEdge edge = edgeItr.next();
      HeapRegionNode hrn = edge.getDst();

      // commit results of last phase
      if( edgesUpdated.contains( edge ) ) {
	edge.applyBetaNew();
      }

      // compute intial condition of 2nd phase
      edge.setBetaNew( edge.getBeta().intersection( hrn.getAlpha() ) );      
    }
        
    // every edge in the graph is the initial workset
    Set<ReferenceEdge> edgeWorkSet = (Set) res.clone();
    while( !edgeWorkSet.isEmpty() ) {
      ReferenceEdge edgePrime = edgeWorkSet.iterator().next();
      edgeWorkSet.remove( edgePrime );

      OwnershipNode on = edgePrime.getSrc();
      if( !(on instanceof HeapRegionNode) ) {
	continue;
      }
      HeapRegionNode hrn = (HeapRegionNode) on;

      Iterator<ReferenceEdge> itrEdge = hrn.iteratorToReferencers();
      while( itrEdge.hasNext() ) {
	ReferenceEdge edge = itrEdge.next();	    

	ReachabilitySet prevResult = edge.getBetaNew();
	assert prevResult != null;

	ReachabilitySet intersection = edge.getBeta().intersection( edgePrime.getBetaNew() );
		    
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
  public void merge(OwnershipGraph og) {

    if( og == null ) {
      return;
    }

    mergeOwnershipNodes(og);
    mergeReferenceEdges(og);
    mergeParamIndexMappings(og);
    mergeAllocationSites(og);
  }


  protected void mergeOwnershipNodes(OwnershipGraph og) {
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
    sA = og.td2ln.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      LabelNode lnA = (LabelNode)      meA.getValue();

      // if the label doesn't exist in B, allocate and add it
      LabelNode lnB = getLabelNodeFromTemp(tdA);
    }
  }

  protected void mergeReferenceEdges(OwnershipGraph og) {

    // heap regions
    Set sA = og.id2hrn.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA  = (Map.Entry)iA.next();
      Integer idA  = (Integer)        meA.getKey();
      HeapRegionNode hrnA = (HeapRegionNode) meA.getValue();

      Iterator<ReferenceEdge> heapRegionsItrA = hrnA.iteratorToReferencees();
      while( heapRegionsItrA.hasNext() ) {
	ReferenceEdge edgeA     = heapRegionsItrA.next();
	HeapRegionNode hrnChildA = edgeA.getDst();
	Integer idChildA  = hrnChildA.getID();

	// at this point we know an edge in graph A exists
	// idA -> idChildA, does this exist in B?
	assert id2hrn.containsKey(idA);
	HeapRegionNode hrnB        = id2hrn.get(idA);
	ReferenceEdge edgeToMerge = null;

	Iterator<ReferenceEdge> heapRegionsItrB = hrnB.iteratorToReferencees();
	while( heapRegionsItrB.hasNext() &&
	       edgeToMerge == null          ) {

	  ReferenceEdge edgeB     = heapRegionsItrB.next();
	  HeapRegionNode hrnChildB = edgeB.getDst();
	  Integer idChildB  = hrnChildB.getID();

	  // don't use the ReferenceEdge.equals() here because
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
	  addReferenceEdge(hrnB, hrnChildB, edgeToMerge);
	}
	// otherwise, the edge already existed in both graphs
	// so merge their reachability sets
	else {
	  // just replace this beta set with the union
	  assert edgeToMerge != null;
	  edgeToMerge.setBeta(
	    edgeToMerge.getBeta().union(edgeA.getBeta() )
	    );
	  if( !edgeA.isInitialParam() ) {
	    edgeToMerge.setIsInitialParam(false);
	  }
	}
      }
    }

    // and then again with label nodes
    sA = og.td2ln.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      LabelNode lnA = (LabelNode)      meA.getValue();

      Iterator<ReferenceEdge> heapRegionsItrA = lnA.iteratorToReferencees();
      while( heapRegionsItrA.hasNext() ) {
	ReferenceEdge edgeA     = heapRegionsItrA.next();
	HeapRegionNode hrnChildA = edgeA.getDst();
	Integer idChildA  = hrnChildA.getID();

	// at this point we know an edge in graph A exists
	// tdA -> idChildA, does this exist in B?
	assert td2ln.containsKey(tdA);
	LabelNode lnB         = td2ln.get(tdA);
	ReferenceEdge edgeToMerge = null;

	Iterator<ReferenceEdge> heapRegionsItrB = lnB.iteratorToReferencees();
	while( heapRegionsItrB.hasNext() &&
	       edgeToMerge == null          ) {

	  ReferenceEdge  edgeB     = heapRegionsItrB.next();
	  HeapRegionNode hrnChildB = edgeB.getDst();
	  Integer        idChildB  = hrnChildB.getID();

	  // don't use the ReferenceEdge.equals() here because
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
	  addReferenceEdge(lnB, hrnChildB, edgeToMerge);
	}
	// otherwise, the edge already existed in both graphs
	// so merge their reachability sets
	else {
	  // just replace this beta set with the union
	  edgeToMerge.setBeta(
	    edgeToMerge.getBeta().union(edgeA.getBeta() )
	    );
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
  protected void mergeParamIndexMappings(OwnershipGraph og) {
    
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

  protected void mergeAllocationSites(OwnershipGraph og) {
    allocationSites.addAll(og.allocationSites);
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
  public boolean equals(OwnershipGraph og) {

    if( og == null ) {
      return false;
    }

    if( !areHeapRegionNodesEqual(og) ) {
      return false;
    }

    if( !areLabelNodesEqual(og) ) {
      return false;
    }

    if( !areReferenceEdgesEqual(og) ) {
      return false;
    }

    if( !areParamIndexMappingsEqual(og) ) {
      return false;
    }

    // if everything is equal up to this point,
    // assert that allocationSites is also equal--
    // this data is redundant and kept for efficiency
    assert allocationSites.equals(og.allocationSites);

    return true;
  }

  protected boolean areHeapRegionNodesEqual(OwnershipGraph og) {

    if( !areallHRNinAalsoinBandequal(this, og) ) {
      return false;
    }

    if( !areallHRNinAalsoinBandequal(og, this) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallHRNinAalsoinBandequal(OwnershipGraph ogA,
                                                       OwnershipGraph ogB) {
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


  protected boolean areLabelNodesEqual(OwnershipGraph og) {

    if( !areallLNinAalsoinBandequal(this, og) ) {
      return false;
    }

    if( !areallLNinAalsoinBandequal(og, this) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallLNinAalsoinBandequal(OwnershipGraph ogA,
                                                      OwnershipGraph ogB) {
    Set sA = ogA.td2ln.entrySet();
    Iterator iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();

      if( !ogB.td2ln.containsKey(tdA) ) {
	return false;
      }
    }

    return true;
  }


  protected boolean areReferenceEdgesEqual(OwnershipGraph og) {
    if( !areallREinAandBequal(this, og) ) {
      return false;
    }

    return true;
  }

  static protected boolean areallREinAandBequal(OwnershipGraph ogA,
                                                OwnershipGraph ogB) {

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
    sA = ogA.td2ln.entrySet();
    iA = sA.iterator();
    while( iA.hasNext() ) {
      Map.Entry meA = (Map.Entry)iA.next();
      TempDescriptor tdA = (TempDescriptor) meA.getKey();
      LabelNode lnA = (LabelNode)      meA.getValue();

      // we should have already checked that the same
      // label nodes exist in both graphs
      assert ogB.td2ln.containsKey(tdA);

      if( !areallREfromAequaltoB(ogA, lnA, ogB) ) {
	return false;
      }

      // then check every edge in B for presence in A, starting
      // from the same parent LabelNode
      LabelNode lnB = ogB.td2ln.get(tdA);

      if( !areallREfromAequaltoB(ogB, lnB, ogA) ) {
	return false;
      }
    }

    return true;
  }


  static protected boolean areallREfromAequaltoB(OwnershipGraph ogA,
                                                 OwnershipNode onA,
                                                 OwnershipGraph ogB) {

    Iterator<ReferenceEdge> itrA = onA.iteratorToReferencees();
    while( itrA.hasNext() ) {
      ReferenceEdge edgeA     = itrA.next();
      HeapRegionNode hrnChildA = edgeA.getDst();
      Integer idChildA  = hrnChildA.getID();

      assert ogB.id2hrn.containsKey(idChildA);

      // at this point we know an edge in graph A exists
      // onA -> idChildA, does this exact edge exist in B?
      boolean edgeFound = false;

      OwnershipNode onB = null;
      if( onA instanceof HeapRegionNode ) {
	HeapRegionNode hrnA = (HeapRegionNode) onA;
	onB = ogB.id2hrn.get(hrnA.getID() );
      } else {
	LabelNode lnA = (LabelNode) onA;
	onB = ogB.td2ln.get(lnA.getTempDescriptor() );
      }

      Iterator<ReferenceEdge> itrB = onB.iteratorToReferencees();
      while( itrB.hasNext() ) {
	ReferenceEdge edgeB     = itrB.next();
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


  protected boolean areParamIndexMappingsEqual(OwnershipGraph og) {

    if( idPrimary2paramIndexSet.size() != og.idPrimary2paramIndexSet.size() ) {
      return false;
    }

    if( idSecondary2paramIndexSet.size() != og.idSecondary2paramIndexSet.size() ) {
      return false;
    }

    return true;
  }


  public Set<HeapRegionNode> hasPotentialAlias( HeapRegionNode hrn1, HeapRegionNode hrn2 ) {
    assert hrn1 != null;
    assert hrn2 != null;

    // then get the various tokens for these heap regions
    TokenTuple h1 = new TokenTuple(hrn1.getID(),
				   !hrn1.isSingleObject(),
                                   TokenTuple.ARITY_ONE).makeCanonical();

    TokenTuple h1plus = new TokenTuple(hrn1.getID(),
                                       !hrn1.isSingleObject(),
                                       TokenTuple.ARITY_ONEORMORE).makeCanonical();

    TokenTuple h1star = new TokenTuple(hrn1.getID(),
                                       !hrn1.isSingleObject(),
                                       TokenTuple.ARITY_ZEROORMORE).makeCanonical();

    TokenTuple h2 = new TokenTuple(hrn2.getID(),
				   !hrn2.isSingleObject(),
                                   TokenTuple.ARITY_ONE).makeCanonical();

    TokenTuple h2plus = new TokenTuple(hrn2.getID(),
                                       !hrn2.isSingleObject(),
                                       TokenTuple.ARITY_ONEORMORE).makeCanonical();

    TokenTuple h2star = new TokenTuple(hrn2.getID(),
                                       !hrn2.isSingleObject(),
                                       TokenTuple.ARITY_ZEROORMORE).makeCanonical();

    // then get the merged beta of all out-going edges from these heap regions
    ReachabilitySet beta1 = new ReachabilitySet().makeCanonical();
    Iterator<ReferenceEdge> itrEdge = hrn1.iteratorToReferencees();
    while( itrEdge.hasNext() ) {
      ReferenceEdge edge = itrEdge.next();
      beta1 = beta1.union( edge.getBeta() );
    }

    ReachabilitySet beta2 = new ReachabilitySet().makeCanonical();
    itrEdge = hrn2.iteratorToReferencees();
    while( itrEdge.hasNext() ) {
      ReferenceEdge edge = itrEdge.next();
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
      assert !common.isEmpty();
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


  public Set<HeapRegionNode> hasPotentialAlias(Integer paramIndex, AllocationSite as) {

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


  public Set<HeapRegionNode> hasPotentialAlias(AllocationSite as1, AllocationSite as2) {     

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
      
      Iterator<ReferenceEdge> edgeItr = hrn.iteratorToReferencees();
      while( edgeItr.hasNext() ) {
	ReferenceEdge edge = edgeItr.next();
	
	if( !reachableNodes1.contains( edge.getDst() ) ) {
	  todoNodes1.add( edge.getDst() );
	}
      }
    }

    while( !todoNodes2.isEmpty() ) {
      HeapRegionNode hrn = todoNodes2.iterator().next();
      todoNodes2.remove( hrn );
      reachableNodes2.add(hrn);
      
      Iterator<ReferenceEdge> edgeItr = hrn.iteratorToReferencees();
      while( edgeItr.hasNext() ) {
	ReferenceEdge edge = edgeItr.next();
	
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


  // for writing ownership graphs to dot files
  public void writeGraph(MethodContext mc,
                         FlatNode fn,
                         boolean writeLabels,
                         boolean labelSelect,
                         boolean pruneGarbage,
                         boolean writeReferencers,
                         boolean writeParamMappings
                         ) throws java.io.IOException {
    writeGraph(
      mc.toString() +
      fn.toString(),
      writeLabels,
      labelSelect,
      pruneGarbage,
      writeReferencers,
      writeParamMappings
      );
  }

  public void writeGraph(MethodContext mc,
                         boolean writeLabels,
                         boolean labelSelect,
                         boolean pruneGarbage,
                         boolean writeReferencers,
                         boolean writeParamMappings
                         ) throws java.io.IOException {

    writeGraph(mc+"COMPLETE",
               writeLabels,
               labelSelect,
               pruneGarbage,
               writeReferencers,
               writeParamMappings
               );
  }

  public void writeGraph(MethodContext mc,
                         Integer numUpdate,
                         boolean writeLabels,
                         boolean labelSelect,
                         boolean pruneGarbage,
                         boolean writeReferencers,
                         boolean writeParamMappings
                         ) throws java.io.IOException {



    writeGraph(mc+"COMPLETE"+String.format("%05d", numUpdate),
               writeLabels,
               labelSelect,
               pruneGarbage,
               writeReferencers,
               writeParamMappings
               );
  }

  public void writeGraph(String graphName,
                         boolean writeLabels,
                         boolean labelSelect,
                         boolean pruneGarbage,
                         boolean writeReferencers,
                         boolean writeParamMappings
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
	                          writeReferencers);
	}
      }
    }

    bw.write("  graphTitle[label=\""+graphName+"\",shape=box];\n");

    if( writeParamMappings ) {
      /* UNMAINTAINED
      Set df = paramIndex2id.entrySet();
      Iterator ih = df.iterator();
      while( ih.hasNext() ) {
	Map.Entry meh = (Map.Entry)ih.next();
	Integer pi = (Integer) meh.getKey();
	Integer id = (Integer) meh.getValue();
	bw.write("  pindex"+pi+"[label=\""+pi+" to "+id+"\",shape=box];\n");
      }
      */
    }

    // then visit every label node, useful for debugging
    if( writeLabels ) {
      s = td2ln.entrySet();
      i = s.iterator();
      while( i.hasNext() ) {
	Map.Entry me = (Map.Entry)i.next();
	LabelNode ln = (LabelNode) me.getValue();

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

	Iterator<ReferenceEdge> heapRegionsItr = ln.iteratorToReferencees();
	while( heapRegionsItr.hasNext() ) {
	  ReferenceEdge edge = heapRegionsItr.next();
	  HeapRegionNode hrn  = edge.getDst();

	  if( pruneGarbage && !visited.contains(hrn) ) {
	    traverseHeapRegionNodes(VISIT_HRN_WRITE_FULL,
	                            hrn,
	                            bw,
	                            null,
	                            visited,
	                            writeReferencers);
	  }

	  bw.write("  "        + ln.toString() +
	           " -> "      + hrn.toString() +
	           "[label=\"" + edge.toGraphEdgeString() +
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
                                         boolean writeReferencers
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
                    hrn.getAlphaString() +
                    "\"]";

      bw.write("  " + hrn.toString() + attributes + ";\n");
      break;
    }


    // useful for debugging
    // UNMAINTAINED
    /*
    if( writeReferencers ) {
      OwnershipNode onRef  = null;
      Iterator refItr = hrn.iteratorToReferencers();
      while( refItr.hasNext() ) {
	onRef = (OwnershipNode) refItr.next();

	switch( mode ) {
	case VISIT_HRN_WRITE_FULL:
	  bw.write("  "                    + hrn.toString() +
	           " -> "                  + onRef.toString() +
	           "[color=lightgray];\n");
	  break;
	}
      }
    }
    */

    Iterator<ReferenceEdge> childRegionsItr = hrn.iteratorToReferencees();
    while( childRegionsItr.hasNext() ) {
      ReferenceEdge edge     = childRegionsItr.next();
      HeapRegionNode hrnChild = edge.getDst();

      switch( mode ) {
      case VISIT_HRN_WRITE_FULL:
	bw.write("  "        + hrn.toString() +
	         " -> "      + hrnChild.toString() +
	         "[label=\"" + edge.toGraphEdgeString() +
	         "\",decorate];\n");
	break;
      }

      traverseHeapRegionNodes(mode,
                              hrnChild,
                              bw,
                              td,
                              visited,
                              writeReferencers);
    }
  }
}
