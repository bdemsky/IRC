package Analysis.MLP;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.Stack;
import java.util.Map.Entry;
import Analysis.CallGraph.CallGraph;
import Analysis.CallGraph.JavaCallGraph;
import Analysis.OwnershipAnalysis.AllocationSite;
import Analysis.OwnershipAnalysis.EffectsKey;
import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.LabelNode;
import Analysis.OwnershipAnalysis.MethodContext;
import Analysis.OwnershipAnalysis.MethodEffects;
import Analysis.OwnershipAnalysis.OwnershipAnalysis;
import Analysis.OwnershipAnalysis.OwnershipGraph;
import Analysis.OwnershipAnalysis.OwnershipNode;
import Analysis.OwnershipAnalysis.ParameterDecomposition;
import Analysis.OwnershipAnalysis.ReachabilitySet;
import Analysis.OwnershipAnalysis.ReferenceEdge;
import Analysis.OwnershipAnalysis.TokenTuple;
import Analysis.OwnershipAnalysis.TokenTupleSet;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.Flat.FKind;
import IR.Flat.FlatCall;
import IR.Flat.FlatCondBranch;
import IR.Flat.FlatEdge;
import IR.Flat.FlatElementNode;
import IR.Flat.FlatFieldNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNew;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.FlatReturnNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.FlatSESEExitNode;
import IR.Flat.FlatSetElementNode;
import IR.Flat.FlatSetFieldNode;
import IR.Flat.FlatWriteDynamicVarNode;
import IR.Flat.TempDescriptor;


public class MLPAnalysis {

  // data from the compiler
  private State             state;
  private TypeUtil          typeUtil;
  private CallGraph         callGraph;
  private OwnershipAnalysis ownAnalysis;


  // an implicit SESE is automatically spliced into
  // the IR graph around the C main before this analysis--it
  // is nothing special except that we can make assumptions
  // about it, such as the whole program ends when it ends
  private FlatSESEEnterNode mainSESE;

  // SESEs that are the root of an SESE tree belong to this
  // set--the main SESE is always a root, statically SESEs
  // inside methods are a root because we don't know how they
  // will fit into the runtime tree of SESEs
  private Set<FlatSESEEnterNode> rootSESEs;

  // simply a set of every reachable SESE in the program, not
  // including caller placeholder SESEs
  private Set<FlatSESEEnterNode> allSESEs;


  // A mapping of flat nodes to the stack of SESEs for that node, where
  // an SESE is the child of the SESE directly below it on the stack.
  // These stacks do not reflect the heirarchy over methods calls--whenever
  // there is an empty stack it means all variables are available.
  private Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks;

  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessRootView;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessVirtualReads;
  private Hashtable< FlatNode, VarSrcTokTable           > variableResults;
  private Hashtable< FlatNode, Set<TempDescriptor>      > notAvailableResults;
  private Hashtable< FlatNode, CodePlan                 > codePlans;

  private Hashtable< FlatSESEEnterNode, Set<TempDescriptor> > notAvailableIntoSESE;

  private Hashtable< FlatEdge, FlatWriteDynamicVarNode  > wdvNodesToSpliceIn;
  
  private Hashtable< MethodContext, HashSet<AllocationSite>> mapMethodContextToLiveInAllocationSiteSet;
  
  private Hashtable < FlatNode, ParentChildConflictsMap > conflictsResults;
  private Hashtable< FlatMethod, MethodSummary > methodSummaryResults;
  private OwnershipAnalysis ownAnalysisForSESEConflicts;
  private Hashtable <FlatNode, ConflictGraph> conflictGraphResults;
  
  // temporal data structures to track analysis progress.
  private MethodSummary currentMethodSummary;
  private HashSet<PreEffectsKey> preeffectsSet;
  private Hashtable<FlatNode, Boolean> isAfterChildSESEIndicatorMap;
  private Hashtable<FlatNode, SESESummary> seseSummaryMap;
  private Hashtable<ConflictGraph, HashSet<SESELock>> conflictGraphLockMap;
  static private int uniqueLockSetId = 0;

  public static int maxSESEage = -1;


  // use these methods in BuildCode to have access to analysis results
  public FlatSESEEnterNode getMainSESE() {
    return mainSESE;
  }

  public Set<FlatSESEEnterNode> getRootSESEs() {
    return rootSESEs;
  }

  public Set<FlatSESEEnterNode> getAllSESEs() {
    return allSESEs;
  }

  public int getMaxSESEage() {
    return maxSESEage;
  }

  // may be null
  public CodePlan getCodePlan( FlatNode fn ) {
    CodePlan cp = codePlans.get( fn );
    return cp;
  }


  public MLPAnalysis( State             state,
		      TypeUtil          tu,
		      CallGraph         callGraph,
		      OwnershipAnalysis ownAnalysis
		      ) {

    double timeStartAnalysis = (double) System.nanoTime();

    this.state       = state;
    this.typeUtil    = tu;
    this.callGraph   = callGraph;
    this.ownAnalysis = ownAnalysis;
    this.maxSESEage  = state.MLP_MAXSESEAGE;

    rootSESEs = new HashSet<FlatSESEEnterNode>();
    allSESEs  = new HashSet<FlatSESEEnterNode>();

    seseStacks           = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();    
    livenessRootView     = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    livenessVirtualReads = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    variableResults      = new Hashtable< FlatNode, VarSrcTokTable           >();
    notAvailableResults  = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    codePlans            = new Hashtable< FlatNode, CodePlan                 >();
    wdvNodesToSpliceIn   = new Hashtable< FlatEdge, FlatWriteDynamicVarNode  >();

    notAvailableIntoSESE = new Hashtable< FlatSESEEnterNode, Set<TempDescriptor> >();
    
    mapMethodContextToLiveInAllocationSiteSet = new Hashtable< MethodContext, HashSet<AllocationSite>>();
    
    conflictsResults = new Hashtable < FlatNode, ParentChildConflictsMap >();
    methodSummaryResults=new Hashtable<FlatMethod, MethodSummary>();
    conflictGraphResults=new Hashtable<FlatNode, ConflictGraph>();
    
    seseSummaryMap= new Hashtable<FlatNode, SESESummary>();
    isAfterChildSESEIndicatorMap= new Hashtable<FlatNode, Boolean>();
    conflictGraphLockMap=new Hashtable<ConflictGraph, HashSet<SESELock>>();

    FlatMethod fmMain = state.getMethodFlat( typeUtil.getMain() );

    mainSESE = (FlatSESEEnterNode) fmMain.getNext(0);    
    mainSESE.setfmEnclosing( fmMain );
    mainSESE.setmdEnclosing( fmMain.getMethod() );
    mainSESE.setcdEnclosing( fmMain.getMethod().getClassDesc() );


    // 1st pass
    // run analysis on each method that is actually called
    // reachability analysis already computed this so reuse
    Iterator<Descriptor> methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // find every SESE from methods that may be called
      // and organize them into roots and children
      buildForestForward( fm );
    }


    // 2nd pass, results are saved in FlatSESEEnterNode, so
    // intermediate results, for safety, are discarded
    Iterator<FlatSESEEnterNode> rootItr = rootSESEs.iterator();
    while( rootItr.hasNext() ) {
      FlatSESEEnterNode root = rootItr.next();
      livenessAnalysisBackward( root, 
                                true, 
                                null );
    }


    // 3rd pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // starting from roots do a forward, fixed-point
      // variable analysis for refinement and stalls
      variableAnalysisForward( fm );
    }

    // 4th pass, compute liveness contribution from
    // virtual reads discovered in variable pass
    rootItr = rootSESEs.iterator();
    while( rootItr.hasNext() ) {
      FlatSESEEnterNode root = rootItr.next();
      livenessAnalysisBackward( root, 
                                true, 
                                null );
    }


    /*
      SOMETHING IS WRONG WITH THIS, DON'T USE IT UNTIL IT CAN BE FIXED

    // 5th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // prune variable results in one traversal
      // by removing reference variables that are not live
      pruneVariableResultsWithLiveness( fm );
    }
    */


    // 6th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );
      
      // compute what is not available at every program
      // point, in a forward fixed-point pass
      notAvailableForward( fm );
    }

    if(state.METHODEFFECTS){
        // new pass, sese effects analysis
        methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    	JavaCallGraph javaCallGraph = new JavaCallGraph(state,tu);
        while( methItr.hasNext() ) {
          Descriptor d  = methItr.next();      
          FlatMethod fm = state.getMethodFlat( d );
          methodEffects(fm,javaCallGraph);
        }
        
        // Parent/child memory conflicts analysis
        seseConflictsForward(javaCallGraph);
        
        Set<MethodContext> keySet=mapMethodContextToLiveInAllocationSiteSet.keySet();
        for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
    		MethodContext methodContext = (MethodContext) iterator.next();
    		HashSet<AllocationSite> asSet=mapMethodContextToLiveInAllocationSiteSet.get(methodContext);
    		for (Iterator iterator2 = asSet.iterator(); iterator2.hasNext();) {
    			AllocationSite allocationSite = (AllocationSite) iterator2.next();
    		}
    	}
        
         // disjoint analysis with a set of flagged allocation sites of live-in variables & stall sites
    	try {
    	  ownAnalysisForSESEConflicts = new OwnershipAnalysis(state, 
                                                            tu, 
                                                            callGraph, 
                                                            ownAnalysis.liveness,
                                                            ownAnalysis.arrayReferencees,
                                                            state.OWNERSHIPALLOCDEPTH, false,
                                                            false, state.OWNERSHIPALIASFILE,
                                                            state.METHODEFFECTS,
                                                            mapMethodContextToLiveInAllocationSiteSet);
    		// debug
    		methItr = ownAnalysisForSESEConflicts.descriptorsToAnalyze.iterator();
    		while (methItr.hasNext()) {
    			Descriptor d = methItr.next();
    			FlatMethod fm = state.getMethodFlat(d);
    			debugFunction(ownAnalysisForSESEConflicts, fm);
    		}
    		//
    	} catch (IOException e) {
    		System.err.println(e);
    	}
    	
        //	postSESEConflictsForward(javaCallGraph);
    	// another pass for making graph
    	makeConflictGraph();
    	
    	// lock synthesis
    	synthesizeLocks();
    	/*
    	methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    	while (methItr.hasNext()) {
    		Descriptor d = methItr.next();
    		FlatMethod fm = state.getMethodFlat(d);
    		makeConflictGraph2(fm);
    	}
    	
    	Enumeration<FlatNode> keyEnum1=conflictGraphResults.keys();
		while (keyEnum1.hasMoreElements()) {
			FlatNode flatNode = (FlatNode) keyEnum1.nextElement();
			ConflictGraph conflictGraph=conflictGraphResults.get(flatNode);
			conflictGraph.analyzeConflicts();
			conflictGraphResults.put(flatNode, conflictGraph);
		}
		*/
    	
    	Enumeration<FlatNode> keyEnum=conflictGraphResults.keys();
    	while (keyEnum.hasMoreElements()) {
			FlatNode key = (FlatNode) keyEnum.nextElement();
			ConflictGraph cg=conflictGraphResults.get(key);
			try {
				if(cg.hasConflictEdge()){
					cg.writeGraph("ConflictGraphFor"+key, false);
				}
			} catch (IOException e) {
				System.out.println("Error writing");
				System.exit(0);
			}
		}
    }


    // 7th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // compute a plan for code injections
      codePlansForward( fm );
    }


    // splice new IR nodes into graph after all
    // analysis passes are complete
    Iterator spliceItr = wdvNodesToSpliceIn.entrySet().iterator();
    while( spliceItr.hasNext() ) {
      Map.Entry               me    = (Map.Entry)               spliceItr.next();
      FlatWriteDynamicVarNode fwdvn = (FlatWriteDynamicVarNode) me.getValue();
      fwdvn.spliceIntoIR();
    }


    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
    String treport = String.format( "The mlp analysis took %.3f sec.", dt );
    System.out.println( treport );

    if( state.MLPDEBUG ) {      
      try {
	writeReports( treport );
      } catch( IOException e ) {}
    }
  }


  private void buildForestForward( FlatMethod fm ) {
    
    // start from flat method top, visit every node in
    // method exactly once, find SESEs and remember
    // roots and child relationships
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );

    Set<FlatNode> visited = new HashSet<FlatNode>();    

    Stack<FlatSESEEnterNode> seseStackFirst = new Stack<FlatSESEEnterNode>();
    seseStacks.put( fm, seseStackFirst );

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      buildForest_nodeActions( fn, seseStack, fm );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );

	  // clone stack and send along each analysis path
	  seseStacks.put( nn, (Stack<FlatSESEEnterNode>)seseStack.clone() );
	}
      }
    }      
  }

  private void buildForest_nodeActions( FlatNode fn, 							   
					Stack<FlatSESEEnterNode> seseStack,
					FlatMethod fm ) {
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      if( !fsen.getIsCallerSESEplaceholder() ) {
	allSESEs.add( fsen );
      }

      fsen.setfmEnclosing( fm );
      fsen.setmdEnclosing( fm.getMethod() );
      fsen.setcdEnclosing( fm.getMethod().getClassDesc() );

      if( seseStack.empty() ) {
        rootSESEs.add( fsen );
        fsen.setParent( null );
      } else {
	seseStack.peek().addChild( fsen );
	fsen.setParent( seseStack.peek() );
      }

      seseStack.push( fsen );
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
      assert !seseStack.empty();
      FlatSESEEnterNode fsen = seseStack.pop();
    } break;

    case FKind.FlatReturnNode: {
      FlatReturnNode frn = (FlatReturnNode) fn;
      if( !seseStack.empty() &&
	  !seseStack.peek().getIsCallerSESEplaceholder() 
	) {
	throw new Error( "Error: return statement enclosed within SESE "+
			 seseStack.peek().getPrettyIdentifier() );
      }
    } break;
      
    }
  }


  private void livenessAnalysisBackward( FlatSESEEnterNode fsen, 
                                         boolean toplevel, 
                                         Hashtable< FlatSESEExitNode, Set<TempDescriptor> > liveout ) {

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped 
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();

    if( toplevel ) {
      flatNodesToVisit.add( fsen.getfmEnclosing().getFlatExit() );
    } else {
      flatNodesToVisit.add( fsen.getFlatExit() );
    }

    Hashtable<FlatNode, Set<TempDescriptor>> livenessResults = 
      new Hashtable< FlatNode, Set<TempDescriptor> >();

    if( toplevel ) {
      liveout = new Hashtable< FlatSESEExitNode, Set<TempDescriptor> >();
    }

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      
      
      Set<TempDescriptor> prev = livenessResults.get( fn );

      // merge sets from control flow joins
      Set<TempDescriptor> u = new HashSet<TempDescriptor>();
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );
        Set<TempDescriptor> s = livenessResults.get( nn );
        if( s != null ) {
          u.addAll( s );
        }
      }
      
      Set<TempDescriptor> curr = liveness_nodeActions( fn, u, fsen, toplevel, liveout);

      // if a new result, schedule backward nodes for analysis
      if( !curr.equals( prev ) ) {
	livenessResults.put( fn, curr );

	// don't flow backwards past current SESE enter
	if( !fn.equals( fsen ) ) {
	  for( int i = 0; i < fn.numPrev(); i++ ) {
	    FlatNode nn = fn.getPrev( i );
	    flatNodesToVisit.add( nn );
	  }
	}
      }
    }
    
    Set<TempDescriptor> s = livenessResults.get( fsen );
    if( s != null ) {
      fsen.addInVarSet( s );
    }
    
    // remember liveness per node from the root view as the
    // global liveness of variables for later passes to use
    if( toplevel ) {
      livenessRootView.putAll( livenessResults );
    }

    // post-order traversal, so do children first
    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      livenessAnalysisBackward( fsenChild, false, liveout );
    }
  }

  private Set<TempDescriptor> liveness_nodeActions( FlatNode fn, 
                                                    Set<TempDescriptor> liveIn,
                                                    FlatSESEEnterNode currentSESE,
						    boolean toplevel,
						    Hashtable< FlatSESEExitNode, Set<TempDescriptor> > liveout 
						  ) {
    switch( fn.kind() ) {
      
    case FKind.FlatSESEExitNode:
      if( toplevel ) {
	FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
	if( !liveout.containsKey( fsexn ) ) {
	  liveout.put( fsexn, new HashSet<TempDescriptor>() );
	}
	liveout.get( fsexn ).addAll( liveIn );
      }
      // no break, sese exits should also execute default actions
      
    default: {
      // handle effects of statement in reverse, writes then reads
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
	liveIn.remove( writeTemps[i] );
	
	if( !toplevel ) {
	  FlatSESEExitNode fsexn = currentSESE.getFlatExit();
	  Set<TempDescriptor> livetemps = liveout.get( fsexn );
	  if( livetemps != null &&
	      livetemps.contains( writeTemps[i] ) ) {
	    // write to a live out temp...
	    // need to put in SESE liveout set
	    currentSESE.addOutVar( writeTemps[i] );
	  }	
	}
      }

      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; ++i ) {
	liveIn.add( readTemps[i] );
      }
      
      Set<TempDescriptor> virtualReadTemps = livenessVirtualReads.get( fn );
      if( virtualReadTemps != null ) {
	liveIn.addAll( virtualReadTemps );
      }     
      
    } break;

    } // end switch

    return liveIn;
  }


  private void variableAnalysisForward( FlatMethod fm ) {
    
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );	 

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      VarSrcTokTable prev = variableResults.get( fn );

      // merge sets from control flow joins
      VarSrcTokTable curr = new VarSrcTokTable();
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );		
	VarSrcTokTable incoming = variableResults.get( nn );
	curr.merge( incoming );
      }

      if( !seseStack.empty() ) {
	variable_nodeActions( fn, curr, seseStack.peek() );
      }

      // if a new result, schedule forward nodes for analysis
      if( !curr.equals( prev ) ) {       
	variableResults.put( fn, curr );

	for( int i = 0; i < fn.numNext(); i++ ) {
	  FlatNode nn = fn.getNext( i );	 
	  flatNodesToVisit.add( nn );	 
	}
      }
    }
  }

  private void variable_nodeActions( FlatNode fn, 
				     VarSrcTokTable vstTable,
				     FlatSESEEnterNode currentSESE ) {
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      assert fsen.equals( currentSESE );

      vstTable.age( currentSESE );
      vstTable.assertConsistency();
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode  fsexn = (FlatSESEExitNode)  fn;
      FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      assert currentSESE.getChildren().contains( fsen );

      // remap all of this child's children tokens to be
      // from this child as the child exits
      vstTable.remapChildTokens( fsen );
      
      // liveness virtual reads are things that might be 
      // written by an SESE and should be added to the in-set
      // anything virtually read by this SESE should be pruned
      // of parent or sibling sources
      Set<TempDescriptor> liveVars         = livenessRootView.get( fn );
      Set<TempDescriptor> fsenVirtReads    = vstTable.calcVirtReadsAndPruneParentAndSiblingTokens( fsen, liveVars );
      Set<TempDescriptor> fsenVirtReadsOld = livenessVirtualReads.get( fn );
      if( fsenVirtReadsOld != null ) {
        fsenVirtReads.addAll( fsenVirtReadsOld );
      }
      livenessVirtualReads.put( fn, fsenVirtReads );


      // then all child out-set tokens are guaranteed
      // to be filled in, so clobber those entries with
      // the latest, clean sources
      Iterator<TempDescriptor> outVarItr = fsen.getOutVarSet().iterator();
      while( outVarItr.hasNext() ) {
        TempDescriptor outVar = outVarItr.next();
        HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
        ts.add( outVar );
        VariableSourceToken vst = 
	  new VariableSourceToken( ts,
				   fsen,
				   new Integer( 0 ),
				   outVar
				   );
        vstTable.remove( outVar );
        vstTable.add( vst );
      }
      vstTable.assertConsistency();

    } break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	TempDescriptor lhs = fon.getDest();
	TempDescriptor rhs = fon.getLeft();        

	vstTable.remove( lhs );

        Set<VariableSourceToken> forAddition = new HashSet<VariableSourceToken>();

	Iterator<VariableSourceToken> itr = vstTable.get( rhs ).iterator();
	while( itr.hasNext() ) {
	  VariableSourceToken vst = itr.next();

          HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
          ts.add( lhs );

	  if( currentSESE.getChildren().contains( vst.getSESE() ) ) {
	    // if the source comes from a child, copy it over
	    forAddition.add( new VariableSourceToken( ts,
						      vst.getSESE(),
						      vst.getAge(),
						      vst.getAddrVar()
						      )
			     );
	  } else {
	    // otherwise, stamp it as us as the source
	    forAddition.add( new VariableSourceToken( ts,
						      currentSESE,
						      new Integer( 0 ),
						      lhs
						      )
			     );
	  }
	}

        vstTable.addAll( forAddition );

	// only break if this is an ASSIGN op node,
	// otherwise fall through to default case
	vstTable.assertConsistency();
	break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {
      TempDescriptor [] writeTemps = fn.writesTemps();
      if( writeTemps.length > 0 ) {


        // for now, when writeTemps > 1, make sure
        // its a call node, programmer enforce only
        // doing stuff like calling a print routine
	//assert writeTemps.length == 1;
        if( writeTemps.length > 1 ) {
          assert fn.kind() == FKind.FlatCall ||
                 fn.kind() == FKind.FlatMethod;
          break;
        }

	vstTable.remove( writeTemps[0] );

        HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
        ts.add( writeTemps[0] );

	vstTable.add( new VariableSourceToken( ts,
					       currentSESE,
					       new Integer( 0 ),
					       writeTemps[0]
					     )
		      );
      }      

      vstTable.assertConsistency();
    } break;

    } // end switch
  }


  private void pruneVariableResultsWithLiveness( FlatMethod fm ) {
    
    // start from flat method top, visit every node in
    // method exactly once
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );

    Set<FlatNode> visited = new HashSet<FlatNode>();    

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      Set<TempDescriptor> rootLiveSet = livenessRootView.get( fn );
      VarSrcTokTable      vstTable    = variableResults.get( fn );
      
      vstTable.pruneByLiveness( rootLiveSet );
      
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );
	}
      }
    }
  }


  private void notAvailableForward( FlatMethod fm ) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );	 

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      Set<TempDescriptor> prev = notAvailableResults.get( fn );

      Set<TempDescriptor> curr = new HashSet<TempDescriptor>();      
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );       
	Set<TempDescriptor> notAvailIn = notAvailableResults.get( nn );
        if( notAvailIn != null ) {
          curr.addAll( notAvailIn );
        }
      }
      
      if( !seseStack.empty() ) {
	notAvailable_nodeActions( fn, curr, seseStack.peek() );     
      }

      // if a new result, schedule forward nodes for analysis
      if( !curr.equals( prev ) ) {
	notAvailableResults.put( fn, curr );

	for( int i = 0; i < fn.numNext(); i++ ) {
	  FlatNode nn = fn.getNext( i );	 
	  flatNodesToVisit.add( nn );	 
	}
      }
    }
  }

  private void notAvailable_nodeActions( FlatNode fn, 
					 Set<TempDescriptor> notAvailSet,
					 FlatSESEEnterNode currentSESE ) {

    // any temps that are removed from the not available set
    // at this node should be marked in this node's code plan
    // as temps to be grabbed at runtime!

    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      assert fsen.equals( currentSESE );

      // keep a copy of what's not available into the SESE
      // and restore it at the matching exit node
      Set<TempDescriptor> notAvailCopy = new HashSet<TempDescriptor>();
      Iterator<TempDescriptor> tdItr = notAvailSet.iterator();
      while( tdItr.hasNext() ) {
        notAvailCopy.add( tdItr.next() );
      }
      notAvailableIntoSESE.put( fsen, notAvailCopy );

      notAvailSet.clear();
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode  fsexn = (FlatSESEExitNode)  fn;
      FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      assert currentSESE.getChildren().contains( fsen );

      notAvailSet.addAll( fsen.getOutVarSet() );

      Set<TempDescriptor> notAvailIn = notAvailableIntoSESE.get( fsen );
      assert notAvailIn != null;
      notAvailSet.addAll( notAvailIn );

    } break;

    case FKind.FlatMethod: {
      notAvailSet.clear();
    }

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	TempDescriptor lhs = fon.getDest();
	TempDescriptor rhs = fon.getLeft();

	// copy makes lhs same availability as rhs
	if( notAvailSet.contains( rhs ) ) {
	  notAvailSet.add( lhs );
	} else {
	  notAvailSet.remove( lhs );
	}

	// only break if this is an ASSIGN op node,
	// otherwise fall through to default case
	break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; i++ ) {
        TempDescriptor wTemp = writeTemps[i];
        notAvailSet.remove( wTemp );
      }
      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; i++ ) {
        TempDescriptor rTemp = readTemps[i];
        notAvailSet.remove( rTemp );

	// if this variable has exactly one source, potentially
	// get other things from this source as well
	VarSrcTokTable vstTable = variableResults.get( fn );

        VSTWrapper vstIfStatic = new VSTWrapper();
	Integer srcType = 
	  vstTable.getRefVarSrcType( rTemp, 
				     currentSESE,
                                     vstIfStatic
                                     );

	if( srcType.equals( VarSrcTokTable.SrcType_STATIC ) ) {

	  VariableSourceToken vst = vstIfStatic.vst;

	  Iterator<VariableSourceToken> availItr = vstTable.get( vst.getSESE(),
								 vst.getAge()
								 ).iterator();

	  // look through things that are also available from same source
	  while( availItr.hasNext() ) {
	    VariableSourceToken vstAlsoAvail = availItr.next();
	  
	    Iterator<TempDescriptor> refVarItr = vstAlsoAvail.getRefVars().iterator();
	    while( refVarItr.hasNext() ) {
	      TempDescriptor refVarAlso = refVarItr.next();

	      // if a variable is available from the same source, AND it ALSO
	      // only comes from one statically known source, mark it available
              VSTWrapper vstIfStaticNotUsed = new VSTWrapper();
	      Integer srcTypeAlso = 
		vstTable.getRefVarSrcType( refVarAlso, 
					   currentSESE,
                                           vstIfStaticNotUsed
                                           );
	      if( srcTypeAlso.equals( VarSrcTokTable.SrcType_STATIC ) ) {
		notAvailSet.remove( refVarAlso );
	      }
	    }
	  }
	}
      }
    } break;

    } // end switch
  }
  
  private void debugFunction(OwnershipAnalysis oa2, FlatMethod fm) {
	  
	  String methodName="SomeWork";
	  
	  MethodDescriptor md=fm.getMethod();
		HashSet<MethodContext> mcSet=oa2.getAllMethodContextSetByDescriptor(md);
		Iterator<MethodContext> mcIter=mcSet.iterator();
		
		while(mcIter.hasNext()){
			MethodContext mc=mcIter.next();
			
			OwnershipGraph og=oa2.getOwnvershipGraphByMethodContext(mc);
			
			if(fm.toString().indexOf(methodName)>0){
				 try {
				   og.writeGraph("SECONDGRAPH"+fm.toString(),
						 true,  // write labels (variables)
						 true,  // selectively hide intermediate temp vars
						 true,  // prune unreachable heap regions
						 false, // show back edges to confirm graph validity
						 false, // show parameter indices (unmaintained!)
						 true,  // hide subset reachability states
						 false);// hide edge taints				 
				 } catch (IOException e) {
				 System.out.println("Error writing debug capture.");
				 System.exit(0);
				 }
			}
		}
	  
  }
  
	private void methodEffects(FlatMethod fm, CallGraph callGraph) {

		MethodDescriptor md=fm.getMethod();
		HashSet<MethodContext> mcSet=ownAnalysis.getAllMethodContextSetByDescriptor(md);
		Iterator<MethodContext> mcIter=mcSet.iterator();
				
		while(mcIter.hasNext()){
			MethodContext mc=mcIter.next();
			
			Set<FlatNode> visited = new HashSet<FlatNode>();
			
			Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
			flatNodesToVisit.add(fm);
			
			Hashtable<TempDescriptor, TempDescriptor> invarMap=new Hashtable<TempDescriptor,TempDescriptor>();

			while (!flatNodesToVisit.isEmpty()) {
				FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
				flatNodesToVisit.remove(fn);

				Stack<FlatSESEEnterNode> seseStack = seseStacks.get(fn);
				assert seseStack != null;

				if (!seseStack.empty()) {
					effects_nodeActions(mc, fn, seseStack.peek(), callGraph,invarMap);
				}

				flatNodesToVisit.remove(fn);
				visited.add(fn);

				for (int i = 0; i < fn.numNext(); i++) {
					FlatNode nn = fn.getNext(i);
					if (!visited.contains(nn)) {
						flatNodesToVisit.add(nn);
					}
				}

			}
			
			
		}
		
	}
	
	private void analyzeRelatedAllocationSite(MethodDescriptor callerMD,
			MethodContext calleeMC, HashSet<Integer> paramIndexSet,
			HashSet<HeapRegionNode> visitedHRN) {

		HashSet<MethodContext> mcSet = ownAnalysis
				.getAllMethodContextSetByDescriptor(callerMD);

		if (mcSet != null) {

			Iterator<MethodContext> mcIter = mcSet.iterator();

			FlatMethod callerFM = state.getMethodFlat(callerMD);

			while (mcIter.hasNext()) {
				MethodContext mc = mcIter.next();

				Set<FlatNode> visited = new HashSet<FlatNode>();
				Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
				flatNodesToVisit.add(callerFM);

				while (!flatNodesToVisit.isEmpty()) {
					FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
					flatNodesToVisit.remove(fn);

					analyzeRelatedAllocationSite_NodeAction(fn, mc, calleeMC,
							paramIndexSet,visitedHRN);

					flatNodesToVisit.remove(fn);
					visited.add(fn);

					for (int i = 0; i < fn.numNext(); i++) {
						FlatNode nn = fn.getNext(i);
						if (!visited.contains(nn)) {
							flatNodesToVisit.add(nn);
						}
					}
				}
			}
		}

	}
	
	private void analyzeRelatedAllocationSite_NodeAction(FlatNode fn, MethodContext callerMC,
 MethodContext calleeMC,
			HashSet<Integer> paramIndexSet, HashSet<HeapRegionNode> visitedHRN) {

		OwnershipGraph og = ownAnalysis
				.getOwnvershipGraphByMethodContext(callerMC);

		switch (fn.kind()) {

		case FKind.FlatCall: {

			FlatCall fc = (FlatCall) fn;
			
			
			if(fc.numArgs()>0 && fc.getMethod().equals(calleeMC.getDescriptor())){
				MethodContext calleeMCfromOG = ownAnalysis.getCalleeMethodContext(
						callerMC, fc);

				// disable below condition. currently collect all possible
				// allocation sites without regarding method context

				// if (calleeMC.equals(calleeMCfromOG)) { // in this case, this
				// method context calls corresponding callee.

				int base;
				if (((MethodDescriptor) calleeMC.getDescriptor()).isStatic()) {
					base = 0;
				} else {
					base = 1;
				}

				for (Iterator iterator = paramIndexSet.iterator(); iterator
						.hasNext();) {
					Integer integer = (Integer) iterator.next();

					int paramIdx = integer - base;
					if (paramIdx >= 0) {
						// if paramIdx is less than 0, assumes that it is
						// related with wrong method contexts.
						TempDescriptor arg = fc.getArg(paramIdx);
						LabelNode argLN = og.td2ln.get(arg);
						if (argLN != null) {
							Iterator<ReferenceEdge> iterEdge = argLN
									.iteratorToReferencees();
							while (iterEdge.hasNext()) {
								ReferenceEdge referenceEdge = (ReferenceEdge) iterEdge
										.next();

								HeapRegionNode dstHRN = referenceEdge.getDst();
								if (dstHRN.isParameter()) {
									if (!visitedHRN.contains(dstHRN)) {
										setupRelatedAllocSiteAnalysis(og, callerMC,
												dstHRN, visitedHRN);
									}
								} else {
//									System.out.println("FLAGGED "+callerMC+":fc="+fc+":arg="+arg+" , paramIdx="+paramIdx);
									flagAllocationSite(callerMC, dstHRN
											.getAllocationSite());
								}
							}
						}
					}
				}
			}
			

			// }

		}
			break;

		}
	}
	
	private void setupRelatedAllocSiteAnalysis(OwnershipGraph og,
			MethodContext mc, HeapRegionNode dstHRN,
			HashSet<HeapRegionNode> visitedHRN) {

		HashSet<Integer> paramIndexSet = new HashSet<Integer>();

		// collect corresponding param index
		Set<Integer> pIndexSet = og.idPrimary2paramIndexSet.get(dstHRN.getID());
		if (pIndexSet != null) {
			for (Iterator iterator = pIndexSet.iterator(); iterator.hasNext();) {
				Integer integer = (Integer) iterator.next();
				paramIndexSet.add(integer);
			}
		}

		Set<Integer> sIndexSet = og.idSecondary2paramIndexSet.get(dstHRN
				.getID());
		if (sIndexSet != null) {
			for (Iterator iterator = sIndexSet.iterator(); iterator.hasNext();) {
				Integer integer = (Integer) iterator.next();
				paramIndexSet.add(integer);
			}
		}

		if (mc.getDescriptor() instanceof MethodDescriptor) {
			Set callerSet = callGraph.getCallerSet((MethodDescriptor) mc
					.getDescriptor());
			for (Iterator iterator = callerSet.iterator(); iterator.hasNext();) {
				Object obj = (Object) iterator.next();
				if (obj instanceof MethodDescriptor) {
					MethodDescriptor callerMD = (MethodDescriptor) obj;

					if(callerMD.equals(mc.getDescriptor())){
						continue;
					}
					analyzeRelatedAllocationSite(callerMD, mc, paramIndexSet,visitedHRN);

				}
			}
		}
	}
  
	private void effects_nodeActions(MethodContext mc, FlatNode fn,
			FlatSESEEnterNode currentSESE, CallGraph callGraph,Hashtable<TempDescriptor, TempDescriptor> invarMap) {

		OwnershipGraph og = ownAnalysis.getOwnvershipGraphByMethodContext(mc);

		switch (fn.kind()) {

		case FKind.FlatSESEEnterNode: {

			FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
			assert fsen.equals(currentSESE);
			
			if (!fsen.getIsCallerSESEplaceholder()) {
				// uniquely taint each live-in variable
				Collection<TempDescriptor> set = fsen.getInVarSet();
				Iterator<TempDescriptor> iter = set.iterator();
				int idx = 0;
				while (iter.hasNext()) {
					TempDescriptor td = iter.next();
					LabelNode ln = og.td2ln.get(td);
					
					if(currentSESE.getSeseEffectsSet().getMapTempDescToInVarIdx().containsKey(td)){
						idx=currentSESE.getSeseEffectsSet().getInVarIdx(td);
					}
					
					if (ln != null) {
						int taint = (int) Math.pow(2, idx);
						taintLabelNode(ln, taint,currentSESE.getSeseEffectsSet());
						currentSESE.getSeseEffectsSet().setInVarIdx(idx, td);

						// collects related allocation sites
						Iterator<ReferenceEdge> referenceeIter = ln
								.iteratorToReferencees();
						while (referenceeIter.hasNext()) {
							ReferenceEdge referenceEdge = (ReferenceEdge) referenceeIter
									.next();
							HeapRegionNode dstHRN = referenceEdge.getDst();
							if (dstHRN.isParameter()) {

								HashSet<HeapRegionNode> visitedHRN = new HashSet<HeapRegionNode>();
								visitedHRN.add(dstHRN);
								setupRelatedAllocSiteAnalysis(og, mc, dstHRN,
										visitedHRN);

							} else {
//								System.out.println("FLAGGED "+fsen+":"+td);
								flagAllocationSite(mc, dstHRN
										.getAllocationSite());
							}
						}

					}

					idx++;
				}
			}

		}
			break;

		case FKind.FlatSESEExitNode: {
			FlatSESEExitNode fsexit = (FlatSESEExitNode) fn;

			if (!fsexit.getFlatEnter().getIsCallerSESEplaceholder()) {
				
				// clear taint information of live-in variables 
				Set<Integer> keySet=og.id2hrn.keySet();
				for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
					Integer hrnID = (Integer) iterator.next();
					HeapRegionNode hrn=og.id2hrn.get(hrnID);
					Iterator<ReferenceEdge> edgeIter=hrn.iteratorToReferencers();
					while (edgeIter.hasNext()) {
						ReferenceEdge refEdge = (ReferenceEdge) edgeIter
								.next();
						refEdge.setSESETaintIdentifier(0);
					}
				}

				FlatSESEEnterNode enterNode = fsexit.getFlatEnter();
				FlatSESEEnterNode parent = enterNode.getParent();
				if (parent != null) {

					SESEEffectsSet set = enterNode.getSeseEffectsSet();
					Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> readTable = set
							.getReadTable();
					Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> parentReadTable = parent
							.getSeseEffectsSet().getReadTable();
					Set<TempDescriptor> keys = readTable.keySet();
					Iterator<TempDescriptor> keyIter = keys.iterator();
					while (keyIter.hasNext()) {
						TempDescriptor td = (TempDescriptor) keyIter.next();
						HashSet<SESEEffectsKey> effectsSet = readTable.get(td);
						HashSet<SESEEffectsKey> parentEffectsSet = parentReadTable
								.get(td);
						if (parentEffectsSet == null) {
							parentEffectsSet = new HashSet<SESEEffectsKey>();
						}

						for (Iterator iterator = effectsSet.iterator(); iterator
								.hasNext();) {
							SESEEffectsKey seseKey = (SESEEffectsKey) iterator
									.next();
							parentEffectsSet.add(new SESEEffectsKey(seseKey
									.getFieldDescriptor(), seseKey
									.getTypeDescriptor(), seseKey.getHRNId(),
									seseKey.getHRNUniqueId()));
						}

						parentReadTable.put(td, parentEffectsSet);
					}

					Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> writeTable = set
							.getWriteTable();
					Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> parentWriteTable = parent
							.getSeseEffectsSet().getWriteTable();
					keys = writeTable.keySet();
					keyIter = keys.iterator();
					while (keyIter.hasNext()) {
						TempDescriptor td = (TempDescriptor) keyIter.next();
						HashSet<SESEEffectsKey> effectsSet = writeTable.get(td);
						HashSet<SESEEffectsKey> parentEffectsSet = parentWriteTable
								.get(td);
						if (parentEffectsSet == null) {
							parentEffectsSet = new HashSet<SESEEffectsKey>();
						}

						for (Iterator iterator = effectsSet.iterator(); iterator
								.hasNext();) {
							SESEEffectsKey seseKey = (SESEEffectsKey) iterator
									.next();
							parentEffectsSet.add(new SESEEffectsKey(seseKey
									.getFieldDescriptor(), seseKey
									.getTypeDescriptor(), seseKey.getHRNId(),
									seseKey.getHRNUniqueId()));
						}

						parentWriteTable.put(td, parentEffectsSet);
					}

					Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> strongUpdateTable = set
							.getStrongUpdateTable();
					Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> parentstrongUpdateTable = parent
							.getSeseEffectsSet().getStrongUpdateTable();
					keys = strongUpdateTable.keySet();
					keyIter = keys.iterator();
					while (keyIter.hasNext()) {
						TempDescriptor td = (TempDescriptor) keyIter.next();
						HashSet<SESEEffectsKey> effectsSet = strongUpdateTable
								.get(td);
						HashSet<SESEEffectsKey> parentEffectsSet = parentstrongUpdateTable
								.get(td);
						if (parentEffectsSet == null) {
							parentEffectsSet = new HashSet<SESEEffectsKey>();
						}

						for (Iterator iterator = effectsSet.iterator(); iterator
								.hasNext();) {
							SESEEffectsKey seseKey = (SESEEffectsKey) iterator
									.next();
							parentEffectsSet.add(new SESEEffectsKey(seseKey
									.getFieldDescriptor(), seseKey
									.getTypeDescriptor(), seseKey.getHRNId(),
									seseKey.getHRNUniqueId()));
						}

						parentstrongUpdateTable.put(td, parentEffectsSet);
					}

				}

			}

		}
			break;

		case FKind.FlatFieldNode: {

			FlatFieldNode ffn = (FlatFieldNode) fn;
			TempDescriptor dst = ffn.getDst();
			TempDescriptor src = ffn.getSrc();
			FieldDescriptor field = ffn.getField();
			
			LabelNode srcLN = og.td2ln.get(src);
			if(srcLN!=null){
				Iterator<ReferenceEdge> edgeIter=srcLN.iteratorToReferencees();
				int taintIdentifier=0;
				while (edgeIter.hasNext()) {
					ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
							.next();
					HeapRegionNode refHRN=referenceEdge.getDst();
					taintIdentifier=currentSESE.getSeseEffectsSet().getTaint(referenceEdge);
//					taintIdentifier=referenceEdge.getSESETaintIdentifier();
					
					// figure out which invar has related effects
					Hashtable<TempDescriptor, Integer> map=currentSESE.getSeseEffectsSet().getMapTempDescToInVarIdx();
					Set<TempDescriptor> keySet=map.keySet();
					for (Iterator iterator = keySet.iterator(); iterator
							.hasNext();) {
						TempDescriptor inVarTD = (TempDescriptor) iterator
								.next();
						int inVarMask=(int) Math.pow(2, map.get(inVarTD).intValue());
						if((inVarMask&taintIdentifier)>0){
							// found related invar, contribute effects
							currentSESE.readEffects(inVarTD, field.getSymbol(),src.getType(), refHRN);
						}
					}
				}
				
				// taint
				if(!field.getType().isImmutable()){
						LabelNode dstLN = og.td2ln.get(dst);
						edgeIter=dstLN.iteratorToReferencees();
						while (edgeIter.hasNext()) {
							ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
									.next();
							currentSESE.getSeseEffectsSet().mapEdgeToTaint(referenceEdge, taintIdentifier);
//							referenceEdge.unionSESETaintIdentifier(taintIdentifier);
						}
				}
			}
			

		}
			break;
			
		case FKind.FlatOpNode:{
			
			FlatOpNode fon=(FlatOpNode)fn;
			TempDescriptor dest=fon.getDest();
			TempDescriptor src=fon.getLeft();
			
			if(currentSESE.getInVarSet().contains(src)){
				int idx=currentSESE.getSeseEffectsSet().getInVarIdx(src);
				if(idx==-1){
					break;
				}
				
				//mark dest's edges for corresponding  sese live in-var.
				LabelNode srcLN = og.td2ln.get(dest);
				if (srcLN != null) {
					Iterator<ReferenceEdge> refEdgeIter=srcLN.iteratorToReferencees();
					while (refEdgeIter.hasNext()) {
						ReferenceEdge edge = refEdgeIter.next();
						int newTaint = (int) Math.pow(2, idx);
//						System.out.println("fon="+fon);
//						System.out.println(currentSESE+" src:"+src+"->"+"dest:"+dest+" with taint="+newTaint);
//						System.out.println("referenceEdge="+edge);
						currentSESE.getSeseEffectsSet().mapEdgeToTaint(edge, newTaint);
//						System.out.println("after tainting="+edge.getSESETaintIdentifier());
					}
				}
			}
		}break;
		
		case FKind.FlatElementNode:{
			
			FlatElementNode fsen=(FlatElementNode)fn;			
			TempDescriptor src = fsen.getSrc();
			TempDescriptor dst = fsen.getDst();
			String field="___element_";
			
			LabelNode srcLN = og.td2ln.get(src);
			int taintIdentifier=0;
			if(srcLN!=null){
				Iterator<ReferenceEdge> edgeIter=srcLN.iteratorToReferencees();
				while (edgeIter.hasNext()) {
					ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
							.next();
					HeapRegionNode dstHRN=referenceEdge.getDst();
					taintIdentifier=currentSESE.getSeseEffectsSet().getTaint(referenceEdge);
//					taintIdentifier=referenceEdge.getSESETaintIdentifier();					
					
					// figure out which invar has related effects
					Hashtable<TempDescriptor, Integer> map=currentSESE.getSeseEffectsSet().getMapTempDescToInVarIdx();
					Set<TempDescriptor> keySet=map.keySet();
					for (Iterator iterator = keySet.iterator(); iterator
							.hasNext();) {
						TempDescriptor inVarTD = (TempDescriptor) iterator
								.next();
						int inVarMask=(int) Math.pow(2, map.get(inVarTD).intValue());
						if((inVarMask&taintIdentifier)>0){
							// found related invar, contribute effects
							currentSESE.readEffects(inVarTD, field,src.getType(), dstHRN);
						}
					}
					
				}
			}
			
			// taint
			LabelNode dstLN = og.td2ln.get(dst);
			if(dstLN!=null){
				Iterator<ReferenceEdge> edgeIter=dstLN.iteratorToReferencees();
				while (edgeIter.hasNext()) {
					ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
							.next();
					currentSESE.getSeseEffectsSet().mapEdgeToTaint(referenceEdge, taintIdentifier);
//					referenceEdge.unionSESETaintIdentifier(taintIdentifier);
				}
			}
			
		}break;
			
		case FKind.FlatSetElementNode: {

			FlatSetElementNode fsen = (FlatSetElementNode) fn;
			TempDescriptor dst = fsen.getDst();
			TypeDescriptor  tdElement = dst.getType().dereference();
			
			String field = "___element_";
			
			LabelNode dstLN=og.td2ln.get(dst);
			if(dst!=null){
				
				Iterator<ReferenceEdge> edgeIter=dstLN.iteratorToReferencees();
				while (edgeIter.hasNext()) {
					ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
							.next();
					HeapRegionNode dstHRN=referenceEdge.getDst();
					int edgeTaint=currentSESE.getSeseEffectsSet().getTaint(referenceEdge);
//					int edgeTaint=referenceEdge.getSESETaintIdentifier();
					
					// we can do a strong update here if one of two cases
					// holds
					boolean strongUpdate=false;
					if (field != null && !dst.getType().isImmutable()
							&& ((dstHRN.getNumReferencers() == 1) || // case 1
							(dstHRN.isSingleObject() && dstLN
									.getNumReferencees() == 1) // case 2
							)) {
						strongUpdate = true;
					}
					
					
					// figure out which invar has related effects
					Hashtable<TempDescriptor, Integer> map=currentSESE.getSeseEffectsSet().getMapTempDescToInVarIdx();
					Set<TempDescriptor> keySet=map.keySet();
					for (Iterator iterator = keySet.iterator(); iterator
							.hasNext();) {
						TempDescriptor inVarTD = (TempDescriptor) iterator
								.next();
						int inVarMask=(int) Math.pow(2, map.get(inVarTD).intValue());
						if((inVarMask&edgeTaint)>0){
							// found related invar, contribute effects
							currentSESE.writeEffects(inVarTD, field, dst.getType(),dstHRN, strongUpdate);						
					}
				}
				
				
			}
			
			}

		}break;
			
		case FKind.FlatSetFieldNode: {

			FlatSetFieldNode fsen = (FlatSetFieldNode) fn;
			TempDescriptor dst = fsen.getDst();
			FieldDescriptor field = fsen.getField();
			
			LabelNode dstLN = og.td2ln.get(dst);
			if(dstLN!=null){
				
				Iterator<ReferenceEdge> edgeIter=dstLN.iteratorToReferencees();
				while (edgeIter.hasNext()) {
					ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
							.next();
					HeapRegionNode dstHRN=referenceEdge.getDst();
					int edgeTaint=currentSESE.getSeseEffectsSet().getTaint(referenceEdge);
//					int edgeTaint=referenceEdge.getSESETaintIdentifier();
					
					// we can do a strong update here if one of two cases
					// holds
					boolean strongUpdate=false;
					if (field != null && !field.getType().isImmutable()
							&& field != OwnershipAnalysis
									.getArrayField(field.getType())
							&& ((dstHRN.getNumReferencers() == 1) || // case 1
							(dstHRN.isSingleObject() && dstLN
									.getNumReferencees() == 1) // case 2
							)) {
						strongUpdate = true;
					}
					
					
					// figure out which invar has related effects
					Hashtable<TempDescriptor, Integer> map = currentSESE
							.getSeseEffectsSet().getMapTempDescToInVarIdx();
					Set<TempDescriptor> keySet = map.keySet();
					for (Iterator iterator = keySet.iterator(); iterator
							.hasNext();) {
						TempDescriptor inVarTD = (TempDescriptor) iterator
								.next();
						int inVarMask = (int) Math.pow(2, map.get(inVarTD)
								.intValue());
						if ((inVarMask & edgeTaint) > 0) {
							// found related invar, contribute effects
							currentSESE.writeEffects(inVarTD,
									field.getSymbol(), dst.getType(), dstHRN,
									strongUpdate);
						}
					}
				
				
			}
			}
		}
			break;

		case FKind.FlatCall: {
			FlatCall fc = (FlatCall) fn;

			MethodContext calleeMC = ownAnalysis.getCalleeMethodContext(mc, fc);

			MethodEffects me = ownAnalysis.getMethodEffectsAnalysis()
					.getMethodEffectsByMethodContext(calleeMC);

			OwnershipGraph calleeOG = ownAnalysis
					.getOwnvershipGraphByMethodContext(calleeMC);
			

			FlatMethod fm = state.getMethodFlat(fc.getMethod());
			ParameterDecomposition decomp = new ParameterDecomposition(
					ownAnalysis, fc, fm, calleeMC, calleeOG, og);

			int base=0;
			if (((MethodDescriptor) calleeMC.getDescriptor()).isStatic()) {
				base = 0;
			} else {
				base = 1;
			}

			for (int i = 0; i < fc.numArgs()+base; i++) {
				
				TempDescriptor arg ;
				Set<EffectsKey> readSet;
				Set<EffectsKey> writeSet;
				Set<EffectsKey> strongUpdateSet;
				
				int paramIdx=0;
				
				boolean isThis=false;
				if(i==fc.numArgs()){
					paramIdx=0;
					 arg = fc.getThis();
						Integer hrnPrimaryID = calleeOG.paramIndex2idPrimary.get(paramIdx);
						Integer hrnSecondaryID = calleeOG.paramIndex2idSecondary.get(paramIdx);
						 readSet = me.getEffects().getReadingSet(
								0);
						 writeSet = me.getEffects().getWritingSet(
								0);
						 strongUpdateSet = me.getEffects()
								.getStrongUpdateSet(0);
						 isThis=true;
				}else{
					paramIdx=i + base;
					 arg = fc.getArg(i);
					 readSet = me.getEffects().getReadingSet(
							i + base);
					 writeSet = me.getEffects().getWritingSet(
							i + base);
					 strongUpdateSet = me.getEffects()
							.getStrongUpdateSet(i + base);
				}

				LabelNode argLN = og.td2ln.get(arg);
				if(	argLN!=null){
					Iterator<ReferenceEdge> edgeIter=argLN.iteratorToReferencees();
					while (edgeIter.hasNext()) {
						ReferenceEdge referenceEdge = (ReferenceEdge) edgeIter
								.next();
						HeapRegionNode dstHRN=referenceEdge.getDst();
						int edgeTaint=currentSESE.getSeseEffectsSet().getTaint(referenceEdge);
//						int edgeTaint=referenceEdge.getSESETaintIdentifier();
						
						// figure out which invar has related effects
						Hashtable<TempDescriptor, Integer> map = currentSESE
								.getSeseEffectsSet().getMapTempDescToInVarIdx();
						Set<TempDescriptor> keySet = map.keySet();
						for (Iterator iterator = keySet.iterator(); iterator
								.hasNext();) {
							TempDescriptor inVarTD = (TempDescriptor) iterator
									.next();
							int inVarMask = (int) Math.pow(2, map.get(inVarTD)
									.intValue());
							
							if ((inVarMask & edgeTaint) > 0) {
								// found related invar, contribute effects
								
								if (readSet != null) {
									Iterator<EffectsKey> readIter = readSet
											.iterator();
									while (readIter.hasNext()) {
										EffectsKey key = readIter.next();
										Set<Integer> hrnSet = getCallerHRNId(
												new Integer(paramIdx), calleeOG,
												key.getHRNId(), decomp);
										Iterator<Integer> hrnIter = hrnSet
												.iterator();
										while (hrnIter.hasNext()) {
											Integer hrnID = (Integer) hrnIter
													.next();

											HeapRegionNode refHRN = og.id2hrn
													.get(hrnID);

											currentSESE.readEffects(inVarTD, key
													.getFieldDescriptor(), key
													.getTypeDescriptor(), refHRN);

										}
									}
								}
								
								if (writeSet != null) {
									Iterator<EffectsKey> writeIter = writeSet
											.iterator();
									while (writeIter.hasNext()) {
										EffectsKey key = writeIter.next();

										Set<Integer> hrnSet = getCallerHRNId(
												new Integer(paramIdx), calleeOG,
												key.getHRNId(), decomp);
										Iterator<Integer> hrnIter = hrnSet
												.iterator();
										while (hrnIter.hasNext()) {
											Integer hrnID = (Integer) hrnIter
													.next();

											HeapRegionNode refHRN = og.id2hrn
													.get(hrnID);
											
											currentSESE.writeEffects(inVarTD,
													key.getFieldDescriptor(), key
															.getTypeDescriptor(),
													refHRN, false);
										}

									}
								}

								if (strongUpdateSet != null) {
									Iterator<EffectsKey> strongUpdateIter = strongUpdateSet
											.iterator();
									while (strongUpdateIter.hasNext()) {
										EffectsKey key = strongUpdateIter
												.next();

										Set<Integer> hrnSet = getCallerHRNId(
												new Integer(paramIdx),
												calleeOG, key.getHRNId(),
												decomp);
										Iterator<Integer> hrnIter = hrnSet
												.iterator();
										while (hrnIter.hasNext()) {
											Integer hrnID = (Integer) hrnIter
													.next();

											HeapRegionNode refHRN = og.id2hrn
													.get(hrnID);

											currentSESE.writeEffects(inVarTD,
													key.getFieldDescriptor(),
													key.getTypeDescriptor(),
													refHRN, true);
										}
									}
								} // end of 	if (strongUpdateSet != null)
								
							} // end of if ((inVarMask & edgeTaint) > 0) 
						}
						
					}
				}
				
			}

		}
			break;

		}
	}
	
	private void flagAllocationSite(MethodContext mc, AllocationSite ac){
		HashSet<AllocationSite> set=mapMethodContextToLiveInAllocationSiteSet.get(mc);
		if(set==null){
			set=new HashSet<AllocationSite>();			
		}
		set.add(ac);
		mapMethodContextToLiveInAllocationSiteSet.put(mc, set);
	}
	
	private void followReference(HeapRegionNode hrn,HashSet<TempDescriptor> tdSet, HashSet<HeapRegionNode> visited, FlatSESEEnterNode currentSESE){
		
		Iterator<ReferenceEdge> referIter=hrn.iteratorToReferencers();
		// check whether hrn is referenced by TD
		while (referIter.hasNext()) {
			ReferenceEdge referEdge = (ReferenceEdge) referIter.next();
			if(referEdge.getSrc() instanceof LabelNode){
				LabelNode ln=(LabelNode)referEdge.getSrc();
				if(currentSESE.getInVarSet().contains(ln.getTempDescriptor())){
					tdSet.add(ln.getTempDescriptor());
				}
			}else if(referEdge.getSrc() instanceof HeapRegionNode){
				HeapRegionNode nextHRN=(HeapRegionNode)referEdge.getSrc();
				if(!visited.contains(nextHRN)){
					visited.add(nextHRN);
					followReference(nextHRN,tdSet,visited,currentSESE);				
				}
				
			}
		}
		
	}
	
	private Set<Integer> getCallerHRNId(Integer paramIdx,
			OwnershipGraph calleeOG, Integer calleeHRNId,
			ParameterDecomposition paramDecom) {
		
		Integer hrnPrimaryID = calleeOG.paramIndex2idPrimary.get(paramIdx);
		Integer hrnSecondaryID = calleeOG.paramIndex2idSecondary.get(paramIdx);
		
		if (calleeHRNId.equals(hrnPrimaryID)) {
			// it references to primary param heap region
			return paramDecom.getParamObject_hrnIDs(paramIdx);
		} else if (calleeHRNId.equals(hrnSecondaryID)) {
			// it references to secondary param heap region
			return paramDecom.getParamReachable_hrnIDs(paramIdx);
		}

		return new HashSet<Integer>();
	}
	
	private void taintLabelNode(LabelNode ln, int identifier, SESEEffectsSet effectSet) {

		Iterator<ReferenceEdge> edgeIter = ln.iteratorToReferencees();
		while (edgeIter.hasNext()) {
			ReferenceEdge edge = edgeIter.next();
			effectSet.mapEdgeToTaint(edge, identifier);
		}

	}
	
	private HashSet<HeapRegionNode> getReferenceHeapIDSet(OwnershipGraph og, TempDescriptor td){
		
		HashSet<HeapRegionNode> returnSet=new HashSet<HeapRegionNode>();
		
		LabelNode ln=og.td2ln.get(td);
		if(ln!=null){
			Iterator<ReferenceEdge> edgeIter=ln.iteratorToReferencees();
			while(edgeIter.hasNext()){
				ReferenceEdge edge=edgeIter.next();
					HeapRegionNode hrn=edge.getDst();
					returnSet.add(hrn);
			}
		}
		return returnSet;
	}
	
	
	private HashSet<ReferenceEdge> getRefEdgeSetReferenceToSameHRN(
			OwnershipGraph og, TempDescriptor td) {

		HashSet<ReferenceEdge> returnSet = new HashSet<ReferenceEdge>();

		HashSet<HeapRegionNode> heapIDs = getReferenceHeapIDSet(og, td);
		for (Iterator<HeapRegionNode> iterator = heapIDs.iterator(); iterator
				.hasNext();) {
			HeapRegionNode heapRegionNode = (HeapRegionNode) iterator.next();
			Iterator<ReferenceEdge> referenceeIter = heapRegionNode
					.iteratorToReferencees();
			while (referenceeIter.hasNext()) {
				ReferenceEdge edge = (ReferenceEdge) referenceeIter.next();
				if (edge.getSrc() instanceof HeapRegionNode) {
					returnSet.add(edge);
				}
			}
		}
		return returnSet;
	}
	
	private HashSet<TempDescriptor> getTempDescSetReferenceToSameHRN(
			OwnershipGraph og, TempDescriptor td) {

		HashSet<TempDescriptor> returnSet = new HashSet<TempDescriptor>();

		HashSet<HeapRegionNode> heapIDs = getReferenceHeapIDSet(og, td);
		for (Iterator<HeapRegionNode> iterator = heapIDs.iterator(); iterator
				.hasNext();) {
			HeapRegionNode heapRegionNode = (HeapRegionNode) iterator.next();
			Iterator<ReferenceEdge> referencerIter = heapRegionNode
					.iteratorToReferencers();
			while (referencerIter.hasNext()) {
				ReferenceEdge edge = (ReferenceEdge) referencerIter.next();
				if (edge.getSrc() instanceof LabelNode) {
					LabelNode ln = (LabelNode) edge.getSrc();
					returnSet.add(ln.getTempDescriptor());
				}
			}
		}
		return returnSet;
	}
	
	private void DFSVisit( MethodDescriptor md,
			 LinkedList<MethodDescriptor> sorted,
			HashSet<MethodDescriptor> discovered, JavaCallGraph javaCallGraph) {

		discovered.add(md);

		Iterator itr = javaCallGraph.getCallerSet(md).iterator();
		while (itr.hasNext()) {
			MethodDescriptor mdCaller = (MethodDescriptor) itr.next();

			if (!discovered.contains(mdCaller)) {
				DFSVisit(mdCaller, sorted, discovered, javaCallGraph);
			}
		}

		sorted.addFirst(md);
	}
	
	
	private LinkedList<MethodDescriptor> topologicalSort(Set set,
			JavaCallGraph javaCallGraph) {
		HashSet<MethodDescriptor> discovered = new HashSet<MethodDescriptor>();
		LinkedList<MethodDescriptor> sorted = new LinkedList<MethodDescriptor>();

		Iterator<MethodDescriptor> itr = set.iterator();
		while (itr.hasNext()) {
			MethodDescriptor md = itr.next();

			if (!discovered.contains(md)) {
				DFSVisit(md, sorted, discovered, javaCallGraph);
			}
		}

		return sorted;
	}
	
	private void calculateCovering(ConflictGraph conflictGraph){
		uniqueLockSetId=0; // reset lock counter for every new conflict graph
		HashSet<ConflictEdge> fineToCover = new HashSet<ConflictEdge>();
		HashSet<ConflictEdge> coarseToCover = new HashSet<ConflictEdge>();
		HashSet<SESELock> lockSet=new HashSet<SESELock>();
		
		HashSet<ConflictEdge> tempCover = conflictGraph.getEdgeSet();
		for (Iterator iterator = tempCover.iterator(); iterator.hasNext();) {
			ConflictEdge conflictEdge = (ConflictEdge) iterator.next();
			if(conflictEdge.getType()==ConflictEdge.FINE_GRAIN_EDGE){
				fineToCover.add(conflictEdge);
			}else if(conflictEdge.getType()==ConflictEdge.COARSE_GRAIN_EDGE){
				coarseToCover.add(conflictEdge);
			}
		}
	
		HashSet<ConflictEdge> toCover=new HashSet<ConflictEdge>();
		toCover.addAll(fineToCover);
		toCover.addAll(coarseToCover);
		
		while (!toCover.isEmpty()) {
			
			SESELock seseLock = new SESELock();
			seseLock.setID(uniqueLockSetId++);
		
			boolean changed;
			
			do{	// fine-grained edge
				
				changed=false;
			
				for (Iterator iterator = fineToCover.iterator(); iterator
						.hasNext();) {
					
					int type;
					ConflictEdge edge = (ConflictEdge) iterator.next();
					if(seseLock.getConflictNodeSet().size()==0){
						//initial setup	
						if(seseLock.isWriteNode(edge.getVertexU())){
							// mark as fine_write
							if(edge.getVertexU() instanceof StallSiteNode){
								type=ConflictNode.PARENT_WRITE;
							}else{
								type=ConflictNode.FINE_WRITE;
							}
							seseLock.addConflictNode(edge.getVertexU(), type);
						}else{
							// mark as fine_read
							if(edge.getVertexU() instanceof StallSiteNode){
								type=ConflictNode.PARENT_READ;
							}else{
								type=ConflictNode.FINE_READ;
							}
							seseLock.addConflictNode(edge.getVertexU(), type);
						}
						if(edge.getVertexV()!=edge.getVertexU()){
							if(seseLock.isWriteNode(edge.getVertexV())){
								// mark as fine_write
								if(edge.getVertexV() instanceof StallSiteNode){
									type=ConflictNode.PARENT_WRITE;
								}else{
									type=ConflictNode.FINE_WRITE;
								}
								seseLock.addConflictNode(edge.getVertexV(), type);
							}else{
								// mark as fine_read
								if(edge.getVertexV() instanceof StallSiteNode){
									type=ConflictNode.PARENT_READ;
								}else{
									type=ConflictNode.FINE_READ;
								}
								seseLock.addConflictNode(edge.getVertexV(), type);
							}		
						}
						changed=true;
						seseLock.addConflictEdge(edge);
						fineToCover.remove(edge);
						break;// exit iterator loop
					}// end of initial setup
					
					ConflictNode newNode;
					if((newNode=seseLock.getNewNodeConnectedWithGroup(edge))!=null){
						// new node has a fine-grained edge to all current node
						// If there is a coarse grained edge where need a fine edge, it's okay to add the node
						// but the edge must remain uncovered.
						
						changed=true;
						
						if(seseLock.isWriteNode(newNode)){
							if(newNode instanceof StallSiteNode){
								type=ConflictNode.PARENT_WRITE;
							}else{
								type=ConflictNode.FINE_WRITE;
							}
							seseLock.setNodeType(newNode,type);
						}else{
							if(newNode instanceof StallSiteNode){
								type=ConflictNode.PARENT_READ;
							}else{
								type=ConflictNode.FINE_READ;
							}
							seseLock.setNodeType(newNode,type);
						}

						seseLock.addEdge(edge);
						HashSet<ConflictEdge> edgeSet=newNode.getEdgeSet();
						for (Iterator iterator2 = edgeSet.iterator(); iterator2
								.hasNext();) {
							ConflictEdge conflictEdge = (ConflictEdge) iterator2
									.next();
							
							
							// mark all fine edges between new node and nodes in the group as covered
							if(!conflictEdge.getVertexU().equals(newNode)){
								if(seseLock.containsConflictNode(conflictEdge.getVertexU())){
									changed=true;
									seseLock.addConflictEdge(conflictEdge);
									fineToCover.remove(conflictEdge);
								}
							}else if(!conflictEdge.getVertexV().equals(newNode)){
								if(seseLock.containsConflictNode(conflictEdge.getVertexV())){
									changed=true;
									seseLock.addConflictEdge(conflictEdge);
									fineToCover.remove(conflictEdge);
								}				
							}
							
						}
					
						break;// exit iterator loop
					}
				}
				
			}while(changed);
			do{		// coarse
				changed=false;
				int type;
				for (Iterator iterator = coarseToCover.iterator(); iterator
				.hasNext();) {
					
					ConflictEdge edge = (ConflictEdge) iterator.next();
					
					if(seseLock.getConflictNodeSet().size()==0){
						//initial setup	
						if(seseLock.hasSelfCoarseEdge(edge.getVertexU())){
							// node has a coarse-grained edge with itself
							if(!(edge.getVertexU() instanceof StallSiteNode)){
								// and it is not parent
								type=ConflictNode.SCC;
							}else{
								type=ConflictNode.PARENT_COARSE;
							}
							seseLock.addConflictNode(edge.getVertexU(), type);
						}else{
							if(edge.getVertexU() instanceof StallSiteNode){
								type=ConflictNode.PARENT_COARSE;
							}else{
								type=ConflictNode.COARSE;
							}
							seseLock.addConflictNode(edge.getVertexU(), type);
						}
						if(seseLock.hasSelfCoarseEdge(edge.getVertexV())){
							// node has a coarse-grained edge with itself
							if(!(edge.getVertexV() instanceof StallSiteNode)){
								// and it is not parent
								type=ConflictNode.SCC;
							}else{
								type=ConflictNode.PARENT_COARSE;
							}
							seseLock.addConflictNode(edge.getVertexV(), type);
						}else{
							if(edge.getVertexV() instanceof StallSiteNode){
								type=ConflictNode.PARENT_COARSE;
							}else{
								type=ConflictNode.COARSE;
							}
							seseLock.addConflictNode(edge.getVertexV(), type);
						}						
						changed=true;
						coarseToCover.remove(edge);
						seseLock.addConflictEdge(edge);
						break;// exit iterator loop
					}// end of initial setup
					
					
					ConflictNode newNode;
					if((newNode=seseLock.getNewNodeConnectedWithGroup(edge))!=null){
						// new node has a coarse-grained edge to all fine-read, fine-write, parent
						changed=true; 
						
						if(seseLock.hasSelfCoarseEdge(newNode)){
							//SCC
							if(newNode instanceof StallSiteNode){
								type=ConflictNode.PARENT_COARSE;
							}else{
								type=ConflictNode.SCC;
							}
							seseLock.setNodeType(newNode, type);
						}else{
							if(newNode instanceof StallSiteNode){
								type=ConflictNode.PARENT_COARSE;
							}else{
								type=ConflictNode.COARSE;
							}
							seseLock.setNodeType(newNode, type);
						}

						seseLock.addEdge(edge);
						HashSet<ConflictEdge> edgeSet=newNode.getEdgeSet();
						for (Iterator iterator2 = edgeSet.iterator(); iterator2
								.hasNext();) {
							ConflictEdge conflictEdge = (ConflictEdge) iterator2
									.next();
							// mark all coarse edges between new node and nodes in the group as covered
							if(!conflictEdge.getVertexU().equals(newNode)){
								if(seseLock.containsConflictNode(conflictEdge.getVertexU())){
									changed=true;
									seseLock.addConflictEdge(conflictEdge);
									coarseToCover.remove(conflictEdge);
								}
							}else if(!conflictEdge.getVertexV().equals(newNode)){
								if(seseLock.containsConflictNode(conflictEdge.getVertexV())){
									changed=true;
									seseLock.addConflictEdge(conflictEdge);
									coarseToCover.remove(conflictEdge);
								}				
							}
							
						}
						break;// exit iterator loop
					}
					
				}
				
			}while(changed);
			lockSet.add(seseLock);
			
			toCover.clear();
			toCover.addAll(fineToCover);
			toCover.addAll(coarseToCover);
			
		}
		
		conflictGraphLockMap.put(conflictGraph, lockSet);
	}

	private void synthesizeLocks(){
		Set<Entry<FlatNode,ConflictGraph>> graphEntrySet=conflictGraphResults.entrySet();
		for (Iterator iterator = graphEntrySet.iterator(); iterator.hasNext();) {
			Entry<FlatNode, ConflictGraph> graphEntry = (Entry<FlatNode, ConflictGraph>) iterator
					.next();
			FlatNode sese=graphEntry.getKey();
			ConflictGraph conflictGraph=graphEntry.getValue();
			calculateCovering(conflictGraph);
		}
	}
	
	private void makeConflictGraph() {
		Iterator<Descriptor> methItr = ownAnalysis.descriptorsToAnalyze
				.iterator();
		while (methItr.hasNext()) {
			Descriptor d = methItr.next();
			FlatMethod fm = state.getMethodFlat(d);

			HashSet<MethodContext> mcSet = ownAnalysisForSESEConflicts
					.getAllMethodContextSetByDescriptor(fm.getMethod());
			Iterator<MethodContext> mcIter = mcSet.iterator();

			while (mcIter.hasNext()) {
				MethodContext mc = mcIter.next();
				OwnershipGraph og=ownAnalysisForSESEConflicts.getOwnvershipGraphByMethodContext(mc);

				Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
				flatNodesToVisit.add(fm);

				Set<FlatNode> visited = new HashSet<FlatNode>();

				SESESummary summary = new SESESummary(null, fm);
				seseSummaryMap.put(fm, summary);
				
				Hashtable<TempDescriptor, TempDescriptor> invarMap=new Hashtable<TempDescriptor,TempDescriptor>();

				while (!flatNodesToVisit.isEmpty()) {
					Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
					FlatNode fn = fnItr.next();

					flatNodesToVisit.remove(fn);
					visited.add(fn);

					// Adding Stall Node of current program statement
					ParentChildConflictsMap currentConflictsMap = conflictsResults
							.get(fn);

					Hashtable<TempDescriptor, StallSite> stallMap = currentConflictsMap
							.getStallMap();
					
					Set<Entry<TempDescriptor, StallSite>> entrySet = stallMap
							.entrySet();

					SESESummary seseSummary = seseSummaryMap.get(fn);

					ConflictGraph conflictGraph = null;
					conflictGraph = conflictGraphResults.get(seseSummary
							.getCurrentSESE());

					if (conflictGraph == null) {
						conflictGraph = new ConflictGraph(og);
					}
					for (Iterator<Entry<TempDescriptor, StallSite>> iterator2 = entrySet
							.iterator(); iterator2.hasNext();) {
						Entry<TempDescriptor, StallSite> entry = iterator2
								.next();
						TempDescriptor td = entry.getKey();
						StallSite stallSite = entry.getValue();

						// reachability set
						og = ownAnalysisForSESEConflicts
								.getOwnvershipGraphByMethodContext(mc);
						Set<Set> reachabilitySet = calculateReachabilitySet(og,
								td);
						conflictGraph.addStallNode(td, fm, stallSite,
								reachabilitySet);

					}

					if (conflictGraph.id2cn.size() > 0) {
						conflictGraphResults.put(seseSummary.getCurrentSESE(),
								conflictGraph);
					}

					conflictGraph_nodeAction(mc, fm, fn,invarMap);

					for (int i = 0; i < fn.numNext(); i++) {
						FlatNode nn = fn.getNext(i);
						if (!visited.contains(nn)) {
							flatNodesToVisit.add(nn);
						}
					}
				} // end of while(flatNodesToVisit)

			} // end of while(mcIter)

		}
		
		// decide fine-grain edge or coarse-grain edge among all vertexes by pair-wise comparison
    	Enumeration<FlatNode> keyEnum1=conflictGraphResults.keys();
		while (keyEnum1.hasMoreElements()) {
			FlatNode flatNode = (FlatNode) keyEnum1.nextElement();
			ConflictGraph conflictGraph=conflictGraphResults.get(flatNode);
			conflictGraph.analyzeConflicts();
			conflictGraphResults.put(flatNode, conflictGraph);
		}
		
	}
	
	private Set<Set> calculateReachabilitySet(OwnershipGraph og,
			TempDescriptor tempDescriptor) {
		// reachability set
		Set<Set> reachabilitySet = new HashSet();
		LabelNode ln = og.td2ln.get(tempDescriptor);
		if(ln!=null){
			Iterator<ReferenceEdge> refEdgeIter = ln.iteratorToReferencees();
			while (refEdgeIter.hasNext()) {
				ReferenceEdge referenceEdge = (ReferenceEdge) refEdgeIter.next();

				ReachabilitySet set = referenceEdge.getBeta();
				Iterator<TokenTupleSet> ttsIter = set.iterator();
				while (ttsIter.hasNext()) {
					TokenTupleSet tokenTupleSet = (TokenTupleSet) ttsIter.next();

					HashSet<GloballyUniqueTokenTuple> newTokenTupleSet = new HashSet<GloballyUniqueTokenTuple>();
					// reachabilitySet.add(tokenTupleSet);

					Iterator iter = tokenTupleSet.iterator();
					while (iter.hasNext()) {
						TokenTuple tt = (TokenTuple) iter.next();
						int token = tt.getToken();
						String uniqueID = og.id2hrn.get(new Integer(token))
								.getGloballyUniqueIdentifier();
						GloballyUniqueTokenTuple gtt = new GloballyUniqueTokenTuple(
								uniqueID, tt);
						newTokenTupleSet.add(gtt);
					}

					reachabilitySet.add(newTokenTupleSet);
				}
			}
		}

		return reachabilitySet;
	}
	
	private ReachabilitySet packupStates(OwnershipGraph og, HeapRegionNode hrn) {
		
		ReachabilitySet betaSet = new ReachabilitySet().makeCanonical();
		
		Iterator<ReferenceEdge> itrEdge = hrn.iteratorToReferencers();
		while (itrEdge.hasNext()) {
			ReferenceEdge edge = itrEdge.next();
			betaSet = betaSet.union(edge.getBeta());
		}
		
		return betaSet;
		
	}
	
	private ReachabilitySet packupStates(OwnershipGraph og, AllocationSite as) {

		ReachabilitySet betaSet = new ReachabilitySet().makeCanonical();
		assert as!=null;
		HeapRegionNode hrnSummary = og.id2hrn.get(as.getSummary());
		if(hrnSummary!=null){
			Iterator<ReferenceEdge> itrEdge = hrnSummary.iteratorToReferencers();
			while (itrEdge.hasNext()) {
				ReferenceEdge edge = itrEdge.next();
				betaSet = betaSet.union(edge.getBeta());
			}
		}

		// check for other nodes
		for (int i = 0; i < as.getAllocationDepth(); ++i) {

			HeapRegionNode hrnIthOldest = og.id2hrn.get(as.getIthOldest(i));
//			betaSet = new ReachabilitySet().makeCanonical();
//			itrEdge = hrnIthOldest.iteratorToReferencees();
			Iterator<ReferenceEdge> itrEdge = hrnIthOldest.iteratorToReferencers();
			while (itrEdge.hasNext()) {
				ReferenceEdge edge = itrEdge.next();
				betaSet = betaSet.union(edge.getBeta());
			}
		}

		Iterator<TokenTupleSet> ttSetIter = betaSet.iterator();
		while (ttSetIter.hasNext()) {
			TokenTupleSet tokenTupleSet = (TokenTupleSet) ttSetIter.next();
			Iterator iter = tokenTupleSet.iterator();
			while (iter.hasNext()) {
				TokenTuple tt = (TokenTuple) iter.next();
				int token = tt.getToken();
				String uniqueID = og.id2hrn.get(new Integer(token))
						.getGloballyUniqueIdentifier();
				GloballyUniqueTokenTuple gtt = new GloballyUniqueTokenTuple(
						uniqueID, tt);
			}
		}
		return betaSet;
	}
	
	private void conflictGraph_nodeAction(MethodContext mc, FlatMethod fm,
			FlatNode fn,Hashtable<TempDescriptor, TempDescriptor> invarMap) {

		switch (fn.kind()) {

		case FKind.FlatSESEEnterNode: {

			FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
			OwnershipGraph og = ownAnalysisForSESEConflicts
					.getOwnvershipGraphByMethodContext(mc);

			if (!fsen.getIsCallerSESEplaceholder()) {
			  Collection<TempDescriptor> invar_set = fsen.getInVarSet();
				
				SESESummary seseSummary=seseSummaryMap.get(fsen);
				ConflictGraph conflictGraph=null;
				conflictGraph=conflictGraphResults.get(seseSummary.getCurrentParent());
				
				if(conflictGraph==null){
					conflictGraph = new ConflictGraph(og);
				}
				

				for (Iterator iterator = invar_set.iterator(); iterator
						.hasNext();) {
					TempDescriptor tempDescriptor = (TempDescriptor) iterator
							.next();
					
					if(!tempDescriptor.getType().isArray() && tempDescriptor.getType().isImmutable()){
						continue;
					}
					
					// effects set
					SESEEffectsSet seseEffectsSet = fsen.getSeseEffectsSet();
					Set<SESEEffectsKey> readEffectsSet = seseEffectsSet
							.getReadingSet(tempDescriptor);
					
					if (readEffectsSet != null) {
						for (Iterator iterator2 = readEffectsSet.iterator(); iterator2
								.hasNext();) {
							SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator2
									.next();
							String uniqueID = seseEffectsKey.getHRNUniqueId();
							HeapRegionNode node = og.gid2hrn.get(uniqueID);
							if(node.isParameter()){
								seseEffectsKey.setRSet(packupStates(og,node));
							}else{
								AllocationSite as = node.getAllocationSite();
								seseEffectsKey.setRSet(packupStates(og,as));
							}
						}
					}
					
					if (readEffectsSet != null) {
						for (Iterator iterator2 = readEffectsSet.iterator(); iterator2
								.hasNext();) {
							SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator2
							.next();
						}
					}
					Set<SESEEffectsKey> writeEffectsSet = seseEffectsSet
							.getWritingSet(tempDescriptor);
										
					if (writeEffectsSet != null) {
						for (Iterator iterator2 = writeEffectsSet.iterator(); iterator2
								.hasNext();) {
							SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator2
									.next();
							String uniqueID = seseEffectsKey.getHRNUniqueId();
							HeapRegionNode node = og.gid2hrn.get(uniqueID);
							
							if(node.isParameter()){
								seseEffectsKey.setRSet(packupStates(og,node));
							}else{
								AllocationSite as = node.getAllocationSite();
								seseEffectsKey.setRSet(packupStates(og,as));
							}
						}
					}
					
					Set<SESEEffectsKey> strongUpdateSet = seseEffectsSet.getStrongUpdateSet(tempDescriptor);		
					
					Set<Set> reachabilitySet = calculateReachabilitySet(og,
							tempDescriptor);

					// add new live-in node
					
					OwnershipGraph lastOG = ownAnalysis
					.getOwnvershipGraphByMethodContext(mc);
					LabelNode ln = lastOG.td2ln.get(tempDescriptor);
					
					
					Set<HeapRegionNode> hrnSet = new HashSet<HeapRegionNode>();
					Iterator<ReferenceEdge> refIter = ln
							.iteratorToReferencees();
					while (refIter.hasNext()) {
						ReferenceEdge referenceEdge = (ReferenceEdge) refIter
								.next();
						//
						SESEEffectsSet seseEffects=fsen.getSeseEffectsSet();
						int taintIdentifier=fsen.getSeseEffectsSet().getTaint(referenceEdge);
						int invarIdx=fsen.getSeseEffectsSet().getInVarIdx(tempDescriptor);
						int inVarMask=(int) Math.pow(2,invarIdx);
						if((inVarMask&taintIdentifier)>0){
							// find tainted edge, add heap root to live-in node
							hrnSet.add(referenceEdge.getDst());
						}
						//
					}
					
					conflictGraph.addLiveInNode(tempDescriptor, hrnSet, fsen,
							readEffectsSet, writeEffectsSet, strongUpdateSet, reachabilitySet);
				}
				
				
				if(conflictGraph.id2cn.size()>0){
					conflictGraphResults.put(seseSummary.getCurrentParent(),conflictGraph);
				}
				
			}

		}

			break;
			
		}

	}
	
	private void seseConflictsForward(JavaCallGraph javaCallGraph) {

		Set methodCallSet = javaCallGraph.getAllMethods(typeUtil.getMain());

		// topologically sort java call chain so that leaf calls are ordered
		// first
		LinkedList<MethodDescriptor> sortedMethodCalls = topologicalSort(
				methodCallSet, javaCallGraph);

		for (Iterator iterator = sortedMethodCalls.iterator(); iterator
				.hasNext();) {

			MethodDescriptor md = (MethodDescriptor) iterator.next();

			FlatMethod fm = state.getMethodFlat(md);

			HashSet<MethodContext> mcSet = ownAnalysis
					.getAllMethodContextSetByDescriptor(md);
			Iterator<MethodContext> mcIter = mcSet.iterator();

			currentMethodSummary = new MethodSummary();
			preeffectsSet = new HashSet<PreEffectsKey>();

			// iterates over all possible method context
			while (mcIter.hasNext()) {
				MethodContext mc = mcIter.next();

				LinkedList<FlatNode> flatNodesToVisit=new LinkedList<FlatNode>();
				flatNodesToVisit.add(fm);

				SESESummary summary = new SESESummary(null, fm);
				seseSummaryMap.put(fm, summary);
				
				Hashtable<TempDescriptor, TempDescriptor> invarMap=new Hashtable<TempDescriptor,TempDescriptor>();

				while (!flatNodesToVisit.isEmpty()) {
					FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
					flatNodesToVisit.remove(fn);
					ParentChildConflictsMap prevResult = conflictsResults
							.get(fn);

					// merge sets from control flow
					Boolean prevSESE=null;
					ParentChildConflictsMap currentConflictsMap = new ParentChildConflictsMap();
					for (int i = 0; i < fn.numPrev(); i++) {
						FlatNode prevFlatNode = fn.getPrev(i);
						ParentChildConflictsMap incoming = conflictsResults
								.get(prevFlatNode);
						if (incoming != null) {
							currentConflictsMap.merge(incoming);
						}
						
						if(prevFlatNode instanceof FlatCondBranch){
							prevSESE=isAfterChildSESEIndicatorMap.get(prevFlatNode);
						}
					}
					SESESummary currentSummary = seseSummaryMap.get(fn);
					//if (currentSummary == null) {
					if(!(fn instanceof FlatMethod)){
						FlatNode current = null;
						FlatNode currentParent = null;
						// calculate sese summary info from previous flat nodes

						for (int i = 0; i < fn.numPrev(); i++) {
							FlatNode prevFlatNode = fn.getPrev(i);
							SESESummary prevSummary = seseSummaryMap
									.get(prevFlatNode);
							if (prevSummary != null) {
								if (prevFlatNode instanceof FlatSESEExitNode
										&& !((FlatSESEExitNode) prevFlatNode)
												.getFlatEnter()
												.getIsCallerSESEplaceholder()) {
									current = prevSummary.getCurrentParent();
									SESESummary temp = seseSummaryMap
											.get(current);
									currentParent = temp.getCurrentParent();
								} else {
									current = prevSummary.getCurrentSESE();
									currentParent = prevSummary
											.getCurrentParent();
								}
								
								break;
							}
						}

						currentSummary = new SESESummary(currentParent, current);
						seseSummaryMap.put(fn, currentSummary);
					}
					
					if(prevSESE!=null){
						if(fn instanceof FlatSESEEnterNode){
							isAfterChildSESEIndicatorMap.put(currentSummary.getCurrentSESE(), currentConflictsMap.isAfterSESE());
						}else{
							isAfterChildSESEIndicatorMap.put(currentSummary.getCurrentSESE(), prevSESE);
						}
					}
					
					Boolean b=isAfterChildSESEIndicatorMap.get(currentSummary.getCurrentSESE());;
					if(b==null){
						currentConflictsMap.setIsAfterSESE(false);
					}else{
						currentConflictsMap.setIsAfterSESE(b.booleanValue());
					}

					FlatNode tempP=currentSummary.getCurrentParent();
					FlatNode tempS=currentSummary.getCurrentSESE();

					conflicts_nodeAction(mc, fn, callGraph, preeffectsSet,
							currentConflictsMap, currentSummary,invarMap);

					
					// if we have a new result, schedule forward nodes for
					// analysis
					if (!currentConflictsMap.equals(prevResult)) {
						seseSummaryMap.put(fn, currentSummary);
						conflictsResults.put(fn, currentConflictsMap);
						for (int i = 0; i < fn.numNext(); i++) {
							FlatNode nn = fn.getNext(i);
							flatNodesToVisit.addFirst(nn);
						}
					}

				}

			}

		}

	}
	
	
	private void conflicts_nodeAction(MethodContext mc, FlatNode fn,
			CallGraph callGraph, HashSet<PreEffectsKey> preeffectsSet,
			ParentChildConflictsMap currentConflictsMap,
			SESESummary currentSummary,
			Hashtable<TempDescriptor, TempDescriptor> invarMap) {

		OwnershipGraph og = ownAnalysis.getOwnvershipGraphByMethodContext(mc);
		
		currentConflictsMap.clearStallMap();

		switch (fn.kind()) {

		case FKind.FlatSESEEnterNode: {

			FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

			if (!fsen.getIsCallerSESEplaceholder()) {
				FlatNode parentNode = currentSummary.getCurrentSESE();
				currentSummary.setCurrentParent(parentNode);
				currentSummary.setCurrentSESE(fsen);
//				seseSummaryMap.put(fsen, currentSummary);
			}

			if (!fsen.getIsCallerSESEplaceholder()) {
				currentMethodSummary.increaseChildSESECount();
			}
			if (currentMethodSummary.getChildSESECount() == 1) {
				// need to store pre-effects
				currentMethodSummary.getEffectsSet().addAll(preeffectsSet);
				for (Iterator iterator = currentMethodSummary.getEffectsSet()
						.iterator(); iterator.hasNext();) {
					PreEffectsKey preEffectsKey = (PreEffectsKey) iterator
							.next();
				}
				preeffectsSet.clear();
			}
		}
			break;

		case FKind.FlatSESEExitNode: {

			FlatSESEExitNode fsen = (FlatSESEExitNode) fn;

			if (!fsen.getFlatEnter().getIsCallerSESEplaceholder()) {
				// all object variables are inaccessible.
				isAfterChildSESEIndicatorMap.put(currentSummary
						.getCurrentParent(), new Boolean(true));
			}
//			currentConflictsMap = new ParentChildConflictsMap();
			currentConflictsMap.clear();

		}
			break;
			
		case FKind.FlatCondBranch: {
			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}
			isAfterChildSESEIndicatorMap.put(fn, new Boolean(isAfterChildSESE));
		}
		break;
		
		case FKind.FlatNew: {

			FlatNew fnew = (FlatNew) fn;

			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}

			if (isAfterChildSESE) {
				TempDescriptor dst = fnew.getDst();
				currentConflictsMap.addAccessibleVar(dst);
			}

		}
			break;
			
		case FKind.FlatElementNode:{
			
			
			FlatElementNode fen = (FlatElementNode) fn;
			TempDescriptor src=fen.getSrc();
			
			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}
			
			if(isAfterChildSESE){
				
				if (!currentConflictsMap.isAccessible(src)) {
					if(invarMap.containsKey(src)){
						currentConflictsMap.addStallSite(src, new HashSet<HeapRegionNode>(),
								new StallTag(fn),invarMap.get(src));
					}else{
						currentConflictsMap.addStallSite(src, new HashSet<HeapRegionNode>(),
								new StallTag(fn),null);
					}
				}
				currentConflictsMap.addAccessibleVar(src);
				
				// contribute read effect on source's stall site
				currentConflictsMap.contributeEffect(src, "", "",
						StallSite.READ_EFFECT);
				
			}
			
			if (currentMethodSummary.getChildSESECount() == 0) {
				// analyze preeffects
				preEffectAnalysis(og, src, null, PreEffectsKey.READ_EFFECT);
			}
			
			
		} break;

		case FKind.FlatFieldNode: {

			FlatFieldNode ffn = (FlatFieldNode) fn;
			TempDescriptor dst = ffn.getDst();
			TempDescriptor src = ffn.getSrc();
			FieldDescriptor field = ffn.getField();

			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}

			if (isAfterChildSESE) {
				
				if (!currentConflictsMap.isAccessible(src)) {
					HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(
							og, src);
					currentConflictsMap.addStallSite(src, refHRN,
							new StallTag(fn),null);

					// flag stall site for disjoint analysis
					for (Iterator iterator2 = refHRN.iterator(); iterator2
							.hasNext();) {
						HeapRegionNode hrn = (HeapRegionNode) iterator2
								.next();
						if (hrn.isParameter()) {
							// if stall site is paramter heap region, need
							// to decompose into caller's
							HashSet<HeapRegionNode> visitedHRN = new HashSet<HeapRegionNode>();
							visitedHRN.add(hrn);
							setupRelatedAllocSiteAnalysis(og, mc, hrn,
									visitedHRN);
						} else {
//							System.out.println("FLAGGED "+mc+":"+ffn);
							flagAllocationSite(mc, hrn.getAllocationSite());
						}
					}

				}
				currentConflictsMap.addAccessibleVar(src);

				// contribute read effect on source's stall site
				currentConflictsMap.contributeEffect(src, field
						.getType().getSafeSymbol(), field.getSymbol(),
						StallSite.READ_EFFECT);
				
				if(field.getType().isImmutable()){
					currentConflictsMap.addAccessibleVar(dst);
				}
			
			}

			if (currentMethodSummary.getChildSESECount() == 0) {
				// analyze preeffects
				preEffectAnalysis(og, src, field, PreEffectsKey.READ_EFFECT);
			}

		}
			break;

		case FKind.FlatSetElementNode:{
			
			FlatSetElementNode fsen=(FlatSetElementNode)fn;			
			TempDescriptor dst = fsen.getDst();
			TempDescriptor src = fsen.getSrc();
			
			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}
			
			if (isAfterChildSESE) {
				
				if (!currentConflictsMap.isAccessible(src)) {
					HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(og,
							src);
					currentConflictsMap.addStallSite(src, refHRN , new StallTag(
							fn),null);
				}
				currentConflictsMap.addAccessibleVar(src);
				
				if (!currentConflictsMap.isAccessible(dst)) {
					if(invarMap.containsKey(dst)){
						currentConflictsMap.addStallSite(dst, new 	HashSet<HeapRegionNode>(),
								new StallTag(fn),invarMap.get(dst));
					}else{
						currentConflictsMap.addStallSite(dst, new 	HashSet<HeapRegionNode>(),
								new StallTag(fn),null);
					}
				}
				currentConflictsMap.addAccessibleVar(dst);
				// contribute write effect on destination's stall site
				currentConflictsMap.contributeEffect(dst, "","",
						StallSite.WRITE_EFFECT);
				
			}
			
			if (currentMethodSummary.getChildSESECount() == 0) {
				// analyze preeffects
				preEffectAnalysis(og, dst, null, PreEffectsKey.WRITE_EFFECT);
			}
			
		} break;
			
		case FKind.FlatSetFieldNode: {

			FlatSetFieldNode fsen = (FlatSetFieldNode) fn;
			TempDescriptor dst = fsen.getDst();
			FieldDescriptor field = fsen.getField();
			TempDescriptor src = fsen.getSrc();

			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}

			if (isAfterChildSESE) {

				if (!currentConflictsMap.isAccessible(src)) {
					HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(og,
							src);
					currentConflictsMap.addStallSite(src, refHRN, new StallTag(
							fn),null);

					// flag stall site for disjoint analysis
					for (Iterator iterator2 = refHRN.iterator(); iterator2
							.hasNext();) {
						HeapRegionNode hrn = (HeapRegionNode) iterator2.next();

						if (hrn.isParameter()) {
							// if stall site is paramter heap region, need
							// to decompose into caller's
							HashSet<HeapRegionNode> visitedHRN = new HashSet<HeapRegionNode>();
							visitedHRN.add(hrn);
							setupRelatedAllocSiteAnalysis(og, mc, hrn,
									visitedHRN);
						} else {
							flagAllocationSite(mc, hrn.getAllocationSite());
						}

					}

				}
				currentConflictsMap.addAccessibleVar(src);


				if (!currentConflictsMap.isAccessible(dst)) {
					HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(
							og, dst);
					currentConflictsMap.addStallSite(dst, refHRN,
							new StallTag(fn),null);

					// flag stall site for disjoint analysis
					for (Iterator iterator2 = refHRN.iterator(); iterator2
							.hasNext();) {
						HeapRegionNode hrn = (HeapRegionNode) iterator2
								.next();
						if (hrn.isParameter()) {
							// if stall site is paramter heap region, need
							// to decompose into caller's
							HashSet<HeapRegionNode> visitedHRN = new HashSet<HeapRegionNode>();
							visitedHRN.add(hrn);
							setupRelatedAllocSiteAnalysis(og, mc, hrn,
									visitedHRN);
						} else {
							flagAllocationSite(mc, hrn.getAllocationSite());
						}
					}
				}

				currentConflictsMap.addAccessibleVar(dst);
				// contribute write effect on destination's stall site
				currentConflictsMap.contributeEffect(dst, field
						.getType().getSafeSymbol(), field.getSymbol(),
						StallSite.WRITE_EFFECT);
			

				// TODO need to create edge mapping for newly created edge
				HashSet<ReferenceEdge> edges = getRefEdgeSetReferenceToSameHRN(
						og, dst);

				StallSite ss = currentConflictsMap.getStallMap().get(dst);
				if (ss != null) {
					for (Iterator iterator = edges.iterator(); iterator
							.hasNext();) {
						ReferenceEdge referenceEdge = (ReferenceEdge) iterator
								.next();
						if (!(referenceEdge.getSrc() instanceof LabelNode)) {
							currentConflictsMap.addStallEdge(referenceEdge,
									new StallTag(fn));
						}
					}
				}
			}

			if (currentMethodSummary.getChildSESECount() == 0) {
				// analyze preeffects
				preEffectAnalysis(og, dst, field, PreEffectsKey.WRITE_EFFECT);
			}

		}
			break;

		case FKind.FlatOpNode: {
			
			FlatOpNode fon = (FlatOpNode) fn;

			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			
				
			if( fon.getOp().getOp() ==Operation.ASSIGN){
				invarMap.put(fon.getDest(), fon.getLeft());
			}
			
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}

			if (isAfterChildSESE) {

				// destination variable gets the status of source.

				if (fon.getOp().getOp() == Operation.ASSIGN) {

					TempDescriptor dst = fon.getDest();
					TempDescriptor src = fon.getLeft();

					Integer sourceStatus = currentConflictsMap
							.getAccessibleMap().get(src);
					if (sourceStatus == null) {
						sourceStatus = ParentChildConflictsMap.INACCESSIBLE;
					}

					HashSet<TempDescriptor> dstTempSet = getTempDescSetReferenceToSameHRN(
							og, dst);

					for (Iterator<TempDescriptor> iterator = dstTempSet
							.iterator(); iterator.hasNext();) {
						TempDescriptor possibleDst = iterator.next();

						if (sourceStatus
								.equals(ParentChildConflictsMap.ACCESSIBLE)) {
							currentConflictsMap.addAccessibleVar(possibleDst);
						} else {
							currentConflictsMap.addInaccessibleVar(possibleDst);

						}

					}
				}

			}
		}
			break;

		case FKind.FlatCall: {

			FlatCall fc = (FlatCall) fn;

			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}

			int base = 0;
			if (!fc.getMethod().isStatic()) {
				base = 1;
			}

			FlatMethod calleeFM = state.getMethodFlat(fc.getMethod());

			// retrieve callee's method summary
			MethodSummary calleeMethodSummary = methodSummaryResults
					.get(calleeFM);

			if (calleeMethodSummary != null
					&& calleeMethodSummary.getChildSESECount() > 0) {

				// when parameter variable is accessible,
				// use callee's preeffects to figure out about how it affects
				// caller's stall site

				for (int i = 0; i < fc.numArgs(); i++) {
					TempDescriptor paramTemp = fc.getArg(i);

					if (isAfterChildSESE) {
						if (currentConflictsMap.isAccessible(paramTemp)
								&& currentConflictsMap.hasStallSite(paramTemp)) {
							// preeffect contribute its effect to caller's stall
							// site

							int offset = 0;
							if (!fc.getMethod().isStatic()) {
								offset = 1;
							}

							HashSet<PreEffectsKey> preeffectSet = calleeMethodSummary
									.getEffectsSetByParamIdx(i + offset);

							for (Iterator iterator = preeffectSet.iterator(); iterator
									.hasNext();) {
								PreEffectsKey preEffectsKey = (PreEffectsKey) iterator
										.next();
								currentConflictsMap.contributeEffect(paramTemp,
										preEffectsKey.getType(), preEffectsKey
												.getField(), preEffectsKey
												.getEffectType());
							}
						}
					}
					// in other cases, child SESE has not been discovered,
					// assumes that all variables are accessible

				}

				// If callee has at least one child sese, all parent object
				// is going to be inaccessible.
				// currentConflictsMap = new ParentChildConflictsMap();
				currentConflictsMap.makeAllInaccessible();
				isAfterChildSESEIndicatorMap.put(currentSummary
						.getCurrentSESE(), new Boolean(true));

				TempDescriptor returnTemp = fc.getReturnTemp();

				if (calleeMethodSummary.getReturnValueAccessibility().equals(
						MethodSummary.ACCESSIBLE)) {
					// when return value is accessible, associate with its
					// stall site
					currentConflictsMap.addAccessibleVar(returnTemp);

					StallSite returnStallSite = calleeMethodSummary
							.getReturnStallSite().copy();
					// handling parameter regions
					HashSet<Integer> stallParamIdx = returnStallSite
							.getCallerParamIdxSet();
					for (Iterator iterator = stallParamIdx.iterator(); iterator
							.hasNext();) {
						Integer idx = (Integer) iterator.next();

						int paramIdx = idx.intValue() - base;
						TempDescriptor paramTD = fc.getArg(paramIdx);

						// TODO: resolve callee's parameter heap regions by
						// following call chain

					}

					// flag stall site's allocation sites for disjointness
					// analysis
					HashSet<HeapRegionNode> hrnSet = returnStallSite
							.getHRNSet();
					for (Iterator iterator = hrnSet.iterator(); iterator
							.hasNext();) {
						HeapRegionNode hrn = (HeapRegionNode) iterator.next();
						if (hrn.isParameter()) {
							// if stall site is paramter heap region, need to
							// decompose into caller's
							HashSet<HeapRegionNode> visitedHRN = new HashSet<HeapRegionNode>();
							visitedHRN.add(hrn);
							setupRelatedAllocSiteAnalysis(og, mc, hrn,
									visitedHRN);
						} else {
							flagAllocationSite(mc, hrn.getAllocationSite());
						}
					}

					currentConflictsMap.addStallSite(returnTemp,
							returnStallSite);

				} else if (calleeMethodSummary.getReturnValueAccessibility()
						.equals(MethodSummary.INACCESSIBLE)) {
					// when return value is inaccessible
					currentConflictsMap.addInaccessibleVar(returnTemp);
				}

				// TODO: need to handle edge mappings from callee
				Set<Integer> stallParamIdx = calleeMethodSummary
						.getStallParamIdxSet();
				for (Iterator iterator = stallParamIdx.iterator(); iterator
						.hasNext();) {
					Integer paramIdx = (Integer) iterator.next();
					HashSet<StallTag> stallTagSet = calleeMethodSummary
							.getStallTagByParamIdx(paramIdx);

					int argIdx = paramIdx.intValue() - base;
					TempDescriptor argTD = fc.getArg(argIdx);

					putStallTagOnReferenceEdges(og, argTD, stallTagSet,
							currentConflictsMap);
				}
			}

		}
			break;

		/*
		 * do we need this case? case FKind.FlatLiteralNode: {
		 * 
		 * if (currentConflictsMap.isAfterChildSESE()) { FlatLiteralNode fln =
		 * (FlatLiteralNode) fn; TempDescriptor dst = fln.getDst();
		 * currentConflictsMap.addAccessibleVar(dst); }
		 * 
		 * } break;
		 */

		case FKind.FlatReturnNode: {

			FlatReturnNode frn = (FlatReturnNode) fn;
			TempDescriptor returnTD = frn.getReturnTemp();

			boolean isAfterChildSESE = false;
			FlatNode current = currentSummary.getCurrentSESE();
			Boolean isAfter = isAfterChildSESEIndicatorMap.get(current);
			if (isAfter != null && isAfter.booleanValue()) {
				isAfterChildSESE = true;
			}

			if (returnTD != null) {
				if (!isAfterChildSESE) {
					// in this case, all variables are accessible. There are no
					// child SESEs.
				} else {
					if (currentConflictsMap.isAccessible(returnTD)) {

						currentMethodSummary
								.setReturnValueAccessibility(MethodSummary.ACCESSIBLE);
						StallSite returnStallSite = currentConflictsMap
								.getStallMap().get(returnTD);

						HashSet<HeapRegionNode> stallSiteHRNSet = returnStallSite
								.getHRNSet();
						for (Iterator iterator = stallSiteHRNSet.iterator(); iterator
								.hasNext();) {
							HeapRegionNode stallSiteHRN = (HeapRegionNode) iterator
									.next();
							Set<Integer> paramSet = og.idPrimary2paramIndexSet
									.get(stallSiteHRN.getID());
							returnStallSite.addCallerParamIdxSet(paramSet);
							paramSet = og.idSecondary2paramIndexSet
									.get(stallSiteHRN.getID());
							returnStallSite.addCallerParamIdxSet(paramSet);
						}

						currentMethodSummary
								.setReturnStallSite(returnStallSite);

					} else {
						currentMethodSummary
								.setReturnValueAccessibility(MethodSummary.INACCESSIBLE);
					}
				}
			}
		}
			break;

		case FKind.FlatExit: {

			// store method summary when it has at least one child SESE
			if (currentMethodSummary.getChildSESECount() > 0) {
				// current flat method
				FlatMethod fm = state.getMethodFlat(mc.getDescriptor());
				Set<TempDescriptor> stallTempSet = currentConflictsMap
						.getStallMap().keySet();
				for (Iterator iterator = stallTempSet.iterator(); iterator
						.hasNext();) {
					TempDescriptor stallTD = (TempDescriptor) iterator.next();
					StallSite stallSite = currentConflictsMap.getStallMap()
							.get(stallTD);

					HashSet<HeapRegionNode> stallSiteHRNSet = stallSite
							.getHRNSet();
					for (Iterator iterator2 = stallSiteHRNSet.iterator(); iterator2
							.hasNext();) {
						HeapRegionNode stallSiteHRN = (HeapRegionNode) iterator2
								.next();

						if (stallSiteHRN.isParameter()) {

							Set<Integer> paramSet = og.idPrimary2paramIndexSet
									.get(stallSiteHRN.getID());
							currentMethodSummary.addStallParamIdxSet(paramSet,
									stallSite.getStallTagSet());

							paramSet = og.idSecondary2paramIndexSet
									.get(stallSiteHRN.getID());
							currentMethodSummary.addStallParamIdxSet(paramSet,
									stallSite.getStallTagSet());
						}

					}

				}
				methodSummaryResults.put(fm, currentMethodSummary);
			}
		}
			break;

		}

//		seseSummaryMap.put(fn, currentSummary);

	}

	private void putStallTagOnReferenceEdges(OwnershipGraph og,
			TempDescriptor argTD, HashSet stallTagSet,
			ParentChildConflictsMap currentConflictsMap) {
				
		LabelNode ln=og.td2ln.get(argTD);
		if(ln!=null){
			
			Iterator<ReferenceEdge> refrenceeIter=ln.iteratorToReferencees();
			while (refrenceeIter.hasNext()) {
				ReferenceEdge refEdge = (ReferenceEdge) refrenceeIter.next();
				HeapRegionNode stallHRN=refEdge.getDst();
				
				Iterator<ReferenceEdge> referencerIter=stallHRN.iteratorToReferencers();
				while (referencerIter.hasNext()) {
					ReferenceEdge referencer = (ReferenceEdge) referencerIter
							.next();
					for (Iterator iterator = stallTagSet.iterator(); iterator
							.hasNext();) {
						StallTag stallTag = (StallTag) iterator.next();
						currentConflictsMap.addStallEdge(referencer, stallTag);
					}
				}
			}
		}
	}

	private void preEffectAnalysis(OwnershipGraph og, TempDescriptor td,
			FieldDescriptor field, Integer effectType) {

		// analyze preeffects
		HashSet<HeapRegionNode> hrnSet = getReferenceHeapIDSet(og, td);
		for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
			HeapRegionNode hrn = (HeapRegionNode) iterator.next();
			if (hrn.isParameter()) {
				// effects on param heap region

				Set<Integer> paramSet = og.idPrimary2paramIndexSet.get(hrn
						.getID());

				if (paramSet != null) {
					Iterator<Integer> paramIter = paramSet.iterator();
					while (paramIter.hasNext()) {
						Integer paramID = paramIter.next();
						PreEffectsKey effectKey=null;
						if(field!=null){
							effectKey = new PreEffectsKey(paramID,
									field.getSymbol(), field.getType()
											.getSafeSymbol(), effectType);
						}else{
							effectKey = new PreEffectsKey(paramID,
									"", "", effectType);
						}
						preeffectsSet.add(effectKey);
					}
				}

				// check weather this heap region is parameter
				// reachable...

				paramSet = og.idSecondary2paramIndexSet.get(hrn.getID());
				if (paramSet != null) {
					Iterator<Integer> paramIter = paramSet.iterator();

					while (paramIter.hasNext()) {
						Integer paramID = paramIter.next();
						PreEffectsKey effectKey=null;
						if(field!=null){
							effectKey = new PreEffectsKey(paramID,
									field.getSymbol(), field.getType()
											.getSafeSymbol(), effectType);
						}else{
							effectKey = new PreEffectsKey(paramID,
									"", "", effectType);
						}
						preeffectsSet.add(effectKey);
					}
				}

			}
		}
	}
	
  private void codePlansForward( FlatMethod fm ) {
    
    // start from flat method top, visit every node in
    // method exactly once
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );

    Set<FlatNode> visited = new HashSet<FlatNode>();    

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      // use incoming results as "dot statement" or just
      // before the current statement
      VarSrcTokTable dotSTtable = new VarSrcTokTable();
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );
	dotSTtable.merge( variableResults.get( nn ) );
      }

      // find dt-st notAvailableSet also
      Set<TempDescriptor> dotSTnotAvailSet = new HashSet<TempDescriptor>();      
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );       
	Set<TempDescriptor> notAvailIn = notAvailableResults.get( nn );
        if( notAvailIn != null ) {
	  dotSTnotAvailSet.addAll( notAvailIn );
        }
      }

      Set<TempDescriptor> dotSTlive = livenessRootView.get( fn );

      if( !seseStack.empty() ) {
	codePlans_nodeActions( fn, 
			       dotSTlive,
			       dotSTtable,
			       dotSTnotAvailSet,
			       seseStack.peek()
			       );
      }

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );
	}
      }
    }
  }

  private void codePlans_nodeActions( FlatNode fn,
				      Set<TempDescriptor> liveSetIn,
				      VarSrcTokTable vstTableIn,
				      Set<TempDescriptor> notAvailSetIn,
				      FlatSESEEnterNode currentSESE ) {
    
    CodePlan plan = new CodePlan( currentSESE);

    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      assert fsen.equals( currentSESE );

      // track the source types of the in-var set so generated
      // code at this SESE issue can compute the number of
      // dependencies properly
      Iterator<TempDescriptor> inVarItr = fsen.getInVarSet().iterator();
      while( inVarItr.hasNext() ) {
	TempDescriptor inVar = inVarItr.next();

        // when we get to an SESE enter node we change the
        // currentSESE variable of this analysis to the
        // child that is declared by the enter node, so
        // in order to classify in-vars correctly, pass
        // the parent SESE in--at other FlatNode types just
        // use the currentSESE
        VSTWrapper vstIfStatic = new VSTWrapper();
	Integer srcType = 
	  vstTableIn.getRefVarSrcType( inVar,
				       fsen.getParent(),
                                       vstIfStatic
                                       );

	// the current SESE needs a local space to track the dynamic
	// variable and the child needs space in its SESE record
	if( srcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  fsen.addDynamicInVar( inVar );
	  fsen.getParent().addDynamicVar( inVar );

	} else if( srcType.equals( VarSrcTokTable.SrcType_STATIC ) ) {
	  fsen.addStaticInVar( inVar );
	  VariableSourceToken vst = vstIfStatic.vst;
	  fsen.putStaticInVar2src( inVar, vst );
	  fsen.addStaticInVarSrc( new SESEandAgePair( vst.getSESE(), 
						      vst.getAge() 
						    ) 
				);
	} else {
	  assert srcType.equals( VarSrcTokTable.SrcType_READY );
	  fsen.addReadyInVar( inVar );
	}	
      }

    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
    } break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	TempDescriptor lhs = fon.getDest();
	TempDescriptor rhs = fon.getLeft();        

	// if this is an op node, don't stall, copy
	// source and delay until we need to use value

	// ask whether lhs and rhs sources are dynamic, static, etc.
        VSTWrapper vstIfStatic = new VSTWrapper();
	Integer lhsSrcType
	  = vstTableIn.getRefVarSrcType( lhs,
					 currentSESE,
                                         vstIfStatic
                                         );
	Integer rhsSrcType
	  = vstTableIn.getRefVarSrcType( rhs,
					 currentSESE,
                                         vstIfStatic
                                         );

	if( rhsSrcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  // if rhs is dynamic going in, lhs will definitely be dynamic
	  // going out of this node, so track that here	  
	  plan.addDynAssign( lhs, rhs );
	  currentSESE.addDynamicVar( lhs );
	  currentSESE.addDynamicVar( rhs );

	} else if( lhsSrcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  // otherwise, if the lhs is dynamic, but the rhs is not, we
	  // need to update the variable's dynamic source as "current SESE"
	  plan.addDynAssign( lhs );
	}       

	// only break if this is an ASSIGN op node,
	// otherwise fall through to default case
	break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {          

      // a node with no live set has nothing to stall for
      if( liveSetIn == null ) {
	break;
      }

      TempDescriptor[] readarray = fn.readsTemps();
      for( int i = 0; i < readarray.length; i++ ) {
        TempDescriptor readtmp = readarray[i];

	// ignore temps that are definitely available 
	// when considering to stall on it
	if( !notAvailSetIn.contains( readtmp ) ) {
	  continue;
	}

	// check the source type of this variable
        VSTWrapper vstIfStatic = new VSTWrapper();
	Integer srcType 
	  = vstTableIn.getRefVarSrcType( readtmp,
					 currentSESE,
                                         vstIfStatic
                                         );

	if( srcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  // 1) It is not clear statically where this variable will
	  // come from, so dynamically we must keep track
	  // along various control paths, and therefore when we stall,
	  // just stall for the exact thing we need and move on
	  plan.addDynamicStall( readtmp );
	  currentSESE.addDynamicVar( readtmp );	 

	} else if( srcType.equals( VarSrcTokTable.SrcType_STATIC ) ) {	  
	  // 2) Single token/age pair: Stall for token/age pair, and copy
	  // all live variables with same token/age pair at the same
	  // time.  This is the same stuff that the notavaialable analysis 
	  // marks as now available.	  
	  VariableSourceToken vst = vstIfStatic.vst;

	  Iterator<VariableSourceToken> availItr = 
	    vstTableIn.get( vst.getSESE(), vst.getAge() ).iterator();

	  while( availItr.hasNext() ) {
	    VariableSourceToken vstAlsoAvail = availItr.next();

	    // only grab additional stuff that is live
	    Set<TempDescriptor> copySet = new HashSet<TempDescriptor>();

	    Iterator<TempDescriptor> refVarItr = vstAlsoAvail.getRefVars().iterator();
	    while( refVarItr.hasNext() ) {
	      TempDescriptor refVar = refVarItr.next();
	      if( liveSetIn.contains( refVar ) ) {
		copySet.add( refVar );
	      }
	    }

	    if( !copySet.isEmpty() ) {
	      plan.addStall2CopySet( vstAlsoAvail, copySet );
	    }
	  }	  	  	 

	} else {
	  // the other case for srcs is READY, so do nothing
	}

	// assert that everything being stalled for is in the
	// "not available" set coming into this flat node and
	// that every VST identified is in the possible "stall set"
	// that represents VST's from children SESE's

      }      
    } break;
      
    } // end switch


    // identify sese-age pairs that are statically useful
    // and should have an associated SESE variable in code
    // JUST GET ALL SESE/AGE NAMES FOR NOW, PRUNE LATER,
    // AND ALWAYS GIVE NAMES TO PARENTS
    Set<VariableSourceToken> staticSet = vstTableIn.get();
    Iterator<VariableSourceToken> vstItr = staticSet.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();

      // placeholder source tokens are useful results, but
      // the placeholder static name is never needed
      if( vst.getSESE().getIsCallerSESEplaceholder() ) {
	continue;
      }

      FlatSESEEnterNode sese = currentSESE;
      while( sese != null ) {
	sese.addNeededStaticName( 
				 new SESEandAgePair( vst.getSESE(), vst.getAge() ) 
				  );
	sese.mustTrackAtLeastAge( vst.getAge() );
      	
	sese = sese.getParent();
      }
    }


    codePlans.put( fn, plan );


    // if any variables at this-node-*dot* have a static source (exactly one vst)
    // but go to a dynamic source at next-node-*dot*, create a new IR graph
    // node on that edge to track the sources dynamically
    VarSrcTokTable thisVstTable = variableResults.get( fn );
    for( int i = 0; i < fn.numNext(); i++ ) {
      FlatNode            nn           = fn.getNext( i );
      VarSrcTokTable      nextVstTable = variableResults.get( nn );
      Set<TempDescriptor> nextLiveIn   = livenessRootView.get( nn );

      // the table can be null if it is one of the few IR nodes
      // completely outside of the root SESE scope
      if( nextVstTable != null && nextLiveIn != null ) {

	Hashtable<TempDescriptor, VSTWrapper> readyOrStatic2dynamicSet = 
	  thisVstTable.getReadyOrStatic2DynamicSet( nextVstTable, 
                                                    nextLiveIn,
                                                    currentSESE
                                                    );
	
	if( !readyOrStatic2dynamicSet.isEmpty() ) {

	  // either add these results to partial fixed-point result
	  // or make a new one if we haven't made any here yet
	  FlatEdge fe = new FlatEdge( fn, nn );
	  FlatWriteDynamicVarNode fwdvn = wdvNodesToSpliceIn.get( fe );

	  if( fwdvn == null ) {
	    fwdvn = new FlatWriteDynamicVarNode( fn, 
						 nn,
						 readyOrStatic2dynamicSet,
						 currentSESE
						 );
	    wdvNodesToSpliceIn.put( fe, fwdvn );
	  } else {
	    fwdvn.addMoreVar2Src( readyOrStatic2dynamicSet );
	  }
	}
      }
    }
  }


  public void writeReports( String timeReport ) throws java.io.IOException {

    BufferedWriter bw = new BufferedWriter( new FileWriter( "mlpReport_summary.txt" ) );
    bw.write( "MLP Analysis Results\n\n" );
    bw.write( timeReport+"\n\n" );
    printSESEHierarchy( bw );
    bw.write( "\n" );
    printSESEInfo( bw );
    bw.close();

    Iterator<Descriptor> methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      MethodDescriptor md = (MethodDescriptor) methItr.next();      
      FlatMethod       fm = state.getMethodFlat( md );
      bw = new BufferedWriter( new FileWriter( "mlpReport_"+
					       md.getClassMethodName()+
					       md.getSafeMethodDescriptor()+
					       ".txt" ) );
      bw.write( "MLP Results for "+md+"\n-------------------\n");
      
      FlatSESEEnterNode implicitSESE = (FlatSESEEnterNode) fm.getNext(0);
      if( !implicitSESE.getIsCallerSESEplaceholder() &&
    	   implicitSESE != mainSESE
    	   ) {
    	  System.out.println( implicitSESE+" is not implicit?!" );
    	  System.exit( -1 );
      }
      bw.write( "Dynamic vars to manage:\n  "+implicitSESE.getDynamicVarSet());
      
      bw.write( "\n\nLive-In, Root View\n------------------\n"          +fm.printMethod( livenessRootView ) );
      bw.write( "\n\nVariable Results-Out\n----------------\n"          +fm.printMethod( variableResults ) );
      bw.write( "\n\nNot Available Results-Out\n---------------------\n"+fm.printMethod( notAvailableResults ) );
      bw.write( "\n\nCode Plans\n----------\n"                          +fm.printMethod( codePlans ) );
      bw.write("\n\nSESE Effects\n----------------------\n"+printSESEEffects());
      bw.close();
    }
  }
  
	private String printSESEEffects() {

		StringWriter writer = new StringWriter();

		Iterator<FlatSESEEnterNode> keyIter = allSESEs.iterator();

		while (keyIter.hasNext()) {
			FlatSESEEnterNode seseEnter = keyIter.next();
			String result = seseEnter.getSeseEffectsSet().printSet();
			if (result.length() > 0) {
				writer.write("\nSESE " + seseEnter + "\n");
				writer.write(result);
			}
		}
		keyIter = rootSESEs.iterator();
		while (keyIter.hasNext()) {
			FlatSESEEnterNode seseEnter = keyIter.next();
			if (seseEnter.getIsCallerSESEplaceholder()) {
				if (!seseEnter.getChildren().isEmpty()) {
					String result = seseEnter.getSeseEffectsSet().printSet();
					if (result.length() > 0) {
						writer.write("\nSESE " + seseEnter + "\n");
						writer.write(result);
					}
				}
			}
		}

		return writer.toString();

	}

  private void printSESEHierarchy( BufferedWriter bw ) throws java.io.IOException {
    bw.write( "SESE Hierarchy\n--------------\n" ); 
    Iterator<FlatSESEEnterNode> rootItr = rootSESEs.iterator();
    while( rootItr.hasNext() ) {
      FlatSESEEnterNode root = rootItr.next();
      if( root.getIsCallerSESEplaceholder() ) {
	if( !root.getChildren().isEmpty() ) {
	  printSESEHierarchyTree( bw, root, 0 );
	}
      } else {
	printSESEHierarchyTree( bw, root, 0 );
      }
    }
  }

  private void printSESEHierarchyTree( BufferedWriter bw,
				       FlatSESEEnterNode fsen,
				       int depth 
				     ) throws java.io.IOException {
    for( int i = 0; i < depth; ++i ) {
      bw.write( "  " );
    }
    bw.write( "- "+fsen.getPrettyIdentifier()+"\n" );

    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESEHierarchyTree( bw, fsenChild, depth + 1 );
    }
  }

  
  private void printSESEInfo( BufferedWriter bw ) throws java.io.IOException {
    bw.write("\nSESE info\n-------------\n" ); 
    Iterator<FlatSESEEnterNode> rootItr = rootSESEs.iterator();
    while( rootItr.hasNext() ) {
      FlatSESEEnterNode root = rootItr.next();
      if( root.getIsCallerSESEplaceholder() ) {
	if( !root.getChildren().isEmpty() ) {
	  printSESEInfoTree( bw, root );
	}
      } else {
	printSESEInfoTree( bw, root );
      }
    }
  }

  private void printSESEInfoTree( BufferedWriter bw,
				  FlatSESEEnterNode fsen 
				) throws java.io.IOException {

    if( !fsen.getIsCallerSESEplaceholder() ) {
      bw.write( "SESE "+fsen.getPrettyIdentifier()+" {\n" );

      bw.write( "  in-set: "+fsen.getInVarSet()+"\n" );
      Iterator<TempDescriptor> tItr = fsen.getInVarSet().iterator();
      while( tItr.hasNext() ) {
	TempDescriptor inVar = tItr.next();
	if( fsen.getReadyInVarSet().contains( inVar ) ) {
	  bw.write( "    (ready)  "+inVar+"\n" );
	}
	if( fsen.getStaticInVarSet().contains( inVar ) ) {
	  bw.write( "    (static) "+inVar+" from "+
                    fsen.getStaticInVarSrc( inVar )+"\n" );
	} 
	if( fsen.getDynamicInVarSet().contains( inVar ) ) {
	  bw.write( "    (dynamic)"+inVar+"\n" );
	}
      }
      
      bw.write( "   Dynamic vars to manage: "+fsen.getDynamicVarSet()+"\n");
      
      bw.write( "  out-set: "+fsen.getOutVarSet()+"\n" );
      bw.write( "}\n" );
    }

    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESEInfoTree( bw, fsenChild );
    }
  }
  
  public Hashtable <FlatNode, ConflictGraph> getConflictGraphResults(){
	  return conflictGraphResults;
  }
  
  public Hashtable < FlatNode, ParentChildConflictsMap > getConflictsResults(){
	  return conflictsResults;
  }
  
  public Hashtable<FlatNode, SESESummary> getSeseSummaryMap(){
	  return seseSummaryMap;
  }
  
  public Hashtable<ConflictGraph, HashSet<SESELock>> getConflictGraphLockMap(){
	  return conflictGraphLockMap;
  }
  
}
