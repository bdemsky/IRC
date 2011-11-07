package Analysis.Disjoint;

import Analysis.CallGraph.*;
import Analysis.Liveness;
import Analysis.ArrayReferencees;
import Analysis.OoOJava.Accessible;
import Analysis.OoOJava.RBlockRelationAnalysis;
import Analysis.FlatIRGraph.*;
import IR.*;
import IR.Flat.*;
import IR.Tree.Modifiers;
import java.util.*;
import java.io.*;


public class DisjointAnalysis implements HeapAnalysis {

  ///////////////////////////////////////////
  //
  //  Public interface to discover possible
  //  sharing in the program under analysis
  //
  ///////////////////////////////////////////

  // if an object allocated at the target site may be
  // reachable from both an object from root1 and an
  // object allocated at root2, return TRUE
  public boolean mayBothReachTarget(FlatMethod fm,
                                    FlatNew fnRoot1,
                                    FlatNew fnRoot2,
                                    FlatNew fnTarget) {

    AllocSite asr1 = getAllocationSiteFromFlatNew(fnRoot1);
    AllocSite asr2 = getAllocationSiteFromFlatNew(fnRoot2);
    assert asr1.isFlagged();
    assert asr2.isFlagged();

    AllocSite ast = getAllocationSiteFromFlatNew(fnTarget);
    ReachGraph rg = getPartial(fm.getMethod() );

    return rg.mayBothReachTarget(asr1, asr2, ast);
  }

  // similar to the method above, return TRUE if ever
  // more than one object from the root allocation site
  // may reach an object from the target site
  public boolean mayManyReachTarget(FlatMethod fm,
                                    FlatNew fnRoot,
                                    FlatNew fnTarget) {

    AllocSite asr = getAllocationSiteFromFlatNew(fnRoot);
    assert asr.isFlagged();

    AllocSite ast = getAllocationSiteFromFlatNew(fnTarget);
    ReachGraph rg = getPartial(fm.getMethod() );

    return rg.mayManyReachTarget(asr, ast);
  }




  public HashSet<AllocSite>
  getFlaggedAllocationSitesReachableFromTask(TaskDescriptor td) {
    checkAnalysisComplete();
    return getFlaggedAllocationSitesReachableFromTaskPRIVATE(td);
  }

  public AllocSite getAllocationSiteFromFlatNew(FlatNew fn) {
    checkAnalysisComplete();
    return getAllocSiteFromFlatNewPRIVATE(fn);
  }

  public AllocSite getAllocationSiteFromHeapRegionNodeID(Integer id) {
    checkAnalysisComplete();
    return mapHrnIdToAllocSite.get(id);
  }

  public Set<HeapRegionNode> hasPotentialSharing(Descriptor taskOrMethod,
                                                 int paramIndex1,
                                                 int paramIndex2) {
    checkAnalysisComplete();
    ReachGraph rg=mapDescriptorToCompleteReachGraph.get(taskOrMethod);
    FlatMethod fm=state.getMethodFlat(taskOrMethod);
    assert(rg != null);
    return rg.mayReachSharedObjects(fm, paramIndex1, paramIndex2);
  }

  public Set<HeapRegionNode> hasPotentialSharing(Descriptor taskOrMethod,
                                                 int paramIndex, AllocSite alloc) {
    checkAnalysisComplete();
    ReachGraph rg = mapDescriptorToCompleteReachGraph.get(taskOrMethod);
    FlatMethod fm=state.getMethodFlat(taskOrMethod);
    assert(rg != null);
    return rg.mayReachSharedObjects(fm, paramIndex, alloc);
  }

  public Set<HeapRegionNode> hasPotentialSharing(Descriptor taskOrMethod,
                                                 AllocSite alloc, int paramIndex) {
    checkAnalysisComplete();
    ReachGraph rg  = mapDescriptorToCompleteReachGraph.get(taskOrMethod);
    FlatMethod fm=state.getMethodFlat(taskOrMethod);
    assert(rg != null);
    return rg.mayReachSharedObjects(fm, paramIndex, alloc);
  }

  public Set<HeapRegionNode> hasPotentialSharing(Descriptor taskOrMethod,
                                                 AllocSite alloc1, AllocSite alloc2) {
    checkAnalysisComplete();
    ReachGraph rg  = mapDescriptorToCompleteReachGraph.get(taskOrMethod);
    assert(rg != null);
    return rg.mayReachSharedObjects(alloc1, alloc2);
  }

  public String prettyPrintNodeSet(Set<HeapRegionNode> s) {
    checkAnalysisComplete();

    String out = "{\n";

    Iterator<HeapRegionNode> i = s.iterator();
    while (i.hasNext()) {
      HeapRegionNode n = i.next();

      AllocSite as = n.getAllocSite();
      if (as == null) {
        out += "  " + n.toString() + ",\n";
      } else {
        out += "  " + n.toString() + ": " + as.toStringVerbose()
               + ",\n";
      }
    }

    out += "}\n";
    return out;
  }

  // use the methods given above to check every possible sharing class
  // between task parameters and flagged allocation sites reachable
  // from the task
  public void writeAllSharing(String outputFile,
                              String timeReport,
                              String justTime,
                              boolean tabularOutput,
                              int numLines
                              )
  throws java.io.IOException {
    checkAnalysisComplete();

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));

    if (!tabularOutput) {
      bw.write("Conducting ownership analysis with allocation depth = "
               + allocationDepth + "\n");
      bw.write(timeReport + "\n");
    }

    int numSharing = 0;

    // look through every task for potential sharing
    Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
    while (taskItr.hasNext()) {
      TaskDescriptor td = (TaskDescriptor) taskItr.next();

      if (!tabularOutput) {
        bw.write("\n---------" + td + "--------\n");
      }

      HashSet<AllocSite> allocSites = getFlaggedAllocationSitesReachableFromTask(td);

      Set<HeapRegionNode> common;

      // for each task parameter, check for sharing classes with
      // other task parameters and every allocation site
      // reachable from this task
      boolean foundSomeSharing = false;

      FlatMethod fm = state.getMethodFlat(td);
      for (int i = 0; i < fm.numParameters(); ++i) {

        // skip parameters with types that cannot reference
        // into the heap
        if( !shouldAnalysisTrack(fm.getParameter(i).getType() ) ) {
          continue;
        }

        // for the ith parameter check for sharing classes to all
        // higher numbered parameters
        for (int j = i + 1; j < fm.numParameters(); ++j) {

          // skip parameters with types that cannot reference
          // into the heap
          if( !shouldAnalysisTrack(fm.getParameter(j).getType() ) ) {
            continue;
          }


          common = hasPotentialSharing(td, i, j);
          if (!common.isEmpty()) {
            foundSomeSharing = true;
            ++numSharing;
            if (!tabularOutput) {
              bw.write("Potential sharing between parameters " + i
                       + " and " + j + ".\n");
              bw.write(prettyPrintNodeSet(common) + "\n");
            }
          }
        }

        // for the ith parameter, check for sharing classes against
        // the set of allocation sites reachable from this
        // task context
        Iterator allocItr = allocSites.iterator();
        while (allocItr.hasNext()) {
          AllocSite as = (AllocSite) allocItr.next();
          common = hasPotentialSharing(td, i, as);
          if (!common.isEmpty()) {
            foundSomeSharing = true;
            ++numSharing;
            if (!tabularOutput) {
              bw.write("Potential sharing between parameter " + i
                       + " and " + as.getFlatNew() + ".\n");
              bw.write(prettyPrintNodeSet(common) + "\n");
            }
          }
        }
      }

      // for each allocation site check for sharing classes with
      // other allocation sites in the context of execution
      // of this task
      HashSet<AllocSite> outerChecked = new HashSet<AllocSite>();
      Iterator allocItr1 = allocSites.iterator();
      while (allocItr1.hasNext()) {
        AllocSite as1 = (AllocSite) allocItr1.next();

        Iterator allocItr2 = allocSites.iterator();
        while (allocItr2.hasNext()) {
          AllocSite as2 = (AllocSite) allocItr2.next();

          if (!outerChecked.contains(as2)) {
            common = hasPotentialSharing(td, as1, as2);

            if (!common.isEmpty()) {
              foundSomeSharing = true;
              ++numSharing;
              if (!tabularOutput) {
                bw.write("Potential sharing between "
                         + as1.getFlatNew() + " and "
                         + as2.getFlatNew() + ".\n");
                bw.write(prettyPrintNodeSet(common) + "\n");
              }
            }
          }
        }

        outerChecked.add(as1);
      }

      if (!foundSomeSharing) {
        if (!tabularOutput) {
          bw.write("No sharing between flagged objects in Task " + td
                   + ".\n");
        }
      }
    }


    if (tabularOutput) {
      bw.write(" & " + numSharing + " & " + justTime + " & " + numLines
               + " & " + numMethodsAnalyzed() + " \\\\\n");
    } else {
      bw.write("\nNumber sharing classes: "+numSharing);
    }

    bw.close();
  }



  // this version of writeAllSharing is for Java programs that have no tasks
  // ***********************************
  // WARNING: THIS DOES NOT DO THE RIGHT THING, REPORTS 0 ALWAYS!
  // It should use mayBothReachTarget and mayManyReachTarget like
  // OoOJava does to query analysis results
  // ***********************************
  public void writeAllSharingJava(String outputFile,
                                  String timeReport,
                                  String justTime,
                                  boolean tabularOutput,
                                  int numLines
                                  )
  throws java.io.IOException {
    checkAnalysisComplete();

    assert !state.TASK;

    int numSharing = 0;

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));

    bw.write("Conducting disjoint reachability analysis with allocation depth = "
             + allocationDepth + "\n");
    bw.write(timeReport + "\n\n");

    boolean foundSomeSharing = false;

    Descriptor d = typeUtil.getMain();
    HashSet<AllocSite> allocSites = getFlaggedAllocationSites(d);

    // for each allocation site check for sharing classes with
    // other allocation sites in the context of execution
    // of this task
    HashSet<AllocSite> outerChecked = new HashSet<AllocSite>();
    Iterator allocItr1 = allocSites.iterator();
    while (allocItr1.hasNext()) {
      AllocSite as1 = (AllocSite) allocItr1.next();

      Iterator allocItr2 = allocSites.iterator();
      while (allocItr2.hasNext()) {
        AllocSite as2 = (AllocSite) allocItr2.next();

        if (!outerChecked.contains(as2)) {
          Set<HeapRegionNode> common = hasPotentialSharing(d,
                                                           as1, as2);

          if (!common.isEmpty()) {
            foundSomeSharing = true;
            bw.write("Potential sharing between "
                     + as1.getDisjointAnalysisId() + " and "
                     + as2.getDisjointAnalysisId() + ".\n");
            bw.write(prettyPrintNodeSet(common) + "\n");
            ++numSharing;
          }
        }
      }

      outerChecked.add(as1);
    }

    if (!foundSomeSharing) {
      bw.write("No sharing classes between flagged objects found.\n");
    } else {
      bw.write("\nNumber sharing classes: "+numSharing);
    }

    bw.write("Number of methods analyzed: "+numMethodsAnalyzed()+"\n");

    bw.close();
  }



  public Alloc getCmdLineArgsAlloc() {
    return getAllocationSiteFromFlatNew( constructedCmdLineArgsNew );
  }
  public Alloc getCmdLineArgAlloc() {
    return getAllocationSiteFromFlatNew( constructedCmdLineArgNew );
  }
  public Alloc getCmdLineArgBytesAlloc() {
    return getAllocationSiteFromFlatNew( constructedCmdLineArgBytesNew );
  }
  public Alloc getNewStringLiteralAlloc() {
    return newStringLiteralAlloc;
  }
  public Alloc getNewStringLiteralBytesAlloc() {
    return newStringLiteralBytesAlloc;
  }

  ///////////////////////////////////////////
  //
  // end public interface
  //
  ///////////////////////////////////////////



  protected void checkAnalysisComplete() {
    if( !analysisComplete ) {
      throw new Error("Warning: public interface method called while analysis is running.");
    }
  }






  // run in faster mode, only when bugs wrung out!
  public static boolean releaseMode;

  // use command line option to set this, analysis
  // should attempt to be deterministic
  public static boolean determinismDesired;

  // when we want to enforce determinism in the
  // analysis we need to sort descriptors rather
  // than toss them in efficient sets, use this
  public static DescriptorComparator dComp =
    new DescriptorComparator();


  // data from the compiler
  public State state;
  public CallGraph callGraph;
  public Liveness liveness;
  public ArrayReferencees arrayReferencees;
  public RBlockRelationAnalysis rblockRel;
  public TypeUtil typeUtil;
  public int allocationDepth;

  protected boolean doEffectsAnalysis = false;
  protected EffectsAnalysis effectsAnalysis;
  protected BuildStateMachines buildStateMachines;

  protected boolean doDefiniteReachAnalysis = false;
  protected DefiniteReachAnalysis definiteReachAnalysis;


  // data structure for public interface
  private Hashtable< Descriptor, HashSet<AllocSite> >
  mapDescriptorToAllocSiteSet;


  // for public interface methods to warn that they
  // are grabbing results during analysis
  private boolean analysisComplete;


  // used to identify HeapRegionNode objects
  // A unique ID equates an object in one
  // ownership graph with an object in another
  // graph that logically represents the same
  // heap region
  // start at 10 and increment to reserve some
  // IDs for special purposes
  static protected int uniqueIDcount = 10;


  // An out-of-scope method created by the
  // analysis that has no parameters, and
  // appears to allocate the command line
  // arguments, then invoke the source code's
  // main method.  The purpose of this is to
  // provide the analysis with an explicit
  // top-level context with no parameters
  protected MethodDescriptor mdAnalysisEntry;
  protected FlatMethod fmAnalysisEntry;

  // main method defined by source program
  protected MethodDescriptor mdSourceEntry;

  // the set of task and/or method descriptors
  // reachable in call graph
  protected Set<Descriptor>
  descriptorsToAnalyze;

  // current descriptors to visit in fixed-point
  // interprocedural analysis, prioritized by
  // dependency in the call graph
  protected Stack<Descriptor>
  descriptorsToVisitStack;
  protected PriorityQueue<DescriptorQWrapper>
  descriptorsToVisitQ;

  // a duplication of the above structure, but
  // for efficient testing of inclusion
  protected HashSet<Descriptor>
  descriptorsToVisitSet;

  // storage for priorities (doesn't make sense)
  // to add it to the Descriptor class, just in
  // this analysis
  protected Hashtable<Descriptor, Integer>
  mapDescriptorToPriority;

  // when analyzing a method and scheduling more:
  // remember set of callee's enqueued for analysis
  // so they can be put on top of the callers in
  // the stack-visit mode
  protected Set<Descriptor>
  calleesToEnqueue;

  // maps a descriptor to its current partial result
  // from the intraprocedural fixed-point analysis--
  // then the interprocedural analysis settles, this
  // mapping will have the final results for each
  // method descriptor
  protected Hashtable<Descriptor, ReachGraph>
  mapDescriptorToCompleteReachGraph;

  // maps a descriptor to its known dependents: namely
  // methods or tasks that call the descriptor's method
  // AND are part of this analysis (reachable from main)
  protected Hashtable< Descriptor, Set<Descriptor> >
  mapDescriptorToSetDependents;

  // if the analysis client wants to flag allocation sites
  // programmatically, it should provide a set of FlatNew
  // statements--this may be null if unneeded
  protected Set<FlatNew> sitesToFlag;

  // maps each flat new to one analysis abstraction
  // allocate site object, these exist outside reach graphs
  protected Hashtable<FlatNew, AllocSite>
  mapFlatNewToAllocSite;

  // maps intergraph heap region IDs to intergraph
  // allocation sites that created them, a redundant
  // structure for efficiency in some operations
  protected Hashtable<Integer, AllocSite>
  mapHrnIdToAllocSite;

  // maps a method to its initial heap model (IHM) that
  // is the set of reachability graphs from every caller
  // site, all merged together.  The reason that we keep
  // them separate is that any one call site's contribution
  // to the IHM may changed along the path to the fixed point
  protected Hashtable< Descriptor, Hashtable< FlatCall, ReachGraph > >
  mapDescriptorToIHMcontributions;

  // additionally, keep a mapping from descriptors to the
  // merged in-coming initial context, because we want this
  // initial context to be STRICTLY MONOTONIC
  protected Hashtable<Descriptor, ReachGraph>
  mapDescriptorToInitialContext;

  // mapping of current partial results for a given node.  Note that
  // to reanalyze a method we discard all partial results because a
  // null reach graph indicates the node needs to be visited on the
  // way to the fixed point.
  // The reason for a persistent mapping is so after the analysis we
  // can ask for the graph of any node at the fixed point, but this
  // option is only enabled with a compiler flag.
  protected Hashtable<FlatNode, ReachGraph> mapFlatNodeToReachGraphPersist;
  protected Hashtable<FlatNode, ReachGraph> mapFlatNodeToReachGraph;


  // make the result for back edges analysis-wide STRICTLY
  // MONOTONIC as well, but notice we use FlatNode as the
  // key for this map: in case we want to consider other
  // nodes as back edge's in future implementations
  protected Hashtable<FlatNode, ReachGraph>
  mapBackEdgeToMonotone;


  public static final String arrayElementFieldName = "___element_";
  static protected Hashtable<TypeDescriptor, FieldDescriptor>
  mapTypeToArrayField;


  protected boolean suppressOutput;

  // for controlling DOT file output
  protected boolean writeFinalDOTs;
  protected boolean writeAllIncrementalDOTs;

  // supporting DOT output--when we want to write every
  // partial method result, keep a tally for generating
  // unique filenames
  protected Hashtable<Descriptor, Integer>
  mapDescriptorToNumUpdates;

  //map task descriptor to initial task parameter
  protected Hashtable<Descriptor, ReachGraph>
  mapDescriptorToReachGraph;

  protected PointerMethod pm;

  //Keeps track of all the reach graphs at every program point
  //DO NOT USE UNLESS YOU REALLY NEED IT
  static protected Hashtable<FlatNode, ReachGraph> fn2rgAtEnter =
    new Hashtable<FlatNode, ReachGraph>();

  static protected Hashtable<FlatNode, ReachGraph> fn2rgAtExit =
    new Hashtable<FlatNode, ReachGraph>();


  private Hashtable<FlatCall, Descriptor> fc2enclosing;

  Accessible accessible;

  
  // we construct an entry method of flat nodes complete
  // with a new allocation site to model the command line
  // args creation just for the analysis, so remember that
  // allocation site.  Later in code gen we might want to
  // know if something is pointing-to to the cmd line args
  // and we can verify by checking the allocation site field.
  protected FlatNew constructedCmdLineArgsNew;
  protected FlatNew constructedCmdLineArgNew;
  protected FlatNew constructedCmdLineArgBytesNew;

  // similar to above, the runtime allocates new strings
  // for literal nodes, so make up an alloc to model that
  protected AllocSite      newStringLiteralAlloc;
  protected AllocSite      newStringLiteralBytesAlloc;

  // both of the above need the descriptor of the field
  // for the String's value field to reference by the
  // byte array from the string object
  protected TypeDescriptor  stringType;
  protected TypeDescriptor  stringBytesType;
  protected FieldDescriptor stringBytesField;


  protected void initImplicitStringsModel() {
    
    ClassDescriptor cdString = typeUtil.getClass( typeUtil.StringClass );
    assert cdString != null;


    stringType = 
      new TypeDescriptor( cdString );

    stringBytesType =
      new TypeDescriptor(TypeDescriptor.CHAR).makeArray( state );


    stringBytesField = null;
    Iterator sFieldsItr = cdString.getFields();
    while( sFieldsItr.hasNext() ) {
      FieldDescriptor fd = (FieldDescriptor) sFieldsItr.next();
      if( fd.getSymbol().equals( typeUtil.StringClassValueField ) ) {
        stringBytesField = fd;
        break;
      }
    }
    assert stringBytesField != null;


    TempDescriptor throwAway1 =
      new TempDescriptor("stringLiteralTemp_dummy1",
                         stringType
                         );
    FlatNew fnStringLiteral =
      new FlatNew(stringType,
                  throwAway1,
                  false  // is global
                  );
    newStringLiteralAlloc
      = getAllocSiteFromFlatNewPRIVATE( fnStringLiteral );    


    TempDescriptor throwAway2 =
      new TempDescriptor("stringLiteralTemp_dummy2",
                         stringBytesType
                         );
    FlatNew fnStringLiteralBytes =
      new FlatNew(stringBytesType,
                  throwAway2,
                  false  // is global
                  );
    newStringLiteralBytesAlloc
      = getAllocSiteFromFlatNewPRIVATE( fnStringLiteralBytes );    
  }




  // allocate various structures that are not local
  // to a single class method--should be done once
  protected void allocateStructures() {

    if( determinismDesired ) {
      // use an ordered set
      descriptorsToAnalyze = new TreeSet<Descriptor>(dComp);
    } else {
      // otherwise use a speedy hashset
      descriptorsToAnalyze = new HashSet<Descriptor>();
    }

    mapDescriptorToCompleteReachGraph =
      new Hashtable<Descriptor, ReachGraph>();

    mapDescriptorToNumUpdates =
      new Hashtable<Descriptor, Integer>();

    mapDescriptorToSetDependents =
      new Hashtable< Descriptor, Set<Descriptor> >();

    mapFlatNewToAllocSite =
      new Hashtable<FlatNew, AllocSite>();

    mapDescriptorToIHMcontributions =
      new Hashtable< Descriptor, Hashtable< FlatCall, ReachGraph > >();

    mapDescriptorToInitialContext =
      new Hashtable<Descriptor, ReachGraph>();

    mapFlatNodeToReachGraphPersist =
      new Hashtable<FlatNode, ReachGraph>();

    mapBackEdgeToMonotone =
      new Hashtable<FlatNode, ReachGraph>();

    mapHrnIdToAllocSite =
      new Hashtable<Integer, AllocSite>();

    mapTypeToArrayField =
      new Hashtable <TypeDescriptor, FieldDescriptor>();

    if( state.DISJOINTDVISITSTACK ||
        state.DISJOINTDVISITSTACKEESONTOP
        ) {
      descriptorsToVisitStack =
        new Stack<Descriptor>();
    }

    if( state.DISJOINTDVISITPQUE ) {
      descriptorsToVisitQ =
        new PriorityQueue<DescriptorQWrapper>();
    }

    descriptorsToVisitSet =
      new HashSet<Descriptor>();

    mapDescriptorToPriority =
      new Hashtable<Descriptor, Integer>();

    calleesToEnqueue =
      new HashSet<Descriptor>();

    mapDescriptorToAllocSiteSet =
      new Hashtable<Descriptor,    HashSet<AllocSite> >();

    mapDescriptorToReachGraph =
      new Hashtable<Descriptor, ReachGraph>();

    pm = new PointerMethod();

    fc2enclosing = new Hashtable<FlatCall, Descriptor>();
  }



  // this analysis generates a disjoint reachability
  // graph for every reachable method in the program
  public DisjointAnalysis(State s,
                          TypeUtil tu,
                          CallGraph cg,
                          Liveness l,
                          ArrayReferencees ar,
                          Set<FlatNew> sitesToFlag,
                          RBlockRelationAnalysis rra
                          ) {
    init(s, tu, cg, l, ar, sitesToFlag, rra, null, false);
  }

  public DisjointAnalysis(State s,
                          TypeUtil tu,
                          CallGraph cg,
                          Liveness l,
                          ArrayReferencees ar,
                          Set<FlatNew> sitesToFlag,
                          RBlockRelationAnalysis rra,
                          boolean suppressOutput
                          ) {
    init(s, tu, cg, l, ar, sitesToFlag, rra, null, suppressOutput);
  }

  public DisjointAnalysis(State s,
                          TypeUtil tu,
                          CallGraph cg,
                          Liveness l,
                          ArrayReferencees ar,
                          Set<FlatNew> sitesToFlag,
                          RBlockRelationAnalysis rra,
                          BuildStateMachines bsm,
                          boolean suppressOutput
                          ) {
    init(s, tu, cg, l, ar, sitesToFlag, rra, bsm, suppressOutput);
  }

  protected void init(State state,
                      TypeUtil typeUtil,
                      CallGraph callGraph,
                      Liveness liveness,
                      ArrayReferencees arrayReferencees,
                      Set<FlatNew> sitesToFlag,
                      RBlockRelationAnalysis rra,
                      BuildStateMachines bsm,
                      boolean suppressOutput
                      ) {

    analysisComplete = false;

    this.state              = state;
    this.typeUtil           = typeUtil;
    this.callGraph          = callGraph;
    this.liveness           = liveness;
    this.arrayReferencees   = arrayReferencees;
    this.sitesToFlag        = sitesToFlag;
    this.rblockRel          = rra;
    this.suppressOutput     = suppressOutput;
    this.buildStateMachines = bsm;

    if( rblockRel != null ) {
      doEffectsAnalysis = true;
      effectsAnalysis   = new EffectsAnalysis();

      EffectsAnalysis.state              = state;
      EffectsAnalysis.buildStateMachines = buildStateMachines;

      //note: instead of reachgraph's isAccessible, using the result of accessible analysis
      //since accessible gives us more accurate results
      accessible=new Accessible(state, callGraph, rra, liveness);
      accessible.doAnalysis();
    }

    this.allocationDepth         = state.DISJOINTALLOCDEPTH;
    this.releaseMode             = state.DISJOINTRELEASEMODE;
    this.determinismDesired      = state.DISJOINTDETERMINISM;

    this.writeFinalDOTs          = state.DISJOINTWRITEDOTS && !state.DISJOINTWRITEALL;
    this.writeAllIncrementalDOTs = state.DISJOINTWRITEDOTS &&  state.DISJOINTWRITEALL;

    this.takeDebugSnapshots      = state.DISJOINTSNAPSYMBOL != null;
    this.descSymbolDebug         = state.DISJOINTSNAPSYMBOL;
    this.visitStartCapture       = state.DISJOINTSNAPVISITTOSTART;
    this.numVisitsToCapture      = state.DISJOINTSNAPNUMVISITS;
    this.stopAfterCapture        = state.DISJOINTSNAPSTOPAFTER;
    this.snapVisitCounter        = 1; // count visits from 1 (user will write 1, means 1st visit)
    this.snapNodeCounter         = 0; // count nodes from 0

    assert
    state.DISJOINTDVISITSTACK ||
    state.DISJOINTDVISITPQUE  ||
    state.DISJOINTDVISITSTACKEESONTOP;
    assert !(state.DISJOINTDVISITSTACK && state.DISJOINTDVISITPQUE);
    assert !(state.DISJOINTDVISITSTACK && state.DISJOINTDVISITSTACKEESONTOP);
    assert !(state.DISJOINTDVISITPQUE  && state.DISJOINTDVISITSTACKEESONTOP);

    // set some static configuration for ReachGraphs
    ReachGraph.allocationDepth = allocationDepth;
    ReachGraph.typeUtil        = typeUtil;
    ReachGraph.state           = state;

    ReachGraph.initOutOfScopeTemps();

    ReachGraph.debugCallSiteVisitStartCapture
      = state.DISJOINTDEBUGCALLVISITTOSTART;

    ReachGraph.debugCallSiteNumVisitsToCapture
      = state.DISJOINTDEBUGCALLNUMVISITS;

    ReachGraph.debugCallSiteStopAfter
      = state.DISJOINTDEBUGCALLSTOPAFTER;

    ReachGraph.debugCallSiteVisitCounter
      = 0; // count visits from 1, is incremented before first visit    

    if( state.DO_DEFINITE_REACH_ANALYSIS ) {
      doDefiniteReachAnalysis = true;
      definiteReachAnalysis = new DefiniteReachAnalysis();
    }


    if( suppressOutput ) {
      System.out.println("* Running disjoint reachability analysis with output suppressed! *");
    }


    allocateStructures();

    initImplicitStringsModel();



    double timeStartAnalysis = (double) System.nanoTime();

    // start interprocedural fixed-point computation
    try {
      analyzeMethods();
    } catch( IOException e ) {
      throw new Error("IO Exception while writing disjointness analysis output.");
    }

    analysisComplete=true;

    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow(10.0, 9.0) );

    String treport;
    if( sitesToFlag != null ) {
      treport = String.format("Disjoint reachability analysis flagged %d sites and took %.3f sec.", sitesToFlag.size(), dt);
      if(sitesToFlag.size()>0) {
        treport+="\nFlagged sites:"+"\n"+sitesToFlag.toString();
      }
    } else {
      treport = String.format("Disjoint reachability analysis took %.3f sec.", dt);
    }
    String justtime = String.format("%.2f", dt);
    System.out.println(treport);


    try {
      if( writeFinalDOTs && !writeAllIncrementalDOTs ) {
        writeFinalGraphs();
      }

      if( state.DISJOINTWRITEIHMS ) {
        writeFinalIHMs();
      }

      if( state.DISJOINTWRITEINITCONTEXTS ) {
        writeInitialContexts();
      }

      if( state.DISJOINT_WRITE_ALL_NODE_FINAL_GRAPHS ) {
        writeFinalGraphsForEveryNode();
      }

      if( state.DISJOINTALIASFILE != null && !suppressOutput ) {
        if( state.TASK ) {
          writeAllSharing(state.DISJOINTALIASFILE, treport, justtime, state.DISJOINTALIASTAB, state.lines);
        } else {
          writeAllSharingJava(state.DISJOINTALIASFILE,
                              treport,
                              justtime,
                              state.DISJOINTALIASTAB,
                              state.lines
                              );
        }
      }

      if( state.RCR ) {
        buildStateMachines.writeStateMachines();
      }

    } catch( IOException e ) {
      throw new Error("IO Exception while writing disjointness analysis output.");
    }
  }


  protected boolean moreDescriptorsToVisit() {
    if( state.DISJOINTDVISITSTACK ||
        state.DISJOINTDVISITSTACKEESONTOP
        ) {
      return !descriptorsToVisitStack.isEmpty();

    } else if( state.DISJOINTDVISITPQUE ) {
      return !descriptorsToVisitQ.isEmpty();
    }

    throw new Error("Neither descriptor visiting mode set");
  }


  // fixed-point computation over the call graph--when a
  // method's callees are updated, it must be reanalyzed
  protected void analyzeMethods() throws java.io.IOException {

    // task or non-task (java) mode determines what the roots
    // of the call chain are, and establishes the set of methods
    // reachable from the roots that will be analyzed

    if( state.TASK ) {
      if( !suppressOutput ) {
        System.out.println("Bamboo mode...");
      }

      Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
      while( taskItr.hasNext() ) {
        TaskDescriptor td = (TaskDescriptor) taskItr.next();
        if( !descriptorsToAnalyze.contains(td) ) {
          // add all methods transitively reachable from the
          // tasks as well
          descriptorsToAnalyze.add(td);
          descriptorsToAnalyze.addAll(callGraph.getAllMethods(td) );
        }
      }

    } else {
      if( !suppressOutput ) {
        System.out.println("Java mode...");
      }

      // add all methods transitively reachable from the
      // source's main to set for analysis
      mdSourceEntry = typeUtil.getMain();
      descriptorsToAnalyze.add(mdSourceEntry);
      descriptorsToAnalyze.addAll(callGraph.getAllMethods(mdSourceEntry) );

      // fabricate an empty calling context that will call
      // the source's main, but call graph doesn't know
      // about it, so explicitly add it
      makeAnalysisEntryMethod(mdSourceEntry);
      descriptorsToAnalyze.add(mdAnalysisEntry);
    }



    // now, depending on the interprocedural mode for visiting
    // methods, set up the needed data structures

    if( state.DISJOINTDVISITPQUE ) {

      // topologically sort according to the call graph so
      // leaf calls are last, helps build contexts up first
      LinkedList<Descriptor> sortedDescriptors =
        topologicalSort(descriptorsToAnalyze);

      // add sorted descriptors to priority queue, and duplicate
      // the queue as a set for efficiently testing whether some
      // method is marked for analysis
      int p = 0;
      Iterator<Descriptor> dItr;

      // for the priority queue, give items at the head
      // of the sorted list a low number (highest priority)
      while( !sortedDescriptors.isEmpty() ) {
        Descriptor d = sortedDescriptors.removeFirst();
        mapDescriptorToPriority.put(d, new Integer(p) );
        descriptorsToVisitQ.add(new DescriptorQWrapper(p, d) );
        descriptorsToVisitSet.add(d);
        ++p;
      }

    } else if( state.DISJOINTDVISITSTACK ||
               state.DISJOINTDVISITSTACKEESONTOP
               ) {
      // if we're doing the stack scheme, just throw the root
      // method or tasks on the stack
      if( state.TASK ) {
        Iterator taskItr = state.getTaskSymbolTable().getDescriptorsIterator();
        while( taskItr.hasNext() ) {
          TaskDescriptor td = (TaskDescriptor) taskItr.next();
          descriptorsToVisitStack.add(td);
          descriptorsToVisitSet.add(td);
        }

      } else {
        descriptorsToVisitStack.add(mdAnalysisEntry);
        descriptorsToVisitSet.add(mdAnalysisEntry);
      }

    } else {
      throw new Error("Unknown method scheduling mode");
    }


    // analyze scheduled methods until there are no more to visit
    while( moreDescriptorsToVisit() ) {
      Descriptor d = null;

      if( state.DISJOINTDVISITSTACK ||
          state.DISJOINTDVISITSTACKEESONTOP
          ) {
        d = descriptorsToVisitStack.pop();

      } else if( state.DISJOINTDVISITPQUE ) {
        d = descriptorsToVisitQ.poll().getDescriptor();
      }

      assert descriptorsToVisitSet.contains(d);
      descriptorsToVisitSet.remove(d);

      // because the task or method descriptor just extracted
      // was in the "to visit" set it either hasn't been analyzed
      // yet, or some method that it depends on has been
      // updated.  Recompute a complete reachability graph for
      // this task/method and compare it to any previous result.
      // If there is a change detected, add any methods/tasks
      // that depend on this one to the "to visit" set.

      if( !suppressOutput ) {
        System.out.println("Analyzing " + d);
      }

      if( state.DISJOINTDVISITSTACKEESONTOP ) {
        assert calleesToEnqueue.isEmpty();
      }

      ReachGraph rg     = analyzeMethod(d);
      ReachGraph rgPrev = getPartial(d);

      if( !rg.equals(rgPrev) ) {
        setPartial(d, rg);

        if( state.DISJOINTDEBUGSCHEDULING ) {
          System.out.println("  complete graph changed, scheduling callers for analysis:");
        }

        // results for d changed, so enqueue dependents
        // of d for further analysis
        Iterator<Descriptor> depsItr = getDependents(d).iterator();
        while( depsItr.hasNext() ) {
          Descriptor dNext = depsItr.next();
          enqueue(dNext);

          if( state.DISJOINTDEBUGSCHEDULING ) {
            System.out.println("    "+dNext);
          }
        }
      }

      // whether or not the method under analysis changed,
      // we may have some callees that are scheduled for
      // more analysis, and they should go on the top of
      // the stack now (in other method-visiting modes they
      // are already enqueued at this point
      if( state.DISJOINTDVISITSTACKEESONTOP ) {
        Iterator<Descriptor> depsItr = calleesToEnqueue.iterator();
        while( depsItr.hasNext() ) {
          Descriptor dNext = depsItr.next();
          enqueue(dNext);
        }
        calleesToEnqueue.clear();
      }

    }
  }

  protected ReachGraph analyzeMethod(Descriptor d)
  throws java.io.IOException {

    // get the flat code for this descriptor
    FlatMethod fm;
    if( d == mdAnalysisEntry ) {
      fm = fmAnalysisEntry;
    } else {
      fm = state.getMethodFlat(d);
    }
    pm.analyzeMethod(fm);

    // intraprocedural work set
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    // if determinism is desired by client, shadow the
    // set with a queue to make visit order deterministic
    Queue<FlatNode> flatNodesToVisitQ = null;
    if( determinismDesired ) {
      flatNodesToVisitQ = new LinkedList<FlatNode>();
      flatNodesToVisitQ.add(fm);
    }

    // start a new mapping of partial results
    mapFlatNodeToReachGraph =
      new Hashtable<FlatNode, ReachGraph>();

    // the set of return nodes partial results that will be combined as
    // the final, conservative approximation of the entire method
    HashSet<FlatReturnNode> setReturns = new HashSet<FlatReturnNode>();



    boolean snapThisMethod = false;
    if( takeDebugSnapshots && d instanceof MethodDescriptor ) {
      MethodDescriptor mdThisMethod = (MethodDescriptor)d;
      ClassDescriptor  cdThisMethod = mdThisMethod.getClassDesc();
      if( cdThisMethod != null ) {
        snapThisMethod = 
          descSymbolDebug.equals( cdThisMethod.getSymbol()+
                                  "."+
                                  mdThisMethod.getSymbol()
                                  );
      }
    }



    while( !flatNodesToVisit.isEmpty() ) {

      FlatNode fn;
      if( determinismDesired ) {
        assert !flatNodesToVisitQ.isEmpty();
        fn = flatNodesToVisitQ.remove();
      } else {
        fn = flatNodesToVisit.iterator().next();
      }
      flatNodesToVisit.remove(fn);

      // effect transfer function defined by this node,
      // then compare it to the old graph at this node
      // to see if anything was updated.

      ReachGraph rg = new ReachGraph();
      TaskDescriptor taskDesc;
      if(fn instanceof FlatMethod && (taskDesc=((FlatMethod)fn).getTask())!=null) {
        if(mapDescriptorToReachGraph.containsKey(taskDesc)) {
          // retrieve existing reach graph if it is not first time
          rg=mapDescriptorToReachGraph.get(taskDesc);
        } else {
          // create initial reach graph for a task
          rg=createInitialTaskReachGraph((FlatMethod)fn);
          rg.globalSweep();
          mapDescriptorToReachGraph.put(taskDesc, rg);
        }
      }

      // start by merging all node's parents' graphs
      for( int i = 0; i < pm.numPrev(fn); ++i ) {
        FlatNode pn = pm.getPrev(fn,i);
        if( mapFlatNodeToReachGraph.containsKey(pn) ) {
          ReachGraph rgParent = mapFlatNodeToReachGraph.get(pn);
          rg.merge(rgParent);
        }
      }


      if( snapThisMethod ) {
        debugSnapshot(rg, fn, true);
      }


      // modify rg with appropriate transfer function
      rg = analyzeFlatNode(d, fm, fn, setReturns, rg);


      if( snapThisMethod ) {
        debugSnapshot(rg, fn, false);
        ++snapNodeCounter;
      }


      // if the results of the new graph are different from
      // the current graph at this node, replace the graph
      // with the update and enqueue the children
      ReachGraph rgPrev = mapFlatNodeToReachGraph.get(fn);
      if( !rg.equals(rgPrev) ) {
        mapFlatNodeToReachGraph.put(fn, rg);

        // we don't necessarily want to keep the reach graph for every
        // node in the program unless a client or the user wants it
        if( state.DISJOINT_WRITE_ALL_NODE_FINAL_GRAPHS ) {
          mapFlatNodeToReachGraphPersist.put(fn, rg);
        }

        for( int i = 0; i < pm.numNext(fn); i++ ) {
          FlatNode nn = pm.getNext(fn, i);

          flatNodesToVisit.add(nn);
          if( determinismDesired ) {
            flatNodesToVisitQ.add(nn);
          }
        }
      }
    }


    // end by merging all return nodes into a complete
    // reach graph that represents all possible heap
    // states after the flat method returns
    ReachGraph completeGraph = new ReachGraph();

    if( setReturns.isEmpty() ) {
      System.out.println( "d = "+d );
      
    }
    assert !setReturns.isEmpty();
    Iterator retItr = setReturns.iterator();
    while( retItr.hasNext() ) {
      FlatReturnNode frn = (FlatReturnNode) retItr.next();

      assert mapFlatNodeToReachGraph.containsKey(frn);
      ReachGraph rgRet = mapFlatNodeToReachGraph.get(frn);

      completeGraph.merge(rgRet);
    }


    if( snapThisMethod ) {
      // increment that we've visited the debug snap
      // method, and reset the node counter
      System.out.println("    @@@ debug snap at visit "+snapVisitCounter);
      ++snapVisitCounter;
      snapNodeCounter = 0;

      if( snapVisitCounter == visitStartCapture + numVisitsToCapture &&
          stopAfterCapture
          ) {
        System.out.println("!!! Stopping analysis after debug snap captures. !!!");
        System.exit(-1);
      }
    }


    return completeGraph;
  }


  protected ReachGraph
  analyzeFlatNode(Descriptor d,
                  FlatMethod fmContaining,
                  FlatNode fn,
                  HashSet<FlatReturnNode> setRetNodes,
                  ReachGraph rg
                  ) throws java.io.IOException {


    // any variables that are no longer live should be
    // nullified in the graph to reduce edges
    //rg.nullifyDeadVars( liveness.getLiveInTemps( fmContaining, fn ) );

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;
    TypeDescriptor tdElement;
    FieldDescriptor fdElement;
    FlatSESEEnterNode sese;
    FlatSESEExitNode fsexn;

    Set<EdgeKey> edgeKeysForLoad;
    Set<EdgeKey> edgeKeysRemoved;
    Set<EdgeKey> edgeKeysAdded;

    //Stores the flatnode's reach graph at enter
    ReachGraph rgOnEnter = new ReachGraph();
    rgOnEnter.merge(rg);
    fn2rgAtEnter.put(fn, rgOnEnter);



    
    boolean didDefReachTransfer = false;    




    // use node type to decide what transfer function
    // to apply to the reachability graph
    switch( fn.kind() ) {

    case FKind.FlatGenReachNode: {
      FlatGenReachNode fgrn = (FlatGenReachNode) fn;

      System.out.println("  Generating reach graph for program point: "+fgrn.getGraphName() );


      rg.writeGraph("genReach"+fgrn.getGraphName(),
                    true,     // write labels (variables)
                    true,    // selectively hide intermediate temp vars
                    true,     // prune unreachable heap regions
                    false,    // hide reachability altogether
                    true,    // hide subset reachability states
                    true,     // hide predicates
                    true); //false);    // hide edge taints
    } break;


    case FKind.FlatGenDefReachNode: {
      FlatGenDefReachNode fgdrn = (FlatGenDefReachNode) fn;
      if( doDefiniteReachAnalysis ) {
        definiteReachAnalysis.writeState( fn, fgdrn.getOutputName() );
      }
    } break;


    case FKind.FlatMethod: {
      // construct this method's initial heap model (IHM)
      // since we're working on the FlatMethod, we know
      // the incoming ReachGraph 'rg' is empty

      Hashtable<FlatCall, ReachGraph> heapsFromCallers =
        getIHMcontributions(d);

      Set entrySet = heapsFromCallers.entrySet();
      Iterator itr = entrySet.iterator();
      while( itr.hasNext() ) {
        Map.Entry me        = (Map.Entry)itr.next();
        FlatCall fc        = (FlatCall)   me.getKey();
        ReachGraph rgContrib = (ReachGraph) me.getValue();

        // note that "fc.getMethod()" like (Object.toString)
        // might not be equal to "d" like (String.toString)
        // because the mapping gets set up when we resolve
        // virtual dispatch
        rg.merge(rgContrib);
      }

      // additionally, we are enforcing STRICT MONOTONICITY for the
      // method's initial context, so grow the context by whatever
      // the previously computed context was, and put the most
      // up-to-date context back in the map
      ReachGraph rgPrevContext = mapDescriptorToInitialContext.get(d);
      rg.merge(rgPrevContext);
      mapDescriptorToInitialContext.put(d, rg);

      if( doDefiniteReachAnalysis ) {
        FlatMethod fm = (FlatMethod) fn;
        Set<TempDescriptor> params = new HashSet<TempDescriptor>();
        for( int i = 0; i < fm.numParameters(); ++i ) {
          params.add( fm.getParameter( i ) );
        }
        definiteReachAnalysis.methodEntry( fn, params );
        didDefReachTransfer = true;
      }
    } break;

    case FKind.FlatOpNode:
      FlatOpNode fon = (FlatOpNode) fn;
      if( fon.getOp().getOp() == Operation.ASSIGN ) {
        lhs = fon.getDest();
        rhs = fon.getLeft();

        // before transfer, do effects analysis support
        if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
          if(rblockRel.isPotentialStallSite(fn)) {
            // x gets status of y
            if(!accessible.isAccessible(fn, rhs)) {
              rg.makeInaccessible(lhs);
            }
          }
        }

        // transfer func
        rg.assignTempXEqualToTempY(lhs, rhs);

        if( doDefiniteReachAnalysis ) {
          definiteReachAnalysis.copy( fn, lhs, rhs );
          didDefReachTransfer = true;
        }
      }
      break;

    case FKind.FlatCastNode:
      FlatCastNode fcn = (FlatCastNode) fn;
      lhs = fcn.getDst();
      rhs = fcn.getSrc();

      TypeDescriptor td = fcn.getType();
      assert td != null;

      // before transfer, do effects analysis support
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        if(rblockRel.isPotentialStallSite(fn)) {
          // x gets status of y
          if(!accessible.isAccessible(fn,rhs)) {
            rg.makeInaccessible(lhs);
          }
        }
      }

      // transfer func
      rg.assignTempXEqualToCastedTempY(lhs, rhs, td);

      if( doDefiniteReachAnalysis ) {
        definiteReachAnalysis.copy( fn, lhs, rhs );
        didDefReachTransfer = true;
      }
      break;

    case FKind.FlatFieldNode:
      FlatFieldNode ffn = (FlatFieldNode) fn;

      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();

      // before graph transform, possible inject
      // a stall-site taint
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {

        if(rblockRel.isPotentialStallSite(fn)) {
          // x=y.f, stall y if not accessible
          // contributes read effects on stall site of y
          if(!accessible.isAccessible(fn,rhs)) {
            rg.taintStallSite(fn, rhs);
          }

          // after this, x and y are accessbile.
          rg.makeAccessible(lhs);
          rg.makeAccessible(rhs);
        }
      }

      edgeKeysForLoad = null;
      if( doDefiniteReachAnalysis ) {
        edgeKeysForLoad = new HashSet<EdgeKey>();
      }

      if( shouldAnalysisTrack(fld.getType() ) ) {
        // transfer func
        rg.assignTempXEqualToTempYFieldF( lhs, rhs, fld, fn, edgeKeysForLoad );

        if( doDefiniteReachAnalysis ) {
          definiteReachAnalysis.load( fn, lhs, rhs, fld, edgeKeysForLoad );
          didDefReachTransfer = true;
        }
      }

      // after transfer, use updated graph to
      // do effects analysis
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        effectsAnalysis.analyzeFlatFieldNode(rg, rhs, fld, fn);
      }
      break;

    case FKind.FlatSetFieldNode:
      FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;

      lhs = fsfn.getDst();
      fld = fsfn.getField();
      rhs = fsfn.getSrc();

      boolean strongUpdate = false;

      edgeKeysRemoved = null;
      edgeKeysAdded   = null;
      if( doDefiniteReachAnalysis ) {
        edgeKeysRemoved = new HashSet<EdgeKey>();
        edgeKeysAdded   = new HashSet<EdgeKey>();
      }

      // before transfer func, possibly inject
      // stall-site taints
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {

        if(rblockRel.isPotentialStallSite(fn)) {
          // x.y=f , stall x and y if they are not accessible
          // also contribute write effects on stall site of x
          if(!accessible.isAccessible(fn,lhs)) {
            rg.taintStallSite(fn, lhs);
          }

          if(!accessible.isAccessible(fn,rhs)) {
            rg.taintStallSite(fn, rhs);
          }

          // accessible status update
          rg.makeAccessible(lhs);
          rg.makeAccessible(rhs);
        }
      }

      if( shouldAnalysisTrack(fld.getType() ) ) {
        // transfer func
        strongUpdate = rg.assignTempXFieldFEqualToTempY( lhs, 
                                                         fld, 
                                                         rhs, 
                                                         fn, 
                                                         edgeKeysRemoved,
                                                         edgeKeysAdded );
        if( doDefiniteReachAnalysis ) {
          definiteReachAnalysis.store( fn, 
                                       lhs,
                                       fld,
                                       rhs,
                                       edgeKeysRemoved,
                                       edgeKeysAdded );
          didDefReachTransfer = true;
        }
      }

      // use transformed graph to do effects analysis
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        effectsAnalysis.analyzeFlatSetFieldNode(rg, lhs, fld, fn, strongUpdate);
      }
      break;

    case FKind.FlatElementNode:
      FlatElementNode fen = (FlatElementNode) fn;

      lhs = fen.getDst();
      rhs = fen.getSrc();

      assert rhs.getType() != null;
      assert rhs.getType().isArray();

      tdElement = rhs.getType().dereference();
      fdElement = getArrayField(tdElement);

      // before transfer func, possibly inject
      // stall-site taint
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        if(rblockRel.isPotentialStallSite(fn)) {
          // x=y.f, stall y if not accessible
          // contributes read effects on stall site of y
          // after this, x and y are accessbile.
          if(!accessible.isAccessible(fn,rhs)) {
            rg.taintStallSite(fn, rhs);
          }

          rg.makeAccessible(lhs);
          rg.makeAccessible(rhs);
        }
      }

      edgeKeysForLoad = null;
      if( doDefiniteReachAnalysis ) {
        edgeKeysForLoad = new HashSet<EdgeKey>();
      }

      if( shouldAnalysisTrack(lhs.getType() ) ) {
        // transfer func
        rg.assignTempXEqualToTempYFieldF( lhs, rhs, fdElement, fn, edgeKeysForLoad );

        if( doDefiniteReachAnalysis ) {
          definiteReachAnalysis.load( fn, lhs, rhs, fdElement, edgeKeysForLoad );
          didDefReachTransfer = true;
        }
      }

      // use transformed graph to do effects analysis
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        effectsAnalysis.analyzeFlatFieldNode(rg, rhs, fdElement, fn);
      }
      break;

    case FKind.FlatSetElementNode:
      FlatSetElementNode fsen = (FlatSetElementNode) fn;

      lhs = fsen.getDst();
      rhs = fsen.getSrc();
      
      assert lhs.getType() != null;
      assert lhs.getType().isArray();

      tdElement = lhs.getType().dereference();
      fdElement = getArrayField(tdElement);

      edgeKeysRemoved = null;
      edgeKeysAdded   = null;
      if( doDefiniteReachAnalysis ) {
        edgeKeysRemoved = new HashSet<EdgeKey>();
        edgeKeysAdded   = new HashSet<EdgeKey>();
      }

      // before transfer func, possibly inject
      // stall-site taints
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {

        if(rblockRel.isPotentialStallSite(fn)) {
          // x.y=f , stall x and y if they are not accessible
          // also contribute write effects on stall site of x
          if(!accessible.isAccessible(fn,lhs)) {
            rg.taintStallSite(fn, lhs);
          }

          if(!accessible.isAccessible(fn,rhs)) {
            rg.taintStallSite(fn, rhs);
          }

          // accessible status update
          rg.makeAccessible(lhs);
          rg.makeAccessible(rhs);
        }
      }

      if( shouldAnalysisTrack(rhs.getType() ) ) {
        // transfer func, BUT
        // skip this node if it cannot create new reachability paths
        if( !arrayReferencees.doesNotCreateNewReaching(fsen) ) {
          rg.assignTempXFieldFEqualToTempY( lhs, 
                                            fdElement, 
                                            rhs, 
                                            fn, 
                                            edgeKeysRemoved,
                                            edgeKeysAdded );
        }

        if( doDefiniteReachAnalysis ) {
          definiteReachAnalysis.store( fn,
                                       lhs,
                                       fdElement, 
                                       rhs, 
                                       edgeKeysRemoved,
                                       edgeKeysAdded );
          didDefReachTransfer = true;
        }
      }

      // use transformed graph to do effects analysis
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        effectsAnalysis.analyzeFlatSetFieldNode(rg, lhs, fdElement, fn,
                                                false);
      }
      break;

    case FKind.FlatNew:
      FlatNew fnn = (FlatNew) fn;
      lhs = fnn.getDst();
      if( shouldAnalysisTrack(lhs.getType() ) ) {
        AllocSite as = getAllocSiteFromFlatNewPRIVATE(fnn);

        // before transform, support effects analysis
        if (doEffectsAnalysis && fmContaining != fmAnalysisEntry) {
          if (rblockRel.isPotentialStallSite(fn)) {
            // after creating new object, lhs is accessible
            rg.makeAccessible(lhs);
          }
        }

        // transfer func
        rg.assignTempEqualToNewAlloc(lhs, as);

        if( doDefiniteReachAnalysis ) {
          definiteReachAnalysis.newObject( fn, lhs );
          didDefReachTransfer = true;
        }
      }
      break;

      
    case FKind.FlatLiteralNode:
      // BIG NOTE: this transfer function is only here for
      // points-to information for String literals.  That's it.
      // Effects and disjoint reachability and all of that don't
      // care about references to literals.
      FlatLiteralNode fln = (FlatLiteralNode) fn;

      if( fln.getType().equals( stringType ) ) {
        rg.assignTempEqualToStringLiteral( fln.getDst(),
                                           newStringLiteralAlloc,
                                           newStringLiteralBytesAlloc,
                                           stringBytesField );
      }      
      break;


    case FKind.FlatSESEEnterNode:
      sese = (FlatSESEEnterNode) fn;

      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {

        // always remove ALL stall site taints at enter
        rg.removeAllStallSiteTaints();

        // inject taints for in-set vars
        rg.taintInSetVars(sese);

      }
      break;

    case FKind.FlatSESEExitNode:
      fsexn = (FlatSESEExitNode) fn;
      sese  = fsexn.getFlatEnter();

      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {

        // @ sese exit make all live variables
        // inaccessible to later parent statements
        rg.makeInaccessible(liveness.getLiveInTemps(fmContaining, fn) );

        // always remove ALL stall site taints at exit
        rg.removeAllStallSiteTaints();

        // remove in-set var taints for the exiting rblock
        rg.removeInContextTaints(sese);
      }
      break;


    case FKind.FlatCall: {
      Descriptor mdCaller;
      if( fmContaining.getMethod() != null ) {
        mdCaller = fmContaining.getMethod();
      } else {
        mdCaller = fmContaining.getTask();
      }
      FlatCall fc       = (FlatCall) fn;
      MethodDescriptor mdCallee = fc.getMethod();
      FlatMethod fmCallee = state.getMethodFlat(mdCallee);


      if( doDefiniteReachAnalysis ) {
        definiteReachAnalysis.methodCall( fn, fc.getReturnTemp() );
        didDefReachTransfer = true;
      }

      
      // the transformation for a call site should update the
      // current heap abstraction with any effects from the callee,
      // or if the method is virtual, the effects from any possible
      // callees, so find the set of callees...
      Set<MethodDescriptor> setPossibleCallees;
      if( determinismDesired ) {
        // use an ordered set
        setPossibleCallees = new TreeSet<MethodDescriptor>(dComp);
      } else {
        // otherwise use a speedy hashset
        setPossibleCallees = new HashSet<MethodDescriptor>();
      }

      if( mdCallee.isStatic() ) {
        setPossibleCallees.add(mdCallee);
      } else {
        TypeDescriptor typeDesc = fc.getThis().getType();
        setPossibleCallees.addAll(callGraph.getMethods(mdCallee,
                                                       typeDesc)
                                  );
      }


      DebugCallSiteData dcsd = new DebugCallSiteData();
      
      ReachGraph rgMergeOfPossibleCallers = new ReachGraph();


      Iterator<MethodDescriptor> mdItr = setPossibleCallees.iterator();
      while( mdItr.hasNext() ) {
        MethodDescriptor mdPossible = mdItr.next();
        FlatMethod fmPossible = state.getMethodFlat(mdPossible);

        addDependent(mdPossible,  // callee
                     d);          // caller


        // decide for each possible resolution of the method whether we
        // want to debug this call site
        decideDebugCallSite( dcsd, mdCaller, mdPossible );



        // calculate the heap this call site can reach--note this is
        // not used for the current call site transform, we are
        // grabbing this heap model for future analysis of the callees,
        // so if different results emerge we will return to this site
        ReachGraph heapForThisCall_old =
          getIHMcontribution(mdPossible, fc);
      
        // the computation of the callee-reachable heap
        // is useful for making the callee starting point
        // and for applying the call site transfer function
        Set<Integer> callerNodeIDsCopiedToCallee =
          new HashSet<Integer>();


        ReachGraph heapForThisCall_cur =
          rg.makeCalleeView(fc,
                            fmPossible,
                            callerNodeIDsCopiedToCallee,
                            dcsd.writeDebugDOTs
                            );


        // enforce that a call site contribution can only
        // monotonically increase
        heapForThisCall_cur.merge(heapForThisCall_old);

        if( !heapForThisCall_cur.equals(heapForThisCall_old) ) {
          // if heap at call site changed, update the contribution,
          // and reschedule the callee for analysis
          addIHMcontribution(mdPossible, fc, heapForThisCall_cur);

          // map a FlatCall to its enclosing method/task descriptor
          // so we can write that info out later
          fc2enclosing.put(fc, mdCaller);

          if( state.DISJOINTDEBUGSCHEDULING ) {
            System.out.println("  context changed at callsite: "+fc+", scheduling callee: "+mdPossible);
          }

          if( state.DISJOINTDVISITSTACKEESONTOP ) {
            calleesToEnqueue.add(mdPossible);
          } else {
            enqueue(mdPossible);
          }
        }


        

        // don't alter the working graph (rg) until we compute a
        // result for every possible callee, merge them all together,
        // then set rg to that
        ReachGraph rgPossibleCaller = new ReachGraph();
        rgPossibleCaller.merge(rg);

        ReachGraph rgPossibleCallee = getPartial(mdPossible);

        if( rgPossibleCallee == null ) {
          // if this method has never been analyzed just schedule it
          // for analysis and skip over this call site for now
          if( state.DISJOINTDVISITSTACKEESONTOP ) {
            calleesToEnqueue.add(mdPossible);
          } else {
            enqueue(mdPossible);
          }

          if( state.DISJOINTDEBUGSCHEDULING ) {
            System.out.println("  callee hasn't been analyzed, scheduling: "+mdPossible);
          }


        } else {
          
          // calculate the method call transform
          rgPossibleCaller.resolveMethodCall(fc,
                                             fmPossible,
                                             rgPossibleCallee,
                                             callerNodeIDsCopiedToCallee,
                                             dcsd.writeDebugDOTs
                                             );


          if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
            if( !accessible.isAccessible(fn, ReachGraph.tdReturn) ) {
              rgPossibleCaller.makeInaccessible(fc.getReturnTemp() );
            }
          }

        }

        rgMergeOfPossibleCallers.merge(rgPossibleCaller);
      }
      


      statusDebugCallSite( dcsd );



      // now that we've taken care of building heap models for
      // callee analysis, finish this transformation
      rg = rgMergeOfPossibleCallers;


      // jjenista: what is this?  It breaks compilation
      // of programs with no tasks/SESEs/rblocks...
      //XXXXXXXXXXXXXXXXXXXXXXXXX
      //need to consider more
      if( state.OOOJAVA ) {
        FlatNode nextFN=fmCallee.getNext(0);
        if( nextFN instanceof FlatSESEEnterNode ) {
          FlatSESEEnterNode calleeSESE=(FlatSESEEnterNode)nextFN;
          if(!calleeSESE.getIsLeafSESE()) {
            rg.makeInaccessible(liveness.getLiveInTemps(fmContaining, fn) );
          }
        }
      }

    } break;


    case FKind.FlatReturnNode:
      FlatReturnNode frn = (FlatReturnNode) fn;
      rhs = frn.getReturnTemp();

      // before transfer, do effects analysis support
      if( doEffectsAnalysis && fmContaining != fmAnalysisEntry ) {
        if(!accessible.isAccessible(fn,rhs)) {
          rg.makeInaccessible(ReachGraph.tdReturn);
        }
      }

      if( rhs != null && shouldAnalysisTrack(rhs.getType() ) ) {
        rg.assignReturnEqualToTemp(rhs);
      }

      setRetNodes.add(frn);
      break;

    } // end switch



    if( doDefiniteReachAnalysis && !didDefReachTransfer ) {
      definiteReachAnalysis.otherStatement( fn );
    }




    // dead variables were removed before the above transfer function
    // was applied, so eliminate heap regions and edges that are no
    // longer part of the abstractly-live heap graph, and sweep up
    // and reachability effects that are altered by the reduction
    //rg.abstractGarbageCollect();
    //rg.globalSweep();


    // back edges are strictly monotonic
    if( pm.isBackEdge(fn) ) {
      ReachGraph rgPrevResult = mapBackEdgeToMonotone.get(fn);
      rg.merge(rgPrevResult);
      mapBackEdgeToMonotone.put(fn, rg);
    }


    ReachGraph rgOnExit = new ReachGraph();
    rgOnExit.merge(rg);
    fn2rgAtExit.put(fn, rgOnExit);



    // at this point rg should be the correct update
    // by an above transfer function, or untouched if
    // the flat node type doesn't affect the heap
    return rg;
  }



  // this method should generate integers strictly greater than zero!
  // special "shadow" regions are made from a heap region by negating
  // the ID
  static public Integer generateUniqueHeapRegionNodeID() {
    ++uniqueIDcount;
    return new Integer(uniqueIDcount);
  }



  static public FieldDescriptor getArrayField(TypeDescriptor tdElement) {
    FieldDescriptor fdElement = mapTypeToArrayField.get(tdElement);
    if( fdElement == null ) {
      fdElement = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC),
                                      tdElement,
                                      arrayElementFieldName,
                                      null,
                                      false);
      mapTypeToArrayField.put(tdElement, fdElement);
    }
    return fdElement;
  }



  private void writeFinalGraphs() {
    Set entrySet = mapDescriptorToCompleteReachGraph.entrySet();
    Iterator itr = entrySet.iterator();
    while( itr.hasNext() ) {
      Map.Entry me = (Map.Entry)itr.next();
      Descriptor d = (Descriptor) me.getKey();
      ReachGraph rg = (ReachGraph) me.getValue();

      String graphName;
      if( d instanceof TaskDescriptor ) {
        graphName = "COMPLETEtask"+d;
      } else {
        graphName = "COMPLETE"+d;
      }

      rg.writeGraph(graphName,
                    true,     // write labels (variables)
                    true,     // selectively hide intermediate temp vars
                    true,     // prune unreachable heap regions
                    true,     // hide reachability altogether
                    true,     // hide subset reachability states
                    true,     // hide predicates
                    false);   // hide edge taints
    }
  }

  private void writeFinalIHMs() {
    Iterator d2IHMsItr = mapDescriptorToIHMcontributions.entrySet().iterator();
    while( d2IHMsItr.hasNext() ) {
      Map.Entry me1 = (Map.Entry)d2IHMsItr.next();
      Descriptor d = (Descriptor)                      me1.getKey();
      Hashtable<FlatCall, ReachGraph> IHMs = (Hashtable<FlatCall, ReachGraph>)me1.getValue();

      Iterator fc2rgItr = IHMs.entrySet().iterator();
      while( fc2rgItr.hasNext() ) {
        Map.Entry me2 = (Map.Entry)fc2rgItr.next();
        FlatCall fc  = (FlatCall)   me2.getKey();
        ReachGraph rg  = (ReachGraph) me2.getValue();

        rg.writeGraph("IHMPARTFOR"+d+"FROM"+fc2enclosing.get(fc)+fc,
                      true,    // write labels (variables)
                      true,    // selectively hide intermediate temp vars
                      true,    // hide reachability altogether
                      true,    // prune unreachable heap regions
                      true,    // hide subset reachability states
                      false,   // hide predicates
                      true);   // hide edge taints
      }
    }
  }

  private void writeInitialContexts() {
    Set entrySet = mapDescriptorToInitialContext.entrySet();
    Iterator itr = entrySet.iterator();
    while( itr.hasNext() ) {
      Map.Entry me = (Map.Entry)itr.next();
      Descriptor d = (Descriptor) me.getKey();
      ReachGraph rg = (ReachGraph) me.getValue();

      rg.writeGraph("INITIAL"+d,
                    true,    // write labels (variables)
                    true,    // selectively hide intermediate temp vars
                    true,    // prune unreachable heap regions
                    false,   // hide all reachability
                    true,    // hide subset reachability states
                    true,    // hide predicates
                    false);  // hide edge taints
    }
  }

  private void writeFinalGraphsForEveryNode() {
    Set entrySet = mapFlatNodeToReachGraphPersist.entrySet();
    Iterator itr = entrySet.iterator();
    while( itr.hasNext() ) {
      Map.Entry  me = (Map.Entry)  itr.next();
      FlatNode   fn = (FlatNode)   me.getKey();
      ReachGraph rg = (ReachGraph) me.getValue();

      rg.writeGraph("NODEFINAL"+fn,
                    true,    // write labels (variables)
                    false,   // selectively hide intermediate temp vars
                    true,    // prune unreachable heap regions
                    true,    // hide all reachability
                    true,    // hide subset reachability states
                    true,    // hide predicates
                    true);   // hide edge taints
    }
  }


  protected ReachGraph getPartial(Descriptor d) {
    return mapDescriptorToCompleteReachGraph.get(d);
  }

  protected void setPartial(Descriptor d, ReachGraph rg) {
    mapDescriptorToCompleteReachGraph.put(d, rg);

    // when the flag for writing out every partial
    // result is set, we should spit out the graph,
    // but in order to give it a unique name we need
    // to track how many partial results for this
    // descriptor we've already written out
    if( writeAllIncrementalDOTs ) {
      if( !mapDescriptorToNumUpdates.containsKey(d) ) {
        mapDescriptorToNumUpdates.put(d, new Integer(0) );
      }
      Integer n = mapDescriptorToNumUpdates.get(d);

      String graphName;
      if( d instanceof TaskDescriptor ) {
        graphName = d+"COMPLETEtask"+String.format("%05d", n);
      } else {
        graphName = d+"COMPLETE"+String.format("%05d", n);
      }

      rg.writeGraph(graphName,
                    true,    // write labels (variables)
                    true,    // selectively hide intermediate temp vars
                    true,    // prune unreachable heap regions
                    false,   // hide all reachability
                    true,    // hide subset reachability states
                    false,   // hide predicates
                    false);  // hide edge taints

      mapDescriptorToNumUpdates.put(d, n + 1);
    }
  }



  // return just the allocation site associated with one FlatNew node
  protected AllocSite getAllocSiteFromFlatNewPRIVATE(FlatNew fnew) {

    boolean flagProgrammatically = false;
    if( sitesToFlag != null && sitesToFlag.contains(fnew) ) {
      flagProgrammatically = true;
    }

    if( !mapFlatNewToAllocSite.containsKey(fnew) ) {
      AllocSite as = AllocSite.factory(allocationDepth,
                                       fnew,
                                       fnew.getDisjointId(),
                                       flagProgrammatically
                                       );

      // the newest nodes are single objects
      for( int i = 0; i < allocationDepth; ++i ) {
        Integer id = generateUniqueHeapRegionNodeID();
        as.setIthOldest(i, id);
        mapHrnIdToAllocSite.put(id, as);
      }

      // the oldest node is a summary node
      as.setSummary(generateUniqueHeapRegionNodeID() );

      mapFlatNewToAllocSite.put(fnew, as);
    }

    return mapFlatNewToAllocSite.get(fnew);
  }


  public static boolean shouldAnalysisTrack(TypeDescriptor type) {
    // don't track primitive types, but an array
    // of primitives is heap memory
    if( type.isImmutable() ) {
      return type.isArray();
    }

    // everything else is an object
    return true;
  }

  protected int numMethodsAnalyzed() {
    return descriptorsToAnalyze.size();
  }




  // Take in source entry which is the program's compiled entry and
  // create a new analysis entry, a method that takes no parameters
  // and appears to allocate the command line arguments and call the
  // source entry with them.  The purpose of this analysis entry is
  // to provide a top-level method context with no parameters left.
  protected void makeAnalysisEntryMethod(MethodDescriptor mdSourceEntry) {

    Modifiers mods = new Modifiers();
    mods.addModifier(Modifiers.PUBLIC);
    mods.addModifier(Modifiers.STATIC);

    TypeDescriptor returnType = new TypeDescriptor(TypeDescriptor.VOID);
    
    this.mdAnalysisEntry =
      new MethodDescriptor(mods,
                           returnType,
                           "analysisEntryMethod"
                           );

    TypeDescriptor argsType = mdSourceEntry.getParamType(0);
    TempDescriptor cmdLineArgs =
      new TempDescriptor("analysisEntryTemp_args",
                         argsType
                         );
    FlatNew fnArgs =
      new FlatNew(argsType,
                  cmdLineArgs,
                  false  // is global
                  );
    this.constructedCmdLineArgsNew = fnArgs;

    TypeDescriptor argType = argsType.dereference();
    TempDescriptor anArg =
      new TempDescriptor("analysisEntryTemp_arg",
                         argType
                         );
    FlatNew fnArg =
      new FlatNew(argType,
                  anArg,
                  false  // is global
                  );
    this.constructedCmdLineArgNew = fnArg;

    TypeDescriptor typeIndex = new TypeDescriptor(TypeDescriptor.INT);
    TempDescriptor index =
      new TempDescriptor("analysisEntryTemp_index",
                         typeIndex
                         );
    FlatLiteralNode fli =
      new FlatLiteralNode(typeIndex,
                          new Integer( 0 ),
                          index
                          );
    
    FlatSetElementNode fse =
      new FlatSetElementNode(cmdLineArgs,
                             index,
                             anArg
                             );

    TypeDescriptor typeSize = new TypeDescriptor(TypeDescriptor.INT);
    TempDescriptor sizeBytes =
      new TempDescriptor("analysisEntryTemp_size",
                         typeSize
                         );
    FlatLiteralNode fls =
      new FlatLiteralNode(typeSize,
                          new Integer( 1 ),
                          sizeBytes
                          );

    TempDescriptor strBytes =
      new TempDescriptor("analysisEntryTemp_strBytes",
                         stringBytesType
                         );
    FlatNew fnBytes =
      new FlatNew(stringBytesType,
                  strBytes,
                  //sizeBytes,
                  false  // is global
                  );
    this.constructedCmdLineArgBytesNew = fnBytes;

    FlatSetFieldNode fsf =
      new FlatSetFieldNode(anArg,
                           stringBytesField,
                           strBytes
                           );

    // throw this in so you can always see what the initial heap context
    // looks like if you want to, its cheap
    FlatGenReachNode fgen = new FlatGenReachNode( "argContext" );

    TempDescriptor[] sourceEntryArgs = new TempDescriptor[1];
    sourceEntryArgs[0] = cmdLineArgs;
    FlatCall fc =
      new FlatCall(mdSourceEntry,
                   null,  // dst temp
                   null,  // this temp
                   sourceEntryArgs
                   );

    FlatReturnNode frn = new FlatReturnNode(null);

    FlatExit fe = new FlatExit();

    this.fmAnalysisEntry =
      new FlatMethod(mdAnalysisEntry,
                     fe
                     );

    List<FlatNode> nodes = new LinkedList<FlatNode>();
    nodes.add( fnArgs );
    nodes.add( fnArg );
    nodes.add( fli );
    nodes.add( fse );
    nodes.add( fls );
    nodes.add( fnBytes );
    nodes.add( fsf );
    nodes.add( fgen );
    nodes.add( fc );
    nodes.add( frn );
    nodes.add( fe );

    FlatNode current = this.fmAnalysisEntry;
    for( FlatNode next: nodes ) {
      current.addNext( next );
      current = next;
    }

    
    // jjenista - this is useful for looking at the FlatIRGraph of the
    // analysis entry method constructed above if you have to modify it.
    // The usual method of writing FlatIRGraphs out doesn't work because
    // this flat method is private to the model of this analysis only.
    //try {
    //  FlatIRGraph flatMethodWriter = 
    //    new FlatIRGraph( state, false, false, false );
    //  flatMethodWriter.writeFlatIRGraph( fmAnalysisEntry, "analysisEntry" );
    //} catch( IOException e ) {}
  }


  protected LinkedList<Descriptor> topologicalSort(Set<Descriptor> toSort) {

    Set<Descriptor> discovered;

    if( determinismDesired ) {
      // use an ordered set
      discovered = new TreeSet<Descriptor>(dComp);
    } else {
      // otherwise use a speedy hashset
      discovered = new HashSet<Descriptor>();
    }

    LinkedList<Descriptor> sorted = new LinkedList<Descriptor>();

    Iterator<Descriptor> itr = toSort.iterator();
    while( itr.hasNext() ) {
      Descriptor d = itr.next();

      if( !discovered.contains(d) ) {
        dfsVisit(d, toSort, sorted, discovered);
      }
    }

    return sorted;
  }

  // While we're doing DFS on call graph, remember
  // dependencies for efficient queuing of methods
  // during interprocedural analysis:
  //
  // a dependent of a method decriptor d for this analysis is:
  //  1) a method or task that invokes d
  //  2) in the descriptorsToAnalyze set
  protected void dfsVisit(Descriptor d,
                          Set       <Descriptor> toSort,
                          LinkedList<Descriptor> sorted,
                          Set       <Descriptor> discovered) {
    discovered.add(d);

    // only methods have callers, tasks never do
    if( d instanceof MethodDescriptor ) {

      MethodDescriptor md = (MethodDescriptor) d;

      // the call graph is not aware that we have a fabricated
      // analysis entry that calls the program source's entry
      if( md == mdSourceEntry ) {
        if( !discovered.contains(mdAnalysisEntry) ) {
          addDependent(mdSourceEntry,   // callee
                       mdAnalysisEntry  // caller
                       );
          dfsVisit(mdAnalysisEntry, toSort, sorted, discovered);
        }
      }

      // otherwise call graph guides DFS
      Iterator itr = callGraph.getCallerSet(md).iterator();
      while( itr.hasNext() ) {
        Descriptor dCaller = (Descriptor) itr.next();

        // only consider callers in the original set to analyze
        if( !toSort.contains(dCaller) ) {
          continue;
        }

        if( !discovered.contains(dCaller) ) {
          addDependent(md,      // callee
                       dCaller  // caller
                       );

          dfsVisit(dCaller, toSort, sorted, discovered);
        }
      }
    }

    // for leaf-nodes last now!
    sorted.addLast(d);
  }


  protected void enqueue(Descriptor d) {

    if( !descriptorsToVisitSet.contains(d) ) {

      if( state.DISJOINTDVISITSTACK ||
          state.DISJOINTDVISITSTACKEESONTOP
          ) {
        descriptorsToVisitStack.add(d);

      } else if( state.DISJOINTDVISITPQUE ) {
        Integer priority = mapDescriptorToPriority.get(d);
        descriptorsToVisitQ.add(new DescriptorQWrapper(priority,
                                                       d)
                                );
      }

      descriptorsToVisitSet.add(d);
    }
  }


  // a dependent of a method decriptor d for this analysis is:
  //  1) a method or task that invokes d
  //  2) in the descriptorsToAnalyze set
  protected void addDependent(Descriptor callee, Descriptor caller) {
    Set<Descriptor> deps = mapDescriptorToSetDependents.get(callee);
    if( deps == null ) {
      deps = new HashSet<Descriptor>();
    }
    deps.add(caller);
    mapDescriptorToSetDependents.put(callee, deps);
  }

  protected Set<Descriptor> getDependents(Descriptor callee) {
    Set<Descriptor> deps = mapDescriptorToSetDependents.get(callee);
    if( deps == null ) {
      deps = new HashSet<Descriptor>();
      mapDescriptorToSetDependents.put(callee, deps);
    }
    return deps;
  }


  public Hashtable<FlatCall, ReachGraph> getIHMcontributions(Descriptor d) {

    Hashtable<FlatCall, ReachGraph> heapsFromCallers =
      mapDescriptorToIHMcontributions.get(d);

    if( heapsFromCallers == null ) {
      heapsFromCallers = new Hashtable<FlatCall, ReachGraph>();
      mapDescriptorToIHMcontributions.put(d, heapsFromCallers);
    }

    return heapsFromCallers;
  }

  public ReachGraph getIHMcontribution(Descriptor d,
                                       FlatCall fc
                                       ) {
    Hashtable<FlatCall, ReachGraph> heapsFromCallers =
      getIHMcontributions(d);

    if( !heapsFromCallers.containsKey(fc) ) {
      return null;
    }

    return heapsFromCallers.get(fc);
  }


  public void addIHMcontribution(Descriptor d,
                                 FlatCall fc,
                                 ReachGraph rg
                                 ) {
    Hashtable<FlatCall, ReachGraph> heapsFromCallers =
      getIHMcontributions(d);

    // ensure inputs to initial contexts increase monotonically
    ReachGraph merged = new ReachGraph();
    merged.merge( rg );
    merged.merge( heapsFromCallers.get( fc ) );

    heapsFromCallers.put( fc, merged );
    
  }


  private AllocSite createParameterAllocSite(ReachGraph rg,
                                             TempDescriptor tempDesc,
                                             boolean flagRegions
                                             ) {

    FlatNew flatNew;
    if( flagRegions ) {
      flatNew = new FlatNew(tempDesc.getType(),  // type
                            tempDesc,            // param temp
                            false,               // global alloc?
                            "param"+tempDesc     // disjoint site ID string
                            );
    } else {
      flatNew = new FlatNew(tempDesc.getType(),  // type
                            tempDesc,            // param temp
                            false,               // global alloc?
                            null                 // disjoint site ID string
                            );
    }

    // create allocation site
    AllocSite as = AllocSite.factory(allocationDepth,
                                     flatNew,
                                     flatNew.getDisjointId(),
                                     false
                                     );
    for (int i = 0; i < allocationDepth; ++i) {
      Integer id = generateUniqueHeapRegionNodeID();
      as.setIthOldest(i, id);
      mapHrnIdToAllocSite.put(id, as);
    }
    // the oldest node is a summary node
    as.setSummary(generateUniqueHeapRegionNodeID() );

    rg.age(as);

    return as;

  }

  private Set<FieldDescriptor> getFieldSetTobeAnalyzed(TypeDescriptor typeDesc) {

    Set<FieldDescriptor> fieldSet=new HashSet<FieldDescriptor>();
    if(!typeDesc.isImmutable()) {
      ClassDescriptor classDesc = typeDesc.getClassDesc();
      for (Iterator it = classDesc.getFields(); it.hasNext(); ) {
        FieldDescriptor field = (FieldDescriptor) it.next();
        TypeDescriptor fieldType = field.getType();
        if (shouldAnalysisTrack(fieldType)) {
          fieldSet.add(field);
        }
      }
    }
    return fieldSet;

  }

  private HeapRegionNode createMultiDeimensionalArrayHRN(ReachGraph rg, AllocSite alloc, HeapRegionNode srcHRN, FieldDescriptor fd, Hashtable<HeapRegionNode, HeapRegionNode> map, Hashtable<TypeDescriptor, HeapRegionNode> mapToExistingNode, ReachSet alpha) {

    int dimCount=fd.getType().getArrayCount();
    HeapRegionNode prevNode=null;
    HeapRegionNode arrayEntryNode=null;
    for(int i=dimCount; i>0; i--) {
      TypeDescriptor typeDesc=fd.getType().dereference();          //hack to get instance of type desc
      typeDesc.setArrayCount(i);
      TempDescriptor tempDesc=new TempDescriptor(typeDesc.getSymbol(),typeDesc);
      HeapRegionNode hrnSummary;
      if(!mapToExistingNode.containsKey(typeDesc)) {
        AllocSite as;
        if(i==dimCount) {
          as = alloc;
        } else {
          as = createParameterAllocSite(rg, tempDesc, false);
        }
        // make a new reference to allocated node
        hrnSummary =
          rg.createNewHeapRegionNode(as.getSummary(),                       // id or null to generate a new one
                                     false,                       // single object?
                                     true,                       // summary?
                                     false,                       // out-of-context?
                                     as.getType(),                       // type
                                     as,                       // allocation site
                                     alpha,                       // inherent reach
                                     alpha,                       // current reach
                                     ExistPredSet.factory(rg.predTrue),                       // predicates
                                     tempDesc.toString()                       // description
                                     );
        rg.id2hrn.put(as.getSummary(),hrnSummary);

        mapToExistingNode.put(typeDesc, hrnSummary);
      } else {
        hrnSummary=mapToExistingNode.get(typeDesc);
      }

      if(prevNode==null) {
        // make a new reference between new summary node and source
        RefEdge edgeToSummary = new RefEdge(srcHRN,       // source
                                            hrnSummary,             // dest
                                            typeDesc,             // type
                                            fd.getSymbol(),             // field name
                                            alpha,             // beta
                                            ExistPredSet.factory(rg.predTrue),       // predicates
                                            null
                                            );

        rg.addRefEdge(srcHRN, hrnSummary, edgeToSummary);
        prevNode=hrnSummary;
        arrayEntryNode=hrnSummary;
      } else {
        // make a new reference between summary nodes of array
        RefEdge edgeToSummary = new RefEdge(prevNode,             // source
                                            hrnSummary,             // dest
                                            typeDesc,             // type
                                            arrayElementFieldName,             // field name
                                            alpha,             // beta
                                            ExistPredSet.factory(rg.predTrue),             // predicates
                                            null
                                            );

        rg.addRefEdge(prevNode, hrnSummary, edgeToSummary);
        prevNode=hrnSummary;
      }

    }

    // create a new obj node if obj has at least one non-primitive field
    TypeDescriptor type=fd.getType();
    if(getFieldSetTobeAnalyzed(type).size()>0) {
      TypeDescriptor typeDesc=type.dereference();
      typeDesc.setArrayCount(0);
      if(!mapToExistingNode.containsKey(typeDesc)) {
        TempDescriptor tempDesc=new TempDescriptor(type.getSymbol(),typeDesc);
        AllocSite as = createParameterAllocSite(rg, tempDesc, false);
        // make a new reference to allocated node
        HeapRegionNode hrnSummary =
          rg.createNewHeapRegionNode(as.getSummary(),                       // id or null to generate a new one
                                     false,                       // single object?
                                     true,                       // summary?
                                     false,                       // out-of-context?
                                     typeDesc,                       // type
                                     as,                       // allocation site
                                     alpha,                       // inherent reach
                                     alpha,                       // current reach
                                     ExistPredSet.factory(rg.predTrue),                       // predicates
                                     tempDesc.toString()                       // description
                                     );
        rg.id2hrn.put(as.getSummary(),hrnSummary);
        mapToExistingNode.put(typeDesc, hrnSummary);
        RefEdge edgeToSummary = new RefEdge(prevNode,             // source
                                            hrnSummary, // dest
                                            typeDesc, // type
                                            arrayElementFieldName, // field name
                                            alpha, // beta
                                            ExistPredSet.factory(rg.predTrue),             // predicates
                                            null
                                            );
        rg.addRefEdge(prevNode, hrnSummary, edgeToSummary);
        prevNode=hrnSummary;
      } else {
        HeapRegionNode hrnSummary=mapToExistingNode.get(typeDesc);
        if(prevNode.getReferenceTo(hrnSummary, typeDesc, arrayElementFieldName)==null) {
          RefEdge edgeToSummary = new RefEdge(prevNode,               // source
                                              hrnSummary, // dest
                                              typeDesc, // type
                                              arrayElementFieldName, // field name
                                              alpha, // beta
                                              ExistPredSet.factory(rg.predTrue),               // predicates
                                              null
                                              );
          rg.addRefEdge(prevNode, hrnSummary, edgeToSummary);
        }
        prevNode=hrnSummary;
      }
    }

    map.put(arrayEntryNode, prevNode);
    return arrayEntryNode;
  }

  private ReachGraph createInitialTaskReachGraph(FlatMethod fm) {
    ReachGraph rg = new ReachGraph();
    TaskDescriptor taskDesc = fm.getTask();

    for (int idx = 0; idx < taskDesc.numParameters(); idx++) {
      Descriptor paramDesc = taskDesc.getParameter(idx);
      TypeDescriptor paramTypeDesc = taskDesc.getParamType(idx);

      // setup data structure
      Set<HashMap<HeapRegionNode, FieldDescriptor>> workSet =
        new HashSet<HashMap<HeapRegionNode, FieldDescriptor>>();
      Hashtable<TypeDescriptor, HeapRegionNode> mapTypeToExistingSummaryNode =
        new Hashtable<TypeDescriptor, HeapRegionNode>();
      Hashtable<HeapRegionNode, HeapRegionNode> mapToFirstDimensionArrayNode =
        new Hashtable<HeapRegionNode, HeapRegionNode>();
      Set<String> doneSet = new HashSet<String>();

      TempDescriptor tempDesc = fm.getParameter(idx);

      AllocSite as = createParameterAllocSite(rg, tempDesc, true);
      VariableNode lnX = rg.getVariableNodeFromTemp(tempDesc);
      Integer idNewest = as.getIthOldest(0);
      HeapRegionNode hrnNewest = rg.id2hrn.get(idNewest);

      // make a new reference to allocated node
      RefEdge edgeNew = new RefEdge(lnX,   // source
                                    hrnNewest,   // dest
                                    taskDesc.getParamType(idx),   // type
                                    null,   // field name
                                    hrnNewest.getAlpha(),   // beta
                                    ExistPredSet.factory(rg.predTrue),   // predicates
                                    null
                                    );
      rg.addRefEdge(lnX, hrnNewest, edgeNew);

      // set-up a work set for class field
      ClassDescriptor classDesc = paramTypeDesc.getClassDesc();
      for (Iterator it = classDesc.getFields(); it.hasNext(); ) {
        FieldDescriptor fd = (FieldDescriptor) it.next();
        TypeDescriptor fieldType = fd.getType();
        if (shouldAnalysisTrack(fieldType)) {
          HashMap<HeapRegionNode, FieldDescriptor> newMap = new HashMap<HeapRegionNode, FieldDescriptor>();
          newMap.put(hrnNewest, fd);
          workSet.add(newMap);
        }
      }

      int uniqueIdentifier = 0;
      while (!workSet.isEmpty()) {
        HashMap<HeapRegionNode, FieldDescriptor> map = workSet
                                                       .iterator().next();
        workSet.remove(map);

        Set<HeapRegionNode> key = map.keySet();
        HeapRegionNode srcHRN = key.iterator().next();
        FieldDescriptor fd = map.get(srcHRN);
        TypeDescriptor type = fd.getType();
        String doneSetIdentifier = srcHRN.getIDString() + "_" + fd;

        if (!doneSet.contains(doneSetIdentifier)) {
          doneSet.add(doneSetIdentifier);
          if (!mapTypeToExistingSummaryNode.containsKey(type)) {
            // create new summary Node
            TempDescriptor td = new TempDescriptor("temp"
                                                   + uniqueIdentifier, type);

            AllocSite allocSite;
            if(type.equals(paramTypeDesc)) {
              //corresponding allocsite has already been created for a parameter variable.
              allocSite=as;
            } else {
              allocSite = createParameterAllocSite(rg, td, false);
            }
            String strDesc = allocSite.toStringForDOT()
                             + "\\nsummary";
            TypeDescriptor allocType=allocSite.getType();

            HeapRegionNode hrnSummary;
            if(allocType.isArray() && allocType.getArrayCount()>0) {
              hrnSummary=createMultiDeimensionalArrayHRN(rg,allocSite,srcHRN,fd,mapToFirstDimensionArrayNode,mapTypeToExistingSummaryNode,hrnNewest.getAlpha());
            } else {
              hrnSummary =
                rg.createNewHeapRegionNode(allocSite.getSummary(),                         // id or null to generate a new one
                                           false,                         // single object?
                                           true,                         // summary?
                                           false,                         // out-of-context?
                                           allocSite.getType(),                         // type
                                           allocSite,                         // allocation site
                                           hrnNewest.getAlpha(),                         // inherent reach
                                           hrnNewest.getAlpha(),                         // current reach
                                           ExistPredSet.factory(rg.predTrue),                         // predicates
                                           strDesc                         // description
                                           );
              rg.id2hrn.put(allocSite.getSummary(),hrnSummary);

              // make a new reference to summary node
              RefEdge edgeToSummary = new RefEdge(srcHRN,       // source
                                                  hrnSummary,       // dest
                                                  type,       // type
                                                  fd.getSymbol(),       // field name
                                                  hrnNewest.getAlpha(),       // beta
                                                  ExistPredSet.factory(rg.predTrue),       // predicates
                                                  null
                                                  );

              rg.addRefEdge(srcHRN, hrnSummary, edgeToSummary);
            }
            uniqueIdentifier++;

            mapTypeToExistingSummaryNode.put(type, hrnSummary);

            // set-up a work set for  fields of the class
            Set<FieldDescriptor> fieldTobeAnalyzed=getFieldSetTobeAnalyzed(type);
            for (Iterator iterator = fieldTobeAnalyzed.iterator(); iterator
                 .hasNext(); ) {
              FieldDescriptor fieldDescriptor = (FieldDescriptor) iterator
                                                .next();
              HeapRegionNode newDstHRN;
              if(mapToFirstDimensionArrayNode.containsKey(hrnSummary)) {
                //related heap region node is already exsited.
                newDstHRN=mapToFirstDimensionArrayNode.get(hrnSummary);
              } else {
                newDstHRN=hrnSummary;
              }
              doneSetIdentifier = newDstHRN.getIDString() + "_" + fieldDescriptor;
              if(!doneSet.contains(doneSetIdentifier)) {
                // add new work item
                HashMap<HeapRegionNode, FieldDescriptor> newMap =
                  new HashMap<HeapRegionNode, FieldDescriptor>();
                newMap.put(newDstHRN, fieldDescriptor);
                workSet.add(newMap);
              }
            }

          } else {
            // if there exists corresponding summary node
            HeapRegionNode hrnDst=mapTypeToExistingSummaryNode.get(type);

            RefEdge edgeToSummary = new RefEdge(srcHRN,         // source
                                                hrnDst,         // dest
                                                fd.getType(),         // type
                                                fd.getSymbol(),         // field name
                                                srcHRN.getAlpha(),         // beta
                                                ExistPredSet.factory(rg.predTrue),         // predicates
                                                null
                                                );
            rg.addRefEdge(srcHRN, hrnDst, edgeToSummary);

          }
        }
      }
    }

    return rg;
  }

// return all allocation sites in the method (there is one allocation
// site per FlatNew node in a method)
  private HashSet<AllocSite> getAllocationSiteSet(Descriptor d) {
    if( !mapDescriptorToAllocSiteSet.containsKey(d) ) {
      buildAllocationSiteSet(d);
    }

    return mapDescriptorToAllocSiteSet.get(d);

  }

  private void buildAllocationSiteSet(Descriptor d) {
    HashSet<AllocSite> s = new HashSet<AllocSite>();

    FlatMethod fm;
    if( d instanceof MethodDescriptor ) {
      fm = state.getMethodFlat( (MethodDescriptor) d);
    } else {
      assert d instanceof TaskDescriptor;
      fm = state.getMethodFlat( (TaskDescriptor) d);
    }
    pm.analyzeMethod(fm);

    // visit every node in this FlatMethod's IR graph
    // and make a set of the allocation sites from the
    // FlatNew node's visited
    HashSet<FlatNode> visited = new HashSet<FlatNode>();
    HashSet<FlatNode> toVisit = new HashSet<FlatNode>();
    toVisit.add(fm);

    while( !toVisit.isEmpty() ) {
      FlatNode n = toVisit.iterator().next();

      if( n instanceof FlatNew ) {
        s.add(getAllocSiteFromFlatNewPRIVATE( (FlatNew) n) );
      }

      toVisit.remove(n);
      visited.add(n);

      for( int i = 0; i < pm.numNext(n); ++i ) {
        FlatNode child = pm.getNext(n, i);
        if( !visited.contains(child) ) {
          toVisit.add(child);
        }
      }
    }

    mapDescriptorToAllocSiteSet.put(d, s);
  }

  private HashSet<AllocSite> getFlaggedAllocationSites(Descriptor dIn) {

    HashSet<AllocSite> out = new HashSet<AllocSite>();
    HashSet<Descriptor> toVisit = new HashSet<Descriptor>();
    HashSet<Descriptor> visited = new HashSet<Descriptor>();

    toVisit.add(dIn);

    while (!toVisit.isEmpty()) {
      Descriptor d = toVisit.iterator().next();
      toVisit.remove(d);
      visited.add(d);

      HashSet<AllocSite> asSet = getAllocationSiteSet(d);
      Iterator asItr = asSet.iterator();
      while (asItr.hasNext()) {
        AllocSite as = (AllocSite) asItr.next();
        if (as.getDisjointAnalysisId() != null) {
          out.add(as);
        }
      }

      // enqueue callees of this method to be searched for
      // allocation sites also
      Set callees = callGraph.getCalleeSet(d);
      if (callees != null) {
        Iterator methItr = callees.iterator();
        while (methItr.hasNext()) {
          MethodDescriptor md = (MethodDescriptor) methItr.next();

          if (!visited.contains(md)) {
            toVisit.add(md);
          }
        }
      }
    }

    return out;
  }


  private HashSet<AllocSite>
  getFlaggedAllocationSitesReachableFromTaskPRIVATE(TaskDescriptor td) {

    HashSet<AllocSite> asSetTotal = new HashSet<AllocSite>();
    HashSet<Descriptor>     toVisit    = new HashSet<Descriptor>();
    HashSet<Descriptor>     visited    = new HashSet<Descriptor>();

    toVisit.add(td);

    // traverse this task and all methods reachable from this task
    while( !toVisit.isEmpty() ) {
      Descriptor d = toVisit.iterator().next();
      toVisit.remove(d);
      visited.add(d);

      HashSet<AllocSite> asSet = getAllocationSiteSet(d);
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

  public Set<Descriptor> getDescriptorsToAnalyze() {
    return descriptorsToAnalyze;
  }

  public EffectsAnalysis getEffectsAnalysis() {
    return effectsAnalysis;
  }

  public ReachGraph getReachGraph(Descriptor d) {
    return mapDescriptorToCompleteReachGraph.get(d);
  }

  public ReachGraph getEnterReachGraph(FlatNode fn) {
    return fn2rgAtEnter.get(fn);
  }



  protected class DebugCallSiteData {
    public boolean debugCallSite;
    public boolean didOneDebug;
    public boolean writeDebugDOTs;
    public boolean stopAfter;

    public DebugCallSiteData() {
      debugCallSite  = false;
      didOneDebug    = false;
      writeDebugDOTs = false;
      stopAfter      = false;
    }
  }

  protected void decideDebugCallSite( DebugCallSiteData dcsd,
                                      Descriptor        taskOrMethodCaller,
                                      MethodDescriptor  mdCallee ) {
    
    // all this jimma jamma to debug call sites is WELL WORTH the
    // effort, so so so many bugs or buggy info appears through call
    // sites

    if( state.DISJOINTDEBUGCALLEE == null ||
        state.DISJOINTDEBUGCALLER == null ) {
      return;
    }


    boolean debugCalleeMatches = false;
    boolean debugCallerMatches = false;
        
    ClassDescriptor cdCallee = mdCallee.getClassDesc();
    if( cdCallee != null ) {
      debugCalleeMatches = 
        state.DISJOINTDEBUGCALLEE.equals( cdCallee.getSymbol()+
                                          "."+
                                          mdCallee.getSymbol()
                                          );
    }


    if( taskOrMethodCaller instanceof MethodDescriptor ) {
      ClassDescriptor cdCaller = ((MethodDescriptor)taskOrMethodCaller).getClassDesc();
      if( cdCaller != null ) {
        debugCallerMatches = 
          state.DISJOINTDEBUGCALLER.equals( cdCaller.getSymbol()+
                                            "."+
                                            taskOrMethodCaller.getSymbol()
                                            );
      }        
    } else {
      // for bristlecone style tasks
      debugCallerMatches =
        state.DISJOINTDEBUGCALLER.equals( taskOrMethodCaller.getSymbol() );
    }


    dcsd.debugCallSite = debugCalleeMatches && debugCallerMatches;


    dcsd.writeDebugDOTs = 
      
      dcsd.debugCallSite &&

      (ReachGraph.debugCallSiteVisitCounter >=
       ReachGraph.debugCallSiteVisitStartCapture)  &&
         
      (ReachGraph.debugCallSiteVisitCounter <
       ReachGraph.debugCallSiteVisitStartCapture +
       ReachGraph.debugCallSiteNumVisitsToCapture);
         


    if( dcsd.debugCallSite ) {
      dcsd.didOneDebug = true;
    }
  }

  protected void statusDebugCallSite( DebugCallSiteData dcsd ) {

    dcsd.writeDebugDOTs = false;
    dcsd.stopAfter      = false;

    if( dcsd.didOneDebug ) {
      System.out.println("    $$$ Debug call site visit "+
                         ReachGraph.debugCallSiteVisitCounter+
                         " $$$"
                         );
      if(
         (ReachGraph.debugCallSiteVisitCounter >=
          ReachGraph.debugCallSiteVisitStartCapture)  &&
         
         (ReachGraph.debugCallSiteVisitCounter <
          ReachGraph.debugCallSiteVisitStartCapture +
          ReachGraph.debugCallSiteNumVisitsToCapture)
         ) {
        dcsd.writeDebugDOTs = true;
        System.out.println("      $$$ Capturing this call site visit $$$");
        if( ReachGraph.debugCallSiteStopAfter &&
            (ReachGraph.debugCallSiteVisitCounter ==
             ReachGraph.debugCallSiteVisitStartCapture +
             ReachGraph.debugCallSiteNumVisitsToCapture - 1)
            ) {
          dcsd.stopAfter = true;
        }
      }

      ++ReachGraph.debugCallSiteVisitCounter;
    }

    if( dcsd.stopAfter ) {
      System.out.println("$$$ Exiting after requested captures of call site. $$$");
      System.exit(-1);
    }
  }
  




  // get successive captures of the analysis state, use compiler
  // flags to control
  boolean takeDebugSnapshots = false;
  String descSymbolDebug    = null;
  boolean stopAfterCapture   = false;
  int snapVisitCounter   = 0;
  int snapNodeCounter    = 0;
  int visitStartCapture  = 0;
  int numVisitsToCapture = 0;


  void debugSnapshot(ReachGraph rg, FlatNode fn, boolean in) {
    if( snapVisitCounter > visitStartCapture + numVisitsToCapture ) {
      return;
    }

    if( in ) {

    }

    if( snapVisitCounter >= visitStartCapture ) {
      System.out.println("    @@@ snapping visit="+snapVisitCounter+
                         ", node="+snapNodeCounter+
                         " @@@");
      String graphName;
      if( in ) {
        graphName = String.format("snap%03d_%04din",
                                  snapVisitCounter,
                                  snapNodeCounter);
      } else {
        graphName = String.format("snap%03d_%04dout",
                                  snapVisitCounter,
                                  snapNodeCounter);
      }
      if( fn != null ) {
        graphName = graphName + fn;
      }
      rg.writeGraph(graphName,
                    true,    // write labels (variables)
                    true,    // selectively hide intermediate temp vars
                    true,    // prune unreachable heap regions
                    false,   // hide reachability
                    true,   // hide subset reachability states
                    true,    // hide predicates
                    true);   // hide edge taints
    }
  }




  public Set<Alloc> canPointToAt( TempDescriptor x,
                                  FlatNode programPoint ) {

    ReachGraph rgAtEnter = fn2rgAtEnter.get( programPoint );
    if( rgAtEnter == null ) {
      return null; 
    }

    return rgAtEnter.canPointTo( x );
  }
  

  public Hashtable< Alloc, Set<Alloc> > canPointToAt( TempDescriptor x,
                                                      FieldDescriptor f,
                                                      FlatNode programPoint ) {

    ReachGraph rgAtEnter = fn2rgAtEnter.get( programPoint );
    if( rgAtEnter == null ) {
      return null; 
    }
    
    return rgAtEnter.canPointTo( x, f.getSymbol(), f.getType() );
  }


  public Hashtable< Alloc, Set<Alloc> > canPointToAtElement( TempDescriptor x,
                                                             FlatNode programPoint ) {

    ReachGraph rgAtEnter = fn2rgAtEnter.get( programPoint );
    if( rgAtEnter == null ) {
      return null; 
    }

    assert x.getType() != null;
    assert x.getType().isArray();

    return rgAtEnter.canPointTo( x, arrayElementFieldName, x.getType().dereference() );
  }


  public Set<Alloc> canPointToAfter( TempDescriptor x,
                                     FlatNode programPoint ) {

    ReachGraph rgAtExit = fn2rgAtExit.get( programPoint );

    if( rgAtExit == null ) {
      return null; 
    }

    return rgAtExit.canPointTo( x );
  }


  public Hashtable< Alloc, Set<Alloc> > canPointToAfter( TempDescriptor x,
                                                         FieldDescriptor f,
                                                         FlatNode programPoint ) {

    ReachGraph rgAtExit = fn2rgAtExit.get( programPoint );
    if( rgAtExit == null ) {
      return null; 
    }
    
    return rgAtExit.canPointTo( x, f.getSymbol(), f.getType() );
  }


  public Hashtable< Alloc, Set<Alloc> > canPointToAfterElement( TempDescriptor x,
                                                                FlatNode programPoint ) {

    ReachGraph rgAtExit = fn2rgAtExit.get( programPoint );
    if( rgAtExit == null ) {
      return null; 
    }

    assert x.getType() != null;
    assert x.getType().isArray();

    return rgAtExit.canPointTo( x, arrayElementFieldName, x.getType().dereference() );
  }
}
