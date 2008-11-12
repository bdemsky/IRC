package Analysis.OwnershipAnalysis;

import Analysis.CallGraph.*;
import IR.*;
import IR.Flat.*;
import IR.Tree.Modifiers;
import java.util.*;
import java.io.*;


public class OwnershipAnalysis {


  ///////////////////////////////////////////
  //
  //  Public interface to discover possible
  //  aliases in the program under analysis
  //
  ///////////////////////////////////////////

  public HashSet<AllocationSite>
  getFlaggedAllocationSitesReachableFromTask(TaskDescriptor td) {
    return getFlaggedAllocationSitesReachableFromTaskPRIVATE(td);
  }

  public AllocationSite getAllocationSiteFromFlatNew(FlatNew fn) {
    return getAllocationSiteFromFlatNewPRIVATE(fn);
  }


  public boolean createsPotentialAliases(Descriptor taskOrMethod,
                                         int paramIndex1,
                                         int paramIndex2) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(paramIndex1, paramIndex2);
  }

  public boolean createsPotentialAliases(Descriptor taskOrMethod,
                                         int paramIndex,
                                         AllocationSite alloc) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(paramIndex, alloc);
  }

  public boolean createsPotentialAliases(Descriptor taskOrMethod,
                                         AllocationSite alloc,
                                         int paramIndex) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(paramIndex, alloc);
  }

  public boolean createsPotentialAliases(Descriptor taskOrMethod,
                                         AllocationSite alloc1,
                                         AllocationSite alloc2) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(alloc1, alloc2);
  }


  protected OwnershipGraph getGraphOfAllContextsFromDescriptor(Descriptor d) {
    assert d != null;

    OwnershipGraph og = new OwnershipGraph( allocationDepth, typeUtil );

    assert mapDescriptorToAllMethodContexts.containsKey( d );
    HashSet<MethodContext> contexts = mapDescriptorToAllMethodContexts.get( d );
    Iterator<MethodContext> mcItr = contexts.iterator();
    while( mcItr.hasNext() ) {
      MethodContext mc = mcItr.next();

      OwnershipGraph ogContext = mapMethodContextToCompleteOwnershipGraph.get(mc);
      assert ogContext != null;

      og.merge( ogContext );
    }

    return og;
  }


  // use the methods given above to check every possible alias
  // between task parameters and flagged allocation sites reachable
  // from the task
  public void writeAllAliases(String outputFile) throws java.io.IOException {

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile) );

    bw.write("Conducting ownership analysis with allocation depth = "+allocationDepth);

    // look through every task for potential aliases
    Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
    while( taskItr.hasNext() ) {
      TaskDescriptor td = (TaskDescriptor) taskItr.next();

      bw.write("\n---------"+td+"--------\n");

      HashSet<AllocationSite> allocSites = getFlaggedAllocationSitesReachableFromTask(td);

      // for each task parameter, check for aliases with
      // other task parameters and every allocation site
      // reachable from this task
      boolean foundSomeAlias = false;

      FlatMethod fm = state.getMethodFlat(td);
      for( int i = 0; i < fm.numParameters(); ++i ) {

	// for the ith parameter check for aliases to all
	// higher numbered parameters
	for( int j = i + 1; j < fm.numParameters(); ++j ) {
	  if( createsPotentialAliases(td, i, j) ) {
	    foundSomeAlias = true;
	    bw.write("Potential alias between parameters "+i+" and "+j+".\n");
	  }
	}

	// for the ith parameter, check for aliases against
	// the set of allocation sites reachable from this
	// task context
	Iterator allocItr = allocSites.iterator();
	while( allocItr.hasNext() ) {
	  AllocationSite as = (AllocationSite) allocItr.next();
	  if( createsPotentialAliases(td, i, as) ) {
	    foundSomeAlias = true;
	    bw.write("Potential alias between parameter "+i+" and "+as.getFlatNew()+".\n");
	  }
	}
      }

      // for each allocation site check for aliases with
      // other allocation sites in the context of execution
      // of this task
      HashSet<AllocationSite> outerChecked = new HashSet<AllocationSite>();
      Iterator allocItr1 = allocSites.iterator();
      while( allocItr1.hasNext() ) {
	AllocationSite as1 = (AllocationSite) allocItr1.next();

	Iterator allocItr2 = allocSites.iterator();
	while( allocItr2.hasNext() ) {
	  AllocationSite as2 = (AllocationSite) allocItr2.next();

	  if( !outerChecked.contains(as2) &&
	      createsPotentialAliases(td, as1, as2) ) {
	    foundSomeAlias = true;
	    bw.write("Potential alias between "+as1.getFlatNew()+" and "+as2.getFlatNew()+".\n");
	  }
	}

	outerChecked.add(as1);
      }

      if( !foundSomeAlias ) {
	bw.write("No aliases between flagged objects in Task "+td+".\n");
      }
    }

    bw.close();
  }

  ///////////////////////////////////////////
  //
  // end public interface
  //
  ///////////////////////////////////////////








  // data from the compiler
  private State state;
  private TypeUtil typeUtil;
  private CallGraph callGraph;
  private int allocationDepth;

  // used to identify HeapRegionNode objects
  // A unique ID equates an object in one
  // ownership graph with an object in another
  // graph that logically represents the same
  // heap region
  // start at 10 and incerement to leave some
  // reserved IDs for special purposes
  static private int uniqueIDcount = 10;


  // Use these data structures to track progress of
  // processing all methods in the program, and by methods
  // TaskDescriptor and MethodDescriptor are combined
  // together, with a common parent class Descriptor
  private Hashtable<MethodContext, OwnershipGraph>           mapMethodContextToInitialParamAllocGraph;
  private Hashtable<MethodContext, OwnershipGraph>           mapMethodContextToCompleteOwnershipGraph;
  private Hashtable<FlatNew,       AllocationSite>           mapFlatNewToAllocationSite;
  private Hashtable<Descriptor,    HashSet<AllocationSite> > mapDescriptorToAllocationSiteSet;
  private Hashtable<MethodContext, Integer>                  mapMethodContextToNumUpdates;
  private Hashtable<Descriptor,    HashSet<MethodContext> >  mapDescriptorToAllMethodContexts;

  // Use these data structures to track progress of one pass of
  // processing the FlatNodes of a particular method
  private HashSet  <FlatNode>                 flatNodesToVisit;
  private Hashtable<FlatNode, OwnershipGraph> mapFlatNodeToOwnershipGraph;
  private HashSet  <FlatReturnNode>           returnNodesToCombineForCompleteOwnershipGraph;

  // descriptorsToAnalyze identifies the set of tasks and methods
  // that are reachable from the program tasks, this set is initialized
  // and then remains static
  private HashSet<Descriptor> descriptorsToAnalyze;

  // descriptorsToVisit is initialized to descriptorsToAnalyze and is
  // reduced by visiting a descriptor during analysis.  When dependents
  // must be scheduled, only those contained in descriptorsToAnalyze
  // should be re-added to this set
  private HashSet<MethodContext> methodContextsToVisit;

  // a special field descriptor for all array elements
  private static FieldDescriptor fdElement = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC),
                                                                 new TypeDescriptor("Array[]"),
                                                                 "elements",
                                                                 null,
                                                                 false);

  // a special temp descriptor for setting more than one parameter label
  // to the all-aliased-parameters heap region node
  protected static TempDescriptor tdAliasedParams = new TempDescriptor("_AllAliasedParams___");


  // for controlling DOT file output
  private boolean writeDOTs;
  private boolean writeAllDOTs;



  // this analysis generates an ownership graph for every task
  // in the program
  public OwnershipAnalysis(State state,
                           TypeUtil tu,
                           CallGraph callGraph,
                           int allocationDepth,
                           boolean writeDOTs,
                           boolean writeAllDOTs,
                           String aliasFile) throws java.io.IOException {

    double timeStartAnalysis = (double) System.nanoTime();

    this.state           = state;
    this.typeUtil        = tu;
    this.callGraph       = callGraph;
    this.allocationDepth = allocationDepth;
    this.writeDOTs       = writeDOTs;
    this.writeAllDOTs    = writeAllDOTs;

    descriptorsToAnalyze = new HashSet<Descriptor>();

    mapMethodContextToInitialParamAllocGraph =
      new Hashtable<MethodContext, OwnershipGraph>();

    mapMethodContextToCompleteOwnershipGraph =
      new Hashtable<MethodContext, OwnershipGraph>();

    mapFlatNewToAllocationSite =
      new Hashtable<FlatNew, AllocationSite>();

    mapDescriptorToAllocationSiteSet =
      new Hashtable<Descriptor, HashSet<AllocationSite> >();

    mapDescriptorToAllMethodContexts = 
      new Hashtable<Descriptor, HashSet<MethodContext> >();


    if( writeAllDOTs ) {
      mapMethodContextToNumUpdates = new Hashtable<MethodContext, Integer>();
    }

    // initialize methods to visit as the set of all tasks in the
    // program and then any method that could be called starting
    // from those tasks
    Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
    while( taskItr.hasNext() ) {
      Descriptor d = (Descriptor) taskItr.next();
      scheduleAllCallees(d);
    }

    // before beginning analysis, initialize every scheduled method
    // with an ownership graph that has populated parameter index tables
    // by analyzing the first node which is always a FlatMethod node
    Iterator<Descriptor> dItr = descriptorsToAnalyze.iterator();
    while( dItr.hasNext() ) {
      Descriptor d  = dItr.next();
      OwnershipGraph og = new OwnershipGraph(allocationDepth, typeUtil);

      FlatMethod fm;
      if( d instanceof MethodDescriptor ) {
	fm = state.getMethodFlat( (MethodDescriptor) d);
      } else {
	assert d instanceof TaskDescriptor;
	fm = state.getMethodFlat( (TaskDescriptor) d);
      }

      MethodContext mc = new MethodContext( d );
      assert !mapDescriptorToAllMethodContexts.containsKey( d );
      HashSet<MethodContext> s = new HashSet<MethodContext>();
      s.add( mc );
      mapDescriptorToAllMethodContexts.put( d, s );

      //System.out.println("Previsiting " + mc);

      og = analyzeFlatNode(mc, fm, null, og);
      setGraphForMethodContext(mc, og);
    }

    //System.out.println("");

    // as mentioned above, analyze methods one-by-one, possibly revisiting
    // a method if the methods that it calls are updated
    analyzeMethods();

    //System.out.println("");

    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
    String treport = String.format( "The analysis took %.3f sec.", dt );
    System.out.println( treport );

    if( aliasFile != null ) {
      writeAllAliases(aliasFile);
    }
  }

  // called from the constructor to help initialize the set
  // of methods that needs to be analyzed by ownership analysis
  private void scheduleAllCallees(Descriptor d) {
    if( descriptorsToAnalyze.contains(d) ) {
      return;
    }
    descriptorsToAnalyze.add(d);

    // start with all method calls to further schedule
    Set moreMethodsToCheck = moreMethodsToCheck = callGraph.getMethodCalls(d);

    if( d instanceof MethodDescriptor ) {
      // see if this method has virtual dispatch
      Set virtualMethods = callGraph.getMethods( (MethodDescriptor)d);
      moreMethodsToCheck.addAll(virtualMethods);
    }

    // keep following any further methods identified in
    // the call chain
    Iterator methItr = moreMethodsToCheck.iterator();
    while( methItr.hasNext() ) {
      Descriptor m = (Descriptor) methItr.next();
      scheduleAllCallees(m);
    }
  }


  // manage the set of tasks and methods to be analyzed
  // and be sure to reschedule tasks/methods when the methods
  // they call are updated
  private void analyzeMethods() throws java.io.IOException {

    methodContextsToVisit = new HashSet<MethodContext>();    
    Iterator<Descriptor> itrd2a = descriptorsToAnalyze.iterator();
    while( itrd2a.hasNext() ) {
      HashSet<MethodContext> mcs = mapDescriptorToAllMethodContexts.get( itrd2a.next() );
      assert mcs != null;

      Iterator<MethodContext> itrmc = mcs.iterator();
      while( itrmc.hasNext() ) {
	methodContextsToVisit.add( itrmc.next() );
      }
    }

    while( !methodContextsToVisit.isEmpty() ) {
      MethodContext mc = methodContextsToVisit.iterator().next();
      methodContextsToVisit.remove(mc);


      // because the task or method descriptor just extracted
      // was in the "to visit" set it either hasn't been analyzed
      // yet, or some method that it depends on has been
      // updated.  Recompute a complete ownership graph for
      // this task/method and compare it to any previous result.
      // If there is a change detected, add any methods/tasks
      // that depend on this one to the "to visit" set.

      //System.out.println("Analyzing " + mc);

      Descriptor d = mc.getDescriptor();
      FlatMethod fm;
      if( d instanceof MethodDescriptor ) {
	fm = state.getMethodFlat( (MethodDescriptor) d);
      } else {
	assert d instanceof TaskDescriptor;
	fm = state.getMethodFlat( (TaskDescriptor) d);
      }

      OwnershipGraph og = analyzeFlatMethod(mc, fm);
      OwnershipGraph ogPrev = mapMethodContextToCompleteOwnershipGraph.get(mc);
      if( !og.equals(ogPrev) ) {
	setGraphForMethodContext(mc, og);

	// only methods have dependents, tasks cannot
	// be invoked by any user program calls
	if( d instanceof MethodDescriptor ) {
	  MethodDescriptor md = (MethodDescriptor) d;
	  Set dependents = callGraph.getCallerSet(md);
	  if( dependents != null ) {
	    Iterator depItr = dependents.iterator();
	    while( depItr.hasNext() ) {
	      Descriptor dependent = (Descriptor) depItr.next();
	      if( descriptorsToAnalyze.contains(dependent) ) {
		
		HashSet<MethodContext> mcs = mapDescriptorToAllMethodContexts.get( dependent );
		assert mcs != null;
		
		Iterator<MethodContext> itrmc = mcs.iterator();
		while( itrmc.hasNext() ) {
		  methodContextsToVisit.add( itrmc.next() );
		}
	      }
	    }
	  }
	}
      }
    }

  }


  // keep passing the Descriptor of the method along for debugging
  // and dot file writing
  private OwnershipGraph
  analyzeFlatMethod(MethodContext mc,
                    FlatMethod flatm) throws java.io.IOException {

    // initialize flat nodes to visit as the flat method
    // because it is the entry point

    flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(flatm);

    // initilize the mapping of flat nodes in this flat method to
    // ownership graph results to an empty mapping
    mapFlatNodeToOwnershipGraph = new Hashtable<FlatNode, OwnershipGraph>();

    // initialize the set of return nodes that will be combined as
    // the final ownership graph result to return as an empty set
    returnNodesToCombineForCompleteOwnershipGraph = new HashSet<FlatReturnNode>();


    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove(fn);

      //System.out.println( "  "+fn );

      // perform this node's contributions to the ownership
      // graph on a new copy, then compare it to the old graph
      // at this node to see if anything was updated.
      OwnershipGraph og = new OwnershipGraph(allocationDepth, typeUtil);

      // start by merging all node's parents' graphs
      for( int i = 0; i < fn.numPrev(); ++i ) {
	FlatNode pn = fn.getPrev(i);
	if( mapFlatNodeToOwnershipGraph.containsKey(pn) ) {
	  OwnershipGraph ogParent = mapFlatNodeToOwnershipGraph.get(pn);
	  og.merge(ogParent);
	}
      }

      // apply the analysis of the flat node to the
      // ownership graph made from the merge of the
      // parent graphs
      og = analyzeFlatNode(mc,
                           fn,
                           returnNodesToCombineForCompleteOwnershipGraph,
                           og);


      //debugSnapshot(og,fn);



      // if the results of the new graph are different from
      // the current graph at this node, replace the graph
      // with the update and enqueue the children for
      // processing
      OwnershipGraph ogPrev = mapFlatNodeToOwnershipGraph.get(fn);
      if( !og.equals(ogPrev) ) {
	mapFlatNodeToOwnershipGraph.put(fn, og);

	for( int i = 0; i < fn.numNext(); i++ ) {
	  FlatNode nn = fn.getNext(i);
	  flatNodesToVisit.add(nn);
	}
      }
    }

    // end by merging all return nodes into a complete
    // ownership graph that represents all possible heap
    // states after the flat method returns
    OwnershipGraph completeGraph = new OwnershipGraph(allocationDepth, typeUtil);
    Iterator retItr = returnNodesToCombineForCompleteOwnershipGraph.iterator();
    while( retItr.hasNext() ) {
      FlatReturnNode frn = (FlatReturnNode) retItr.next();
      assert mapFlatNodeToOwnershipGraph.containsKey(frn);
      OwnershipGraph ogr = mapFlatNodeToOwnershipGraph.get(frn);
      completeGraph.merge(ogr);
    }

    return completeGraph;
  }


  private OwnershipGraph
  analyzeFlatNode(MethodContext mc,
                  FlatNode fn,
                  HashSet<FlatReturnNode> setRetNodes,
                  OwnershipGraph og) throws java.io.IOException {

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
      OwnershipGraph ogInitParamAlloc = mapMethodContextToInitialParamAllocGraph.get(mc);
      if( ogInitParamAlloc == null ) {

	// if the method context has aliased parameters, make sure
	// there is a blob region for all those param labels to
	// reference
	Set<Integer> aliasedParamIndices = mc.getAliasedParamIndices();
	if( !aliasedParamIndices.isEmpty() ) {
	  og.makeAliasedParamHeapRegionNode( tdAliasedParams );
	}

	// set up each parameter
	for( int i = 0; i < fm.numParameters(); ++i ) {
	  TempDescriptor tdParam = fm.getParameter( i );
	  Integer        paramIndex = new Integer( i );

	  if( aliasedParamIndices.contains( paramIndex ) ) {
	    // just point this one to the alias blob
	    og.assignTempEqualToAliasedParam( tdParam,
					      tdAliasedParams,
					      paramIndex );	    
	  } else {
	    // this parameter is not aliased to others, give it
	    // a fresh parameter heap region
	    
	    og.assignTempEqualToParamAlloc(tdParam,
					   mc.getDescriptor() instanceof TaskDescriptor,
					   paramIndex );
	  }
	}
	
	// cache the graph
	OwnershipGraph ogResult = new OwnershipGraph(allocationDepth, typeUtil);
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

    case FKind.FlatFieldNode:
      FlatFieldNode ffn = (FlatFieldNode) fn;
      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();
      if( !fld.getType().isImmutable() ) {
	og.assignTempXEqualToTempYFieldF(lhs, rhs, fld);
      }
      break;

    case FKind.FlatSetFieldNode:
      FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
      lhs = fsfn.getDst();
      fld = fsfn.getField();
      rhs = fsfn.getSrc();
      if( !fld.getType().isImmutable() ) {
	og.assignTempXFieldFEqualToTempY(lhs, fld, rhs);
      }
      break;

    case FKind.FlatElementNode:
      FlatElementNode fen = (FlatElementNode) fn;
      lhs = fen.getDst();
      rhs = fen.getSrc();
      if( !lhs.getType().isImmutable() ) {
	og.assignTempXEqualToTempYFieldF(lhs, rhs, fdElement);
      }
      break;

    case FKind.FlatSetElementNode:
      FlatSetElementNode fsen = (FlatSetElementNode) fn;
      lhs = fsen.getDst();
      rhs = fsen.getSrc();
      if( !rhs.getType().isImmutable() ) {
	og.assignTempXFieldFEqualToTempY(lhs, fdElement, rhs);
      }
      break;

    case FKind.FlatNew:
      FlatNew fnn = (FlatNew) fn;
      lhs = fnn.getDst();
      if( !lhs.getType().isImmutable() ) {
	AllocationSite as = getAllocationSiteFromFlatNewPRIVATE(fnn);
	og.assignTempEqualToNewAlloc(lhs, as);
      }
      break;

    case FKind.FlatCall:
      FlatCall fc = (FlatCall) fn;
      MethodDescriptor md = fc.getMethod();
      FlatMethod flatm = state.getMethodFlat(md);
      OwnershipGraph ogMergeOfAllPossibleCalleeResults = new OwnershipGraph(allocationDepth, typeUtil);

      if( md.isStatic() ) {
	// a static method is simply always the same, makes life easy
	ogMergeOfAllPossibleCalleeResults = og;

	Set<Integer> aliasedParamIndices = 
	  ogMergeOfAllPossibleCalleeResults.calculateAliasedParamSet(fc, md.isStatic(), flatm);
	MethodContext mcNew = new MethodContext( md, aliasedParamIndices );      
	OwnershipGraph onlyPossibleCallee = mapMethodContextToCompleteOwnershipGraph.get( mcNew );

	if( onlyPossibleCallee == null ) {
	  // if this method context has never been analyzed just schedule it for analysis
	  // and skip over this call site for now
	  methodContextsToVisit.add( mcNew );
	  
	} else {
	  ogMergeOfAllPossibleCalleeResults.resolveMethodCall(fc, md.isStatic(), flatm, onlyPossibleCallee);
	}

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
	  OwnershipGraph ogCopy = new OwnershipGraph(allocationDepth, typeUtil);
	  ogCopy.merge(og);

	  Set<Integer> aliasedParamIndices = 
	    ogCopy.calculateAliasedParamSet(fc, possibleMd.isStatic(), pflatm);
	  MethodContext mcNew = new MethodContext( possibleMd, aliasedParamIndices );
	  OwnershipGraph ogPotentialCallee = mapMethodContextToCompleteOwnershipGraph.get( mcNew );

	  if( ogPotentialCallee == null ) {
	    // if this method context has never been analyzed just schedule it for analysis
	    // and skip over this call site for now
	    methodContextsToVisit.add( mcNew );
	    
	  } else {
	    ogCopy.resolveMethodCall(fc, possibleMd.isStatic(), pflatm, ogPotentialCallee);
	  }

	  ogMergeOfAllPossibleCalleeResults.merge(ogCopy);
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

    return og;
  }


  // insert a call to debugSnapshot() somewhere in the analysis to get
  // successive captures of the analysis state
  int debugCounter        = 0;
  int numStartCountReport = 0;
  int freqCountReport     = 1000;
  int iterStartCapture    = 20000;
  int numIterToCapture    = 400;
  void debugSnapshot(OwnershipGraph og, FlatNode fn) {
    ++debugCounter;
    if( debugCounter > numStartCountReport &&
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
	og.writeGraph(graphName, true, true, false, false, false);
      } catch( Exception e ) {
	System.out.println("Error writing debug capture.");
	System.exit(0);
      }
    }
    if( debugCounter == iterStartCapture + numIterToCapture ) {
      System.out.println("Stopping analysis after debug captures.");
      System.exit(0);
    }
  }



  // this method should generate integers strictly greater than zero!
  // special "shadow" regions are made from a heap region by negating
  // the ID
  static public Integer generateUniqueHeapRegionNodeID() {
    ++uniqueIDcount;
    return new Integer(uniqueIDcount);
  }


  private void setGraphForMethodContext(MethodContext mc, OwnershipGraph og)
  throws IOException {

    mapMethodContextToCompleteOwnershipGraph.put(mc, og);

    // arguments to writeGraph are:
    // boolean writeLabels,
    // boolean labelSelect,
    // boolean pruneGarbage,
    // boolean writeReferencers
    // boolean writeParamMappings

    if( writeDOTs ) {

      if( !writeAllDOTs ) {
	og.writeGraph(mc, true, true, true, false, false);

      } else {
	if( !mapMethodContextToNumUpdates.containsKey(mc) ) {
	  mapMethodContextToNumUpdates.put(mc, new Integer(0) );
	}
	Integer n = mapMethodContextToNumUpdates.get(mc);
	og.writeGraph(mc, n, true, true, true, false, false);
	mapMethodContextToNumUpdates.put(mc, n + 1);
      }
    }
  }


  // return just the allocation site associated with one FlatNew node
  private AllocationSite getAllocationSiteFromFlatNewPRIVATE(FlatNew fn) {

    if( !mapFlatNewToAllocationSite.containsKey(fn) ) {
      AllocationSite as = new AllocationSite(allocationDepth, fn);

      // the newest nodes are single objects
      for( int i = 0; i < allocationDepth; ++i ) {
	Integer id = generateUniqueHeapRegionNodeID();
	as.setIthOldest(i, id);
      }

      // the oldest node is a summary node
      Integer idSummary = generateUniqueHeapRegionNodeID();
      as.setSummary(idSummary);

      mapFlatNewToAllocationSite.put(fn, as);
    }

    return mapFlatNewToAllocationSite.get(fn);
  }


  // return all allocation sites in the method (there is one allocation
  // site per FlatNew node in a method)
  private HashSet<AllocationSite> getAllocationSiteSet(Descriptor d) {
    if( !mapDescriptorToAllocationSiteSet.containsKey(d) ) {
      buildAllocationSiteSet(d);
    }

    return mapDescriptorToAllocationSiteSet.get(d);

  }

  private void buildAllocationSiteSet(Descriptor d) {
    HashSet<AllocationSite> s = new HashSet<AllocationSite>();

    FlatMethod fm;
    if( d instanceof MethodDescriptor ) {
      fm = state.getMethodFlat( (MethodDescriptor) d);
    } else {
      assert d instanceof TaskDescriptor;
      fm = state.getMethodFlat( (TaskDescriptor) d);
    }

    // visit every node in this FlatMethod's IR graph
    // and make a set of the allocation sites from the
    // FlatNew node's visited
    HashSet<FlatNode> visited = new HashSet<FlatNode>();
    HashSet<FlatNode> toVisit = new HashSet<FlatNode>();
    toVisit.add(fm);

    while( !toVisit.isEmpty() ) {
      FlatNode n = toVisit.iterator().next();

      if( n instanceof FlatNew ) {
	s.add(getAllocationSiteFromFlatNewPRIVATE( (FlatNew) n) );
      }

      toVisit.remove(n);
      visited.add(n);

      for( int i = 0; i < n.numNext(); ++i ) {
	FlatNode child = n.getNext(i);
	if( !visited.contains(child) ) {
	  toVisit.add(child);
	}
      }
    }

    mapDescriptorToAllocationSiteSet.put(d, s);
  }


  private HashSet<AllocationSite>
  getFlaggedAllocationSitesReachableFromTaskPRIVATE(TaskDescriptor td) {

    HashSet<AllocationSite> asSetTotal = new HashSet<AllocationSite>();
    HashSet<Descriptor>     toVisit    = new HashSet<Descriptor>();
    HashSet<Descriptor>     visited    = new HashSet<Descriptor>();

    toVisit.add(td);

    // traverse this task and all methods reachable from this task
    while( !toVisit.isEmpty() ) {
      Descriptor d = toVisit.iterator().next();
      toVisit.remove(d);
      visited.add(d);

      HashSet<AllocationSite> asSet = getAllocationSiteSet(d);
      Iterator asItr = asSet.iterator();
      while( asItr.hasNext() ) {
	AllocationSite as = (AllocationSite) asItr.next();
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
}
