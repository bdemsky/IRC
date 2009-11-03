package Analysis.MLP;

import Analysis.CallGraph.*;
import Analysis.Liveness;
import Analysis.OwnershipAnalysis.*;
import IR.*;
import IR.Flat.*;
import IR.Tree.*;
import java.util.*;
import java.io.*;


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

  private Hashtable< FlatEdge, FlatWriteDynamicVarNode  > wdvNodesToSpliceIn;
  
  private Hashtable< MethodContext, HashSet<AllocationSite>> mapMethodContextToLiveInAllocationSiteSet;
  
  private Hashtable < FlatNode, ParentChildConflictsMap > conflictsResults;
  private Hashtable< FlatMethod, MethodSummary > methodSummaryResults;
  private OwnershipAnalysis ownAnalysisForSESEConflicts;
  
  // temporal data structures to track analysis progress.
  private MethodSummary currentMethodSummary;
  private HashSet<PreEffectsKey> preeffectsSet;

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
    
    mapMethodContextToLiveInAllocationSiteSet = new Hashtable< MethodContext, HashSet<AllocationSite>>();
    
    conflictsResults = new Hashtable < FlatNode, ParentChildConflictsMap >();
    methodSummaryResults=new Hashtable<FlatMethod, MethodSummary>();

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
    
     // disjoint analysis with a set of flagged allocation sites of live-in variables & stall sites
	try {
	  OwnershipAnalysis oa2 = new OwnershipAnalysis(state, 
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
	
	postSESEConflictsForward(javaCallGraph);

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
      notAvailSet.clear();
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode  fsexn = (FlatSESEExitNode)  fn;
      FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      assert currentSESE.getChildren().contains( fsen );
      notAvailSet.addAll( fsen.getOutVarSet() );
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

	Integer srcType = 
	  vstTable.getRefVarSrcType( rTemp, 
				     currentSESE,
				     currentSESE.getParent() );

	if( srcType.equals( VarSrcTokTable.SrcType_STATIC ) ) {

	  VariableSourceToken vst = vstTable.get( rTemp ).iterator().next();

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
	      Integer srcTypeAlso = 
		vstTable.getRefVarSrcType( refVarAlso, 
					   currentSESE,
					   currentSESE.getParent() );
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

			while (!flatNodesToVisit.isEmpty()) {
				FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
				flatNodesToVisit.remove(fn);

				Stack<FlatSESEEnterNode> seseStack = seseStacks.get(fn);
				assert seseStack != null;

				if (!seseStack.empty()) {
					effects_nodeActions(mc, fn, seseStack.peek(), callGraph);
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
			FlatSESEEnterNode currentSESE, CallGraph callGraph) {

		OwnershipGraph og = ownAnalysis.getOwnvershipGraphByMethodContext(mc);

		switch (fn.kind()) {

		case FKind.FlatSESEEnterNode: {

			FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
			assert fsen.equals(currentSESE);
			
			if (!fsen.getIsCallerSESEplaceholder()) {
				// uniquely taint each live-in variable
				Set<TempDescriptor> set = fsen.getInVarSet();
				Iterator<TempDescriptor> iter = set.iterator();
				int idx = 0;
				while (iter.hasNext()) {
					TempDescriptor td = iter.next();
					LabelNode ln = og.td2ln.get(td);
					if (ln != null) {
						int taint = (int) Math.pow(2, idx);
						taintLabelNode(ln, taint);

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
									.getTypeDescriptor(), seseKey.getHRNId()));
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
									.getTypeDescriptor(), seseKey.getHRNId()));
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
									.getTypeDescriptor(), seseKey.getHRNId()));
						}

						parentstrongUpdateTable.put(td, parentEffectsSet);
					}

				}

			}

		}
			break;

		case FKind.FlatFieldNode: {

			FlatFieldNode ffn = (FlatFieldNode) fn;
			TempDescriptor src = ffn.getSrc();
			FieldDescriptor field = ffn.getField();

			LabelNode srcLN = og.td2ln.get(src);
			if (srcLN != null) {
				HashSet<TempDescriptor> affectedTDSet = getAccessedTaintNodeSet(srcLN);
				Iterator<TempDescriptor> affectedIter = affectedTDSet
						.iterator();
				while (affectedIter.hasNext()) {
					TempDescriptor affectedTD = affectedIter.next();

					if (currentSESE.getInVarSet().contains(affectedTD)) {

						HashSet<HeapRegionNode> hrnSet = getReferenceHeapIDSet(
								og, affectedTD);
						Iterator<HeapRegionNode> hrnIter = hrnSet.iterator();
						while (hrnIter.hasNext()) {
							HeapRegionNode hrn = hrnIter.next();

							Iterator<ReferenceEdge> referencers = hrn
									.iteratorToReferencers();
							while (referencers.hasNext()) {
								ReferenceEdge referenceEdge = (ReferenceEdge) referencers
										.next();
								if (field.getSymbol().equals(
										referenceEdge.getField())) {
									currentSESE.readEffects(affectedTD, field
											.getSymbol(), src.getType(),
											referenceEdge.getDst().getID());
								}
							}

						}
					}
				}

				// handle tainted case

				Iterator<ReferenceEdge> edgeIter = srcLN
						.iteratorToReferencees();
				while (edgeIter.hasNext()) {
					ReferenceEdge edge = edgeIter.next();
					HeapRegionNode accessHRN = edge.getDst();
					// / follow the chain of reference to identify possible
					// accesses
					Iterator<ReferenceEdge> referIter = accessHRN
							.iteratorToReferencers();
					while (referIter.hasNext()) {
						ReferenceEdge referEdge = (ReferenceEdge) referIter
								.next();

						// if (referEdge.getTaintIdentifier() >0 ||
						// referEdge.getSESETaintIdentifier()>0 ) {
						HashSet<TempDescriptor> referSet = new HashSet<TempDescriptor>();
						followReference(accessHRN, referSet,
								new HashSet<HeapRegionNode>(), currentSESE);

						Iterator<TempDescriptor> referSetIter = referSet
								.iterator();
						while (referSetIter.hasNext()) {
							TempDescriptor tempDescriptor = (TempDescriptor) referSetIter
									.next();
							currentSESE.readEffects(tempDescriptor, field
									.getSymbol(), src.getType(), accessHRN
									.getID());
						}
						// }
					}
					// /
					if (edge.getTaintIdentifier() > 0
							|| edge.getSESETaintIdentifier() > 0) {

						affectedTDSet = getReferenceNodeSet(accessHRN);
						affectedIter = affectedTDSet.iterator();
						while (affectedIter.hasNext()) {
							TempDescriptor affectedTD = affectedIter.next();

							if (currentSESE.getInVarSet().contains(affectedTD)) {

								HashSet<HeapRegionNode> hrnSet = getReferenceHeapIDSet(
										og, affectedTD);
								Iterator<HeapRegionNode> hrnIter = hrnSet
										.iterator();
								while (hrnIter.hasNext()) {
									HeapRegionNode hrn = hrnIter.next();
									currentSESE.readEffects(affectedTD, field
											.getSymbol(), src.getType(), hrn
											.getID());
								}

							}

						}
					}
				}
			}

		}
			break;

		case FKind.FlatSetFieldNode: {

			FlatSetFieldNode fsen = (FlatSetFieldNode) fn;
			TempDescriptor dst = fsen.getDst();
			FieldDescriptor field = fsen.getField();

			LabelNode dstLN = og.td2ln.get(dst);
			if (dstLN != null) {
				// check possible strong updates
				boolean strongUpdate = false;

				if (!field.getType().isImmutable() || field.getType().isArray()) {
					Iterator<ReferenceEdge> itrXhrn = dstLN
							.iteratorToReferencees();
					while (itrXhrn.hasNext()) {
						ReferenceEdge edgeX = itrXhrn.next();
						HeapRegionNode hrnX = edgeX.getDst();

						// we can do a strong update here if one of two cases
						// holds
						if (field != null
								&& field != OwnershipAnalysis
										.getArrayField(field.getType())
								&& ((hrnX.getNumReferencers() == 1) || // case 1
								(hrnX.isSingleObject() && dstLN
										.getNumReferencees() == 1) // case 2
								)) {
							strongUpdate = true;
						}
					}
				}

				HashSet<TempDescriptor> affectedTDSet = getAccessedTaintNodeSet(dstLN);

				Iterator<TempDescriptor> affectedIter = affectedTDSet
						.iterator();

				while (affectedIter.hasNext()) {
					TempDescriptor affectedTD = affectedIter.next();
					if (currentSESE.getInVarSet().contains(affectedTD)) {

						HashSet<HeapRegionNode> hrnSet = getReferenceHeapIDSet(
								og, affectedTD);
						Iterator<HeapRegionNode> hrnIter = hrnSet.iterator();
						while (hrnIter.hasNext()) {
							HeapRegionNode hrn = hrnIter.next();

							Iterator<ReferenceEdge> referencers = hrn
									.iteratorToReferencers();
							while (referencers.hasNext()) {
								ReferenceEdge referenceEdge = (ReferenceEdge) referencers
										.next();
								if (field.getSymbol().equals(
										referenceEdge.getField())) {
									currentSESE.writeEffects(affectedTD, field
											.getSymbol(), dst.getType(),
											referenceEdge.getDst().getID(),
											strongUpdate);
								}
							}

						}
					}
				}

				// handle tainted case
				Iterator<ReferenceEdge> edgeIter = dstLN
						.iteratorToReferencees();
				while (edgeIter.hasNext()) {
					ReferenceEdge edge = edgeIter.next();

					HeapRegionNode accessHRN = edge.getDst();
					// / follow the chain of reference to identify possible
					// accesses
					Iterator<ReferenceEdge> referIter = accessHRN
							.iteratorToReferencers();
					while (referIter.hasNext()) {
						ReferenceEdge referEdge = (ReferenceEdge) referIter
								.next();

						// if (referEdge.getTaintIdentifier() > 0 ||
						// referEdge.getSESETaintIdentifier() > 0 ) {
						HashSet<TempDescriptor> referSet = new HashSet<TempDescriptor>();
						followReference(accessHRN, referSet,
								new HashSet<HeapRegionNode>(), currentSESE);
						Iterator<TempDescriptor> referSetIter = referSet
								.iterator();
						while (referSetIter.hasNext()) {
							TempDescriptor tempDescriptor = (TempDescriptor) referSetIter
									.next();
							currentSESE.writeEffects(tempDescriptor, field
									.getSymbol(), dst.getType(), accessHRN
									.getID(), strongUpdate);
						}
						// }
					}
					// /
					if (edge.getTaintIdentifier() > 0
							|| edge.getSESETaintIdentifier() > 0) {
						affectedTDSet = getReferenceNodeSet(accessHRN);
						affectedIter = affectedTDSet.iterator();
						while (affectedIter.hasNext()) {
							TempDescriptor affectedTD = affectedIter.next();
							if (currentSESE.getInVarSet().contains(affectedTD)) {

								HashSet<HeapRegionNode> hrnSet = getReferenceHeapIDSet(
										og, affectedTD);
								Iterator<HeapRegionNode> hrnIter = hrnSet
										.iterator();
								while (hrnIter.hasNext()) {
									HeapRegionNode hrn = hrnIter.next();
									currentSESE.writeEffects(affectedTD, field
											.getSymbol(), dst.getType(), hrn
											.getID(), strongUpdate);

								}

							}

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

			int base;
			if (((MethodDescriptor) calleeMC.getDescriptor()).isStatic()) {
				base = 0;
			} else {
				base = 1;
			}

			for (int i = 0; i < fc.numArgs(); i++) {

				TempDescriptor arg = fc.getArg(i);
				Set<EffectsKey> readSet = me.getEffects().getReadingSet(
						i + base);
				Set<EffectsKey> writeSet = me.getEffects().getWritingSet(
						i + base);

				Set<EffectsKey> strongUpdateSet = me.getEffects()
						.getStrongUpdateSet(i + base);

				LabelNode argLN = og.td2ln.get(arg);
				if (argLN != null) {
					HashSet<TempDescriptor> affectedTDSet = getAccessedTaintNodeSet(argLN);
					Iterator<TempDescriptor> affectedIter = affectedTDSet
							.iterator();

					while (affectedIter.hasNext()) {

						TempDescriptor affectedTD = affectedIter.next();
						if (currentSESE.getInVarSet().contains(affectedTD)) {

							if (readSet != null) {
								Iterator<EffectsKey> readIter = readSet
										.iterator();
								while (readIter.hasNext()) {
									EffectsKey key = readIter.next();
									Set<Integer> hrnSet = getCallerHRNId(
											new Integer(i + base), calleeOG,
											key.getHRNId(), decomp);
									Iterator<Integer> hrnIter = hrnSet
											.iterator();
									while (hrnIter.hasNext()) {
										Integer hrnID = (Integer) hrnIter
												.next();
										currentSESE.readEffects(affectedTD, key
												.getFieldDescriptor(), key
												.getTypeDescriptor(), hrnID);
									}
								}
							}

							if (writeSet != null) {
								Iterator<EffectsKey> writeIter = writeSet
										.iterator();
								while (writeIter.hasNext()) {
									EffectsKey key = writeIter.next();

									Set<Integer> hrnSet = getCallerHRNId(
											new Integer(i + base), calleeOG,
											key.getHRNId(), decomp);
									Iterator<Integer> hrnIter = hrnSet
											.iterator();
									while (hrnIter.hasNext()) {
										Integer hrnID = (Integer) hrnIter
												.next();
										currentSESE.writeEffects(affectedTD,
												key.getFieldDescriptor(), key
														.getTypeDescriptor(),
												hrnID, false);
									}

								}
							}

							if (strongUpdateSet != null) {
								Iterator<EffectsKey> strongUpdateIter = strongUpdateSet
										.iterator();
								while (strongUpdateIter.hasNext()) {
									EffectsKey key = strongUpdateIter.next();

									Set<Integer> hrnSet = getCallerHRNId(
											new Integer(i + base), calleeOG,
											key.getHRNId(), decomp);
									Iterator<Integer> hrnIter = hrnSet
											.iterator();
									while (hrnIter.hasNext()) {
										Integer hrnID = (Integer) hrnIter
												.next();
										currentSESE.writeEffects(affectedTD,
												key.getFieldDescriptor(), key
														.getTypeDescriptor(),
												hrnID, true);
									}

								}
							}

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
	
	private void taintLabelNode(LabelNode ln, int identifier) {

		Iterator<ReferenceEdge> edgeIter = ln.iteratorToReferencees();
		while (edgeIter.hasNext()) {
			ReferenceEdge edge = edgeIter.next();
			HeapRegionNode hrn = edge.getDst();

			Iterator<ReferenceEdge> edgeReferencerIter = hrn
					.iteratorToReferencers();
			while (edgeReferencerIter.hasNext()) {
				ReferenceEdge referencerEdge = edgeReferencerIter.next();
				OwnershipNode node = referencerEdge.getSrc();
				if (node instanceof LabelNode) {
					referencerEdge.unionSESETaintIdentifier(identifier);
				}else if(node instanceof HeapRegionNode){
					referencerEdge.unionSESETaintIdentifier(identifier);
				}
			}

		}

	}
	
	private HashSet<TempDescriptor> getReferenceNodeSet(HeapRegionNode hrn){
		
		HashSet<TempDescriptor> returnSet=new HashSet<TempDescriptor>();
		
		Iterator<ReferenceEdge> edgeIter=hrn.iteratorToReferencers();
		while(edgeIter.hasNext()){
			ReferenceEdge edge=edgeIter.next();
			if(edge.getSrc() instanceof LabelNode){
				LabelNode ln=(LabelNode)edge.getSrc();
				returnSet.add(ln.getTempDescriptor());
			}
		}
		
		return returnSet;
		
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
	
	
	private HashSet<TempDescriptor> getAccessedTaintNodeSet(LabelNode ln) {

		HashSet<TempDescriptor> returnSet = new HashSet<TempDescriptor>();

		Iterator<ReferenceEdge> edgeIter = ln.iteratorToReferencees();
		while (edgeIter.hasNext()) {
			ReferenceEdge edge = edgeIter.next();
			HeapRegionNode hrn = edge.getDst();

			Iterator<ReferenceEdge> edgeReferencerIter = hrn
					.iteratorToReferencers();
			while (edgeReferencerIter.hasNext()) {
				ReferenceEdge referencerEdge = edgeReferencerIter.next();

				if (referencerEdge.getSrc() instanceof LabelNode) {
					if (!((LabelNode) referencerEdge.getSrc()).equals(ln)) {

						if (referencerEdge.getSESETaintIdentifier() > 0) {
							TempDescriptor td = ((LabelNode) referencerEdge
									.getSrc()).getTempDescriptor();
							returnSet.add(td);
						}
					}
				}
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
	 
	private void postSESEConflictsForward(JavaCallGraph javaCallGraph) {

		// store the reachability set in stall site data structure 
		Set methodCallSet = javaCallGraph.getAllMethods(typeUtil.getMain());
		LinkedList<MethodDescriptor> sortedMethodCalls = topologicalSort(
				methodCallSet, javaCallGraph);

		for (Iterator iterator = sortedMethodCalls.iterator(); iterator
				.hasNext();) {
			MethodDescriptor md = (MethodDescriptor) iterator.next();
			FlatMethod fm = state.getMethodFlat(md);

			HashSet<MethodContext> mcSet = ownAnalysis
					.getAllMethodContextSetByDescriptor(md);
			Iterator<MethodContext> mcIter = mcSet.iterator();

			while (mcIter.hasNext()) {
				MethodContext mc = mcIter.next();

				Set<FlatNode> visited = new HashSet<FlatNode>();

				Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
				flatNodesToVisit.add(fm);

				while (!flatNodesToVisit.isEmpty()) {
					FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
					flatNodesToVisit.remove(fn);
					visited.add(fn);

					ParentChildConflictsMap currentConflictsMap = conflictsResults
							.get(fn);

					postConflicts_nodeAction(mc, fn,
							currentConflictsMap);

					// if we have a new result, schedule forward nodes for
					// analysis
					conflictsResults.put(fn, currentConflictsMap);
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
	
	private void postConflicts_nodeAction(MethodContext mc, FlatNode fn,
			ParentChildConflictsMap currentConflictsMap) {
		
		OwnershipGraph og = ownAnalysisForSESEConflicts.getOwnvershipGraphByMethodContext(mc);
		
		Hashtable<TempDescriptor,StallSite> stallMap=currentConflictsMap.getStallMap();
		Set<TempDescriptor> keySet=stallMap.keySet();
		
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			TempDescriptor key = (TempDescriptor) iterator.next();
			StallSite stallSite=stallMap.get(key);
			
			Set<HeapRegionNode> hrnSet=stallSite.getHRNSet();
			for (Iterator iterator2 = hrnSet.iterator(); iterator2.hasNext();) {
				HeapRegionNode hrn = (HeapRegionNode) iterator2
						.next();

				HeapRegionNode hrnOG=og.id2hrn.get(hrn.getID());
				if(hrnOG!=null){
					ReachabilitySet rSet=hrnOG.getAlpha();
					Iterator<TokenTupleSet> ttIterator=rSet.iterator();
					while (ttIterator.hasNext()) {
						TokenTupleSet tts = (TokenTupleSet) ttIterator.next();
						stallSite.addTokenTupleSet(tts);
					}
				}

			}
		}
		
		//DEBUG
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			TempDescriptor key = (TempDescriptor) iterator.next();
			StallSite stallSite=stallMap.get(key);
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

			while (mcIter.hasNext()) {
				MethodContext mc = mcIter.next();

				Set<FlatNode> visited = new HashSet<FlatNode>();
                                              
				Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
				flatNodesToVisit.add(fm);

				while (!flatNodesToVisit.isEmpty()) {
					FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
					flatNodesToVisit.remove(fn);
					visited.add(fn);

					ParentChildConflictsMap prevResult = conflictsResults
							.get(fn);

					// merge sets from control flow
					ParentChildConflictsMap currentConflictsMap = new ParentChildConflictsMap();
					for (int i = 0; i < fn.numPrev(); i++) {
						FlatNode prevFlatNode = fn.getPrev(i);
						ParentChildConflictsMap incoming = conflictsResults
								.get(prevFlatNode);
						if (incoming != null) {
							currentConflictsMap.merge(incoming);
						}
					}

					conflicts_nodeAction(mc, fn, callGraph, preeffectsSet,
							currentConflictsMap);

					// if we have a new result, schedule forward nodes for
					// analysis
					if (!currentConflictsMap.isAfterChildSESE()) {
						conflictsResults.put(fn, currentConflictsMap);
						for (int i = 0; i < fn.numNext(); i++) {
							FlatNode nn = fn.getNext(i);
							if (!visited.contains(nn)) {
								flatNodesToVisit.add(nn);
							}
						}
					} else {
						if (!currentConflictsMap.equals(prevResult)) {
							conflictsResults.put(fn, currentConflictsMap);
							for (int i = 0; i < fn.numNext(); i++) {
								FlatNode nn = fn.getNext(i);
								flatNodesToVisit.add(nn);
							}
						}
					}

				}
			}
			methodSummaryResults.put(fm, currentMethodSummary);
		}

		// if the method has at least one child SESE, we need to calculate the
		// reachability set of its stall sites.
		for (Iterator iterator = sortedMethodCalls.iterator(); iterator
				.hasNext();) {
			MethodDescriptor md = (MethodDescriptor) iterator.next();
			FlatMethod fm = state.getMethodFlat(md);
			
			
		}
		
		
		
		
		/*
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
				addLiveInAllocationSite(mc, dstHRN
						.getAllocationSite());
			}
		}
		*/
		

		// if(currentConflictsMap.isAfterChildSESE()){
		//			
		// }

	}
	
	private void conflicts_nodeAction(MethodContext mc, FlatNode fn,
			CallGraph callGraph, HashSet<PreEffectsKey> preeffectsSet,ParentChildConflictsMap currentConflictsMap) {

		OwnershipGraph og = ownAnalysis.getOwnvershipGraphByMethodContext(mc);

		switch (fn.kind()) {

		case FKind.FlatSESEEnterNode: {
			
			FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
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
				currentConflictsMap.setAfterChildSESE(true);
				currentConflictsMap = new ParentChildConflictsMap();
			}
		
		}
			break;

		case FKind.FlatNew: {

			if (currentConflictsMap.isAfterChildSESE()) {
				FlatNew fnew = (FlatNew) fn;
				TempDescriptor dst = fnew.getDst();
				currentConflictsMap.addAccessibleVar(dst);
			}

		}
			break;

		case FKind.FlatFieldNode: {

			FlatFieldNode ffn = (FlatFieldNode) fn;
			TempDescriptor dst = ffn.getDst();
			TempDescriptor src = ffn.getSrc();
			FieldDescriptor field = ffn.getField();
			
			if (currentConflictsMap.isAfterChildSESE()) {
				
				HashSet<TempDescriptor> srcTempSet = getTempDescSetReferenceToSameHRN(
						og, src);
				for (Iterator iterator = srcTempSet.iterator(); iterator
						.hasNext();) {
					TempDescriptor possibleSrc = (TempDescriptor) iterator
							.next();
					if (!currentConflictsMap.isAccessible(possibleSrc)) {
						HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(
								og, possibleSrc);
						currentConflictsMap.addStallSite(possibleSrc, refHRN,
								new StallTag(fn));

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

					currentConflictsMap.addAccessibleVar(possibleSrc);

					// contribute read effect on source's stall site
					currentConflictsMap.contributeEffect(possibleSrc, field.getType()
							.getSafeSymbol(), field.toString(),
							StallSite.READ_EFFECT);
				}

								HashSet<TempDescriptor> dstTempSet = getTempDescSetReferenceToSameHRN(
						og, dst);
				for (Iterator iterator = dstTempSet.iterator(); iterator
						.hasNext();) {
					TempDescriptor possibleDst = (TempDescriptor) iterator
							.next();
					currentConflictsMap.addAccessibleVar(possibleDst);
				}
			}

			if (currentMethodSummary.getChildSESECount() == 0) {
				// analyze preeffects
				preEffectAnalysis(og, src, field, PreEffectsKey.READ_EFFECT);
			}

		}
			break;

		case FKind.FlatSetFieldNode: {

			FlatSetFieldNode fsen = (FlatSetFieldNode) fn;
			TempDescriptor dst = fsen.getDst();
			FieldDescriptor field = fsen.getField();
			TempDescriptor src = fsen.getSrc();
			
			if (currentConflictsMap.isAfterChildSESE()) {

				HashSet<TempDescriptor> srcTempSet = getTempDescSetReferenceToSameHRN(
						og, src);
				for (Iterator iterator = srcTempSet.iterator(); iterator
						.hasNext();) {
					TempDescriptor possibleSrc = (TempDescriptor) iterator
							.next();
					if (!currentConflictsMap.isAccessible(possibleSrc)) {
						HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(
								og, possibleSrc);
						currentConflictsMap.addStallSite(possibleSrc, refHRN,
								new StallTag(fn));

						// flag stall site for disjoint analysis
						for (Iterator iterator2 = refHRN.iterator(); iterator2
								.hasNext();) {
							HeapRegionNode hrn = (HeapRegionNode) iterator2
									.next();
							
							if (hrn.isParameter()) {
								// if stall site is paramter heap region, need to decompose into caller's
								HashSet<HeapRegionNode> visitedHRN = new HashSet<HeapRegionNode>();
								visitedHRN.add(hrn);
								setupRelatedAllocSiteAnalysis(og, mc, hrn,
										visitedHRN);
							} else {
								flagAllocationSite(mc, hrn.getAllocationSite());
							}
							
						}

					}
					currentConflictsMap.addAccessibleVar(possibleSrc);
				}

				HashSet<TempDescriptor> dstTempSet = getTempDescSetReferenceToSameHRN(
						og, dst);
				for (Iterator iterator = dstTempSet.iterator(); iterator
						.hasNext();) {
					TempDescriptor possibleDst = (TempDescriptor) iterator
							.next();

					if (!currentConflictsMap.isAccessible(possibleDst)) {
						HashSet<HeapRegionNode> refHRN = getReferenceHeapIDSet(
								og, possibleDst);
						currentConflictsMap.addStallSite(possibleDst, refHRN,
								new StallTag(fn));

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
					
					currentConflictsMap.addAccessibleVar(possibleDst);
					// contribute write effect on destination's stall site
					currentConflictsMap.contributeEffect(possibleDst, field
							.getType().getSafeSymbol(), field.toString(),
							StallSite.WRITE_EFFECT);
				}

				// TODO need to create edge mapping for newly created edge
				HashSet<ReferenceEdge> edges = getRefEdgeSetReferenceToSameHRN(
						og, dst);

				StallSite ss = currentConflictsMap.getStallMap().get(dst);
				if (ss != null) {
					for (Iterator iterator = edges.iterator(); iterator
							.hasNext();) {
						ReferenceEdge referenceEdge = (ReferenceEdge) iterator
								.next();
						if(!(referenceEdge.getSrc() instanceof LabelNode)){
							currentConflictsMap.addStallEdge(referenceEdge,new StallTag(fn));
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
			if (currentConflictsMap.isAfterChildSESE()) {

				// destination variable gets the status of source.
				FlatOpNode fon = (FlatOpNode) fn;

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

					if (currentConflictsMap.isAfterChildSESE()) {
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
				currentConflictsMap.setAfterChildSESE(true);

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

					// flag stall site's allocation sites for disjointness analysis 
					HashSet<HeapRegionNode> hrnSet=returnStallSite.getHRNSet();
					for (Iterator iterator = hrnSet.iterator(); iterator
							.hasNext();) {
						HeapRegionNode hrn = (HeapRegionNode) iterator
								.next();
						if (hrn.isParameter()) {
							// if stall site is paramter heap region, need to decompose into caller's
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

		case FKind.FlatReturnNode: {

			FlatReturnNode frn = (FlatReturnNode) fn;
			TempDescriptor returnTD = frn.getReturnTemp();

			if (returnTD != null) {
				if (!currentConflictsMap.isAfterChildSESE()) {
					// in this case, all variables are accessible. There are no
					// child SESEs.
				} else {
					if (currentConflictsMap.isAccessible(returnTD)) {
						
						currentMethodSummary
								.setReturnValueAccessibility(MethodSummary.ACCESSIBLE);
						StallSite returnStallSite = currentConflictsMap
								.getStallMap().get(returnTD);
						
						HashSet<HeapRegionNode> stallSiteHRNSet=returnStallSite.getHRNSet();
						for (Iterator iterator = stallSiteHRNSet.iterator(); iterator
								.hasNext();) {
							HeapRegionNode stallSiteHRN = (HeapRegionNode) iterator
									.next();
							Set<Integer> paramSet=og.idPrimary2paramIndexSet
							.get(stallSiteHRN.getID());
							returnStallSite.addCallerParamIdxSet(paramSet);
							paramSet=og.idSecondary2paramIndexSet
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
				Set<TempDescriptor> stallTempSet=currentConflictsMap.getStallMap().keySet();
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
						PreEffectsKey effectKey = new PreEffectsKey(paramID,
								field.toString(), field.getType()
										.getSafeSymbol(), effectType);
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
						PreEffectsKey effectKey = new PreEffectsKey(paramID,
								field.toString(), field.getType()
										.getSafeSymbol(), effectType);
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

      // track the source types of the in-var set so generated
      // code at this SESE issue can compute the number of
      // dependencies properly
      Iterator<TempDescriptor> inVarItr = fsen.getInVarSet().iterator();
      while( inVarItr.hasNext() ) {
	TempDescriptor inVar = inVarItr.next();
	Integer srcType = 
	  vstTableIn.getRefVarSrcType( inVar, 
				       fsen,
				       fsen.getParent() );

	// the current SESE needs a local space to track the dynamic
	// variable and the child needs space in its SESE record
	if( srcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  fsen.addDynamicInVar( inVar );
	  fsen.getParent().addDynamicVar( inVar );

	} else if( srcType.equals( VarSrcTokTable.SrcType_STATIC ) ) {
	  fsen.addStaticInVar( inVar );
	  VariableSourceToken vst = vstTableIn.get( inVar ).iterator().next();
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
	Integer lhsSrcType
	  = vstTableIn.getRefVarSrcType( lhs,
					 currentSESE,
					 currentSESE.getParent() );

	Integer rhsSrcType
	  = vstTableIn.getRefVarSrcType( rhs,
					 currentSESE,
					 currentSESE.getParent() );

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
	Integer srcType 
	  = vstTableIn.getRefVarSrcType( readtmp,
					 currentSESE,
					 currentSESE.getParent() );

	if( srcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  // 1) It is not clear statically where this variable will
	  // come from statically, so dynamically we must keep track
	  // along various control paths, and therefore when we stall,
	  // just stall for the exact thing we need and move on
	  plan.addDynamicStall( readtmp );
	  currentSESE.addDynamicVar( readtmp );	 

	} else if( srcType.equals( VarSrcTokTable.SrcType_STATIC ) ) {	  
	  // 2) Single token/age pair: Stall for token/age pair, and copy
	  // all live variables with same token/age pair at the same
	  // time.  This is the same stuff that the notavaialable analysis 
	  // marks as now available.	  

	  VariableSourceToken vst = vstTableIn.get( readtmp ).iterator().next();

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

	Hashtable<TempDescriptor, VariableSourceToken> static2dynamicSet = 
	  thisVstTable.getStatic2DynamicSet( nextVstTable, 
					     nextLiveIn,
					     currentSESE,
					     currentSESE.getParent() 
					   );
	
	if( !static2dynamicSet.isEmpty() ) {

	  // either add these results to partial fixed-point result
	  // or make a new one if we haven't made any here yet
	  FlatEdge fe = new FlatEdge( fn, nn );
	  FlatWriteDynamicVarNode fwdvn = wdvNodesToSpliceIn.get( fe );

	  if( fwdvn == null ) {
	    fwdvn = new FlatWriteDynamicVarNode( fn, 
						 nn,
						 static2dynamicSet,
						 currentSESE
						 );
	    wdvNodesToSpliceIn.put( fe, fwdvn );
	  } else {
	    fwdvn.addMoreVar2Src( static2dynamicSet );
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
	  bw.write( "    (static) "+inVar+"\n" );
	} 
	if( fsen.getDynamicInVarSet().contains( inVar ) ) {
	  bw.write( "    (dynamic)"+inVar+"\n" );
	}
      }
      
      bw.write( "  out-set: "+fsen.getOutVarSet()+"\n" );
      bw.write( "}\n" );
    }

    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESEInfoTree( bw, fsenChild );
    }
  }
}
