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
  private State state;
  private TypeUtil typeUtil;
  private CallGraph callGraph;
  private OwnershipAnalysis ownAnalysis;

  private SESENode          rootTree;
  private FlatSESEEnterNode rootSESE;
  private FlatSESEExitNode  rootExit;

  private Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessRootView;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessVirtualReads;
  private Hashtable< FlatNode, VarSrcTokTable           > variableResults;
  private Hashtable< FlatNode, Set<TempDescriptor>      > isAvailableResults;
  private Hashtable< FlatNode, CodePlan                 > codePlans;


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

    // initialize analysis data structures
    seseStacks           = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();
    livenessVirtualReads = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    variableResults      = new Hashtable< FlatNode, VarSrcTokTable           >();
    isAvailableResults   = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    codePlans            = new Hashtable< FlatNode, CodePlan                 >();



    // build an implicit root SESE to wrap contents of main method
    rootTree = new SESENode( "root" );
    rootSESE = new FlatSESEEnterNode( rootTree );
    rootExit = new FlatSESEExitNode ( rootTree );
    rootSESE.setFlatExit ( rootExit );
    rootExit.setFlatEnter( rootSESE );

    FlatMethod fmMain = state.getMethodFlat( tu.getMain() );


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


    // 5th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // compute is available for every live variable at
      // every program point, in a forward fixed-point pass
      isAvailableForward( fm );
    }


    // 5th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // compute a plan for code injections
      computeStallsForward( fm );
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
    seseStackFirst.push( rootSESE );
    seseStacks.put( fm, seseStackFirst );

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      buildForest_nodeActions( fn, seseStack );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );

	  // clone stack and send along each analysis path
	  seseStacks.put( nn, (Stack<FlatSESEEnterNode>)seseStack.clone() );
	}
      }
    }      

    if( state.MLPDEBUG ) { 
      printSESEForest();
    }
  }

  private void buildForest_nodeActions( FlatNode fn, 							   
					Stack<FlatSESEEnterNode> seseStack ) {
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;      
      assert !seseStack.empty();
      seseStack.peek().addChild( fsen );
      fsen.setParent( seseStack.peek() );
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
	  !seseStack.peek().equals( rootSESE ) ) {
	throw new Error( "Error: return statement enclosed within "+seseStack.peek() );
      }
    } break;
      
    }
  }

  private void printSESEForest() {
    // our forest is actually a tree now that
    // there is an implicit root SESE
    printSESETree( rootSESE, 0 );
    System.out.println( "" );
  }

  private void printSESETree( FlatSESEEnterNode fsen, int depth ) {
    for( int i = 0; i < depth; ++i ) {
      System.out.print( "  " );
    }
    System.out.println( fsen.getPrettyIdentifier() );

    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESETree( fsenChild, depth + 1 );
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
      if(!curr.equals(prev)) {
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
    
    if( state.MLPDEBUG ) { 
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
      livenessAnalysisBackward( fsenChild, false, liveout, null);
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
      VarSrcTokTable inUnion = new VarSrcTokTable();
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );		
	VarSrcTokTable incoming = variableResults.get( nn );
	inUnion.merge( incoming );
      }

      VarSrcTokTable curr = variable_nodeActions( fn, inUnion, seseStack.peek() );     

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

  private VarSrcTokTable variable_nodeActions( FlatNode fn, 
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

      Set<TempDescriptor> liveIn       = currentSESE.getInVarSet();
      Set<TempDescriptor> virLiveIn    = vstTable.removeParentAndSiblingTokens( fsen, liveIn );
      Set<TempDescriptor> virLiveInOld = livenessVirtualReads.get( fn );
      if( virLiveInOld != null ) {
        virLiveIn.addAll( virLiveInOld );
      }
      livenessVirtualReads.put( fn, virLiveIn );
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

          // if this is from a child, keep the source information
          if( currentSESE.getChildren().contains( vst.getSESE() ) ) {	  
            forAddition.add( new VariableSourceToken( ts,
                                                      vst.getSESE(),
                                                      vst.getAge(),
                                                      vst.getAddrVar()
                                                      )
                             );

          // otherwise, it's our or an ancestor's token so we
          // can assume we have everything we need
          } else {
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
	assert writeTemps.length == 1;

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

    return vstTable;
  }


  private void isAvailableForward( FlatMethod fm ) {

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );	 

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      Set<TempDescriptor> prev = isAvailableResults.get( fn );

      // merge control flow joins where something is available
      // into this node only if it is available on every incoming
      // node, so do intersect of all incoming nodes with live in
      HashSet<TempDescriptor> rootLiveSet = (HashSet<TempDescriptor>) livenessRootView.get( fn );
      Set<TempDescriptor>     inIntersect = (Set<TempDescriptor>)     rootLiveSet.clone();
      
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );       
	Set<TempDescriptor> availIn = isAvailableResults.get( nn );
        if( availIn != null ) {
          inIntersect.retainAll( availIn );
        }
      }

      Set<TempDescriptor> curr = isAvailable_nodeActions( fn, inIntersect, seseStack.peek() );     

      // if a new result, schedule forward nodes for analysis
      if( !curr.equals( prev ) ) {
	isAvailableResults.put( fn, curr );

	for( int i = 0; i < fn.numNext(); i++ ) {
	  FlatNode nn = fn.getNext( i );	 
	  flatNodesToVisit.add( nn );	 
	}
      }
    }
  }

  private Set<TempDescriptor> isAvailable_nodeActions( FlatNode fn, 
						       Set<TempDescriptor> isAvailSet,
						       FlatSESEEnterNode currentSESE ) {
    // any temps that get added to the available set
    // at this node should be marked in this node's
    // code plan as temps to be grabbed at runtime!

    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      assert fsen.equals( currentSESE );
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode  fsexn = (FlatSESEExitNode)  fn;
      FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      assert currentSESE.getChildren().contains( fsen );
      isAvailSet.addAll( fsen.getOutVarSet() );
    } break;

    default: {
      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; i++ ) {
        TempDescriptor rTemp = readTemps[i];
        isAvailSet.add( rTemp );
      }
    } break;

    } // end switch

    return isAvailSet;
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
      VarSrcTokTable dotST = new VarSrcTokTable();
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );
	dotST.merge( variableResults.get( nn ) );
      }

      computeStalls_nodeActions( fn, dotST, seseStack.peek() );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );
	}
      }
    }

    if( state.MLPDEBUG ) { 
      //System.out.println( fm.printMethod( livenessRootView ) );
      //System.out.println( fm.printMethod( variableResults ) );
      //System.out.println( fm.printMethod( isAvailableResults ) );
      System.out.println( fm.printMethod( codePlans ) );
    }
  }

  private void computeStalls_nodeActions( FlatNode fn,
                                          VarSrcTokTable vstTable,
                                          FlatSESEEnterNode currentSESE ) {
    String before = null;
    String after  = null;

    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;      
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
    } break;

    default: {          
      // decide if we must stall for variables 
      // dereferenced at this node
      Set<VariableSourceToken> stallSet = vstTable.getStallSet( currentSESE );
      TempDescriptor[] readarray = fn.readsTemps();
      for( int i = 0; i < readarray.length; i++ ) {
        Set<VariableSourceToken> forRemoval = new HashSet<VariableSourceToken>();
        TempDescriptor readtmp = readarray[i];
        Set<VariableSourceToken> readSet = vstTable.get( readtmp );
        //containsAny
        for( Iterator<VariableSourceToken> readit = readSet.iterator(); 
             readit.hasNext(); ) {
          VariableSourceToken vst = readit.next();
          if( stallSet.contains( vst ) ) {
            if( before == null ) {
              before = "**STALL for:";
            }
            before += "("+vst+" "+readtmp+")";

            // mark any other variables from the static SESE for
            // removal, because after this stall we'll have them
            // all for later statements
            forRemoval.addAll( vstTable.get( vst.getSESE(), 
                                             vst.getAge() 
                                           ) 
                             );
          }
        }

        Iterator<VariableSourceToken> vstItr = forRemoval.iterator();
        while( vstItr.hasNext() ) {
          VariableSourceToken vst = vstItr.next();
          vstTable.remove( vst );
        }
      }

      // if any variable at this node has a static source (exactly one sese)
      // but goes to a dynamic source at a next node, write its dynamic addr      
      Set<VariableSourceToken> static2dynamicSet = new HashSet<VariableSourceToken>();
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );
        VarSrcTokTable nextVstTable = variableResults.get( nn );
        assert nextVstTable != null;
        static2dynamicSet.addAll( vstTable.getStatic2DynamicSet( nextVstTable ) );
      }
      Iterator<VariableSourceToken> vstItr = static2dynamicSet.iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vst = vstItr.next();
        if( after == null ) {
          after = "** Write dynamic: ";
        }
        after += "("+vst+")";
      }
      
    } break;

    } // end switch
    if( before == null ) {
      before = "";
    }

    if( after == null ) {
      after = "";
    }

    codePlans.put( fn, new CodePlan( before, after ) );
  }
}
