package Analysis.MLP;

import Analysis.CallGraph.*;
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

  private FlatSESEEnterNode      rootSESE;  
  private Set<FlatSESEEnterNode> allSESEs;

  private Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessRootView;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessVirtualReads;
  private Hashtable< FlatNode, VarSrcTokTable           > variableResults;
  private Hashtable< FlatNode, Set<TempDescriptor>      > notAvailableResults;
  private Hashtable< FlatNode, CodePlan                 > codePlans;

  private Hashtable<FlatEdge, FlatWriteDynamicVarNode> wdvNodesToSpliceIn;

  public static int maxSESEage = -1;


  // use these methods in BuildCode to have access to analysis results
  public FlatSESEEnterNode getRootSESE() {
    return rootSESE;
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

    // initialize analysis data structures
    allSESEs = new HashSet<FlatSESEEnterNode>();

    seseStacks           = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();
    livenessVirtualReads = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    variableResults      = new Hashtable< FlatNode, VarSrcTokTable           >();
    notAvailableResults  = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    codePlans            = new Hashtable< FlatNode, CodePlan                 >();

    wdvNodesToSpliceIn = new Hashtable<FlatEdge, FlatWriteDynamicVarNode>();


    FlatMethod fmMain = state.getMethodFlat( tu.getMain() );

    rootSESE = (FlatSESEEnterNode) fmMain.getNext(0);    
    rootSESE.setfmEnclosing( fmMain );
    rootSESE.setmdEnclosing( fmMain.getMethod() );
    rootSESE.setcdEnclosing( fmMain.getMethod().getClassDesc() );

    if( state.MLPDEBUG ) {      
      System.out.println( "" );
    }

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
    if( state.MLPDEBUG ) {      
      //System.out.println( "\nSESE Hierarchy\n--------------\n" ); printSESEHierarchy();
    }


    // 2nd pass, results are saved in FlatSESEEnterNode, so
    // intermediate results, for safety, are discarded
    livenessAnalysisBackward( rootSESE, true, null, fmMain.getFlatExit() );


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
    livenessAnalysisBackward( rootSESE, true, null, fmMain.getFlatExit() );        
    if( state.MLPDEBUG ) {      
      //System.out.println( "\nLive-In, SESE View\n-------------\n" ); printSESELiveness();
      //System.out.println( "\nLive-In, Root View\n------------------\n"+fmMain.printMethod( livenessRootView ) );
    }


    // 5th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // prune variable results in one traversal
      // by removing reference variables that are not live
      pruneVariableResultsWithLiveness( fm );
    }
    if( state.MLPDEBUG ) {      
      //System.out.println( "\nVariable Results-Out\n----------------\n"+fmMain.printMethod( variableResults ) );
    }
    

    // 6th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // compute what is not available at every program
      // point, in a forward fixed-point pass
      notAvailableForward( fm );
    }
    if( state.MLPDEBUG ) {      
      //System.out.println( "\nNot Available Results-Out\n---------------------\n"+fmMain.printMethod( notAvailableResults ) );
    }


    // 7th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // compute a plan for code injections
      computeStallsForward( fm );
    }
    if( state.MLPDEBUG ) {
      System.out.println( "\nCode Plans\n----------\n"+fmMain.printMethod( codePlans ) );
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

      allSESEs.add( fsen );
      fsen.setfmEnclosing( fm );
      fsen.setmdEnclosing( fm.getMethod() );
      fsen.setcdEnclosing( fm.getMethod().getClassDesc() );

      if( !seseStack.empty() ) {
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
      if( !seseStack.empty() ) {
	throw new Error( "Error: return statement enclosed within SESE "+
			 seseStack.peek().getPrettyIdentifier() );
      }
    } break;
      
    }
  }

  private void printSESEHierarchy() {
    // our forest is actually a tree now that
    // there is an implicit root SESE
    printSESEHierarchyTree( rootSESE, 0 );
    System.out.println( "" );
  }

  private void printSESEHierarchyTree( FlatSESEEnterNode fsen, int depth ) {
    for( int i = 0; i < depth; ++i ) {
      System.out.print( "  " );
    }
    System.out.println( "- "+fsen.getPrettyIdentifier() );

    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESEHierarchyTree( fsenChild, depth + 1 );
    }
  }


  private void livenessAnalysisBackward( FlatSESEEnterNode fsen, 
                                         boolean toplevel, 
                                         Hashtable< FlatSESEExitNode, Set<TempDescriptor> > liveout, 
                                         FlatExit fexit ) {

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped 
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();

    FlatSESEExitNode fsexn = fsen.getFlatExit();
    if (toplevel) {
	//handle root SESE
	flatNodesToVisit.add( fexit );
    } else
	flatNodesToVisit.add( fsexn );
    Hashtable<FlatNode, Set<TempDescriptor>> livenessResults=new Hashtable<FlatNode, Set<TempDescriptor>>();

    if (toplevel==true)
	liveout=new Hashtable<FlatSESEExitNode, Set<TempDescriptor>>();
    
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
    if( toplevel == true ) {
      livenessRootView = livenessResults;
    }

    // post-order traversal, so do children first
    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      livenessAnalysisBackward( fsenChild, false, liveout, null );
    }
  }

  private Set<TempDescriptor> liveness_nodeActions( FlatNode fn, 
                                                    Set<TempDescriptor> liveIn,
                                                    FlatSESEEnterNode currentSESE,
						    boolean toplevel,
						    Hashtable< FlatSESEExitNode, Set<TempDescriptor> > liveout ) {

    switch( fn.kind() ) {

    case FKind.FlatSESEExitNode:
      if (toplevel==true) {
	  FlatSESEExitNode exitn=(FlatSESEExitNode) fn;
	//update liveout set for FlatSESEExitNode
	  if (!liveout.containsKey(exitn))
	    liveout.put(exitn, new HashSet<TempDescriptor>());
	  liveout.get(exitn).addAll(liveIn);
      }
      // no break, sese exits should also execute default actions
      
    default: {
      // handle effects of statement in reverse, writes then reads
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
	liveIn.remove( writeTemps[i] );

	if (!toplevel) {
          FlatSESEExitNode exitnode=currentSESE.getFlatExit();
          Set<TempDescriptor> livetemps=liveout.get(exitnode);
          if (livetemps.contains(writeTemps[i])) {
            //write to a live out temp...
            //need to put in SESE liveout set
            currentSESE.addOutVar(writeTemps[i]);
          }
	}
      }

      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; ++i ) {
	liveIn.add( readTemps[i] );
      }

      Set<TempDescriptor> virtualReadTemps = livenessVirtualReads.get( fn );
      if( virtualReadTemps != null ) {
	Iterator<TempDescriptor> vrItr = virtualReadTemps.iterator();
	while( vrItr.hasNext() ) {
          TempDescriptor vrt = vrItr.next();
	  liveIn.add( vrt );
	}
      }
    } break;

    } // end switch

    return liveIn;
  }

  private void printSESELiveness() {
    // our forest is actually a tree now that
    // there is an implicit root SESE
    printSESELivenessTree( rootSESE );
    System.out.println( "" );
  }

  private void printSESELivenessTree( FlatSESEEnterNode fsen ) {

    System.out.println( "SESE "+fsen.getPrettyIdentifier()+" has in-set:" );
    Iterator<TempDescriptor> tItr = fsen.getInVarSet().iterator();
    while( tItr.hasNext() ) {
      System.out.println( "  "+tItr.next() );
    }
    System.out.println( "and out-set:" );
    tItr = fsen.getOutVarSet().iterator();
    while( tItr.hasNext() ) {
      System.out.println( "  "+tItr.next() );
    }
    System.out.println( "" );


    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESELivenessTree( fsenChild );
    }
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

      vstTable.ownInSet( currentSESE );
      vstTable.assertConsistency();
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode  fsexn = (FlatSESEExitNode)  fn;
      FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      assert currentSESE.getChildren().contains( fsen );
      vstTable.remapChildTokens( fsen );

      Set<TempDescriptor> liveIn       = currentSESE.getInVarSet();
      Set<TempDescriptor> virLiveIn    = vstTable.removeParentAndSiblingTokens( fsen, liveIn );
      Set<TempDescriptor> virLiveInOld = livenessVirtualReads.get( fn );
      if( virLiveInOld != null ) {
        virLiveIn.addAll( virLiveInOld );
      }
      livenessVirtualReads.put( fn, virLiveIn );
      vstTable.assertConsistency();

      // then all child out-set tokens are guaranteed
      // to be filled in, so clobber those entries with
      // the latest, clean sources
      Iterator<TempDescriptor> outVarItr = fsen.getOutVarSet().iterator();
      while( outVarItr.hasNext() ) {
        TempDescriptor outVar = outVarItr.next();
        HashSet<TempDescriptor> ts = new HashSet<TempDescriptor>();
        ts.add( outVar );
        VariableSourceToken vst = new VariableSourceToken( ts,
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

          forAddition.add( new VariableSourceToken( ts,
                                                    vst.getSESE(),
                                                    vst.getAge(),
                                                    vst.getAddrVar()
                                                    )
                           );
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
      
      // fix later, not working, only wanted it to make tables easier to read
      //vstTable.pruneByLiveness( rootLiveSet );
      
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

      Set<TempDescriptor> liveTemps = livenessRootView.get( fn );
      assert liveTemps != null;

      notAvailSet.addAll( liveTemps );
    } break;

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


  private void computeStallsForward( FlatMethod fm ) {
    
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
	computeStalls_nodeActions( fn, 
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

  private void computeStalls_nodeActions( FlatNode fn,
					  Set<TempDescriptor> liveSetIn,
                                          VarSrcTokTable vstTableIn,
					  Set<TempDescriptor> notAvailSetIn,
                                          FlatSESEEnterNode currentSESE ) {
    CodePlan plan = new CodePlan();


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

	// but check the source type of rhs variable
	// and if dynamic, lhs becomes dynamic, too,
	// and we need to keep dynamic sources during
	Integer srcType 
	  = vstTableIn.getRefVarSrcType( rhs,
					 currentSESE,
					 currentSESE.getParent() );

	if( srcType.equals( VarSrcTokTable.SrcType_DYNAMIC ) ) {
	  plan.addDynAssign( lhs, rhs );
	  currentSESE.addDynamicVar( lhs );
	  currentSESE.addDynamicVar( rhs );
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
	  // the other case for srcs is READY from a parent, however
	  // since we are only examining variables that come from
	  // children tokens, this should never occur
	  assert false;
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
    Set<VariableSourceToken> staticSet = vstTableIn.getStaticSet();
    Iterator<VariableSourceToken> vstItr = staticSet.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();
      currentSESE.addNeededStaticName( 
        new SESEandAgePair( vst.getSESE(), vst.getAge() ) 
				     );
      currentSESE.mustTrackAtLeastAge( vst.getAge() );
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
	  thisVstTable.getStatic2DynamicSet( nextVstTable, nextLiveIn );
	
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
}
