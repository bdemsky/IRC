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

  private Set<FlatSESEEnterNode> allSESEs;

  private Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessRootView;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessVirtualReads;
  private Hashtable< FlatNode, VarSrcTokTable           > variableResults;
  private Hashtable< FlatNode, Set<TempDescriptor>      > notAvailableResults;
  private Hashtable< FlatNode, CodePlan                 > codePlans;


  // use these methods in BuildCode to have access to analysis results
  public Set<FlatSESEEnterNode> getAllSESEs() {
    return allSESEs;
  }

  public CodePlan getCodePlan( FlatNode fn ) {
    CodePlan cp = codePlans.get( fn );
    assert cp != null;
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

    // initialize analysis data structures
    allSESEs = new HashSet<FlatSESEEnterNode>();

    seseStacks           = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();
    livenessVirtualReads = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    variableResults      = new Hashtable< FlatNode, VarSrcTokTable           >();
    notAvailableResults  = new Hashtable< FlatNode, Set<TempDescriptor>      >();
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

      // compute what is not available at every program
      // point, in a forward fixed-point pass
      notAvailableForward( fm );
    }


    // 5th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // compute a plan for code injections
      computeStallsForward( fm );
    }


    if( state.MLPDEBUG ) {      
      System.out.println( "" );
      //printSESEHierarchy();
      //printSESELiveness();
      //System.out.println( fmMain.printMethod( livenessRootView ) );
      //System.out.println( fmMain.printMethod( variableResults ) );
      //System.out.println( fmMain.printMethod( notAvailableResults ) );
      System.out.println( "CODE PLANS\n"+fmMain.printMethod( codePlans ) );
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
      fsen.setEnclosingFlatMeth( fm );

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
    System.out.println( fsen.getPrettyIdentifier() );

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

    return vstTable;
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

      Set<TempDescriptor> inUnion = new HashSet<TempDescriptor>();      
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );       
	Set<TempDescriptor> notAvailIn = notAvailableResults.get( nn );
        if( notAvailIn != null ) {
          inUnion.addAll( notAvailIn );
        }
      }

      Set<TempDescriptor> curr = notAvailable_nodeActions( fn, inUnion, seseStack.peek() );     

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

  private Set<TempDescriptor> notAvailable_nodeActions( FlatNode fn, 
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

      VarSrcTokTable vstTable = variableResults.get( fn );
      assert vstTable != null;

      Set<TempDescriptor> notAvailAtEnter = notAvailableResults.get( fsen );
      assert notAvailAtEnter != null;

      Iterator<TempDescriptor> tdItr = liveTemps.iterator();
      while( tdItr.hasNext() ) {
	TempDescriptor td = tdItr.next();

	if( vstTable.get( fsen, td ).size() > 0 ) {
	  // there is at least one child token for this variable
	  notAvailSet.add( td );
	  continue;
	}

	if( notAvailAtEnter.contains( td ) ) {
	  // wasn't available at enter, not available now
	  notAvailSet.add( td );
	  continue;
	}
      }
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

	// if this variable has exactly one source, mark everything
	// else from that source as available as well
	VarSrcTokTable table = variableResults.get( fn );
	Set<VariableSourceToken> srcs = table.get( rTemp );

	if( srcs.size() == 1 ) {
	  VariableSourceToken vst = srcs.iterator().next();
	  
	  Iterator<VariableSourceToken> availItr = table.get( vst.getSESE(), 
							      vst.getAge()
							    ).iterator();
	  while( availItr.hasNext() ) {
	    VariableSourceToken vstAlsoAvail = availItr.next();
	    notAvailSet.removeAll( vstAlsoAvail.getRefVars() );
	  }
	}
      }
    } break;

    } // end switch

    return notAvailSet;
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

      computeStalls_nodeActions( fn, dotSTtable, dotSTnotAvailSet, seseStack.peek() );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );
	}
      }
    }
  }

  private void computeStalls_nodeActions( FlatNode fn,
                                          VarSrcTokTable vstTable,
					  Set<TempDescriptor> notAvailSet,
                                          FlatSESEEnterNode currentSESE ) {
    CodePlan plan = new CodePlan();


    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;
      plan.setSESEtoIssue( fsen );
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
    } break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	// if this is an op node, don't stall, copy
	// source and delay until we need to use value

	// only break if this is an ASSIGN op node,
	// otherwise fall through to default case
	break;
      }
    }

    // note that FlatOpNode's that aren't ASSIGN
    // fall through to this default case
    default: {          
      // decide if we must stall for variables dereferenced at this node
      Set<VariableSourceToken> stallSet = vstTable.getStallSet( currentSESE );

      TempDescriptor[] readarray = fn.readsTemps();
      for( int i = 0; i < readarray.length; i++ ) {
        TempDescriptor readtmp = readarray[i];

	// ignore temps that are definitely available 
	// when considering to stall on it
	if( !notAvailSet.contains( readtmp ) ) {
	  continue;
	}

        Set<VariableSourceToken> readSet = vstTable.get( readtmp );

	//Two cases:

	//1) Multiple token/age pairs or unknown age: Stall for
	//dynamic name only.
	


	//2) Single token/age pair: Stall for token/age pair, and copy
	//all live variables with same token/age pair at the same
	//time.  This is the same stuff that the notavaialable analysis 
	//marks as now available.

	//VarSrcTokTable table = variableResults.get( fn );
	//Set<VariableSourceToken> srcs = table.get( rTemp );

	//XXXXXXXXXX: Note: We have to generate code to do these
	//copies in the codeplan.  Note we should only copy the
	//variables that are live!

	/*
	if( srcs.size() == 1 ) {
	  VariableSourceToken vst = srcs.iterator().next();
	  
	  Iterator<VariableSourceToken> availItr = table.get( vst.getSESE(), 
							      vst.getAge()
							    ).iterator();
	  while( availItr.hasNext() ) {
	    VariableSourceToken vstAlsoAvail = availItr.next();
	    notAvailSet.removeAll( vstAlsoAvail.getRefVars() );
	  }
	}
	*/


	// assert notAvailSet.containsAll( writeSet );

        /*
        for( Iterator<VariableSourceToken> readit = readSet.iterator(); 
             readit.hasNext(); ) {
          VariableSourceToken vst = readit.next();
          if( stallSet.contains( vst ) ) {
            if( before == null ) {
              before = "**STALL for:";
            }
            before += "("+vst+" "+readtmp+")";	    
          }
        }
        */
      }      
    } break;

    } // end switch


    // if any variable at this node has a static source (exactly one sese)
    // but goes to a dynamic source at a next node, write its dynamic addr      
    Set<VariableSourceToken> static2dynamicSet = new HashSet<VariableSourceToken>();
    for( int i = 0; i < fn.numNext(); i++ ) {
      FlatNode nn = fn.getNext( i );
      VarSrcTokTable nextVstTable = variableResults.get( nn );
      assert nextVstTable != null;
      static2dynamicSet.addAll( vstTable.getStatic2DynamicSet( nextVstTable ) );
    }
    /*
    Iterator<VariableSourceToken> vstItr = static2dynamicSet.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();
      if( after == null ) {
	after = "** Write dynamic: ";
      }
      after += "("+vst+")";
    }
    */

    codePlans.put( fn, plan );
  }
}
