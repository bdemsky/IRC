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
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessResults;
  private Hashtable< FlatNode, VarSrcTokTable           > variableResults;
  private Hashtable< FlatNode, String                   > codePlan;


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
    seseStacks      = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();
    livenessResults = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    variableResults = new Hashtable< FlatNode, VarSrcTokTable           >();
    codePlan        = new Hashtable< FlatNode, String                   >();


    // build an implicit root SESE to wrap contents of main method
    rootTree = new SESENode( "root" );
    rootSESE = new FlatSESEEnterNode( rootTree );
    rootExit = new FlatSESEExitNode ( rootTree );
    rootSESE.setFlatExit ( rootExit );
    rootExit.setFlatEnter( rootSESE );


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


    // 2nd pass
    livenessAnalysisBackward( rootSESE );


    // 3rd pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

      // starting from roots do a forward, fixed-point
      // variable analysis for refinement and stalls
      variableAnalysisForward( fm );
    }


    // 4th pass
    methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d  = methItr.next();      
      FlatMethod fm = state.getMethodFlat( d );

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


  private void livenessAnalysisBackward( FlatSESEEnterNode fsen ) {
    
    // post-order traversal, so do children first
    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      livenessAnalysisBackward( fsenChild );
    }

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped 
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    FlatSESEExitNode fsexn = fsen.getFlatExit();
    flatNodesToVisit.add( fsexn );

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

      Set<TempDescriptor> curr = liveness_nodeActions( fn, u, fsen );

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
    
    if( state.MLPDEBUG ) { 
      System.out.println( "SESE "+fsen.getPrettyIdentifier()+" has in-set:" );
      Iterator<TempDescriptor> tItr = fsen.getInVarSet().iterator();
      while( tItr.hasNext() ) {
	System.out.println( "  "+tItr.next() );
      }
      System.out.println( "" );
    }
  }

  private Set<TempDescriptor> liveness_nodeActions( FlatNode fn, 
                                                    Set<TempDescriptor> liveIn,
                                                    FlatSESEEnterNode currentSESE ) {
    switch( fn.kind() ) {
      
    default: {
      // handle effects of statement in reverse, writes then reads
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
	liveIn.remove( writeTemps[i] );
      }

      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; ++i ) {
	liveIn.add( readTemps[i] );
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
	inUnion.merge( variableResults.get( nn ) );
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
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
      assert currentSESE.getChildren().contains( fsexn.getFlatEnter() );
      vstTable = vstTable.remapChildTokens( currentSESE );
      vstTable = vstTable.removeParentAndSiblingTokens( currentSESE );
    } break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;

      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	TempDescriptor lhs = fon.getDest();
	TempDescriptor rhs = fon.getLeft();

	vstTable.remove( lhs );

	Iterator<VariableSourceToken> itr = vstTable.get( rhs ).iterator();
	while( itr.hasNext() ) {
	  VariableSourceToken vst = itr.next();

          // if this is from a child, keep the source information
          if( currentSESE.getChildren().contains( vst.getSESE() ) ) {	  
            vstTable.add( new VariableSourceToken( lhs,
                                                   vst.getSESE(),
                                                   vst.getAge(),
                                                   vst.getVarSrc()
                                                   )
                          );

          // otherwise, it's our or an ancestor's token so we
          // can assume we have everything we need
          } else {
            vstTable.add( new VariableSourceToken( lhs,
                                                   currentSESE,
                                                   new Integer( 0 ),
                                                   lhs
                                                   )
                          );
          }
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
      if( writeTemps.length > 0 ) {
	assert writeTemps.length == 1;

	vstTable.remove( writeTemps[0] );

	vstTable.add( new VariableSourceToken( writeTemps[0],
					       currentSESE,
					       new Integer( 0 ),
					       writeTemps[0]
					     )
		      );
      }      
    } break;

    } // end switch

    return vstTable;
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
      System.out.println( fm.printMethod( codePlan ) );
    }
  }

  private void computeStalls_nodeActions( FlatNode fn,
                                          VarSrcTokTable vstTable,
                                          FlatSESEEnterNode currentSESE ) {
    String s = "no op";

    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;      
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
    } break;

    default: {
      Set<VariableSourceToken> stallSet = vstTable.getStallSet( currentSESE );
      if( !stallSet.isEmpty() ) {

	s = "STALL for:";

	Iterator<VariableSourceToken> itr = stallSet.iterator();
	while( itr.hasNext() ) {
	  VariableSourceToken vst = itr.next();
	  s += "  "+vst.getVarLive();
	}	
      }      
    } break;

    } // end switch

    codePlan.put( fn, s );
  }
}
