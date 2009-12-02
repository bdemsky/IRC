package Analysis.Disjoint;

import Analysis.CallGraph.*;
import Analysis.Liveness;
import Analysis.ArrayReferencees;
import IR.*;
import IR.Flat.*;
import IR.Tree.Modifiers;
import java.util.*;
import java.io.*;


public class DisjointAnalysis {


  // data from the compiler
  public State            state;
  public CallGraph        callGraph;
  public Liveness         liveness;
  public ArrayReferencees arrayReferencees;
  public TypeUtil         typeUtil;
  public int              allocationDepth;


  // used to identify HeapRegionNode objects
  // A unique ID equates an object in one
  // ownership graph with an object in another
  // graph that logically represents the same
  // heap region
  // start at 10 and increment to reserve some
  // IDs for special purposes
  static private int uniqueIDcount = 10;


  // An out-of-scope method created by the
  // analysis that has no parameters, and
  // appears to allocate the command line
  // arguments, then invoke the source code's
  // main method.  The purpose of this is to
  // provide the analysis with an explicit
  // top-level context with no parameters
  private MethodDescriptor mdAnalysisEntry;
  private FlatMethod       fmAnalysisEntry;

  // main method defined by source program
  private MethodDescriptor mdSourceEntry;

  // the set of task and/or method descriptors
  // reachable in call graph
  private Set<Descriptor> descriptorsToAnalyze;


  // for controlling DOT file output
  private boolean writeFinalDOTs;
  private boolean writeAllIncrementalDOTs;


  // this analysis generates a disjoint reachability
  // graph for every reachable method in the program
  public DisjointAnalysis( State s,
			   TypeUtil tu,
			   CallGraph cg,
			   Liveness l,
			   ArrayReferencees ar
                           ) throws java.io.IOException {
    init( s, tu, cg, l, ar );
  }
  
  private void init( State state,
		     TypeUtil typeUtil,
		     CallGraph callGraph,
		     Liveness liveness,
		     ArrayReferencees arrayReferencees
                     ) throws java.io.IOException {
    
    this.state                   = state;
    this.typeUtil                = typeUtil;
    this.callGraph               = callGraph;
    this.liveness                = liveness;
    this.arrayReferencees        = arrayReferencees;
    this.allocationDepth         = state.DISJOINTALLOCDEPTH;
    this.writeFinalDOTs          = state.DISJOINTWRITEDOTS && !state.DISJOINTWRITEALL;
    this.writeAllIncrementalDOTs = state.DISJOINTWRITEDOTS &&  state.DISJOINTWRITEALL;

	    
    // set some static configuration for ReachGraphs
    ReachGraph.allocationDepth = allocationDepth;
    ReachGraph.typeUtil        = typeUtil;


    // This analysis does not support Bamboo at the moment,
    // but if it does in the future we would initialize the
    // set of descriptors to analyze as the program-reachable
    // tasks and the methods callable by them.  For Java,
    // just methods reachable from the main method.
    assert !state.TASK;
    descriptorsToAnalyze = new HashSet<Descriptor>();


    double timeStartAnalysis = (double) System.nanoTime();

    // start interprocedural fixed-point computation
    analyzeMethods();

    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
    String treport = String.format( "The reachability analysis took %.3f sec.", dt );
    String justtime = String.format( "%.2f", dt );
    System.out.println( treport );

    if( writeFinalDOTs && !writeAllIncrementalDOTs ) {
      //writeFinalContextGraphs();      
    }  

    if( state.DISJOINTALIASFILE != null ) {
      if( state.TASK ) {
        // not supporting tasks yet...
      } else {
        /*
        writeAllAliasesJava( aliasFile, 
                             treport, 
                             justtime, 
                             state.DISJOINTALIASTAB, 
                             state.lines );
        */
      }
    }
  }



  // fixed-point computation over the call graph--when a
  // method's callees are updated, it must be reanalyzed
  private void analyzeMethods() throws java.io.IOException {  

    mdSourceEntry = typeUtil.getMain();

    // add all methods transitively reachable from the
    // source's main to set for analysis
    descriptorsToAnalyze.add( mdSourceEntry );
    descriptorsToAnalyze.addAll( 
      callGraph.getAllMethods( mdSourceEntry ) 
                                 );

    // fabricate an empty calling context that will call
    // the source's main, but call graph doesn't know
    // about it, so explicitly add it
    makeAnalysisEntryMethod( mdSourceEntry );
    descriptorsToAnalyze.add( mdAnalysisEntry );


    // topologically sort according to the call graph so 
    // leaf calls are ordered first, smarter analysis order
    LinkedList<Descriptor> sortedDescriptors = 
      topologicalSort( descriptorsToAnalyze );


    System.out.println( "topological:\n"+sortedDescriptors );


    /*
    methodContextsToVisitQ   = new PriorityQueue<MethodContextQWrapper>();
    methodContextsToVisitSet = new HashSet<MethodContext>();

    int p = 0;
    Iterator<MethodContext> mcItr = sortedMethodContexts.iterator();
    while( mcItr.hasNext() ) {
      MethodContext mc = mcItr.next();
      mapDescriptorToPriority.put( mc.getDescriptor(), new Integer( p ) );
      methodContextsToVisitQ.add( new MethodContextQWrapper( p, mc ) );
      methodContextsToVisitSet.add( mc );
      ++p;
    }

    // analyze methods from the priority queue until it is empty
    while( !methodContextsToVisitQ.isEmpty() ) {
      MethodContext mc = methodContextsToVisitQ.poll().getMethodContext();
      assert methodContextsToVisitSet.contains( mc );
      methodContextsToVisitSet.remove( mc );

      // because the task or method descriptor just extracted
      // was in the "to visit" set it either hasn't been analyzed
      // yet, or some method that it depends on has been
      // updated.  Recompute a complete ownership graph for
      // this task/method and compare it to any previous result.
      // If there is a change detected, add any methods/tasks
      // that depend on this one to the "to visit" set.

      System.out.println("Analyzing " + mc);

      Descriptor d = mc.getDescriptor();
      FlatMethod fm;
      if( d instanceof MethodDescriptor ) {
	fm = state.getMethodFlat( (MethodDescriptor) d);
      } else {
	assert d instanceof TaskDescriptor;
	fm = state.getMethodFlat( (TaskDescriptor) d);
      }

      ReachGraph og = analyzeFlatMethod(mc, fm);
      ReachGraph ogPrev = mapMethodContextToCompleteReachabilityGraph.get(mc);
      if( !og.equals(ogPrev) ) {
	setGraphForMethodContext(mc, og);

	Iterator<MethodContext> depsItr = iteratorDependents( mc );
	while( depsItr.hasNext() ) {
	  MethodContext mcNext = depsItr.next();

	  if( !methodContextsToVisitSet.contains( mcNext ) ) {
	    methodContextsToVisitQ.add( new MethodContextQWrapper( mapDescriptorToPriority.get( mcNext.getDescriptor() ), 
								   mcNext ) );
	    methodContextsToVisitSet.add( mcNext );
	  }
	}
      }
    }
    */
  }

  /*
  // keep passing the Descriptor of the method along for debugging
  // and dot file writing
  private ReachGraph
  analyzeFlatMethod(MethodContext mc,
                    FlatMethod flatm) throws java.io.IOException {

    // initialize flat nodes to visit as the flat method
    // because it is the entry point

    flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(flatm);

    // initilize the mapping of flat nodes in this flat method to
    // ownership graph results to an empty mapping
    mapFlatNodeToReachabilityGraph = new Hashtable<FlatNode, ReachGraph>();

    // initialize the set of return nodes that will be combined as
    // the final ownership graph result to return as an empty set
    returnNodesToCombineForCompleteReachabilityGraph = new HashSet<FlatReturnNode>();


    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      //System.out.println( "  "+fn );

      // perform this node's contributions to the ownership
      // graph on a new copy, then compare it to the old graph
      // at this node to see if anything was updated.
      ReachGraph og = new ReachGraph();

      // start by merging all node's parents' graphs
      for( int i = 0; i < fn.numPrev(); ++i ) {
	FlatNode pn = fn.getPrev(i);
	if( mapFlatNodeToReachabilityGraph.containsKey(pn) ) {
	  ReachabilityGraph ogParent = mapFlatNodeToReachabilityGraph.get(pn);
	  og.merge(ogParent);
	}
      }

      // apply the analysis of the flat node to the
      // ownership graph made from the merge of the
      // parent graphs
      og = analyzeFlatNode(mc,
			   flatm,
                           fn,
                           returnNodesToCombineForCompleteReachabilityGraph,
                           og);

      

     
      if( takeDebugSnapshots && 
	  mc.getDescriptor().getSymbol().equals( mcDescSymbolDebug ) ) {
	debugSnapshot(og,fn);
      }


      // if the results of the new graph are different from
      // the current graph at this node, replace the graph
      // with the update and enqueue the children for
      // processing
      ReachGraph ogPrev = mapFlatNodeToReachabilityGraph.get(fn);
      if( !og.equals(ogPrev) ) {
	mapFlatNodeToReachabilityGraph.put(fn, og);

	for( int i = 0; i < fn.numNext(); i++ ) {
	  FlatNode nn = fn.getNext(i);
	  flatNodesToVisit.add(nn);
	}
      }
    }

    // end by merging all return nodes into a complete
    // ownership graph that represents all possible heap
    // states after the flat method returns
    ReachGraph completeGraph = new ReachGraph();
    Iterator retItr = returnNodesToCombineForCompleteReachabilityGraph.iterator();
    while( retItr.hasNext() ) {
      FlatReturnNode frn = (FlatReturnNode) retItr.next();
      assert mapFlatNodeToReachabilityGraph.containsKey(frn);
      ReachGraph ogr = mapFlatNodeToReachabilityGraph.get(frn);
      completeGraph.merge(ogr);
    }

    return completeGraph;
  }


  private ReachGraph
  analyzeFlatNode(MethodContext mc,
		  FlatMethod fmContaining,
                  FlatNode fn,
                  HashSet<FlatReturnNode> setRetNodes,
                  ReachGraph og) throws java.io.IOException {


    // any variables that are no longer live should be
    // nullified in the graph to reduce edges
    // NOTE: it is not clear we need this.  It costs a
    // liveness calculation for every method, so only
    // turn it on if we find we actually need it.
    //og.nullifyDeadVars( liveness.getLiveInTemps( fmContaining, fn ) );

	  
    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    // use node type to decide what alterations to make
    // to the ownership graph
    switch( fn.kind() ) {

    case FKind.FlatMethod:
      FlatMethod fm = (FlatMethod) fn;

      // there should only be one FlatMethod node as the
      // parent of all other FlatNode objects, so take
      // the opportunity to construct the initial graph by
      // adding parameters labels to new heap regions
      // AND this should be done once globally so that the
      // parameter IDs are consistent between analysis
      // iterations, so if this step has been done already
      // just merge in the cached version
      ReachGraph ogInitParamAlloc = mapMethodContextToInitialParamAllocGraph.get(mc);
      if( ogInitParamAlloc == null ) {

	// if the method context has aliased parameters, make sure
	// there is a blob region for all those param to reference
	Set<Integer> aliasedParamIndices = mc.getAliasedParamIndices();

	if( !aliasedParamIndices.isEmpty() ) {
	  og.makeAliasedParamHeapRegionNode(fm);
	}

	// set up each parameter
	for( int i = 0; i < fm.numParameters(); ++i ) {
	  TempDescriptor tdParam    = fm.getParameter( i );
	  TypeDescriptor typeParam  = tdParam.getType();
	  Integer        paramIndex = new Integer( i );

	  if( typeParam.isImmutable() && !typeParam.isArray() ) {
	    // don't bother with this primitive parameter, it
	    // cannot affect reachability
	    continue;
	  }

	  if( aliasedParamIndices.contains( paramIndex ) ) {
	    // use the alias blob but give parameters their
	    // own primary obj region
	    og.assignTempEqualToAliasedParam( tdParam,
					      paramIndex, fm );	    
	  } else {
	    // this parameter is not aliased to others, give it
	    // a fresh primary obj and secondary object
	    og.assignTempEqualToParamAlloc( tdParam,
					    mc.getDescriptor() instanceof TaskDescriptor,
					    paramIndex, fm );
	  }
	}
	
	// add additional edges for aliased regions if necessary
	if( !aliasedParamIndices.isEmpty() ) {
	  og.addParam2ParamAliasEdges( fm, aliasedParamIndices );
	}
	
	// clean up reachability on initial parameter shapes
	og.globalSweep();

	// this maps tokens to parameter indices and vice versa
	// for when this method is a callee
	og.prepareParamTokenMaps( fm );

	// cache the graph
	ReachGraph ogResult = new ReachGraph();
	ogResult.merge(og);
	mapMethodContextToInitialParamAllocGraph.put(mc, ogResult);

      } else {
	// or just leverage the cached copy
	og.merge(ogInitParamAlloc);
      }
      break;
      
    case FKind.FlatOpNode:
      FlatOpNode fon = (FlatOpNode) fn;
      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	lhs = fon.getDest();
	rhs = fon.getLeft();
	og.assignTempXEqualToTempY(lhs, rhs);
      }
      break;

    case FKind.FlatCastNode:
      FlatCastNode fcn = (FlatCastNode) fn;
      lhs = fcn.getDst();
      rhs = fcn.getSrc();

      TypeDescriptor td = fcn.getType();
      assert td != null;
      
      og.assignTempXEqualToCastedTempY(lhs, rhs, td);
      break;

    case FKind.FlatFieldNode:
      FlatFieldNode ffn = (FlatFieldNode) fn;
      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();
      if( !fld.getType().isImmutable() || fld.getType().isArray() ) {
	og.assignTempXEqualToTempYFieldF(lhs, rhs, fld);
      }
      
      meAnalysis.analyzeFlatFieldNode(mc, og, rhs, fld);
      
      break;

    case FKind.FlatSetFieldNode:
      FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
      lhs = fsfn.getDst();
      fld = fsfn.getField();
      rhs = fsfn.getSrc();
      if( !fld.getType().isImmutable() || fld.getType().isArray() ) {
	og.assignTempXFieldFEqualToTempY(lhs, fld, rhs);
      }
      
      meAnalysis.analyzeFlatSetFieldNode(mc, og, lhs, fld);
      
      break;

    case FKind.FlatElementNode:
      FlatElementNode fen = (FlatElementNode) fn;
      lhs = fen.getDst();
      rhs = fen.getSrc();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {

	assert rhs.getType() != null;
	assert rhs.getType().isArray();
	
	TypeDescriptor  tdElement = rhs.getType().dereference();
	FieldDescriptor fdElement = getArrayField( tdElement );
  
	og.assignTempXEqualToTempYFieldF(lhs, rhs, fdElement);
      }
      break;

    case FKind.FlatSetElementNode:
      FlatSetElementNode fsen = (FlatSetElementNode) fn;

      if( arrayReferencees.doesNotCreateNewReaching( fsen ) ) {
	// skip this node if it cannot create new reachability paths
        break;
      }

      lhs = fsen.getDst();
      rhs = fsen.getSrc();
      if( !rhs.getType().isImmutable() || rhs.getType().isArray() ) {

	assert lhs.getType() != null;
	assert lhs.getType().isArray();
	
	TypeDescriptor  tdElement = lhs.getType().dereference();
	FieldDescriptor fdElement = getArrayField( tdElement );

	og.assignTempXFieldFEqualToTempY(lhs, fdElement, rhs);
      }
      break;

    case FKind.FlatNew:
      FlatNew fnn = (FlatNew) fn;
      lhs = fnn.getDst();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {
	AllocSite as = getAllocSiteFromFlatNewPRIVATE(fnn);
	
	if (mapMethodContextToLiveInAllocSiteSet != null){
		HashSet<AllocSite> alllocSet=mapMethodContextToLiveInAllocSiteSet.get(mc);
		if(alllocSet!=null){
			for (Iterator iterator = alllocSet.iterator(); iterator
					.hasNext();) {
				AllocSite allocationSite = (AllocSite) iterator
						.next();
				if(allocationSite.flatNew.equals(as.flatNew)){
					as.setFlag(true);
				}
			}
		}
	}
	
	og.assignTempEqualToNewAlloc(lhs, as);
      }
      break;

    case FKind.FlatCall:
      FlatCall fc = (FlatCall) fn;
      MethodDescriptor md = fc.getMethod();
      FlatMethod flatm = state.getMethodFlat(md);
      ReachGraph ogMergeOfAllPossibleCalleeResults = new ReachGraph();

      if( md.isStatic() ) {
	// a static method is simply always the same, makes life easy
	ogMergeOfAllPossibleCalleeResults = og;

	Set<Integer> aliasedParamIndices = 
	  ogMergeOfAllPossibleCalleeResults.calculateAliasedParamSet(fc, md.isStatic(), flatm);

	MethodContext mcNew = new MethodContext( md, aliasedParamIndices );
	Set contexts = mapDescriptorToAllMethodContexts.get( md );
	assert contexts != null;
	contexts.add( mcNew );

	addDependent( mc, mcNew );

	ReachGraph onlyPossibleCallee = mapMethodContextToCompleteReachabilityGraph.get( mcNew );

	if( onlyPossibleCallee == null ) {
	  // if this method context has never been analyzed just schedule it for analysis
	  // and skip over this call site for now
	  if( !methodContextsToVisitSet.contains( mcNew ) ) {
	    methodContextsToVisitQ.add( new MethodContextQWrapper( mapDescriptorToPriority.get( md ), 
								   mcNew ) );
	    methodContextsToVisitSet.add( mcNew );
	  }
	  
	} else {
	  ogMergeOfAllPossibleCalleeResults.resolveMethodCall(fc, md.isStatic(), flatm, onlyPossibleCallee, mc, null);
	}
	
	meAnalysis.createNewMapping(mcNew);
	meAnalysis.analyzeFlatCall(ogMergeOfAllPossibleCalleeResults, mcNew, mc, fc);
	

      } else {
	// if the method descriptor is virtual, then there could be a
	// set of possible methods that will actually be invoked, so
	// find all of them and merge all of their results together
	TypeDescriptor typeDesc = fc.getThis().getType();
	Set possibleCallees = callGraph.getMethods(md, typeDesc);

	Iterator i = possibleCallees.iterator();
	while( i.hasNext() ) {
	  MethodDescriptor possibleMd = (MethodDescriptor) i.next();
	  FlatMethod pflatm = state.getMethodFlat(possibleMd);

	  // don't alter the working graph (og) until we compute a result for every
	  // possible callee, merge them all together, then set og to that
	  ReachGraph ogCopy = new ReachGraph();
	  ogCopy.merge(og);

	  Set<Integer> aliasedParamIndices = 
	    ogCopy.calculateAliasedParamSet(fc, possibleMd.isStatic(), pflatm);

	  MethodContext mcNew = new MethodContext( possibleMd, aliasedParamIndices );
	  Set contexts = mapDescriptorToAllMethodContexts.get( md );
	  assert contexts != null;
	  contexts.add( mcNew );
	  
		
	meAnalysis.createNewMapping(mcNew);
		
	  
	  addDependent( mc, mcNew );

	  ReachGraph ogPotentialCallee = mapMethodContextToCompleteReachabilityGraph.get( mcNew );

	  if( ogPotentialCallee == null ) {
	    // if this method context has never been analyzed just schedule it for analysis
	    // and skip over this call site for now
	    if( !methodContextsToVisitSet.contains( mcNew ) ) {
	      methodContextsToVisitQ.add( new MethodContextQWrapper( mapDescriptorToPriority.get( md ), 
								     mcNew ) );
	      methodContextsToVisitSet.add( mcNew );
	    }
	    
	  } else {
	    ogCopy.resolveMethodCall(fc, possibleMd.isStatic(), pflatm, ogPotentialCallee, mc, null);
	  }
		
	  ogMergeOfAllPossibleCalleeResults.merge(ogCopy);
	  
	  meAnalysis.analyzeFlatCall(ogMergeOfAllPossibleCalleeResults, mcNew, mc, fc);
	}
	
      }

      og = ogMergeOfAllPossibleCalleeResults;
      break;

    case FKind.FlatReturnNode:
      FlatReturnNode frn = (FlatReturnNode) fn;
      rhs = frn.getReturnTemp();
      if( rhs != null && !rhs.getType().isImmutable() ) {
	og.assignReturnEqualToTemp(rhs);
      }
      setRetNodes.add(frn);
      break;
    }


    if( methodEffects ) {
      Hashtable<FlatNode, ReachabilityGraph> table=mapMethodContextToFlatNodeReachabilityGraph.get(mc);
      if(table==null){
    	table=new     Hashtable<FlatNode, ReachabilityGraph>();    	
      }
      table.put(fn, og);
      mapMethodContextToFlatNodeReachabilityGraph.put(mc, table);
    }

    return og;
  }


  // this method should generate integers strictly greater than zero!
  // special "shadow" regions are made from a heap region by negating
  // the ID
  static public Integer generateUniqueHeapRegionNodeID() {
    ++uniqueIDcount;
    return new Integer(uniqueIDcount);
  }


  static public FieldDescriptor getArrayField( TypeDescriptor tdElement ) {
    FieldDescriptor fdElement = mapTypeToArrayField.get( tdElement );
    if( fdElement == null ) {
      fdElement = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC),
				      tdElement,
				      arrayElementFieldName,
				      null,
				      false);
      mapTypeToArrayField.put( tdElement, fdElement );
    }
    return fdElement;
  }

  
  private void setGraphForMethodContext(MethodContext mc, ReachabilityGraph og) {

    mapMethodContextToCompleteReachabilityGraph.put(mc, og);

    if( writeFinalDOTs && writeAllIncrementalDOTs ) {
      if( !mapMethodContextToNumUpdates.containsKey(mc) ) {
	mapMethodContextToNumUpdates.put(mc, new Integer(0) );
      }
      Integer n = mapMethodContextToNumUpdates.get(mc);
      try {
	og.writeGraph(mc+"COMPLETE"+String.format("%05d", n),
		      true,  // write labels (variables)
		      true,  // selectively hide intermediate temp vars
		      true,  // prune unreachable heap regions
		      false, // show back edges to confirm graph validity
		      false, // show parameter indices (unmaintained!)
		      true,  // hide subset reachability states
		      true); // hide edge taints
      } catch( IOException e ) {}
      mapMethodContextToNumUpdates.put(mc, n + 1);
    }
  }


  private void addDependent( MethodContext caller, MethodContext callee ) {
    HashSet<MethodContext> deps = mapMethodContextToDependentContexts.get( callee );
    if( deps == null ) {
      deps = new HashSet<MethodContext>();
    }
    deps.add( caller );
    mapMethodContextToDependentContexts.put( callee, deps );
  }

  private Iterator<MethodContext> iteratorDependents( MethodContext callee ) {
    HashSet<MethodContext> deps = mapMethodContextToDependentContexts.get( callee );
    if( deps == null ) {
      deps = new HashSet<MethodContext>();
      mapMethodContextToDependentContexts.put( callee, deps );
    }
    return deps.iterator();
  }


  private void writeFinalContextGraphs() {
    Set entrySet = mapMethodContextToCompleteReachabilityGraph.entrySet();
    Iterator itr = entrySet.iterator();
    while( itr.hasNext() ) {
      Map.Entry      me = (Map.Entry)      itr.next();
      MethodContext  mc = (MethodContext)  me.getKey();
      ReachabilityGraph og = (ReachabilityGraph) me.getValue();

      try {
	og.writeGraph(mc+"COMPLETE",
		      true,  // write labels (variables)
		      true,  // selectively hide intermediate temp vars
		      true,  // prune unreachable heap regions
		      false, // show back edges to confirm graph validity
		      false, // show parameter indices (unmaintained!)
		      true,  // hide subset reachability states
		      true); // hide edge taints
      } catch( IOException e ) {}    
    }
  }
  
  

  // return just the allocation site associated with one FlatNew node
  private AllocSite getAllocSiteFromFlatNewPRIVATE(FlatNew fn) {

    if( !mapFlatNewToAllocSite.containsKey(fn) ) {
      AllocSite as = new AllocSite(allocationDepth, fn, fn.getDisjointAnalysisId());

      // the newest nodes are single objects
      for( int i = 0; i < allocationDepth; ++i ) {
	Integer id = generateUniqueHeapRegionNodeID();
	as.setIthOldest(i, id);
	mapHrnIdToAllocSite.put( id, as );
      }

      // the oldest node is a summary node
      Integer idSummary = generateUniqueHeapRegionNodeID();
      as.setSummary(idSummary);

      mapFlatNewToAllocSite.put(fn, as);
    }

    return mapFlatNewToAllocSite.get(fn);
  }


  // return all allocation sites in the method (there is one allocation
  // site per FlatNew node in a method)
  private HashSet<AllocSite> getAllocSiteSet(Descriptor d) {
    if( !mapDescriptorToAllocSiteSet.containsKey(d) ) {
      buildAllocSiteSet(d);
    }

    return mapDescriptorToAllocSiteSet.get(d);

  }

  private void buildAllocSiteSet(Descriptor d) {
    HashSet<AllocSite> s = new HashSet<AllocSite>();

    FlatMethod fm = state.getMethodFlat( d );

    // visit every node in this FlatMethod's IR graph
    // and make a set of the allocation sites from the
    // FlatNew node's visited
    HashSet<FlatNode> visited = new HashSet<FlatNode>();
    HashSet<FlatNode> toVisit = new HashSet<FlatNode>();
    toVisit.add( fm );

    while( !toVisit.isEmpty() ) {
      FlatNode n = toVisit.iterator().next();

      if( n instanceof FlatNew ) {
	s.add(getAllocSiteFromFlatNewPRIVATE( (FlatNew) n) );
      }

      toVisit.remove( n );
      visited.add( n );

      for( int i = 0; i < n.numNext(); ++i ) {
	FlatNode child = n.getNext( i );
	if( !visited.contains( child ) ) {
	  toVisit.add( child );
	}
      }
    }

    mapDescriptorToAllocSiteSet.put( d, s );
  }


  private HashSet<AllocSite> getFlaggedAllocSites(Descriptor dIn) {
    
    HashSet<AllocSite> out     = new HashSet<AllocSite>();
    HashSet<Descriptor>     toVisit = new HashSet<Descriptor>();
    HashSet<Descriptor>     visited = new HashSet<Descriptor>();

    toVisit.add(dIn);

    while( !toVisit.isEmpty() ) {
      Descriptor d = toVisit.iterator().next();
      toVisit.remove(d);
      visited.add(d);

      HashSet<AllocSite> asSet = getAllocSiteSet(d);
      Iterator asItr = asSet.iterator();
      while( asItr.hasNext() ) {
	AllocSite as = (AllocSite) asItr.next();
	if( as.getDisjointAnalysisId() != null ) {
	  out.add(as);
	}
      }

      // enqueue callees of this method to be searched for
      // allocation sites also
      Set callees = callGraph.getCalleeSet(d);
      if( callees != null ) {
	Iterator methItr = callees.iterator();
	while( methItr.hasNext() ) {
	  MethodDescriptor md = (MethodDescriptor) methItr.next();

	  if( !visited.contains(md) ) {
	    toVisit.add(md);
	  }
	}
      }
    }
    
    return out;
  }


  private HashSet<AllocSite>
  getFlaggedAllocSitesReachableFromTaskPRIVATE(TaskDescriptor td) {

    HashSet<AllocSite> asSetTotal = new HashSet<AllocSite>();
    HashSet<Descriptor>     toVisit    = new HashSet<Descriptor>();
    HashSet<Descriptor>     visited    = new HashSet<Descriptor>();

    toVisit.add(td);

    // traverse this task and all methods reachable from this task
    while( !toVisit.isEmpty() ) {
      Descriptor d = toVisit.iterator().next();
      toVisit.remove(d);
      visited.add(d);

      HashSet<AllocSite> asSet = getAllocSiteSet(d);
      Iterator asItr = asSet.iterator();
      while( asItr.hasNext() ) {
	AllocSite as = (AllocSite) asItr.next();
	TypeDescriptor typed = as.getType();
	if( typed != null ) {
	  ClassDescriptor cd = typed.getClassDesc();
	  if( cd != null && cd.hasFlags() ) {
	    asSetTotal.add(as);
	  }
	}
      }

      // enqueue callees of this method to be searched for
      // allocation sites also
      Set callees = callGraph.getCalleeSet(d);
      if( callees != null ) {
	Iterator methItr = callees.iterator();
	while( methItr.hasNext() ) {
	  MethodDescriptor md = (MethodDescriptor) methItr.next();

	  if( !visited.contains(md) ) {
	    toVisit.add(md);
	  }
	}
      }
    }


    return asSetTotal;
  }
  */

  private LinkedList<Descriptor> topologicalSort( Set<Descriptor> toSort ) {

    Set       <Descriptor> discovered = new HashSet   <Descriptor>();
    LinkedList<Descriptor> sorted     = new LinkedList<Descriptor>();
  
    Iterator<Descriptor> itr = toSort.iterator();
    while( itr.hasNext() ) {
      Descriptor d = itr.next();
          
      if( !discovered.contains( d ) ) {
	dfsVisit( d, toSort, sorted, discovered );
      }
    }
    
    return sorted;
  }
  
  private void dfsVisit( Descriptor             d,
                         Set       <Descriptor> toSort,			 
			 LinkedList<Descriptor> sorted,
			 Set       <Descriptor> discovered ) {
    discovered.add( d );
    
    // only methods have callers, tasks never do
    if( d instanceof MethodDescriptor ) {

      MethodDescriptor md = (MethodDescriptor) d;

      // the call graph is not aware that we have a fabricated
      // analysis entry that calls the program source's entry
      if( md == mdSourceEntry ) {
        if( !discovered.contains( mdAnalysisEntry ) ) {
          dfsVisit( mdAnalysisEntry, toSort, sorted, discovered );
        }
      }

      // otherwise call graph guides DFS
      Iterator itr = callGraph.getCallerSet( md ).iterator();
      while( itr.hasNext() ) {
	Descriptor dCaller = (Descriptor) itr.next();
	
	// only consider callers in the original set to analyze
        if( !toSort.contains( dCaller ) ) {
	  continue;
        }
          
	if( !discovered.contains( dCaller ) ) {
	  dfsVisit( dCaller, toSort, sorted, discovered );
	}
      }
    }
    
    sorted.addFirst( d );
  }


  /*
  private String computeAliasContextHistogram() {
    
    Hashtable<Integer, Integer> mapNumContexts2NumDesc = 
      new Hashtable<Integer, Integer>();
  
    Iterator itr = mapDescriptorToAllMethodContexts.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry me = (Map.Entry) itr.next();
      HashSet<MethodContext> s = (HashSet<MethodContext>) me.getValue();
      
      Integer i = mapNumContexts2NumDesc.get( s.size() );
      if( i == null ) {
	i = new Integer( 0 );
      }
      mapNumContexts2NumDesc.put( s.size(), i + 1 );
    }   

    String s = "";
    int total = 0;

    itr = mapNumContexts2NumDesc.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry me = (Map.Entry) itr.next();
      Integer c0 = (Integer) me.getKey();
      Integer d0 = (Integer) me.getValue();
      total += d0;
      s += String.format( "%4d methods had %4d unique alias contexts.\n", d0, c0 );
    }

    s += String.format( "\n%4d total methods analayzed.\n", total );

    return s;
  }

  private int numMethodsAnalyzed() {    
    return descriptorsToAnalyze.size();
  }
  */


  /*
  // insert a call to debugSnapshot() somewhere in the analysis 
  // to get successive captures of the analysis state
  boolean takeDebugSnapshots = false;
  String mcDescSymbolDebug = "setRoute";
  boolean stopAfterCapture = true;

  // increments every visit to debugSnapshot, don't fiddle with it
  // IMPORTANT NOTE FOR SETTING THE FOLLOWING VALUES: this
  // counter increments just after every node is analyzed
  // from the body of the method whose symbol is specified
  // above.
  int debugCounter = 0;

  // the value of debugCounter to start reporting the debugCounter
  // to the screen to let user know what debug iteration we're at
  int numStartCountReport = 0;

  // the frequency of debugCounter values to print out, 0 no report
  int freqCountReport = 0;

  // the debugCounter value at which to start taking snapshots
  int iterStartCapture = 0;

  // the number of snapshots to take
  int numIterToCapture = 300;

  void debugSnapshot(ReachabilityGraph og, FlatNode fn) {
    if( debugCounter > iterStartCapture + numIterToCapture ) {
      return;
    }

    ++debugCounter;
    if( debugCounter > numStartCountReport &&
	freqCountReport > 0 &&
        debugCounter % freqCountReport == 0 ) {
      System.out.println("    @@@ debug counter = "+debugCounter);
    }
    if( debugCounter > iterStartCapture ) {
      System.out.println("    @@@ capturing debug "+(debugCounter-iterStartCapture)+" @@@");
      String graphName = String.format("snap%04d",debugCounter-iterStartCapture);
      if( fn != null ) {
	graphName = graphName+fn;
      }
      try {
	og.writeGraph(graphName,
		      true,  // write labels (variables)
		      true,  // selectively hide intermediate temp vars
		      true,  // prune unreachable heap regions
		      false, // show back edges to confirm graph validity
		      false, // show parameter indices (unmaintained!)
		      true,  // hide subset reachability states
		      true); // hide edge taints
      } catch( Exception e ) {
	System.out.println("Error writing debug capture.");
	System.exit(0);
      }
    }

    if( debugCounter == iterStartCapture + numIterToCapture && stopAfterCapture ) {
      System.out.println("Stopping analysis after debug captures.");
      System.exit(0);
    }
  }
  */
  
  
  // Take in sourceEntry which is the program's compiled entry and
  // create a new analysis entry, a method that takes no parameters
  // and appears to allocate the command line arguments and call the
  // sourceEntry with them.  The purpose of this analysis entry is to
  // provide a top-level method context with no parameters left.
  private void makeAnalysisEntryMethod( MethodDescriptor sourceEntry ) {

    Modifiers mods = new Modifiers();
    mods.addModifier( Modifiers.PUBLIC );
    mods.addModifier( Modifiers.STATIC );

    TypeDescriptor returnType = 
      new TypeDescriptor( TypeDescriptor.VOID );

    this.mdAnalysisEntry = 
      new MethodDescriptor( mods,
                            returnType,
                            "analysisEntryMethod"
                            );
    
    FlatExit fe = new FlatExit();    

    TempDescriptor cmdLineArgs = new TempDescriptor( "args" );
    
    FlatNew fn = new FlatNew( sourceEntry.getParamType( 0 ),
                              cmdLineArgs,
                              false // is global 
                              );

    //FlatCall fc = new

    this.fmAnalysisEntry = new FlatMethod( mdAnalysisEntry, fe );    
  }
}
