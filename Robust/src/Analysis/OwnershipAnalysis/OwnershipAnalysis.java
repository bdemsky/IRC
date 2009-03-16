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


  public Set<HeapRegionNode> createsPotentialAliases(Descriptor taskOrMethod,
                                         int paramIndex1,
                                         int paramIndex2) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(paramIndex1, paramIndex2);
  }

  public Set<HeapRegionNode> createsPotentialAliases(Descriptor taskOrMethod,
                                         int paramIndex,
                                         AllocationSite alloc) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(paramIndex, alloc);
  }

  public Set<HeapRegionNode> createsPotentialAliases(Descriptor taskOrMethod,
                                         AllocationSite alloc,
                                         int paramIndex) {

    OwnershipGraph og = getGraphOfAllContextsFromDescriptor(taskOrMethod);
    assert(og != null);
    return og.hasPotentialAlias(paramIndex, alloc);
  }

  public Set<HeapRegionNode> createsPotentialAliases(Descriptor taskOrMethod,
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


  public String prettyPrintNodeSet( Set<HeapRegionNode> s ) {
    String out = "{\n";

    Iterator<HeapRegionNode> i = s.iterator();
    while( i.hasNext() ) {
      HeapRegionNode n = i.next();

      AllocationSite as = n.getAllocationSite();
      if( as == null ) {
	out += "  "+n.toString()+",\n";
      } else {
	out += "  "+n.toString()+": "+as.toStringVerbose()+",\n";
      }
    }

    out += "}\n";
    return out;
  }


  // use the methods given above to check every possible alias
  // between task parameters and flagged allocation sites reachable
  // from the task
  public void writeAllAliases(String outputFile, String timeReport) throws java.io.IOException {

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile) );

    bw.write("Conducting ownership analysis with allocation depth = "+allocationDepth+"\n");
    bw.write(timeReport+"\n");

    // look through every task for potential aliases
    Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
    while( taskItr.hasNext() ) {
      TaskDescriptor td = (TaskDescriptor) taskItr.next();

      bw.write("\n---------"+td+"--------\n");

      HashSet<AllocationSite> allocSites = getFlaggedAllocationSitesReachableFromTask(td);

      Set<HeapRegionNode> common;

      // for each task parameter, check for aliases with
      // other task parameters and every allocation site
      // reachable from this task
      boolean foundSomeAlias = false;

      FlatMethod fm = state.getMethodFlat(td);
      for( int i = 0; i < fm.numParameters(); ++i ) {

	// for the ith parameter check for aliases to all
	// higher numbered parameters
	for( int j = i + 1; j < fm.numParameters(); ++j ) {
	  common = createsPotentialAliases(td, i, j);
	  if( !common.isEmpty() ) {
	    foundSomeAlias = true;
	    bw.write("Potential alias between parameters "+i+" and "+j+".\n");
	    bw.write(prettyPrintNodeSet( common )+"\n" );
	  }
	}

	// for the ith parameter, check for aliases against
	// the set of allocation sites reachable from this
	// task context
	Iterator allocItr = allocSites.iterator();
	while( allocItr.hasNext() ) {
	  AllocationSite as = (AllocationSite) allocItr.next();
	  common = createsPotentialAliases(td, i, as);
	  if( !common.isEmpty() ) {
	    foundSomeAlias = true;
	    bw.write("Potential alias between parameter "+i+" and "+as.getFlatNew()+".\n");
	    bw.write(prettyPrintNodeSet( common )+"\n" );
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
	  
	  if( !outerChecked.contains(as2) ) {
	    common = createsPotentialAliases(td, as1, as2);

	    if( !common.isEmpty() ) {
	      foundSomeAlias = true;
	      bw.write("Potential alias between "+as1.getFlatNew()+" and "+as2.getFlatNew()+".\n");
	      bw.write(prettyPrintNodeSet( common )+"\n" );
	    }
	  }
	}

	outerChecked.add(as1);
      }

      if( !foundSomeAlias ) {
	bw.write("No aliases between flagged objects in Task "+td+".\n");
      }
    }

    bw.write( "\n"+computeAliasContextHistogram() );
    bw.close();
  }


  // this version of writeAllAliases is for Java programs that have no tasks
  public void writeAllAliasesJava(String outputFile, String timeReport) throws java.io.IOException {
    assert !state.TASK;    

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile) );

    bw.write("Conducting ownership analysis with allocation depth = "+allocationDepth+"\n");
    bw.write(timeReport+"\n\n");

    boolean foundSomeAlias = false;

    Descriptor d = typeUtil.getMain();
    HashSet<AllocationSite> allocSites = getFlaggedAllocationSites(d);

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
	
	if( !outerChecked.contains(as2) ) {	  
	  Set<HeapRegionNode> common = createsPotentialAliases(d, as1, as2);

	  if( !common.isEmpty() ) {
	    foundSomeAlias = true;
	    bw.write("Potential alias between "+as1.getDisjointId()+" and "+as2.getDisjointId()+".\n");
	    bw.write( prettyPrintNodeSet( common )+"\n" );
	  }
	}
      }
      
      outerChecked.add(as1);
    }
    
    if( !foundSomeAlias ) {
      bw.write("No aliases between flagged objects found.\n");
    }

    bw.write( "\n"+computeAliasContextHistogram() );
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
  // start at 10 and increment to leave some
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
  private Hashtable<MethodContext, HashSet<MethodContext> >  mapMethodContextToDependentContexts;

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
  // should be re-added to this queue
  private PriorityQueue<MethodContextQWrapper> methodContextsToVisitQ;
  private Set          <MethodContext>         methodContextsToVisitSet;
  private Hashtable<Descriptor, Integer> mapDescriptorToPriority;


  // special field descriptors for array elements
  private Hashtable<TypeDescriptor, FieldDescriptor> mapTypeToArrayField;
  public static final String arrayElementFieldName = "___element_";

  // special field descriptors for variables with type, no field name
  private Hashtable<TypeDescriptor, FieldDescriptor> mapTypeToVarField;


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

    mapTypeToArrayField =
      new Hashtable<TypeDescriptor, FieldDescriptor>();

    mapTypeToVarField =
      new Hashtable<TypeDescriptor, FieldDescriptor>();

    mapMethodContextToDependentContexts =
      new Hashtable<MethodContext, HashSet<MethodContext> >();

    mapDescriptorToPriority = 
      new Hashtable<Descriptor, Integer>();


    if( writeAllDOTs ) {
      mapMethodContextToNumUpdates = new Hashtable<MethodContext, Integer>();
    }


    if( state.TASK ) {
      // initialize methods to visit as the set of all tasks in the
      // program and then any method that could be called starting
      // from those tasks
      Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
      while( taskItr.hasNext() ) {
	Descriptor d = (Descriptor) taskItr.next();
	scheduleAllCallees(d);
      }

    } else {
      // we are not in task mode, just normal Java, so start with
      // the main method
      Descriptor d = typeUtil.getMain();
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

      og = analyzeFlatNode(mc, fm, null, og);
      setGraphForMethodContext(mc, og);
    }

    // as mentioned above, analyze methods one-by-one, possibly revisiting
    // a method if the methods that it calls are updated
    analyzeMethods();

    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
    String treport = String.format( "The analysis took %.3f sec.", dt );
    System.out.println( treport );

    if( writeDOTs && !writeAllDOTs ) {
      writeFinalContextGraphs();      
    }  

    if( aliasFile != null ) {
      if( state.TASK ) {
	writeAllAliases(aliasFile, treport);
      } else {
	writeAllAliasesJava(aliasFile, treport);
      }
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

    // first gather all of the method contexts to analyze
    HashSet<MethodContext> allContexts = new HashSet<MethodContext>();
    Iterator<Descriptor> itrd2a = descriptorsToAnalyze.iterator();
    while( itrd2a.hasNext() ) {
      HashSet<MethodContext> mcs = mapDescriptorToAllMethodContexts.get( itrd2a.next() );
      assert mcs != null;

      Iterator<MethodContext> itrmc = mcs.iterator();
      while( itrmc.hasNext() ) {
	allContexts.add( itrmc.next() );
      }
    }

    // topologically sort them according to the caller graph so leaf calls are
    // ordered first; use that ordering to give method contexts priorities
    LinkedList<MethodContext> sortedMethodContexts = topologicalSort( allContexts );   

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

      OwnershipGraph og = analyzeFlatMethod(mc, fm);
      OwnershipGraph ogPrev = mapMethodContextToCompleteOwnershipGraph.get(mc);
      if( !og.equals(ogPrev) ) {
	setGraphForMethodContext(mc, og);

	Iterator<MethodContext> depsItr = iteratorDependents( mc );
	while( depsItr.hasNext() ) {
	  MethodContext mcNext = depsItr.next();

	  if( !methodContextsToVisitSet.contains( mcNext ) ) {
	    //System.out.println( "  queuing "+mcNext );
	    methodContextsToVisitQ.add( new MethodContextQWrapper( mapDescriptorToPriority.get( mcNext.getDescriptor() ), 
								   mcNext ) );
	    methodContextsToVisitSet.add( mcNext );
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

      

     
      if( takeDebugSnapshots && 
	  mc.getDescriptor().getSymbol().equals( mcDescSymbolDebug ) ) {
	debugSnapshot(og,fn);
      }
     


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

    case FKind.FlatCastNode:
      FlatCastNode fcn = (FlatCastNode) fn;
      lhs = fcn.getDst();
      rhs = fcn.getSrc();

      TypeDescriptor td = fcn.getType();
      assert td != null;

      FieldDescriptor fd = mapTypeToVarField.get( td );
      if( fd == null ) {
	fd = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC),
				 td,
				 "",
				 null,
				 false);
	mapTypeToVarField.put( td, fd );
      }
      
      og.assignTypedTempXEqualToTempY(lhs, rhs, fd);
      break;

    case FKind.FlatFieldNode:
      FlatFieldNode ffn = (FlatFieldNode) fn;
      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();
      if( !fld.getType().isImmutable() || fld.getType().isArray() ) {
	og.assignTempXEqualToTempYFieldF(lhs, rhs, fld);
      }
      break;

    case FKind.FlatSetFieldNode:
      FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
      lhs = fsfn.getDst();
      fld = fsfn.getField();
      rhs = fsfn.getSrc();
      if( !fld.getType().isImmutable() || fld.getType().isArray() ) {
	og.assignTempXFieldFEqualToTempY(lhs, fld, rhs);
      }
      break;

    case FKind.FlatElementNode:
      FlatElementNode fen = (FlatElementNode) fn;
      lhs = fen.getDst();
      rhs = fen.getSrc();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {

	assert rhs.getType() != null;
	assert rhs.getType().isArray();
	
	TypeDescriptor  tdElement = rhs.getType().dereference();
	FieldDescriptor fdElement = mapTypeToArrayField.get( tdElement );
	if( fdElement == null ) {
	  fdElement = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC),
					  tdElement,
					  arrayElementFieldName,
					  null,
					  false);
	  mapTypeToArrayField.put( tdElement, fdElement );
	}
  
	og.assignTempXEqualToTempYFieldF(lhs, rhs, fdElement);
      }
      break;

    case FKind.FlatSetElementNode:
      FlatSetElementNode fsen = (FlatSetElementNode) fn;
      lhs = fsen.getDst();
      rhs = fsen.getSrc();
      if( !rhs.getType().isImmutable() || rhs.getType().isArray() ) {

	assert lhs.getType() != null;
	assert lhs.getType().isArray();
	
	TypeDescriptor  tdElement = lhs.getType().dereference();
	FieldDescriptor fdElement = mapTypeToArrayField.get( tdElement );
	if( fdElement == null ) {
	  fdElement = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC),
					  tdElement,
					  arrayElementFieldName,
					  null,
					  false);
	  mapTypeToArrayField.put( tdElement, fdElement );
	}

	og.assignTempXFieldFEqualToTempY(lhs, fdElement, rhs);
      }
      break;

    case FKind.FlatNew:
      FlatNew fnn = (FlatNew) fn;
      lhs = fnn.getDst();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {
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
	Set contexts = mapDescriptorToAllMethodContexts.get( md );
	assert contexts != null;
	contexts.add( mcNew );

	addDependent( mc, mcNew );

	OwnershipGraph onlyPossibleCallee = mapMethodContextToCompleteOwnershipGraph.get( mcNew );

	if( onlyPossibleCallee == null ) {
	  // if this method context has never been analyzed just schedule it for analysis
	  // and skip over this call site for now
	  if( !methodContextsToVisitSet.contains( mcNew ) ) {
	    methodContextsToVisitQ.add( new MethodContextQWrapper( mapDescriptorToPriority.get( md ), 
								   mcNew ) );
	    methodContextsToVisitSet.add( mcNew );
	  }
	  
	} else {
	  ogMergeOfAllPossibleCalleeResults.resolveMethodCall(fc, md.isStatic(), flatm, onlyPossibleCallee, mc);
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
	  Set contexts = mapDescriptorToAllMethodContexts.get( md );
	  assert contexts != null;
	  contexts.add( mcNew );

	  addDependent( mc, mcNew );

	  OwnershipGraph ogPotentialCallee = mapMethodContextToCompleteOwnershipGraph.get( mcNew );

	  if( ogPotentialCallee == null ) {
	    // if this method context has never been analyzed just schedule it for analysis
	    // and skip over this call site for now
	    if( !methodContextsToVisitSet.contains( mcNew ) ) {
	      methodContextsToVisitQ.add( new MethodContextQWrapper( mapDescriptorToPriority.get( md ), 
								     mcNew ) );
	      methodContextsToVisitSet.add( mcNew );
	    }
	    
	  } else {
	    ogCopy.resolveMethodCall(fc, possibleMd.isStatic(), pflatm, ogPotentialCallee, mc);
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


  // this method should generate integers strictly greater than zero!
  // special "shadow" regions are made from a heap region by negating
  // the ID
  static public Integer generateUniqueHeapRegionNodeID() {
    ++uniqueIDcount;
    return new Integer(uniqueIDcount);
  }


  private void setGraphForMethodContext(MethodContext mc, OwnershipGraph og) {

    mapMethodContextToCompleteOwnershipGraph.put(mc, og);

    if( writeDOTs && writeAllDOTs ) {
      if( !mapMethodContextToNumUpdates.containsKey(mc) ) {
	mapMethodContextToNumUpdates.put(mc, new Integer(0) );
      }
      Integer n = mapMethodContextToNumUpdates.get(mc);
      try {
	og.writeGraph(mc, n, true, true, true, false, false);
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
    // arguments to writeGraph are:
    // boolean writeLabels,
    // boolean labelSelect,
    // boolean pruneGarbage,
    // boolean writeReferencers
    // boolean writeParamMappings

    Set entrySet = mapMethodContextToCompleteOwnershipGraph.entrySet();
    Iterator itr = entrySet.iterator();
    while( itr.hasNext() ) {
      Map.Entry      me = (Map.Entry)      itr.next();
      MethodContext  mc = (MethodContext)  me.getKey();
      OwnershipGraph og = (OwnershipGraph) me.getValue();

      try {
	og.writeGraph(mc, true, true, true, false, false);
      } catch( IOException e ) {}    
    }
  }
  

  // return just the allocation site associated with one FlatNew node
  private AllocationSite getAllocationSiteFromFlatNewPRIVATE(FlatNew fn) {

    if( !mapFlatNewToAllocationSite.containsKey(fn) ) {
      AllocationSite as = new AllocationSite(allocationDepth, fn, fn.getDisjointId());

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


  private HashSet<AllocationSite> getFlaggedAllocationSites(Descriptor dIn) {
    
    HashSet<AllocationSite> out     = new HashSet<AllocationSite>();
    HashSet<Descriptor>     toVisit = new HashSet<Descriptor>();
    HashSet<Descriptor>     visited = new HashSet<Descriptor>();

    toVisit.add(dIn);

    while( !toVisit.isEmpty() ) {
      Descriptor d = toVisit.iterator().next();
      toVisit.remove(d);
      visited.add(d);

      HashSet<AllocationSite> asSet = getAllocationSiteSet(d);
      Iterator asItr = asSet.iterator();
      while( asItr.hasNext() ) {
	AllocationSite as = (AllocationSite) asItr.next();
	if( as.getDisjointId() != null ) {
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


  private LinkedList<MethodContext> topologicalSort( HashSet<MethodContext> set ) {
    HashSet   <MethodContext> discovered = new HashSet   <MethodContext>();
    LinkedList<MethodContext> sorted     = new LinkedList<MethodContext>();
  
    Iterator<MethodContext> itr = set.iterator();
    while( itr.hasNext() ) {
      MethodContext mc = itr.next();
          
      if( !discovered.contains( mc ) ) {
	dfsVisit( set, mc, sorted, discovered );
      }
    }
    
    return sorted;
  }
  
  private void dfsVisit( HashSet<MethodContext> set,
			 MethodContext mc,
			 LinkedList<MethodContext> sorted,
			 HashSet   <MethodContext> discovered ) {
    discovered.add( mc );
    
    Descriptor d = mc.getDescriptor();
    if( d instanceof MethodDescriptor ) {
      MethodDescriptor md = (MethodDescriptor) d;      
      Iterator itr = callGraph.getCallerSet( md ).iterator();
      while( itr.hasNext() ) {
	Descriptor dCaller = (Descriptor) itr.next();
	
	// only consider the callers in the original set to analyze
	Set<MethodContext> callerContexts = mapDescriptorToAllMethodContexts.get( dCaller );
	if( callerContexts == null )
	  continue;	
	
	// since the analysis hasn't started, there should be exactly one
	// context if there are any at all
	assert callerContexts.size() == 1;	
	MethodContext mcCaller = callerContexts.iterator().next();
	assert set.contains( mcCaller );

	if( !discovered.contains( mcCaller ) ) {
	  dfsVisit( set, mcCaller, sorted, discovered );
	}
      }
    }

    sorted.addFirst( mc );
  }



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



  // insert a call to debugSnapshot() somewhere in the analysis 
  // to get successive captures of the analysis state
  boolean takeDebugSnapshots = false;
  String mcDescSymbolDebug = "main";
  boolean stopAfterCapture = true;

  // increments every visit to debugSnapshot, don't fiddle with it
  int debugCounter = 0;

  // the value of debugCounter to start reporting the debugCounter
  // to the screen to let user know what debug iteration we're at
  int numStartCountReport = 100;

  // the frequency of debugCounter values to print out, 0 no report
  int freqCountReport = 0;

  // the debugCounter value at which to start taking snapshots
  int iterStartCapture = 134;

  // the number of snapshots to take
  int numIterToCapture = 50;

  void debugSnapshot(OwnershipGraph og, FlatNode fn) {
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
	og.writeGraph(graphName, true, true, true, false, false);
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
}
